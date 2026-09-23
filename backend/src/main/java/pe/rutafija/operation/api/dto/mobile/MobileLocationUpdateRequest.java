package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/** One current point only. The TTL and source are never client-provided. */
public record MobileLocationUpdateRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @NotNull @DecimalMin("0.0") BigDecimal accuracyM,
        @NotNull Instant capturedAt,
        @NotNull Boolean permissionGranted
) {
}
