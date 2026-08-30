package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.operation.domain.IncidentCategory;

import java.util.UUID;

public record IncidentCreateRequest(
        @NotNull UUID driverId,
        UUID assignmentId,
        @NotNull IncidentCategory category,
        @NotBlank @Size(max = 1000) String description
) {
}
