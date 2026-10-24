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
    private final pe.rutafija.qualification.application.OperationalQualificationPolicy qualification;
    private final OrganizationSchedulingLock scheduling;
    private final pe.rutafija.routing.application.RouteDeclarationService declarations;
    private final jakarta.persistence.EntityManager entityManager;
    private final OfferSchedulingPolicy marginPolicy;
    private final ServiceExecution execution;
    private final ServiceHandover handovers;

    public MobileAssignmentService(
            MobileDriverContextService mobileDriverContextService,
            AssignmentRepository assignmentRepository,
            DriverCurrentLocationStore currentLocationStore,
            MobileCommandProcessor commandProcessor,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock,
            pe.rutafija.qualification.application.OperationalQualificationPolicy qualification,
            OrganizationSchedulingLock scheduling,
            pe.rutafija.routing.application.RouteDeclarationService declarations,
            jakarta.persistence.EntityManager entityManager,
            OfferSchedulingPolicy marginPolicy,
            ServiceExecution execution,
            ServiceHandover handovers
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.assignmentRepository = assignmentRepository;
        this.currentLocationStore = currentLocationStore;
        this.commandProcessor = commandProcessor;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.qualification = qualification;
        this.scheduling = scheduling;
        this.declarations = declarations;
        this.entityManager = entityManager;
        this.marginPolicy = marginPolicy;
        this.execution = execution;
        this.handovers = handovers;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> list(AssignmentStatus status, Pageable pageable) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        Page<Assignment> assignments = assignmentRepository.findMobileAssignments(
                actor.user().getOrganizationId(), actor.driver().getId(), status, pageable
        );
        return PageResponse.from(assignments, AssignmentResponse::from);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse get(UUID assignmentId) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
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
                    marginPolicy.requireCaptured(assignment);
                    if(assignment.getDriver().getDeclaredAvailabilityStatus()!=DriverAvailabilityStatus.DISPONIBLE)
                        throw mobileTransition("La aceptación requiere disponibilidad declarada vigente.");
                    qualification.require(assignment.getDriver().getId(),assignment.getOrganizationId(),assignment.getVehicle().getId(),assignment.getScheduledAt(),assignment.getScheduledEndAt());
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
                        if(assignment.getDriver().getAvailabilityStatus()==DriverAvailabilityStatus.RESERVADO
                            &&!marginPolicy.hasOtherConfirmed(assignment.getOrganizationId(),assignment.getDriver().getId(),assignment.getId()))
                            throw mobileTransition("El estado reservado no tiene un compromiso compatible acreditado; consulta la ocupación vigente.");
                        if(assignment.getDriver().getAvailabilityStatus()!=DriverAvailabilityStatus.RESERVADO)
                            assignment.getDriver().reserveForAssignment();
                        assignment.acceptMobileResponse(now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    return persistAndPublish(assignment,AssignmentStatus.PENDING_RESPONSE,"ASSIGNMENT_MOBILE_ACCEPTED", "assignment.accepted");
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
                    return persistAndPublish(assignment,AssignmentStatus.PENDING_RESPONSE,"ASSIGNMENT_MOBILE_REJECTED", "assignment.rejected");
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
                request.gps()==null?null:request.gps().canonical(),
                (assignment, now) -> {
                    normalizeExpiredIfDue(assignment, now);
                    assertVersion(assignment, request.version());
                    marginPolicy.requireCaptured(assignment);
                    qualification.requireStart(assignment.getDriver().getId(),assignment.getOrganizationId(),assignment.getVehicle().getId(),assignment.getScheduledAt(),assignment.getScheduledEndAt());
                    if(assignment.getHandoverId()!=null)throw mobileTransition("La oferta de relevo requiere Asumir relevo; no inicia por la acción ordinaria.");
                    if (assignment.getAcceptedAt() == null || assignment.getResponseMode() != pe.rutafija.operation.domain.AssignmentResponseMode.MOBILE_CONFIRMATION)
                        throw mobileTransition("El inicio requiere aceptación auténtica de la oferta por CONDUCTOR.");
                    assertCanStart(assignment);
                    execution.requireStart(assignment,request.gps());
                    try {
                        assignment.getDriver().beginService();
                        assignment.getVehicle().beginService();
                        assignment.start(now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    assignmentRepository.flush();
                    execution.recordStart(assignment,mobileDriverContextService.requireMobileConsultation(),request.clientEventId(),now);
                    currentLocationStore.deleteByDriverId(assignment.getDriver().getId());
                    return persistAndPublish(assignment,AssignmentStatus.SCHEDULED,"ASSIGNMENT_MOBILE_STARTED", "assignment.started");
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
                    if(assignment.getAcceptedAt()==null||assignment.getStartedAt()==null)throw mobileTransition("Ruta finalizada requiere aceptación auténtica e inicio del propio servicio.");
                    execution.requireCompletion(assignment);
                    if(assignment.getServiceId()!=null)handovers.closePendingOnCompletion(assignment,mobileDriverContextService.requireMobileConsultation(),now);
                    try {
                        assignment.getDriver().completeService();
                        assignment.getVehicle().finishService();
                        assignment.complete(now);
                    } catch (IllegalStateException exception) {
                        throw mobileTransition(exception.getMessage());
                    }
                    assignmentRepository.flush();
                    declarations.record(assignment,mobileDriverContextService.requireMobileConsultation(),now);
                    execution.recordCompletion(assignment,mobileDriverContextService.requireMobileConsultation(),request.clientEventId(),now);
                    currentLocationStore.deleteByDriverId(assignment.getDriver().getId());
                    if(!qualification.eligible(assignment.getDriver().getId(),assignment.getOrganizationId(),true))
                        assignment.getDriver().changeAdministrativeAvailability(DriverAvailabilityStatus.NO_DISPONIBLE);
                    else if(marginPolicy.hasOtherConfirmed(assignment.getOrganizationId(),assignment.getDriver().getId(),assignment.getId()))
                        assignment.getDriver().reserveForAssignment();
                    return persistAndPublish(assignment,AssignmentStatus.EN_SERVICIO,"ASSIGNMENT_MOBILE_COMPLETED", "assignment.completed");
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
        return executeCommand(assignmentId,eventId,version,occurredAt,rejectionReason,commandType,null,mutation);
    }

    private MobileCommandExecution<AssignmentResponse> executeCommand(
            UUID assignmentId, UUID eventId, Long version, Instant occurredAt, String rejectionReason,
            MobileCommandType commandType, String executionProof, AssignmentMutation mutation
    ) {
        MobileDriverActor actor = commandType==MobileCommandType.ASSIGNMENT_COMPLETE
                ?mobileDriverContextService.requireMobileConsultation():mobileDriverContextService.requireMobileDriver();
        scheduling.acquire(actor.user().getOrganizationId());
        entityManager.refresh(actor.driver(),jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        Assignment receiptAssignment = findOwnAssignment(actor, assignmentId);
        String requestHash = commandProcessor.hash(canonical(
                commandType, assignmentId, version, occurredAt, rejectionReason
        )+(executionProof==null?"":"|GPS|"+executionProof));
        return commandProcessor.execute(
                actor,
                eventId,
                occurredAt,
                commandType,
                receiptAssignment,
                requestHash,
                receipt -> AssignmentResponse.from(findOwnAssignment(actor, receipt.getAssignment().getId())),
                () -> receiptAssignment.getStatus().name(),
                () -> mutation.apply(lockOwnAssignment(actor, assignmentId), Instant.now(clock)),
                response -> response.status().name()
        );
    }

    private AssignmentResponse persistAndPublish(Assignment assignment,AssignmentStatus previousStatus,String auditAction,String eventName) {
        Assignment persisted = assignmentRepository.saveAndFlush(assignment);
        if (persisted.getDriver().getAvailabilityStatus() != DriverAvailabilityStatus.DISPONIBLE
                && persisted.getDriver().getAvailabilityStatus() != DriverAvailabilityStatus.EN_SERVICIO) {
            currentLocationStore.deleteByDriverId(persisted.getDriver().getId());
        }
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        auditService.record(actor.user(), auditAction, "ASSIGNMENT", persisted.getId(), Map.of(
                "status", persisted.getStatus().name(),
                "mode", persisted.getResponseMode().name(),"changes",pe.rutafija.audit.application.AuditChanges.fields("status",previousStatus,persisted.getStatus())
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
