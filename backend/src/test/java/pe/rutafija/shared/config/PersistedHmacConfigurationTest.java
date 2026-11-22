package pe.rutafija.shared.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.personidentity.application.PersonIdentityService;
import pe.rutafija.personidentity.infrastructure.PersonIdentityRepository;
import pe.rutafija.shared.exception.ApplicationException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class PersistedHmacConfigurationTest {
    private static final String SETTING = "RF_PERSISTED_HMAC_KEY_BASE64";
    private static final String FINGERPRINT_CONTEXT = "person-identity:key-fingerprint:v1";
    private final SecurityConfig configuration = new SecurityConfig();
    private final SecretKey historical = syntheticKey();
    private final SecretKey newJwt = syntheticKey();

    @Test
    void preservesHistoricalFingerprintAndInvitationHashesWithSeparateJwt() {
        var legacy = new InvitationCodeHasher(historical);
        var current = configuration.invitationCodeHasher(historical, new MockEnvironment());
        var rotated = configuration.invitationCodeHasher(newJwt, configured(historical));
        assertThat(current.hashOrigin(FINGERPRINT_CONTEXT)).isEqualTo(legacy.hashOrigin(FINGERPRINT_CONTEXT));
        assertThat(rotated.hashOrigin(FINGERPRINT_CONTEXT)).isEqualTo(legacy.hashOrigin(FINGERPRINT_CONTEXT));
        assertThat(rotated.hashCode("TEST-CODE", "synthetic@rutafija.test"))
                .isEqualTo(legacy.hashCode("TEST-CODE", "synthetic@rutafija.test"));
        assertThat(configuration.invitationCodeHasher(newJwt, new MockEnvironment()).hashOrigin(FINGERPRINT_CONTEXT))
                .isNotEqualTo(legacy.hashOrigin(FINGERPRINT_CONTEXT));
    }

    @Test
    void refusesWrongKeyWithoutReplacingStoredFingerprint(CapturedOutput output) {
        var legacy = new InvitationCodeHasher(historical);
        var repo = mock(PersonIdentityRepository.class);
        String stored = legacy.hashOrigin(FINGERPRINT_CONTEXT);
        when(repo.keyFingerprint()).thenReturn(Optional.of(stored));
        var wrong = configuration.invitationCodeHasher(newJwt, configured(syntheticKey()));
        var service = new PersonIdentityService(repo, null, wrong, null, null, null, null, null,
                Clock.systemUTC(), null, null);

        assertThatThrownBy(() -> service.requireReviewedConductorDocument(UUID.randomUUID(), "DNI", "00000000"))
                .isInstanceOf(ApplicationException.class)
                .hasMessageContaining("La clave estable de identidad cambio");
        verify(repo).keyFingerprint();
        verifyNoMoreInteractions(repo);
        assertThat(output.getAll()).contains("person_identity_key_check result=FINGERPRINT_MISMATCH")
                .doesNotContain(stored, wrong.hashOrigin(FINGERPRINT_CONTEXT));
    }

    @Test
    void refusesAnExplicitMalformedKeyWithoutEchoingIt() {
        var environment = new MockEnvironment().withProperty(SETTING, "synthetic!invalid");
        assertThatThrownBy(() -> configuration.invitationCodeHasher(historical, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage(SETTING + " must contain valid Base64")
                .hasNoCause();
    }

    private static MockEnvironment configured(SecretKey key) {
        return new MockEnvironment().withProperty(SETTING, Base64.getEncoder().encodeToString(key.getEncoded()));
    }

    private static SecretKey syntheticKey() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
