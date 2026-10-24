package pe.rutafija.operation.api.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.UUID;

/** Client event metadata; transition time and authority are supplied by the server. */
@Schema(
        description = "Comando propio online con recibo idempotente. Ante respuesta incierta consulte el estado; "
                + "no hay cola offline ni reenvío automático. Si se consulta el recibo usando el comando, debe ser idéntico."
)
public record MobileAssignmentCommandRequest(
        @Schema(description = "UUID global y durable del evento. No se genera uno nuevo en un reintento.")
        @NotNull UUID clientEventId,
        @Schema(description = "Versión optimista recibida de la asignación.", example = "0")
        @NotNull @PositiveOrZero Long version,
        @Schema(description = "Hora ISO-8601 declarada por el dispositivo; el servidor conserva su propia hora.")
        @NotNull Instant occurredAt,
        @Schema(description = "Obligatorio al iniciar: permiso, servicio de ubicación y primer plano declarados por el cliente.")
        MobileGpsStartProof gps
) {
    public MobileAssignmentCommandRequest(UUID clientEventId, Long version, Instant occurredAt) {
        this(clientEventId,version,occurredAt,null);
    }
}
