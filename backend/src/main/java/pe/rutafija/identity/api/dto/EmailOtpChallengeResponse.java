package pe.rutafija.identity.api.dto;

import pe.rutafija.identity.application.EmailOtpTicket;

import java.time.Instant;

public record EmailOtpChallengeResponse(
        String verificationToken,
        Instant otpExpiresAt,
        Instant challengeExpiresAt,
        boolean emailSent
) {

    public static EmailOtpChallengeResponse from(EmailOtpTicket ticket) {
        return new EmailOtpChallengeResponse(
                ticket.verificationToken(),
                ticket.otpExpiresAt(),
                ticket.challengeExpiresAt(),
                ticket.emailSent()
        );
    }
}
