package pe.rutafija.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/** The verification token is returned once to the authorizing ADMIN for secure handoff. */
public record ConductorRecoveryStartedResponse(
        UUID recoveryId,
        String status,
        String verificationToken,
        Instant otpExpiresAt,
        Instant challengeExpiresAt,
        boolean emailSent,
        boolean passwordResetRequired
) {
}
