package pe.rutafija.onboarding.api.dto;

import java.time.Instant;

public record ConductorOnboardingOtpResponse(
        String verificationToken,
        Instant otpExpiresAt,
        Instant challengeExpiresAt,
        boolean emailSent
) {
}
