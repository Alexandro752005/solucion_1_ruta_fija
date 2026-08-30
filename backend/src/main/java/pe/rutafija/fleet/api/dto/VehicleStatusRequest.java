package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.NotNull;
import pe.rutafija.fleet.domain.VehicleStatus;

public record VehicleStatusRequest(@NotNull VehicleStatus status) {
}
