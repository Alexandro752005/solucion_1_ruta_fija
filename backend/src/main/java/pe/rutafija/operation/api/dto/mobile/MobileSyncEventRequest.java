package pe.rutafija.operation.api.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Typed, minimal offline event. Cross-field validation is enforced by the
 * application service because each event type has a different aggregate.
 */
@Schema(description = "Evento móvil permitido en la cola offline limitada.")
public record MobileSyncEventRequest(
        @NotNull UUID eventId,
        @NotNull MobileSyncEventType type,
        UUID assignmentId,
        @NotNull Instant occurredAt,
        @PositiveOrZero Long version,
        DriverAvailabilityStatus targetAvailability,
        Instant expectedAvailabilityUpdatedAt
) {
}
