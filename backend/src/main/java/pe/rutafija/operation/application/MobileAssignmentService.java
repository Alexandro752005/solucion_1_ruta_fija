package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.VehicleStatus;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.mobile.MobileAssignmentCommandRequest;
import pe.rutafija.operation.api.dto.mobile.MobileAssignmentRejectRequest;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.domain.MobileCommandReceipt;
import pe.rutafija.operation.domain.MobileCommandType;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.DriverCurrentLocationStore;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.MobileDriverActor;
import pe.rutafija.shared.security.MobileDriverContextService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Own-assignment commands. A CRM cannot call these routes or impersonate a conductor response. */
@Service
public class MobileAssignmentService {

    private final MobileDriverContextService mobileDriverContextService;
    private final AssignmentRepository assignmentRepository;
    private final DriverCurrentLocationStore currentLocationStore;
    private final MobileCommandProcessor commandProcessor;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public MobileAssignmentService(
            MobileDriverContextService mobileDriverContextService,
            AssignmentRepository assignmentRepository,
            DriverCurrentLocationStore currentLocationStore,
            MobileCommandProcessor commandProcessor,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.assignmentRepository = assignmentRepository;
        this.currentLocationStore = currentLocationStore;
        this.commandProcessor = commandProcessor;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> list(AssignmentStatus status, Pageable pageable) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Page<Assignment> assignments = assignmentRepository.findMobileAssignments(
                actor.user().getOrganizationId(), actor.driver().getId(), status, pageable
        );
        return PageResponse.from(assignments, AssignmentResponse::from);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse get(UUID assignmentId) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        return AssignmentResponse.from(findOwnAssignment(actor, assignmentId));
    }

