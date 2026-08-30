package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IncidentFollowUpRequest(
        @NotNull @Min(0) Long version,
        @NotBlank @Size(max = 1000) String note,
        boolean resolve
) {
}
