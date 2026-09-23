package pe.rutafija.operation.api.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/** Client event metadata; transition time and authority are supplied by the server. */
@Schema(
        description = "Comando móvil idempotente. Reintente exactamente el mismo clientEventId, version y occurredAt "
                + "hasta conocer el resultado; el servidor conserva un recibo durable."
)
public record MobileAssignmentCommandRequest(
        @Schema(description = "UUID global y durable del evento. No se genera uno nuevo en un reintento.")
        @NotNull UUID clientEventId,
        @Schema(description = "Versión optimista recibida de la asignación.", example = "0")
        @NotNull @PositiveOrZero Long version,
        @Schema(description = "Hora ISO-8601 declarada por el dispositivo; el servidor conserva su propia hora.")
        @NotNull Instant occurredAt
) {
}
