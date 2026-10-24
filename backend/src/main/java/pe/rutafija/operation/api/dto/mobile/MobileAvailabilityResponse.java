package pe.rutafija.operation.api.dto.mobile;

import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record MobileAvailabilityResponse(
        UUID driverId,
        DriverAvailabilityStatus status,
        Instant updatedAt,
        DriverAvailabilityStatus declaredStatus,
        Instant declaredAt,
        boolean canChangeDeclaration
) {
    public MobileAvailabilityResponse(UUID driverId, DriverAvailabilityStatus status, Instant updatedAt) {
        this(driverId,status,updatedAt,null,null,status!=DriverAvailabilityStatus.RESERVADO&&status!=DriverAvailabilityStatus.EN_SERVICIO);
    }
}
