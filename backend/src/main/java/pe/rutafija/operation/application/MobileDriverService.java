package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.DriverVehicleLink;
import pe.rutafija.fleet.infrastructure.DriverVehicleLinkRepository;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityRequest;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityResponse;
import pe.rutafija.operation.api.dto.mobile.MobileDriverProfileResponse;
import pe.rutafija.operation.api.dto.mobile.MobileLocationConsentRequest;
import pe.rutafija.operation.api.dto.mobile.MobileLocationUpdateRequest;
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
    private final DriverVehicleLinkRepository driverVehicleLinkRepository;
    private final DriverCurrentLocationStore currentLocationStore;
    private final MobileOperationProperties properties;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public MobileDriverService(
            MobileDriverContextService mobileDriverContextService,
            DriverVehicleLinkRepository driverVehicleLinkRepository,
            DriverCurrentLocationStore currentLocationStore,
            MobileOperationProperties properties,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.driverVehicleLinkRepository = driverVehicleLinkRepository;
        this.currentLocationStore = currentLocationStore;
        this.properties = properties;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public MobileDriverProfileResponse profile() {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
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
        Driver driver = actor.driver();
        if (request.status() != DriverAvailabilityStatus.DISPONIBLE
                && request.status() != DriverAvailabilityStatus.DESCANSO
                && request.status() != DriverAvailabilityStatus.NO_DISPONIBLE) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.AVAILABILITY_INVALID_TRANSITION,
                    "RESERVADO y EN_SERVICIO solo cambian por una asignacion"
            );
        }
        try {
            driver.changeAdministrativeAvailability(request.status());
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
        auditService.record(actor.user(), "MOBILE_DRIVER_AVAILABILITY_CHANGED", "DRIVER", driver.getId(), Map.of(
                "status", driver.getAvailabilityStatus().name()
        ));
        eventPublisher.publish(actor.user().getOrganizationId(), driver.getGroup().getId(), "driver.status.changed", Map.of(
                "driverId", driver.getId().toString(),
                "status", driver.getAvailabilityStatus().name()
        ));
        return availabilityResponse(driver);
    }

    @Transactional
    public MobileDriverProfileResponse changeLocationConsent(MobileLocationConsentRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Driver driver = actor.driver();
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
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Driver driver = actor.driver();
        if (!driver.isLocationConsent()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.LOCATION_CONSENT_REQUIRED,
                    "Debe otorgar consentimiento funcional antes de enviar ubicacion"
            );
        }
        if (!Boolean.TRUE.equals(request.permissionGranted())) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.LOCATION_PERMISSION_NOT_REPORTED,
                    "La aplicacion movil debe confirmar el permiso del sistema operativo"
            );
        }
        if (!allowsCurrentLocation(driver)) {
            currentLocationStore.deleteByDriverId(driver.getId());
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.LOCATION_STATE_NOT_ALLOWED,
                    "La ubicacion vigente solo se admite en DISPONIBLE o EN_SERVICIO"
            );
        }

        Instant receivedAt = Instant.now(clock);
        if (request.capturedAt().isAfter(receivedAt.plus(properties.locationTtl()))
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
                request.latitude().setScale(6, RoundingMode.HALF_UP),
                request.longitude().setScale(6, RoundingMode.HALF_UP),
                request.accuracyM().setScale(2, RoundingMode.HALF_UP),
                request.capturedAt(),
                receivedAt,
                receivedAt.plus(properties.locationTtl())
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
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        currentLocationStore.deleteByDriverId(actor.driver().getId());
        auditService.record(actor.user(), "MOBILE_CURRENT_LOCATION_CLEARED", "DRIVER", actor.driver().getId(), Map.of());
        eventPublisher.publish(actor.user().getOrganizationId(), actor.driver().getGroup().getId(), "driver.location.cleared", Map.of(
                "driverId", actor.driver().getId().toString()
        ));
    }

    private boolean allowsCurrentLocation(Driver driver) {
        return driver.getAvailabilityStatus() == DriverAvailabilityStatus.DISPONIBLE
                || driver.getAvailabilityStatus() == DriverAvailabilityStatus.EN_SERVICIO;
    }

    private MobileAvailabilityResponse availabilityResponse(Driver driver) {
        return new MobileAvailabilityResponse(driver.getId(), driver.getAvailabilityStatus());
    }
}
