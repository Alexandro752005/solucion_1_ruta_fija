package pe.rutafija.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.rutafija.shared.exception.ApiErrorResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Rechaza orígenes no confiables antes de que CorsFilter produzca una respuesta
 * textual genérica. La política CORS permanece restrictiva; solo se normaliza
 * el contrato de error de la API.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ApiOriginValidationFilter extends OncePerRequestFilter {

    private final TrustedOriginValidator trustedOriginValidator;
    private final ObjectMapper objectMapper;

    public ApiOriginValidationFilter(
            TrustedOriginValidator trustedOriginValidator,
            ObjectMapper objectMapper
    ) {
        this.trustedOriginValidator = trustedOriginValidator;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        return !path.startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            trustedOriginValidator.validate(request);
            filterChain.doFilter(request, response);
        } catch (ApplicationException exception) {
            if (exception.getCode() != ErrorCode.CROSS_ORIGIN_REQUEST_DENIED) {
                throw exception;
            }
            writeOriginDeniedResponse(request, response, exception);
        }
    }

    private void writeOriginDeniedResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            ApplicationException exception
    ) throws IOException {
        response.setStatus(exception.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        objectMapper.writeValue(
                response.getOutputStream(),
                ApiErrorResponse.of(
                        exception.getStatus().value(),
                        exception.getCode(),
                        exception.getMessage(),
                        request.getRequestURI(),
                        CorrelationIdFilter.from(request)
                )
        );
    }
}
