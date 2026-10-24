package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SuperAdminRecoveryProofRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z0-9_-]{43}")
        String emergencyCode,
        @NotBlank @Size(max = 128)
        String signatureBase64Url
) {
}
