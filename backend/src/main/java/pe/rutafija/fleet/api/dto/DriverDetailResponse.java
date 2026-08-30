package pe.rutafija.fleet.api.dto;

import java.util.List;

public record DriverDetailResponse(
        DriverResponse driver,
        List<DriverVehicleResponse> vehicles
) {
    public static DriverDetailResponse of(DriverResponse driver, List<DriverVehicleResponse> vehicles) {
        return new DriverDetailResponse(driver, List.copyOf(vehicles));
    }
}
