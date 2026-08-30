package pe.rutafija.identity.application;

import pe.rutafija.identity.api.dto.AuthTokenResponse;

public record IssuedSession(AuthTokenResponse response, String rawRefreshToken) {
}
