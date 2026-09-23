package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record MobileAssignmentRejectRequest(
        @NotNull UUID clientEventId,
        @NotNull @PositiveOrZero Long version,
        @NotNull Instant occurredAt,
        @Size(max = 300) String reason
) {
}
