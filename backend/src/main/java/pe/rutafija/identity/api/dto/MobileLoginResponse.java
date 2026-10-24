package pe.rutafija.identity.api.dto;

import pe.rutafija.identity.application.EmailOtpTicket;
import pe.rutafija.identity.application.MobileIssuedSession;

public record MobileLoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        Long refreshExpiresIn,
        MobileAuthenticatedUserResponse user,
        boolean emailVerificationRequired,
        EmailOtpChallengeResponse verification,
        String accessScope
) {

    public static MobileLoginResponse authenticated(MobileIssuedSession session) {
        MobileAuthTokenResponse response = session.response();
        return new MobileLoginResponse(
                response.accessToken(),
                response.refreshToken(),
                response.tokenType(),
                response.expiresIn(),
                response.refreshExpiresIn(),
                response.user(),
                false,
                null,
                response.accessScope()
        );
    }

    public static MobileLoginResponse verificationRequired(EmailOtpTicket ticket) {
        return new MobileLoginResponse(null, null, null, null, null, null, true,
                EmailOtpChallengeResponse.from(ticket), "INCORPORATION");
    }
}
