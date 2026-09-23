package pe.rutafija.operation.api.dto.mobile;

import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

import java.util.UUID;

public record MobileAvailabilityResponse(UUID driverId, DriverAvailabilityStatus status) {
}
