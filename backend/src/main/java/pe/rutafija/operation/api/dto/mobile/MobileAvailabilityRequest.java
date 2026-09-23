package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.NotNull;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

public record MobileAvailabilityRequest(@NotNull DriverAvailabilityStatus status) {
}
