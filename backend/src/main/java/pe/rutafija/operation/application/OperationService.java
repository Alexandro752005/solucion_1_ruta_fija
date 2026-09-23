package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.api.dto.DriverResponse;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.domain.VehicleStatus;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.DriverVehicleLinkRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.AssignmentCancelRequest;
import pe.rutafija.operation.api.dto.AssignmentCreateRequest;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.AssignmentUpdateRequest;
import pe.rutafija.operation.api.dto.AssignmentVersionRequest;
import pe.rutafija.operation.api.dto.DriverAvailabilityRequest;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.AssignmentResponseMode;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.DriverCurrentLocationStore;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class OperationService {

    private static final List<AssignmentStatus> SCHEDULING_STATUSES = List.of(
            AssignmentStatus.PENDING_RESPONSE,
            AssignmentStatus.SCHEDULED,
            AssignmentStatus.EN_SERVICIO
    );

    private final AssignmentRepository assignmentRepository;
    private final DriverCurrentLocationStore currentLocationStore;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverVehicleLinkRepository driverVehicleLinkRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public OperationService(
            AssignmentRepository assignmentRepository,
            DriverCurrentLocationStore currentLocationStore,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            DriverVehicleLinkRepository driverVehicleLinkRepository,
            CurrentUserService currentUserService,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.assignmentRepository = assignmentRepository;
        this.currentLocationStore = currentLocationStore;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverVehicleLinkRepository = driverVehicleLinkRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AssignmentResponse> listAssignments(
            UUID driverId,
            UUID vehicleId,
            AssignmentStatus status,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        AppUser actor = requireOperationalActor();
        if (driverId != null) {
            findTenantDriver(driverId, actor.getOrganizationId());
        }
        Page<Assignment> assignments = assignmentRepository.search(
                actor.getOrganizationId(), driverId, vehicleId, status, from, to, pageable
        );
        return PageResponse.from(assignments, AssignmentResponse::from);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(UUID assignmentId) {
        AppUser actor = requireOperationalActor();
        Assignment assignment = findTenantAssignment(assignmentId, actor.getOrganizationId());
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentCreationResult createAssignment(AssignmentCreateRequest request, String idempotencyKey) {
        AppUser actor = requireOperationalActor();
        AssignmentResponseMode responseMode = request.responseMode() == null
                ? AssignmentResponseMode.ADMIN_DIRECT
                : request.responseMode();
        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey != null) {
            Assignment existing = assignmentRepository
                    .findByOrganization_IdAndIdempotencyKey(actor.getOrganizationId(), normalizedKey)
                    .orElse(null);
            if (existing != null) {
                return new AssignmentCreationResult(AssignmentResponse.from(existing), false);
            }
        }

        Driver driver = findTenantDriver(request.driverId(), actor.getOrganizationId());
        Vehicle vehicle = findTenantVehicle(request.vehicleId(), actor.getOrganizationId());
        validateSchedulingEligibility(driver, vehicle, request.scheduledAt(), request.scheduledEndAt());
        Instant now = Instant.now(clock);
        validateResponseMode(driver, responseMode, request.responseDeadlineAt(), request.scheduledAt(), now);
        assertNoSchedulingConflict(
                actor.getOrganizationId(),
                driver.getId(),
                vehicle.getId(),
                request.scheduledAt(),
                request.scheduledEndAt(),
                null
        );

        Assignment assignment = responseMode == AssignmentResponseMode.MOBILE_CONFIRMATION
                ? Assignment.requestMobileConfirmation(
                        actor.getOrganization(), driver, vehicle, actor,
                        request.originText(), request.destinationText(), request.scheduledAt(), request.scheduledEndAt(),
                        request.notes(), normalizedKey, request.responseDeadlineAt()
                )
                : Assignment.schedule(
                        actor.getOrganization(), driver, vehicle, actor,
                        request.originText(), request.destinationText(), request.scheduledAt(), request.scheduledEndAt(),
                        request.notes(), normalizedKey
                );
        assignment = assignmentRepository.saveAndFlush(assignment);
        auditService.record(actor,
                responseMode == AssignmentResponseMode.MOBILE_CONFIRMATION
                        ? "ASSIGNMENT_MOBILE_CONFIRMATION_REQUESTED"
                        : "ASSIGNMENT_SCHEDULED",
                "ASSIGNMENT", assignment.getId(), Map.of(
                "driverId", driver.getId().toString(),
                "vehicleId", vehicle.getId().toString(),
                "responseMode", responseMode.name()
        ));
        eventPublisher.publish(
                actor.getOrganizationId(),
                assignment.getDriver().getGroup().getId(),
                responseMode == AssignmentResponseMode.MOBILE_CONFIRMATION
                        ? "assignment.pending-response"
                        : "assignment.scheduled",
                assignmentEventData(assignment)
        );
        return new AssignmentCreationResult(AssignmentResponse.from(assignment), true);
    }

    @Transactional
    public AssignmentResponse updateAssignment(UUID assignmentId, AssignmentUpdateRequest request) {
        AppUser actor = requireOperationalActor();
        Assignment assignment = findTenantAssignment(assignmentId, actor.getOrganizationId());
        assertVersion(assignment, request.version());
        Driver driver = findTenantDriver(request.driverId(), actor.getOrganizationId());
        Vehicle vehicle = findTenantVehicle(request.vehicleId(), actor.getOrganizationId());
        validateSchedulingEligibility(driver, vehicle, request.scheduledAt(), request.scheduledEndAt());
        assertNoSchedulingConflict(
                actor.getOrganizationId(),
                driver.getId(),
                vehicle.getId(),
                request.scheduledAt(),
                request.scheduledEndAt(),
                assignment.getId()
        );

        try {
            assignment.updateSchedule(
                    driver,
                    vehicle,
                    request.originText(),
                    request.destinationText(),
                    request.scheduledAt(),
                    request.scheduledEndAt(),
                    request.notes()
            );
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        assignment = assignmentRepository.saveAndFlush(assignment);
        auditService.record(actor, "ASSIGNMENT_UPDATED", "ASSIGNMENT", assignment.getId(), Map.of());
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "assignment.updated", assignmentEventData(assignment));
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse reserveAssignment(UUID assignmentId, AssignmentVersionRequest request) {
        AppUser actor = requireOperationalActor();
        Assignment assignment = findTenantAssignment(assignmentId, actor.getOrganizationId());
        assertVersion(assignment, request.version());
        ensureVehicleOperationalForReservation(assignment.getVehicle());
        try {
            assignment.getDriver().reserveForAssignment();
            assignment.reserve(Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        assignment = assignmentRepository.saveAndFlush(assignment);
        auditService.record(actor, "ASSIGNMENT_RESERVED", "ASSIGNMENT", assignment.getId(), Map.of());
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "assignment.reserved", assignmentEventData(assignment));
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "driver.status.changed", driverEventData(assignment.getDriver()));
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse startAssignment(UUID assignmentId, AssignmentVersionRequest request) {
        AppUser actor = requireOperationalActor();
        Assignment assignment = findTenantAssignment(assignmentId, actor.getOrganizationId());
        assertVersion(assignment, request.version());
        try {
            assignment.getDriver().beginService();
            assignment.getVehicle().beginService();
            assignment.start(Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        assignment = assignmentRepository.saveAndFlush(assignment);
        auditService.record(actor, "ASSIGNMENT_STARTED", "ASSIGNMENT", assignment.getId(), Map.of());
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "assignment.started", assignmentEventData(assignment));
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "driver.status.changed", driverEventData(assignment.getDriver()));
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "vehicle.status.changed", vehicleEventData(assignment.getVehicle()));
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse completeAssignment(UUID assignmentId, AssignmentVersionRequest request) {
        AppUser actor = requireOperationalActor();
        Assignment assignment = findTenantAssignment(assignmentId, actor.getOrganizationId());
        assertVersion(assignment, request.version());
        try {
            assignment.getDriver().completeService();
            assignment.getVehicle().finishService();
            assignment.complete(Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        assignment = assignmentRepository.saveAndFlush(assignment);
        auditService.record(actor, "ASSIGNMENT_COMPLETED", "ASSIGNMENT", assignment.getId(), Map.of());
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "assignment.completed", assignmentEventData(assignment));
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "driver.status.changed", driverEventData(assignment.getDriver()));
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "vehicle.status.changed", vehicleEventData(assignment.getVehicle()));
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public AssignmentResponse cancelAssignment(UUID assignmentId, AssignmentCancelRequest request) {
        AppUser actor = requireOperationalActor();
        Assignment assignment = findTenantAssignment(assignmentId, actor.getOrganizationId());
        assertVersion(assignment, request.version());
        AssignmentStatus previousStatus = assignment.getStatus();
        boolean driverChanged = false;
        boolean vehicleChanged = false;
        try {
            if (previousStatus == AssignmentStatus.SCHEDULED && assignment.getReservedAt() != null) {
                assignment.getDriver().releaseReservation();
                driverChanged = true;
            } else if (previousStatus == AssignmentStatus.EN_SERVICIO) {
                assignment.getDriver().completeService();
                assignment.getVehicle().finishService();
                driverChanged = true;
                vehicleChanged = true;
            }
            assignment.cancel(request.reason(), Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        assignment = assignmentRepository.saveAndFlush(assignment);
        auditService.record(actor, "ASSIGNMENT_CANCELLED", "ASSIGNMENT", assignment.getId(), Map.of(
                "reason", request.reason().strip()
        ));
        eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "assignment.cancelled", assignmentEventData(assignment));
        if (driverChanged) {
            eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "driver.status.changed", driverEventData(assignment.getDriver()));
        }
        if (vehicleChanged) {
            eventPublisher.publish(actor.getOrganizationId(), assignment.getDriver().getGroup().getId(), "vehicle.status.changed", vehicleEventData(assignment.getVehicle()));
        }
        return AssignmentResponse.from(assignment);
    }

    @Transactional
    public DriverResponse changeDriverAvailability(UUID driverId, DriverAvailabilityRequest request) {
        AppUser actor = requireOperationalActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        try {
            driver.changeAdministrativeAvailability(request.status());
        } catch (IllegalStateException exception) {
            throw invalidTransition(exception.getMessage());
        }
        if (driver.getAvailabilityStatus() != DriverAvailabilityStatus.DISPONIBLE
                && driver.getAvailabilityStatus() != DriverAvailabilityStatus.EN_SERVICIO) {
            currentLocationStore.deleteByDriverId(driver.getId());
        }
        auditService.record(actor, "DRIVER_AVAILABILITY_CHANGED", "DRIVER", driver.getId(), Map.of(
                "status", driver.getAvailabilityStatus().name()
        ));
        eventPublisher.publish(actor.getOrganizationId(), driver.getGroup().getId(), "driver.status.changed", driverEventData(driver));
        return DriverResponse.from(driver);
    }

    private AppUser requireOperationalActor() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La operación web requiere el rol ADMIN"
            );
        }
        return actor;
    }

    private void validateSchedulingEligibility(
            Driver driver,
            Vehicle vehicle,
            Instant scheduledAt,
            Instant scheduledEndAt
    ) {
        if (!driver.isActive()) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.DRIVER_NOT_ELIGIBLE,
                    "El conductor no está activo"
            );
        }
        if (!vehicle.isActive() || vehicle.getStatus() != VehicleStatus.DISPONIBLE) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VEHICLE_NOT_ELIGIBLE,
                    "El vehículo debe estar activo y DISPONIBLE para programarlo"
            );
        }
        if (!driverVehicleLinkRepository.existsByDriver_IdAndVehicle_IdAndActiveTrue(driver.getId(), vehicle.getId())) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VEHICLE_NOT_ELIGIBLE,
                    "El vehículo debe estar vinculado al conductor seleccionado"
            );
        }
        if (scheduledAt == null || scheduledEndAt == null || !scheduledEndAt.isAfter(scheduledAt)) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "El fin programado debe ser posterior al inicio programado"
            );
        }
    }

    private void validateResponseMode(
            Driver driver,
            AssignmentResponseMode responseMode,
            Instant responseDeadlineAt,
            Instant scheduledAt,
            Instant now
    ) {
        if (responseMode == AssignmentResponseMode.ADMIN_DIRECT) {
            if (responseDeadlineAt != null) {
                throw new ApplicationException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_ERROR,
                        "ADMIN_DIRECT no admite un plazo de respuesta mÃ³vil"
                );
            }
            return;
        }
        if (driver.getUser() == null
                || !driver.getUser().isActive()
                || driver.getUser().getRole() != UserRole.CONDUCTOR) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.DRIVER_NOT_ELIGIBLE,
                    "MOBILE_CONFIRMATION requiere un conductor activo con usuario CONDUCTOR vinculado"
            );
        }
        if (responseDeadlineAt == null
                || !responseDeadlineAt.isAfter(now)
                || !responseDeadlineAt.isBefore(scheduledAt)) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "El plazo de respuesta debe ser posterior al reloj del servidor y anterior al inicio programado"
            );
        }
    }

    private void ensureVehicleOperationalForReservation(Vehicle vehicle) {
        if (!vehicle.isActive() || vehicle.getStatus() != VehicleStatus.DISPONIBLE) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VEHICLE_NOT_ELIGIBLE,
                    "El vehículo debe estar activo y DISPONIBLE para reservar la asignación"
            );
        }
    }

    private void assertNoSchedulingConflict(
            UUID organizationId,
            UUID driverId,
            UUID vehicleId,
            Instant scheduledAt,
            Instant scheduledEndAt,
            UUID assignmentId
    ) {
        boolean conflict = assignmentId == null
                ? assignmentRepository.existsSchedulingConflict(
                        organizationId,
                        driverId,
                        vehicleId,
                        SCHEDULING_STATUSES,
                        scheduledAt,
                        scheduledEndAt
                )
                : assignmentRepository.existsSchedulingConflictExcluding(
                        organizationId,
                        assignmentId,
                        driverId,
                        vehicleId,
                        SCHEDULING_STATUSES,
                        scheduledAt,
                        scheduledEndAt
                );
        if (conflict) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.ASSIGNMENT_SCHEDULE_CONFLICT,
                    "El conductor o vehículo ya tiene una asignación en ese intervalo"
            );
        }
    }

    private Assignment findTenantAssignment(UUID assignmentId, UUID organizationId) {
        return assignmentRepository.findByIdAndOrganization_Id(assignmentId, organizationId)
                .orElseThrow(this::notFound);
    }

    private Driver findTenantDriver(UUID driverId, UUID organizationId) {
        return driverRepository.findByIdAndOrganization_Id(driverId, organizationId)
                .orElseThrow(this::notFound);
    }

    private Vehicle findTenantVehicle(UUID vehicleId, UUID organizationId) {
        return vehicleRepository.findByIdAndOrganization_Id(vehicleId, organizationId)
                .orElseThrow(this::notFound);
    }

    private void assertVersion(Assignment assignment, Long requestVersion) {
        if (requestVersion == null || assignment.getVersion() != requestVersion) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    "La asignación fue modificada por otro usuario; actualice la información"
            );
        }
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > 100) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "Idempotency-Key no puede superar 100 caracteres"
            );
        }
        return normalized;
    }

    private ApplicationException invalidTransition(String message) {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.ASSIGNMENT_INVALID_TRANSITION,
                message == null || message.isBlank()
                        ? "La transición operativa no está permitida"
                        : message
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

    private Map<String, Object> driverEventData(Driver driver) {
        return Map.of(
                "driverId", driver.getId().toString(),
                "status", driver.getAvailabilityStatus().name()
        );
    }

    private Map<String, Object> vehicleEventData(Vehicle vehicle) {
        return Map.of(
                "vehicleId", vehicle.getId().toString(),
                "status", vehicle.getStatus().name()
        );
    }
}
