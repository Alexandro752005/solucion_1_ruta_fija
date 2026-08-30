package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignmentVersionRequest(@NotNull @Min(0) Long version) {
}
