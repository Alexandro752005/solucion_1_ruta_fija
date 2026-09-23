package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/** Client event metadata; transition time and authority are supplied by the server. */
public record MobileAssignmentCommandRequest(
        @NotNull UUID clientEventId,
        @NotNull @PositiveOrZero Long version,
        @NotNull Instant occurredAt
) {
}
