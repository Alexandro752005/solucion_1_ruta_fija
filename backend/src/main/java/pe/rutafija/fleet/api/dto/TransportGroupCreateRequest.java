package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TransportGroupCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 300) String description
) {
}
