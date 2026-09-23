package pe.rutafija.operation.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.operation.domain.AssignmentResponseMode;

import java.time.Instant;
import java.util.UUID;

@Schema(
        description = "Alta administrativa de asignación. Si responseMode se omite, se conserva ADMIN_DIRECT. "
                + "MOBILE_CONFIRMATION exige un plazo UTC futuro y no habilita al CRM a aceptar o rechazar."
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
                description = "ADMIN_DIRECT crea SCHEDULED; MOBILE_CONFIRMATION crea PENDING_RESPONSE.",
                allowableValues = {"ADMIN_DIRECT", "MOBILE_CONFIRMATION"},
                example = "ADMIN_DIRECT"
        )
        AssignmentResponseMode responseMode,
        @Schema(
                description = "Plazo UTC obligatorio solo para MOBILE_CONFIRMATION; lo evalúa el servidor.",
                example = "2026-09-22T18:00:00Z"
        )
        Instant responseDeadlineAt
) {
}
