package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record EmailOtpVerificationRequest(
        @NotBlank @Size(max = 128) String verificationToken,
        @NotBlank @Pattern(regexp = "[0-9]{6}", message = "El código debe tener seis dígitos") String code
) {
}
