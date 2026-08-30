package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VehicleCreateRequest(
        @NotBlank @Size(max = 15) String plate,
        @Size(max = 60) String brand,
        @Size(max = 60) String model,
        @Min(1900) @Max(2100) Short year,
        @Size(max = 40) String color
) {
}
