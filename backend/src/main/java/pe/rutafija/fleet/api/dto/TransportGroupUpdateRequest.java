package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TransportGroupUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 300) String description,
        @NotNull Boolean active
) {
}
