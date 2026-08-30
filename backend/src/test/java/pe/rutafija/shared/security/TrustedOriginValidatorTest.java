package pe.rutafija.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import pe.rutafija.shared.config.SecurityProperties;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrustedOriginValidatorTest {

    private final TrustedOriginValidator validator = new TrustedOriginValidator(properties());

    @Test
    void acceptsTheConfiguredBrowserOrigin() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.ORIGIN, "http://LOCALHOST:4200");

        assertThatCode(() -> validator.validate(request)).doesNotThrowAnyException();
    }

    @Test
    void acceptsCommandLineRequestsWithoutBrowserOriginMetadata() {
        assertThatCode(() -> validator.validate(new MockHttpServletRequest()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUntrustedOriginAndCrossSiteFetchMetadata() {
        MockHttpServletRequest originRequest = new MockHttpServletRequest();
        originRequest.addHeader(HttpHeaders.ORIGIN, "https://attacker.invalid");
        MockHttpServletRequest fetchMetadataRequest = new MockHttpServletRequest();
        fetchMetadataRequest.addHeader("Sec-Fetch-Site", "cross-site");

        assertDenied(originRequest);
        assertDenied(fetchMetadataRequest);
    }

    private void assertDenied(MockHttpServletRequest request) {
        assertThatThrownBy(() -> validator.validate(request))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception -> assertThat(((ApplicationException) exception).getCode())
                        .isEqualTo(ErrorCode.CROSS_ORIGIN_REQUEST_DENIED));
    }

    private SecurityProperties properties() {
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
