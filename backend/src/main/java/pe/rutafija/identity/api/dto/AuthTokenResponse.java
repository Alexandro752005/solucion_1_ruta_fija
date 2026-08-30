package pe.rutafija.identity.api.dto;

public record AuthTokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        AuthenticatedUserResponse user
) {
}
