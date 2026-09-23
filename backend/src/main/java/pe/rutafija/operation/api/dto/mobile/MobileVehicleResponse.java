package pe.rutafija.operation.api.dto.mobile;

import pe.rutafija.fleet.domain.VehicleStatus;

import java.util.UUID;

public record MobileVehicleResponse(UUID id, String plate, VehicleStatus status) {
}