    // A rejected offline retry must retain its durable receipt. The mutation
    // paths validate before changing fleet state, while expiry itself is an
    // intentional server-side state transition that must also be committed.
    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileCommandExecution<AssignmentResponse> accept(
            UUID assignmentId,
            MobileAssignmentCommandRequest request
    ) {
        return executeCommand(
                assignmentId,
                request.clientEventId(),
                request.version(),
                request.occurredAt(),
                null,
                MobileCommandType.ASSIGNMENT_ACCEPT,
                (assignment, now) -> {
                    normalizeExpiredIfDue(assignment, now);
                    assertVersion(assignment, request.version());
                    if (!assignment.getVehicle().isActive()
                            || assignment.getVehicle().getStatus() != VehicleStatus.DISPONIBLE) {
                        throw new ApplicationException(
                                HttpStatus.CONFLICT,
                                ErrorCode.VEHICLE_NOT_ELIGIBLE,
                                "El vehiculo ya no esta disponible para la asignacion"
                        );
                    }
                    try {
                        assignment.requirePendingMobileResponse(now);
                        assignment.getDriver().reserveForAssignment();
                        assignment.acceptMobileResponse(now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    return persistAndPublish(assignment, "ASSIGNMENT_MOBILE_ACCEPTED", "assignment.accepted");
                }
        );
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileCommandExecution<AssignmentResponse> reject(
            UUID assignmentId,
            MobileAssignmentRejectRequest request
    ) {
        return executeCommand(
                assignmentId,
                request.clientEventId(),
                request.version(),
                request.occurredAt(),
                request.reason(),
                MobileCommandType.ASSIGNMENT_REJECT,
                (assignment, now) -> {
                    normalizeExpiredIfDue(assignment, now);
                    assertVersion(assignment, request.version());
                    try {
                        assignment.rejectMobileResponse(request.reason(), now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    return persistAndPublish(assignment, "ASSIGNMENT_MOBILE_REJECTED", "assignment.rejected");
                }
        );
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileCommandExecution<AssignmentResponse> start(
            UUID assignmentId,
            MobileAssignmentCommandRequest request
    ) {
        return executeCommand(
                assignmentId,
                request.clientEventId(),
                request.version(),
                request.occurredAt(),
                null,
                MobileCommandType.ASSIGNMENT_START,
                (assignment, now) -> {
                    normalizeExpiredIfDue(assignment, now);
                    assertVersion(assignment, request.version());
                    assertCanStart(assignment);
                    try {
                        assignment.getDriver().beginService();
                        assignment.getVehicle().beginService();
                        assignment.start(now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    return persistAndPublish(assignment, "ASSIGNMENT_MOBILE_STARTED", "assignment.started");
                }
        );
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileCommandExecution<AssignmentResponse> complete(
            UUID assignmentId,
            MobileAssignmentCommandRequest request
    ) {
        return executeCommand(
                assignmentId,
                request.clientEventId(),
                request.version(),
                request.occurredAt(),
                null,
                MobileCommandType.ASSIGNMENT_COMPLETE,
                (assignment, now) -> {
                    normalizeExpiredIfDue(assignment, now);
                    assertVersion(assignment, request.version());
                    assertCanComplete(assignment);
                    try {
                        assignment.getDriver().completeService();
                        assignment.getVehicle().finishService();
                        assignment.complete(now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    return persistAndPublish(assignment, "ASSIGNMENT_MOBILE_COMPLETED", "assignment.completed");
                }
        );
    }

    private MobileCommandExecution<AssignmentResponse> executeCommand(
            UUID assignmentId,
            UUID eventId,
            Long version,
            Instant occurredAt,
            String rejectionReason,
            MobileCommandType commandType,
            AssignmentMutation mutation
    ) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Assignment receiptAssignment = findOwnAssignment(actor, assignmentId);
        String requestHash = commandProcessor.hash(canonical(
                commandType, assignmentId, version, occurredAt, rejectionReason
        ));
        return commandProcessor.execute(
                actor,
                eventId,
                occurredAt,
                commandType,
                receiptAssignment,
                requestHash,
                receipt -> AssignmentResponse.from(findOwnAssignment(actor, receipt.getAssignment().getId())),
                () -> mutation.apply(lockOwnAssignment(actor, assignmentId), Instant.now(clock)),
                response -> response.status().name()
        );
    }

    private AssignmentResponse persistAndPublish(Assignment assignment, String auditAction, String eventName) {
        Assignment persisted = assignmentRepository.saveAndFlush(assignment);
        if (persisted.getDriver().getAvailabilityStatus() != DriverAvailabilityStatus.DISPONIBLE
                && persisted.getDriver().getAvailabilityStatus() != DriverAvailabilityStatus.EN_SERVICIO) {
            currentLocationStore.deleteByDriverId(persisted.getDriver().getId());
        }
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        auditService.record(actor.user(), auditAction, "ASSIGNMENT", persisted.getId(), Map.of(
                "status", persisted.getStatus().name(),
                "responseMode", persisted.getResponseMode().name()
        ));
        eventPublisher.publish(
                persisted.getOrganizationId(),
                persisted.getDriver().getGroup().getId(),
                eventName,
                assignmentEventData(persisted)
        );
        eventPublisher.publish(
                persisted.getOrganizationId(),
                persisted.getDriver().getGroup().getId(),
                "driver.status.changed",
                Map.of(
                        "driverId", persisted.getDriver().getId().toString(),
                        "status", persisted.getDriver().getAvailabilityStatus().name()
                )
        );
        if (persisted.getStatus() == AssignmentStatus.EN_SERVICIO
                || persisted.getStatus() == AssignmentStatus.COMPLETED) {
            eventPublisher.publish(
                    persisted.getOrganizationId(),
                    persisted.getDriver().getGroup().getId(),
                    "vehicle.status.changed",
                    Map.of(
                            "vehicleId", persisted.getVehicle().getId().toString(),
                            "status", persisted.getVehicle().getStatus().name()
                    )
            );
        }
        return AssignmentResponse.from(persisted);
    }

    private void normalizeExpiredIfDue(Assignment assignment, Instant now) {
        if (assignment.getStatus() == AssignmentStatus.EXPIRED) {
            throw responseExpired();
        }
        if (!assignment.expireIfDue(now)) {
            return;
        }
        Assignment expired = assignmentRepository.saveAndFlush(assignment);
        auditService.recordSystem(
                expired.getOrganization(),
                "ASSIGNMENT_RESPONSE_EXPIRED",
                "ASSIGNMENT",
                expired.getId(),
                Map.of("responseMode", expired.getResponseMode().name())
        );
        eventPublisher.publish(
                expired.getOrganizationId(),
                expired.getDriver().getGroup().getId(),
                "assignment.expired",
                assignmentEventData(expired)
        );
        throw responseExpired();
    }

    private ApplicationException responseExpired() {
        return new ApplicationException(
                HttpStatus.CONFLICT,
                ErrorCode.ASSIGNMENT_RESPONSE_EXPIRED,
                "El plazo de respuesta de la asignacion vencio"
        );
    }

    private void assertCanStart(Assignment assignment) {
        if (assignment.getStatus() != AssignmentStatus.SCHEDULED || assignment.getReservedAt() == null) {
            throw mobileTransition("La asignacion debe estar reservada antes de iniciar");
        }
        if (assignment.getDriver().getAvailabilityStatus() != DriverAvailabilityStatus.RESERVADO) {
            throw mobileTransition("El conductor debe estar RESERVADO antes de iniciar");
        }
        if (!assignment.getVehicle().isActive() || assignment.getVehicle().getStatus() != VehicleStatus.DISPONIBLE) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.VEHICLE_NOT_ELIGIBLE,
                    "El vehiculo debe estar DISPONIBLE para iniciar"
            );
        }
    }

    private void assertCanComplete(Assignment assignment) {
        if (assignment.getStatus() != AssignmentStatus.EN_SERVICIO
                || assignment.getDriver().getAvailabilityStatus() != DriverAvailabilityStatus.EN_SERVICIO
                || assignment.getVehicle().getStatus() != VehicleStatus.EN_SERVICIO) {
            throw mobileTransition("La asignacion y sus recursos deben estar EN_SERVICIO para completar");
        }
    }

    private void assertVersion(Assignment assignment, Long expectedVersion) {
        if (expectedVersion == null || assignment.getVersion() != expectedVersion) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    "La asignacion fue modificada; actualice los datos antes de reintentar"
            );
        }
    }

    private Assignment findOwnAssignment(MobileDriverActor actor, UUID assignmentId) {
        return assignmentRepository.findMobileAssignment(
                        assignmentId, actor.user().getOrganizationId(), actor.driver().getId()
                )
                .orElseThrow(this::notFound);
    }

    private Assignment lockOwnAssignment(MobileDriverActor actor, UUID assignmentId) {
        return assignmentRepository.lockMobileAssignment(
                        assignmentId, actor.user().getOrganizationId(), actor.driver().getId()
                )
                .orElseThrow(this::notFound);
    }

    private String canonical(
            MobileCommandType commandType,
            UUID assignmentId,
            Long version,
            Instant occurredAt,
            String rejectionReason
    ) {
        String normalizedReason = rejectionReason == null ? "" : rejectionReason.strip();
        return commandType.name() + "|" + assignmentId + "|" + version + "|" + occurredAt + "|" + normalizedReason;
    }

    private ApplicationException mobileTransition(String message) {
        return new ApplicationException(
                HttpStatus.CONFLICT,
                ErrorCode.ASSIGNMENT_INVALID_TRANSITION,
                message
        );
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe"
        );
    }

    private Map<String, Object> assignmentEventData(Assignment assignment) {
        return Map.of(
                "assignmentId", assignment.getId().toString(),
                "status", assignment.getStatus().name(),
                "driverId", assignment.getDriver().getId().toString(),
                "vehicleId", assignment.getVehicle().getId().toString(),
                "version", assignment.getVersion()
        );
    }

    @FunctionalInterface
    private interface AssignmentMutation {
        AssignmentResponse apply(Assignment assignment, Instant now);
    }
}
