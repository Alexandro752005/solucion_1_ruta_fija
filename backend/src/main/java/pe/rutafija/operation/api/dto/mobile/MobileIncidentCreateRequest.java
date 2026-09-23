package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.operation.domain.IncidentCategory;

import java.time.Instant;
import java.util.UUID;

public record MobileIncidentCreateRequest(
        UUID assignmentId,
        @NotNull IncidentCategory category,
        @NotBlank @Size(max = 1000) String description,
        @NotNull Instant occurredAt
) {
}
