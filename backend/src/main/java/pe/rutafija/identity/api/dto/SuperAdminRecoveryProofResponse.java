package pe.rutafija.identity.api.dto;

import java.time.Instant;

public record SuperAdminRecoveryProofResponse(
        String verificationToken,
        Instant otpExpiresAt,
        Instant challengeExpiresAt,
        boolean emailSent
) {
}
