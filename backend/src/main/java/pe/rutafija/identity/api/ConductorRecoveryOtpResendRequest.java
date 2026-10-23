package pe.rutafija.identity.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConductorRecoveryOtpResendRequest(
        @NotBlank @Size(max = 128) String verificationToken
) {
}
