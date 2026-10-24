package pe.rutafija.onboarding.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResendConductorOnboardingOtpRequest(
        @NotBlank @Size(max = 128) String verificationToken
) {
}
