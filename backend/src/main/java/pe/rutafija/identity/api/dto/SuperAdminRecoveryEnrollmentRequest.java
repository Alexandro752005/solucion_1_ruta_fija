package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuperAdminRecoveryEnrollmentRequest(
        @NotBlank @Size(max = 256) String publicKeyX509Base64,
        @NotBlank @Size(max = 200) String currentPassword
) {
}
