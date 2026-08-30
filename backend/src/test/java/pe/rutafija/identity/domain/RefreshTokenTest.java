package pe.rutafija.identity.domain;

import org.junit.jupiter.api.Test;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RefreshTokenTest {

    @Test
    void rotationKeepsFamilyAndMarksPreviousTokenAsUsed() {
        Organization organization = Organization.active("Legal", "Trade", "America/Lima");
        AppUser user = AppUser.organizationUser(
                organization,
                "admin@example.test",
                "hash",
                "Admin",
                UserRole.ADMINISTRADOR
        );
        Instant now = Instant.parse("2026-08-28T22:00:00Z");
        RefreshToken current = RefreshToken.firstInFamily(user, "a".repeat(64), now.plusSeconds(60));

        RefreshToken successor = current.successor("b".repeat(64), now.plusSeconds(120));
        current.markRotated(successor, now);

        assertThat(current.hasBeenUsed()).isTrue();
        assertThat(current.isRevoked()).isTrue();
        assertThat(successor.getFamilyId()).isEqualTo(current.getFamilyId());
        assertThat(successor.isExpiredAt(now)).isFalse();
    }
}
