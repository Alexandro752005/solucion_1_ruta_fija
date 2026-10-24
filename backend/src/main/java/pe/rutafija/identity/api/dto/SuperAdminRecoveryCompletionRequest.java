package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SuperAdminRecoveryCompletionRequest(
        @NotBlank @Size(max = 100) String verificationToken,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code,
        @NotBlank @Size(min = 12, max = 128) String newPassword
) {
}
