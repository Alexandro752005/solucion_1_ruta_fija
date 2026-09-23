package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.operation.domain.AssignmentResponseMode;

import java.time.Instant;
import java.util.UUID;

public record AssignmentCreateRequest(
        @NotNull UUID driverId,
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 250) String originText,
        @NotBlank @Size(max = 250) String destinationText,
        @NotNull Instant scheduledAt,
        @NotNull Instant scheduledEndAt,
        @Size(max = 500) String notes,
        AssignmentResponseMode responseMode,
        Instant responseDeadlineAt
) {
}
