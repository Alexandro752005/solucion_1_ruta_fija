package pe.rutafija.invitation.application;

import org.junit.jupiter.api.Test;

import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;

class InvitationCodeHasherTest {

    private final InvitationCodeHasher hasher = new InvitationCodeHasher(
            new SecretKeySpec(new byte[32], "HmacSHA256")
    );

    @Test
    void preservesLeadingZeroesAndDoesNotStoreTheRawCode() {
        String code = "000042";
        String hash = hasher.hashCode(code, "driver@example.test");

        assertThat(hash).matches("[0-9a-f]{64}").isNotEqualTo(code);
        assertThat(hasher.matchesCode(code, "driver@example.test", hash)).isTrue();
        assertThat(hasher.matchesCode("42", "driver@example.test", hash)).isFalse();
        assertThat(hasher.matchesCode(code, "other@example.test", hash)).isFalse();
    }

    @Test
    void hashesTheOriginWithoutRetainingItsAddress() {
        String rawAddress = "198.51.100.10";
        String hash = hasher.hashOrigin(rawAddress);

        assertThat(hash).matches("[0-9a-f]{64}").doesNotContain(rawAddress);
        assertThat(hasher.hashOrigin("198.51.100.11")).isNotEqualTo(hash);
    }
}
