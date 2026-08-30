package pe.rutafija.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pe.rutafija.shared.config.SecurityProperties;
import pe.rutafija.shared.exception.ApiErrorResponse;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiOriginValidationFilterTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ApiOriginValidationFilter filter = new ApiOriginValidationFilter(
            new TrustedOriginValidator(properties()),
            objectMapper
    );

    @Test
    void returnsNormalizedErrorBeforeCorsForAnUntrustedApiOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, "https://attacker.invalid");
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "origin-test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ApiErrorResponse body = objectMapper.readValue(response.getContentAsByteArray(), ApiErrorResponse.class);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(body.code()).isEqualTo(ErrorCode.CROSS_ORIGIN_REQUEST_DENIED.name());
        assertThat(body.path()).isEqualTo("/api/v1/auth/refresh");
        assertThat(body.correlationId()).isEqualTo("origin-test");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void letsAConfiguredOriginReachTheRestOfTheFilterChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:4200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    private static SecurityProperties properties() {
        return new SecurityProperties(
                new SecurityProperties.Jwt(
                        "test",
                        "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                        Duration.ofMinutes(15),
                        Duration.ofDays(7)
                ),
                new SecurityProperties.RefreshCookie("rf_refresh", "/api/v1/auth", "Strict", false),
                new SecurityProperties.Cors(List.of("http://localhost:4200")),
                new SecurityProperties.LoginRateLimit(
                        true,
                        5,
                        Duration.ofMinutes(5),
                        100,
                        Duration.ofMinutes(1),
                        1000
                )
        );
    }
}
