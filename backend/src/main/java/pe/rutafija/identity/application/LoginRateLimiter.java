package pe.rutafija.identity.application;

import org.springframework.stereotype.Component;
import pe.rutafija.shared.config.SecurityProperties;
import pe.rutafija.shared.exception.RateLimitExceededException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Protección local y acotada para el trabajo costoso de BCrypt. La cuota por
 * cuenta reduce ataques distribuidos y la cuota global protege la instancia.
 * No se conserva el correo en claro ni se persisten credenciales.
 */
@Component
public class LoginRateLimiter {

    private final Object monitor = new Object();
    private final Map<String, WindowCounter> accounts = new LinkedHashMap<>(128, 0.75f, true);
    private final SecurityProperties.LoginRateLimit properties;
    private final Clock clock;
    private WindowCounter globalCounter;

    public LoginRateLimiter(SecurityProperties securityProperties, Clock clock) {
        this.properties = securityProperties.loginRateLimit();
        this.clock = clock;
        requirePositive(properties.accountWindow(), "accountWindow");
        requirePositive(properties.globalWindow(), "globalWindow");
    }

    public void checkAndRecordAttempt(String accountIdentifier) {
        if (!properties.enabled()) {
            return;
        }

        String accountKey = fingerprint(accountIdentifier);
        Instant now = Instant.now(clock);

        synchronized (monitor) {
            globalCounter = currentWindow(globalCounter, now, properties.globalWindow());
            if (globalCounter.count >= properties.globalMaxAttempts()) {
                throw exceeded(globalCounter, now, properties.globalWindow());
            }

            removeExpiredAccounts(now);
            WindowCounter accountCounter = accounts.get(accountKey);
            if (accountCounter != null && accountCounter.count >= properties.accountMaxAttempts()) {
                throw exceeded(accountCounter, now, properties.accountWindow());
            }

            globalCounter.count++;
            if (accountCounter == null) {
                evictEldestAccountIfNecessary();
                accountCounter = new WindowCounter(now);
                accounts.put(accountKey, accountCounter);
            }
            accountCounter.count++;
        }
    }

    public void recordSuccess(String accountIdentifier) {
        if (!properties.enabled()) {
            return;
        }
        synchronized (monitor) {
            accounts.remove(fingerprint(accountIdentifier));
        }
    }

    int trackedAccountCount() {
        synchronized (monitor) {
            return accounts.size();
        }
    }

    private WindowCounter currentWindow(WindowCounter counter, Instant now, Duration window) {
        if (counter == null || counter.isExpiredAt(now, window)) {
            return new WindowCounter(now);
        }
        return counter;
    }

    private void removeExpiredAccounts(Instant now) {
        accounts.entrySet().removeIf(entry -> entry.getValue().isExpiredAt(now, properties.accountWindow()));
    }

    private void evictEldestAccountIfNecessary() {
        if (accounts.size() < properties.maxTrackedAccounts()) {
            return;
        }
        Iterator<String> iterator = accounts.keySet().iterator();
        if (iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private RateLimitExceededException exceeded(WindowCounter counter, Instant now, Duration window) {
        Instant resetAt = counter.startedAt.plus(window);
        long remainingMillis = Math.max(1, Duration.between(now, resetAt).toMillis());
        long retryAfterSeconds = Math.max(1, (remainingMillis + 999) / 1000);
        return new RateLimitExceededException(retryAfterSeconds);
    }

    private String fingerprint(String value) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private void requirePositive(Duration value, String name) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalStateException("Login rate limit " + name + " must be positive");
        }
    }

    private static final class WindowCounter {
        private final Instant startedAt;
        private int count;

        private WindowCounter(Instant startedAt) {
            this.startedAt = startedAt;
        }

        private boolean isExpiredAt(Instant now, Duration window) {
            return !now.isBefore(startedAt.plus(window));
        }
    }
}
