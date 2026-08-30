package pe.rutafija.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import pe.rutafija.shared.exception.ApiErrorResponse;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.io.IOException;
import java.util.Locale;

@Component
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception
    ) throws IOException {
        ErrorCode code = isExpired(exception) ? ErrorCode.AUTH_TOKEN_EXPIRED : ErrorCode.AUTH_TOKEN_INVALID;
        String message = code == ErrorCode.AUTH_TOKEN_EXPIRED
                ? "El access token ha expirado"
                : "La sesión no es válida o ha expirado";
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        code,
                        message,
                        request.getRequestURI(),
                        CorrelationIdFilter.from(request)
                )
        );
    }

    private boolean isExpired(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("expired")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
