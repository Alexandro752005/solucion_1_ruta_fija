package pe.rutafija.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A reset token is present once, only after the requested new email passed OTP verification. */
public record ConductorRecoveryOtpResponse(
        UUID recoveryId,
        String status,
        String requestedEmail,
        boolean passwordResetRequired,
        String passwordResetToken,
        Instant passwordResetExpiresAt
) {
}
