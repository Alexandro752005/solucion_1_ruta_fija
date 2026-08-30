package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.NotNull;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

public record DriverAvailabilityRequest(@NotNull DriverAvailabilityStatus status) {
}
