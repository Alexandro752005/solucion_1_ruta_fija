package pe.rutafija.shared.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.rutafija.shared.config.SecurityProperties;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Validación CSRF complementaria para endpoints autenticados mediante cookie. */
@Component
public class TrustedOriginValidator {

    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private final Set<String> allowedOrigins;

    public TrustedOriginValidator(SecurityProperties properties) {
        this.allowedOrigins = properties.cors().allowedOrigins().stream()
                .map(this::canonicalOrigin)
                .collect(Collectors.toUnmodifiableSet());
    }

    public void validate(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin != null && !origin.isBlank()) {
            if (!isAllowedOrigin(origin)) {
                throw denied();
            }
            return;
        }

        // Herramientas CLI no envían cabeceras Fetch Metadata. Un navegador que
        // declare explícitamente origen cruzado no debe poder usar la cookie.
        if ("cross-site".equalsIgnoreCase(request.getHeader(SEC_FETCH_SITE))) {
            throw denied();
        }
    }

    /** El WebSocket exige un Origin explícito y permitido antes del handshake. */
    public boolean isAllowedOrigin(String origin) {
        if (origin == null || origin.isBlank()) {
            return false;
        }
        try {
            return allowedOrigins.contains(canonicalOrigin(origin));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String canonicalOrigin(String value) {
        URI uri = URI.create(value.strip());
        String scheme = uri.getScheme();
        String host = uri.getHost();
        String path = uri.getRawPath();
        if (scheme == null
                || host == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null
                || (path != null && !path.isEmpty() && !path.equals("/"))) {
            throw new IllegalArgumentException("A valid HTTP origin is required");
        }

        String normalizedScheme = scheme.toLowerCase(Locale.ROOT);
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.contains(":")) {
            normalizedHost = '[' + normalizedHost + ']';
        }
        StringBuilder canonical = new StringBuilder()
                .append(normalizedScheme)
                .append("://")
                .append(normalizedHost);
        boolean defaultPort = (normalizedScheme.equals("http") && uri.getPort() == 80)
                || (normalizedScheme.equals("https") && uri.getPort() == 443);
        if (uri.getPort() >= 0 && !defaultPort) {
            canonical.append(':').append(uri.getPort());
        }
        return canonical.toString();
    }

    private ApplicationException denied() {
        return new ApplicationException(
                HttpStatus.FORBIDDEN,
                ErrorCode.CROSS_ORIGIN_REQUEST_DENIED,
                "El origen de la solicitud no está autorizado"
        );
    }
}
