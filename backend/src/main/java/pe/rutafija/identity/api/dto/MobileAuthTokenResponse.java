package pe.rutafija.identity.api.dto;

/**
 * Native-only session response. The refresh token must be stored using the
 * operating-system secure storage; it is deliberately never emitted by web auth.
 */
public record MobileAuthTokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        long refreshExpiresIn,
        MobileAuthenticatedUserResponse user
) {
}
