package pe.rutafija.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void rateLimitIncludesRetryAfterPathAndCorrelationId() {
        MockHttpServletRequest request = request("/api/v1/auth/login");

        ResponseEntity<ApiErrorResponse> response = handler.handleRateLimit(
                new RateLimitExceededException(42),
                request
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().path()).isEqualTo("/api/v1/auth/login");
        assertThat(response.getBody().correlationId()).isEqualTo("test-correlation");
    }

    @Test
    void methodNotAllowedAdvertisesTheSupportedMethod() {
        ResponseEntity<ApiErrorResponse> response = handler.handleMethodNotAllowed(
                new HttpRequestMethodNotSupportedException("GET", List.of("POST")),
                request("/api/v1/auth/login")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getHeaders().getAllow())
                .containsExactly(HttpMethod.POST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI(path);
        request.setAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE, "test-correlation");
        return request;
    }
}
