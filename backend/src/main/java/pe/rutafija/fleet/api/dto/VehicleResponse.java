package pe.rutafija.fleet.api.dto;

import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.domain.VehicleStatus;

import java.time.Instant;
import java.util.UUID;

public record VehicleResponse(
        UUID id,
        String plate,
        String brand,
        String model,
        Short year,
        String color,
        VehicleStatus status,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static VehicleResponse from(Vehicle vehicle) {
        return new VehicleResponse(
                vehicle.getId(),
                vehicle.getPlate(),
                vehicle.getBrand(),
                vehicle.getModel(),
                vehicle.getYear(),
                vehicle.getColor(),
                vehicle.getStatus(),
                vehicle.isActive(),
                vehicle.getCreatedAt(),
                vehicle.getUpdatedAt()
        );
    }
}
