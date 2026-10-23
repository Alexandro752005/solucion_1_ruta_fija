package pe.rutafija.identity.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConductorRecoveryRequestTest {

    @Test
    void resetTokenIsInvalidAtExactExpiryAndCannotBeReusedAfterConsumption() {
        Instant issuedAt = Instant.parse("2026-10-14T12:00:00Z");
        Instant expiresAt = issuedAt.plusSeconds(30 * 60);
        ConductorRecoveryRequest request = ConductorRecoveryRequest.authorize(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "old@rutafija.test",
                "new@rutafija.test",
                RecoveryEvidenceMethod.PRIOR_SECURE_CONTACT,
                "contacto previo validado",
                "Pérdida del canal",
                true,
                issuedAt
        );
        request.markEmailVerified(issuedAt.plusSeconds(1));
        request.issuePasswordResetToken("a".repeat(64), expiresAt, issuedAt.plusSeconds(1));

        assertThat(request.hasActivePasswordResetAt(expiresAt.minusNanos(1))).isTrue();
        assertThat(request.hasActivePasswordResetAt(expiresAt)).isFalse();
        assertThatThrownBy(() -> request.completePasswordReset(expiresAt))
                .isInstanceOf(IllegalStateException.class);

        Instant consumedAt = expiresAt.minusNanos(1);
        request.completePasswordReset(consumedAt);
        assertThat(request.hasActivePasswordResetAt(consumedAt)).isFalse();
        assertThat(request.getPasswordResetConsumedAt()).isEqualTo(consumedAt);
    }
}
