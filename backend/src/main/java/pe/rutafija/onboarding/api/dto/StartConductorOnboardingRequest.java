package pe.rutafija.onboarding.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartConductorOnboardingRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 64) String invitationCode
) {
}
