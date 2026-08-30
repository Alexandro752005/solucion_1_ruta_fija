package pe.rutafija.identity.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpaqueTokenServiceTest {

    private final OpaqueTokenService service = new OpaqueTokenService();

    @Test
    void generatesIndependentHighEntropyUrlSafeValues() {
        String first = service.generate();
        String second = service.generate();

        assertThat(first)
                .hasSize(43)
                .matches("[A-Za-z0-9_-]+");
        assertThat(second).isNotEqualTo(first);
    }

    @Test
    void hashesTokensDeterministicallyWithoutRetainingRawValue() {
        String hash = service.hash("refresh-value");

        assertThat(hash)
                .hasSize(64)
                .matches("[0-9a-f]+")
                .isEqualTo(service.hash("refresh-value"))
                .isNotEqualTo(service.hash("another-value"));
    }
}
