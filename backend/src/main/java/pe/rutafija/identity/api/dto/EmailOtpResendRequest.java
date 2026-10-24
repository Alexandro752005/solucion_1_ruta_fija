package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmailOtpResendRequest(
        @NotBlank @Size(max = 128) String verificationToken
) {
}
