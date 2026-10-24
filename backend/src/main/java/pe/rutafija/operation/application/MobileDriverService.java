package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.DriverVehicleLink;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.DriverVehicleLinkRepository;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityRequest;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityResponse;
import pe.rutafija.operation.api.dto.mobile.MobileDriverProfileResponse;
import pe.rutafija.operation.api.dto.mobile.MobileLocationConsentRequest;
import pe.rutafija.operation.api.dto.mobile.MobileLocationUpdateRequest;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventRequest;
import pe.rutafija.operation.domain.MobileCommandType;
import pe.rutafija.operation.config.MobileOperationProperties;
import pe.rutafija.operation.infrastructure.DriverCurrentLocationStore;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.MobileDriverActor;
import pe.rutafija.shared.security.MobileDriverContextService;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Own-profile, availability and privacy-preserving current-location operations. */
@Service
public class MobileDriverService {

    private final MobileDriverContextService mobileDriverContextService;
    private final DriverRepository driverRepository;
    private final DriverVehicleLinkRepository driverVehicleLinkRepository;
    private final DriverCurrentLocationStore currentLocationStore;
    private final MobileOperationProperties properties;
    private final MobileCommandProcessor commandProcessor;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;
    private final OrganizationSchedulingLock scheduling;
    private final pe.rutafija.operation.infrastructure.AssignmentRepository assignments;
    private final pe.rutafija.qualification.application.OperationalQualificationPolicy qualification;
    private final jakarta.persistence.EntityManager entityManager;
    private final ServiceExecution execution;

    public MobileDriverService(
            MobileDriverContextService mobileDriverContextService,
            DriverRepository driverRepository,
            DriverVehicleLinkRepository driverVehicleLinkRepository,
            DriverCurrentLocationStore currentLocationStore,
            MobileOperationProperties properties,
            MobileCommandProcessor commandProcessor,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock,
            OrganizationSchedulingLock scheduling,
            pe.rutafija.operation.infrastructure.AssignmentRepository assignments,
            pe.rutafija.qualification.application.OperationalQualificationPolicy qualification,
            jakarta.persistence.EntityManager entityManager,
            ServiceExecution execution
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.driverRepository = driverRepository;
        this.driverVehicleLinkRepository = driverVehicleLinkRepository;
        this.currentLocationStore = currentLocationStore;
        this.properties = properties;
        this.commandProcessor = commandProcessor;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.scheduling = scheduling; this.assignments = assignments; this.qualification = qualification;
        this.entityManager = entityManager;
        this.execution = execution;
    }

    @Transactional(readOnly = true)
    public MobileDriverProfileResponse profile() {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        List<DriverVehicleLink> links = driverVehicleLinkRepository.findAllByDriver_IdAndActiveTrue(actor.driver().getId());
        return MobileDriverProfileResponse.from(actor.driver(), links);
    }

    @Transactional(readOnly = true)
    public MobileAvailabilityResponse availability() {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        return availabilityResponse(actor.driver());
    }

    @Transactional
    public MobileAvailabilityResponse changeAvailability(MobileAvailabilityRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        return applyAvailabilityChange(actor, lockOwnDriver(actor), request.status());
    }

