package pe.rutafija.identity.domain;

import org.junit.jupiter.api.Test;
import pe.rutafija.organization.domain.Organization;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmailOtpChallengeTest {

    private static final Duration SEND_WINDOW = Duration.ofMinutes(60);
    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(30);

    @Test
    void allowsExactlyThreeSendsInTheRollingWindowAndExpiresTheOldestAtTheBoundary() {
        Instant first = Instant.parse("2026-10-14T10:00:00Z");
        EmailOtpChallenge challenge = newChallenge(first);
        issue(challenge, first, EmailOtpPurpose.LOGIN, "driver@example.test");
        issue(challenge, first.plus(Duration.ofMinutes(10)), EmailOtpPurpose.LOGIN, "driver@example.test");
        issue(challenge, first.plus(Duration.ofMinutes(20)), EmailOtpPurpose.SUPER_ADMIN_RECOVERY,
                "new-address@example.test");

        assertThat(challenge.nextSendAllowedAt(first.plus(Duration.ofMinutes(59).plusSeconds(59)), SEND_WINDOW))
                .isEqualTo(first.plus(SEND_WINDOW));
        assertThat(challenge.nextSendAllowedAt(first.plus(SEND_WINDOW), SEND_WINDOW)).isNull();

        issue(challenge, first.plus(SEND_WINDOW), EmailOtpPurpose.SUPER_ADMIN_RECOVERY,
                "new-address@example.test");

        assertThat(challenge.nextSendAllowedAt(first.plus(SEND_WINDOW), SEND_WINDOW))
                .isEqualTo(first.plus(Duration.ofMinutes(70)));
        assertThat(challenge.nextSendAllowedAt(first.plus(Duration.ofMinutes(70)), SEND_WINDOW)).isNull();
    }

    @Test
    void secondFailedAttemptLocksValidationAndInvalidatesCurrentCodeForThirtyMinutes() {
        Instant issued = Instant.parse("2026-10-14T10:00:00Z");
        EmailOtpChallenge challenge = newChallenge(issued);
        issue(challenge, issued, EmailOtpPurpose.LOGIN, "driver@example.test");

        challenge.recordInvalidCode(issued.plusSeconds(1), Duration.ofMinutes(30));
        challenge.recordInvalidCode(issued.plusSeconds(2), Duration.ofMinutes(30));

        assertThat(challenge.getFailedAttempts()).isEqualTo((short) 2);
        assertThat(challenge.getOtpHash()).isNull();
        Instant blockExpiry = issued.plusSeconds(2).plus(Duration.ofMinutes(30));
        assertThat(challenge.isBlockedAt(blockExpiry.minusNanos(1))).isTrue();
        assertThat(challenge.isBlockedAt(blockExpiry)).isFalse();
    }

    @Test
    void expiresOtpAtTheExactFiveMinuteBoundaryAndConsumedCodeIsInactive() {
        Instant issued = Instant.parse("2026-10-14T10:00:00Z");
        EmailOtpChallenge challenge = newChallenge(issued);
        issue(challenge, issued, EmailOtpPurpose.LOGIN, "driver@example.test");

        assertThat(challenge.hasActiveCodeAt(issued.plus(Duration.ofMinutes(5)).minusNanos(1))).isTrue();
        assertThat(challenge.hasActiveCodeAt(issued.plus(Duration.ofMinutes(5)))).isFalse();

        challenge.consume(issued.plus(Duration.ofMinutes(2)));
        assertThat(challenge.hasActiveCodeAt(issued.plus(Duration.ofMinutes(2)))).isFalse();
    }

    private EmailOtpChallenge newChallenge(Instant now) {
        Organization organization = Organization.active("OTP test", "OTP", "America/Lima");
        AppUser user = AppUser.organizationUser(
                organization,
                "otp@example.test",
                "test-hash",
                "OTP test",
                UserRole.CONDUCTOR
        );
        return EmailOtpChallenge.create(user, now);
    }

    private void issue(EmailOtpChallenge challenge, Instant now, EmailOtpPurpose purpose, String recipient) {
        challenge.issueCode(
                "token-hash-" + now,
                "otp-hash-" + now,
                now,
                now.plus(OTP_TTL),
                now.plus(CHALLENGE_TTL),
                SEND_WINDOW,
                purpose,
                recipient,
                purpose == EmailOtpPurpose.SUPER_ADMIN_RECOVERY ? UUID.randomUUID() : null
        );
    }
}
