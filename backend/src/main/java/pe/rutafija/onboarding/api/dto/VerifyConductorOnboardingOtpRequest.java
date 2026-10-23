package pe.rutafija.onboarding.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyConductorOnboardingOtpRequest(
        @NotBlank @Size(max = 128) String verificationToken,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
}
