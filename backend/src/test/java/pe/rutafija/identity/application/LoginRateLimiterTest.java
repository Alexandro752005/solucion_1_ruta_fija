package pe.rutafija.identity.application;

import org.junit.jupiter.api.Test;
import pe.rutafija.shared.config.SecurityProperties;
import pe.rutafija.shared.exception.RateLimitExceededException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginRateLimiterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-29T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void blocksAnAccountAfterTheConfiguredNumberOfExpensiveAttempts() {
        LoginRateLimiter limiter = limiter(5, 100, 100);

        for (int attempt = 0; attempt < 5; attempt++) {
            limiter.checkAndRecordAttempt("Admin@RutaFija.pe");
        }

        assertThatThrownBy(() -> limiter.checkAndRecordAttempt(" admin@rutafija.pe "))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(exception -> assertThat(
                        ((RateLimitExceededException) exception).getRetryAfterSeconds()
                ).isEqualTo(300));
    }

    @Test
    void aSuccessfulLoginResetsOnlyThatAccountQuota() {
        LoginRateLimiter limiter = limiter(2, 100, 100);
        limiter.checkAndRecordAttempt("admin@rutafija.pe");
        limiter.checkAndRecordAttempt("admin@rutafija.pe");

        limiter.recordSuccess("ADMIN@RUTAFIJA.PE");

        assertThatCode(() -> limiter.checkAndRecordAttempt("admin@rutafija.pe"))
                .doesNotThrowAnyException();
    }

    @Test
    void globalQuotaProtectsTheInstanceAcrossDifferentAccounts() {
        LoginRateLimiter limiter = limiter(10, 3, 100);
        limiter.checkAndRecordAttempt("one@rutafija.pe");
        limiter.checkAndRecordAttempt("two@rutafija.pe");
        limiter.checkAndRecordAttempt("three@rutafija.pe");

        assertThatThrownBy(() -> limiter.checkAndRecordAttempt("four@rutafija.pe"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void trackedAccountStateRemainsBounded() {
        LoginRateLimiter limiter = limiter(10, 100, 2);

        limiter.checkAndRecordAttempt("one@rutafija.pe");
        limiter.checkAndRecordAttempt("two@rutafija.pe");
        limiter.checkAndRecordAttempt("three@rutafija.pe");

        assertThat(limiter.trackedAccountCount()).isEqualTo(2);
    }

    private LoginRateLimiter limiter(int accountAttempts, int globalAttempts, int maxAccounts) {
        SecurityProperties properties = new SecurityProperties(
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
                        accountAttempts,
                        Duration.ofMinutes(5),
                        globalAttempts,
                        Duration.ofMinutes(1),
                        maxAccounts
                )
        );
        return new LoginRateLimiter(properties, CLOCK);
    }
}