    /**
     * Applies a bounded outbox availability event under the same durable receipt
     * mechanism as assignment commands. The updatedAt token prevents a stale
     * device intention from overwriting a later administrative change.
     */
    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileCommandExecution<MobileAvailabilityResponse> syncAvailability(MobileSyncEventRequest event) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Driver driver = lockOwnDriver(actor);
        String requestHash = commandProcessor.hash(
                MobileCommandType.AVAILABILITY_CHANGE.name()
                        + "|" + driver.getId()
                        + "|" + event.occurredAt()
                        + "|" + event.targetAvailability()
                        + "|" + event.expectedAvailabilityUpdatedAt()
        );
        return commandProcessor.execute(
                actor,
                event.eventId(),
                event.occurredAt(),
                MobileCommandType.AVAILABILITY_CHANGE,
                null,
                requestHash,
                receipt -> new MobileAvailabilityResponse(
                        driver.getId(),
                        DriverAvailabilityStatus.valueOf(receipt.getServerState()),
                        driver.getUpdatedAt()
                ),
                () -> driver.getAvailabilityStatus().name(),
                () -> {
                    if (!driver.getUpdatedAt().equals(event.expectedAvailabilityUpdatedAt())) {
                        throw new ApplicationException(
                                HttpStatus.CONFLICT,
                                ErrorCode.RESOURCE_VERSION_CONFLICT,
                                "La disponibilidad cambio; actualice antes de conciliar"
                        );
                    }
                    return applyAvailabilityChange(actor, driver, event.targetAvailability());
                },
                response -> response.status().name()
        );
    }

    @Transactional
    public MobileDriverProfileResponse changeLocationConsent(MobileLocationConsentRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        Driver driver = lockOwnDriver(actor);
        try {
            driver.recordLocationConsent(request.consent());
        } catch (IllegalStateException exception) {
            throw new ApplicationException(HttpStatus.FORBIDDEN, ErrorCode.DRIVER_INACTIVE, "El conductor no esta activo");
        }
        if (!request.consent()) {
            currentLocationStore.deleteByDriverId(driver.getId());
        }
        auditService.record(actor.user(), "MOBILE_LOCATION_CONSENT_CHANGED", "DRIVER", driver.getId(), Map.of(
                "granted", request.consent()
        ));
        List<DriverVehicleLink> links = driverVehicleLinkRepository.findAllByDriver_IdAndActiveTrue(driver.getId());
        return MobileDriverProfileResponse.from(driver, links);
    }

    @Transactional
    public void upsertCurrentLocation(MobileLocationUpdateRequest request) {
        // A loss of operational qualification while driving must not fabricate a loss of GPS evidence.
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        Driver driver = lockOwnDriver(actor);
        if (!driver.isLocationConsent()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.LOCATION_CONSENT_REQUIRED,
                    "Debe otorgar consentimiento funcional antes de enviar ubicacion"
            );
        }
        if (!Boolean.TRUE.equals(request.permissionGranted()) || !Boolean.TRUE.equals(request.locationServiceEnabled())
                || !Boolean.TRUE.equals(request.foreground())) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.LOCATION_PERMISSION_NOT_REPORTED,
                    "La aplicación debe declarar permiso, ubicación habilitada y primer plano"
            );
        }
        if (!allowsCurrentLocation(driver)) {
            currentLocationStore.deleteByDriverId(driver.getId());
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.LOCATION_STATE_NOT_ALLOWED,
                    "La ubicación solo se admite durante un servicio propio iniciado"
            );
        }

        Instant receivedAt = Instant.now(clock);
        var ongoing=assignments.findMobileAssignments(driver.getOrganizationId(),driver.getId(),
            pe.rutafija.operation.domain.AssignmentStatus.EN_SERVICIO,org.springframework.data.domain.PageRequest.of(0,2)).getContent();
        var segment=ongoing.size()==1?execution.active(ongoing.getFirst()):null;
        if(segment==null)
            throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.LOCATION_STATE_NOT_ALLOWED,"No hay un tramo propio en ejecución; no se guarda seguimiento histórico inferido.");
        if (request.capturedAt().isAfter(receivedAt)
                || request.capturedAt().isBefore(segment.startedAt())
                || request.capturedAt().isBefore(receivedAt.minus(properties.locationCaptureMaxAge()))) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.LOCATION_CAPTURE_TOO_OLD,
                    "La hora de captura no esta dentro de la tolerancia permitida"
            );
        }
        currentLocationStore.upsert(
                driver.getId(),
                driver.getOrganizationId(),
                segment.id(),
                request.latitude().setScale(6, RoundingMode.HALF_UP),
                request.longitude().setScale(6, RoundingMode.HALF_UP),
                request.accuracyM().setScale(2, RoundingMode.HALF_UP),
                request.capturedAt(),
                receivedAt,
                receivedAt.plus(properties.locationTtl()).isBefore(request.capturedAt().plus(java.time.Duration.ofDays(7)))
                    ?receivedAt.plus(properties.locationTtl()):request.capturedAt().plus(java.time.Duration.ofDays(7))
        );
        // No coordinates, accuracy or client timestamp enter audit metadata or WebSocket payloads.
        auditService.record(actor.user(), "MOBILE_CURRENT_LOCATION_UPSERTED", "DRIVER", driver.getId(), Map.of(
                "source", "MOBILE_APP"
        ));
        eventPublisher.publish(actor.user().getOrganizationId(), driver.getGroup().getId(), "driver.location.updated", Map.of(
                "driverId", driver.getId().toString()
        ));
    }

    @Transactional
    public void deleteCurrentLocation() {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        lockOwnDriver(actor);
        currentLocationStore.deleteByDriverId(actor.driver().getId());
        auditService.record(actor.user(), "MOBILE_CURRENT_LOCATION_CLEARED", "DRIVER", actor.driver().getId(), Map.of());
        eventPublisher.publish(actor.user().getOrganizationId(), actor.driver().getGroup().getId(), "driver.location.cleared", Map.of(
                "driverId", actor.driver().getId().toString()
        ));
    }

    private boolean allowsCurrentLocation(Driver driver) {
        return driver.getAvailabilityStatus() == DriverAvailabilityStatus.EN_SERVICIO;
    }

    private MobileAvailabilityResponse availabilityResponse(Driver driver) {
        boolean commitments=assignments.existsByDriver_IdAndStatusIn(driver.getId(),List.of(pe.rutafija.operation.domain.AssignmentStatus.SCHEDULED,pe.rutafija.operation.domain.AssignmentStatus.EN_SERVICIO));
        return new MobileAvailabilityResponse(driver.getId(), driver.getAvailabilityStatus(), driver.getUpdatedAt(),
                driver.getDeclaredAvailabilityStatus(),driver.getDeclaredAvailabilityAt(),!commitments
                && driver.getAvailabilityStatus()!=DriverAvailabilityStatus.RESERVADO && driver.getAvailabilityStatus()!=DriverAvailabilityStatus.EN_SERVICIO && driver.getAvailabilityStatus()!=DriverAvailabilityStatus.ATENCION);
    }

    private Driver lockOwnDriver(MobileDriverActor actor) {
        scheduling.acquire(actor.user().getOrganizationId());
        Driver driver=driverRepository.lockByIdAndOrganizationId(actor.driver().getId(), actor.user().getOrganizationId())
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "El conductor de la sesion no existe"
                ));
        // The identity lookup may have cached Driver before waiting for the
        // scheduling lock. Reload current state before applying an intention.
        entityManager.refresh(driver,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return driver;
    }

    private MobileAvailabilityResponse applyAvailabilityChange(
            MobileDriverActor actor,
            Driver driver,
            DriverAvailabilityStatus requested
    ) {
        if (requested != DriverAvailabilityStatus.DISPONIBLE
                && requested != DriverAvailabilityStatus.DESCANSO
                && requested != DriverAvailabilityStatus.NO_DISPONIBLE) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AVAILABILITY_INVALID_TRANSITION,
                    "RESERVADO y EN_SERVICIO solo cambian por una asignacion"
            );
        }
        if (requested != DriverAvailabilityStatus.DISPONIBLE && assignments.existsByDriver_IdAndStatusIn(driver.getId(),
                List.of(pe.rutafija.operation.domain.AssignmentStatus.SCHEDULED,pe.rutafija.operation.domain.AssignmentStatus.EN_SERVICIO)))
            throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.AVAILABILITY_INVALID_TRANSITION,"La declaración no puede invalidar compromisos aceptados; coordina su resolución expresa.");
        if (requested == DriverAvailabilityStatus.DISPONIBLE && !qualification.eligible(driver.getId(),driver.getOrganizationId(),true))
            throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.DRIVER_NOT_ELIGIBLE,"La declaración no concede habilitación operativa.");
        try {
            driver.declareAvailability(requested,Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AVAILABILITY_INVALID_TRANSITION,
                    "La transicion de disponibilidad no esta permitida"
            );
        }
        if (!allowsCurrentLocation(driver)) {
            currentLocationStore.deleteByDriverId(driver.getId());
        }
        Driver persisted = driverRepository.saveAndFlush(driver);
        auditService.record(actor.user(), "MOBILE_DRIVER_AVAILABILITY_CHANGED", "DRIVER", persisted.getId(), Map.of(
                "status", persisted.getAvailabilityStatus().name()
        ));
        eventPublisher.publish(actor.user().getOrganizationId(), persisted.getGroup().getId(), "driver.status.changed", Map.of(
                "driverId", persisted.getId().toString(),
                "status", persisted.getAvailabilityStatus().name()
        ));
        return availabilityResponse(persisted);
    }
}
