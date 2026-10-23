package pe.rutafija.operation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.operation.domain.AssignmentResponseMode;

import java.time.Instant;
import java.util.UUID;

@Schema(
        description = "ADMIN emite una oferta MOBILE_CONFIRMATION, también por omisión. "
                + "Servidor fija min(emisión+10min,inicio). Solo CONDUCTOR acepta; ADMIN_DIRECT no admite nuevas altas."
)
public record AssignmentCreateRequest(
        @NotNull UUID driverId,
        @NotNull UUID vehicleId,
        @NotBlank @Size(max = 250) String originText,
        @NotBlank @Size(max = 250) String destinationText,
        @NotNull Instant scheduledAt,
        @NotNull Instant scheduledEndAt,
        @Size(max = 500) String notes,
        @Schema(
                description = "MOBILE_CONFIRMATION crea PENDING_RESPONSE. ADMIN_DIRECT es únicamente histórico.",
                allowableValues = {"MOBILE_CONFIRMATION"},
                example = "MOBILE_CONFIRMATION"
        )
        AssignmentResponseMode responseMode,
        @Schema(
                description = "Campo heredado ignorado al calcular el plazo. El servidor siempre determina el vencimiento.",
                example = "2026-09-22T18:00:00Z"
        )
        Instant responseDeadlineAt,
        @NotNull UUID routeVersionId,
        @NotNull UUID serviceTypeVersionId
) {
    /** Kept for source compatibility of historical, non-HTTP fixtures. HTTP requires a route version. */
    public AssignmentCreateRequest(UUID driverId,UUID vehicleId,String originText,String destinationText,Instant scheduledAt,
        Instant scheduledEndAt,String notes,AssignmentResponseMode mode,Instant deadline) {
        this(driverId,vehicleId,originText,destinationText,scheduledAt,scheduledEndAt,notes,mode,deadline,null,null);
    }
    public AssignmentCreateRequest(UUID driverId,UUID vehicleId,String originText,String destinationText,Instant scheduledAt,
        Instant scheduledEndAt,String notes,AssignmentResponseMode mode,Instant deadline,UUID routeVersionId) {
        this(driverId,vehicleId,originText,destinationText,scheduledAt,scheduledEndAt,notes,mode,deadline,routeVersionId,null);
    }
}
