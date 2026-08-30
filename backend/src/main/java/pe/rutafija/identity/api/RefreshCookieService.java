package pe.rutafija.identity.api;

import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import pe.rutafija.shared.config.SecurityProperties;

import java.time.Duration;

@Component
public class RefreshCookieService {

    private final SecurityProperties properties;

    public RefreshCookieService(SecurityProperties properties) {
        this.properties = properties;
    }

    public String create(String rawRefreshToken) {
        return baseCookie(rawRefreshToken)
                .maxAge(properties.jwt().refreshTtl())
                .build()
                .toString();
    }

    public String clear() {
        return baseCookie("")
                .maxAge(Duration.ZERO)
                .build()
                .toString();
    }

    public String cookieName() {
        return properties.refreshCookie().name();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        return ResponseCookie.from(properties.refreshCookie().name(), value)
                .httpOnly(true)
                .secure(properties.refreshCookie().secure())
                .sameSite(properties.refreshCookie().sameSite())
                .path(properties.refreshCookie().path());
    }
}
