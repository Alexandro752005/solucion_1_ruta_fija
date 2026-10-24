package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.identity.domain.RecoveryEvidenceMethod;

public record StartConductorRecoveryRequest(
        @NotBlank @Email @Size(max = 180) String newEmail,
        @NotNull RecoveryEvidenceMethod evidenceMethod,
        @NotBlank @Size(max = 200) String evidenceReference,
        @NotBlank @Size(max = 300) String reason,
        @NotNull Boolean expedienteReviewed,
        @NotNull Boolean independentEvidenceConfirmed,
        @NotNull Boolean passwordResetRequired
) {
}
