package pe.rutafija.fleet.api.dto;

import pe.rutafija.fleet.domain.DriverVehicleLink;
import pe.rutafija.fleet.domain.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverVehicleResponse(
        UUID vehicleId,
        String plate,
        String brand,
        String model,
        VehicleStatus status,
        boolean primary,
        Instant linkedAt
) {
    public static DriverVehicleResponse from(DriverVehicleLink link) {
        return new DriverVehicleResponse(
                link.getVehicle().getId(),
                link.getVehicle().getPlate(),
                link.getVehicle().getBrand(),
                link.getVehicle().getModel(),
                link.getVehicle().getStatus(),
                link.isPrimary(),
                link.getLinkedAt()
        );
    }
}
