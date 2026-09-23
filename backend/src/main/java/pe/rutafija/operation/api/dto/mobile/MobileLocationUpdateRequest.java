package pe.rutafija.operation.api.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** One current point only. The TTL and source are never client-provided. */
@Schema(
        description = "Reemplaza solamente la ubicación vigente propia. El TTL, la fuente y la retención son del servidor; "
                + "no crea historial ni expone coordenadas mediante auditoría, reportes o WebSocket."
)
public record MobileLocationUpdateRequest(
        @Schema(description = "Latitud WGS84 dentro de [-90, 90].", example = "-12.046374")
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @Schema(description = "Longitud WGS84 dentro de [-180, 180].", example = "-77.042793")
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Schema(description = "Precisión declarada en metros, no negativa.", example = "8.2")
        @NotNull @DecimalMin("0.0") BigDecimal accuracyM,
        @Schema(description = "Hora ISO-8601 de captura, validada contra una tolerancia de servidor.")
        @NotNull Instant capturedAt,
        @Schema(description = "Atestación del permiso del sistema operativo; false se rechaza.", example = "true")
        @NotNull Boolean permissionGranted
) {
}
