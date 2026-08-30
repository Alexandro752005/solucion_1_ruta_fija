package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record AssignmentUpdateRequest(
        @NotNull @Min(0) Long version,
        @NotNull UUID driverId,
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 250) String originText,
        @NotBlank @Size(max = 250) String destinationText,
        @NotNull Instant scheduledAt,
        @NotNull Instant scheduledEndAt,
        @Size(max = 500) String notes
) {
}
