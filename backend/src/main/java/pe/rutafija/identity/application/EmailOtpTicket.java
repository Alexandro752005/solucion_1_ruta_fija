package pe.rutafija.identity.application;

import java.time.Instant;

public record EmailOtpTicket(
        String verificationToken,
        Instant otpExpiresAt,
        Instant challengeExpiresAt,
        boolean emailSent
) {
}
