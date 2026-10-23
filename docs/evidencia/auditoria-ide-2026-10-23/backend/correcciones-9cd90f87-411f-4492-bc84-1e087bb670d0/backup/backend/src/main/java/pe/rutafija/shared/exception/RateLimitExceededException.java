package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class RateLimitExceededException extends ApplicationException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.RATE_LIMIT_EXCEEDED,
                "Demasiados intentos de inicio de sesión; inténtelo nuevamente más tarde"
        );
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
