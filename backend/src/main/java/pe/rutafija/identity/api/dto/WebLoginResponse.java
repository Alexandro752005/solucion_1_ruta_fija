package pe.rutafija.identity.api.dto;

import pe.rutafija.identity.application.EmailOtpTicket;
import pe.rutafija.identity.application.IssuedSession;

public record WebLoginResponse(
        String accessToken,
        String tokenType,
        Long expiresIn,
        AuthenticatedUserResponse user,
        boolean emailVerificationRequired,
        EmailOtpChallengeResponse verification
) {

    public static WebLoginResponse authenticated(IssuedSession session) {
        AuthTokenResponse response = session.response();
        return new WebLoginResponse(
                response.accessToken(),
                response.tokenType(),
                response.expiresIn(),
                response.user(),
                false,
                null
        );
    }

    public static WebLoginResponse verificationRequired(EmailOtpTicket ticket) {
        return new WebLoginResponse(null, null, null, null, true, EmailOtpChallengeResponse.from(ticket));
    }
}
