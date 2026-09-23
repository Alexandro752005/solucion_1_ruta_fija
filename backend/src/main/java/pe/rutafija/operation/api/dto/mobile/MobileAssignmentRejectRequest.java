package pe.rutafija.operation.api.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Rechazo móvil idempotente de una solicitud propia MOBILE_CONFIRMATION.")
public record MobileAssignmentRejectRequest(
        @Schema(description = "UUID global y durable del evento de rechazo.")
        @NotNull UUID clientEventId,
        @Schema(description = "Versión optimista recibida de la asignación.", example = "0")
        @NotNull @PositiveOrZero Long version,
        @Schema(description = "Hora ISO-8601 declarada por el dispositivo.")
        @NotNull Instant occurredAt,
        @Schema(description = "Motivo opcional de hasta 300 caracteres.")
        @Size(max = 300) String reason
) {
}
