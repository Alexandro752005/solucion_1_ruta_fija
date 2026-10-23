package pe.rutafija.availability.monitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MonitorConfigurationTest {
    private List<MonitorConfiguration.Probe> probes(String origin) {
        return List.of(new MonitorConfiguration.Probe("api-readiness", URI.create(origin + "/ready"), MonitorConfiguration.Expected.HEALTH_UP),
            new MonitorConfiguration.Probe("api-liveness", URI.create(origin + "/live"), MonitorConfiguration.Expected.HEALTH_UP),
            new MonitorConfiguration.Probe("crm-application", URI.create(origin + "/crm"), MonitorConfiguration.Expected.HTML_PAGE));
    }
    private MonitorConfiguration production(Duration interval, String origin) {
        return new MonitorConfiguration(MonitorConfiguration.Mode.PRODUCTION_CADENCE_LOCAL_ALERT_ONLY, interval,
            Duration.ofSeconds(2), Duration.ofSeconds(5), probes(origin));
    }
    @Test void productionCadenceIsExactlySixtySecondsAndHttpsOnlyWithoutClaimingExternalAlerts() {
        var configuration = production(Duration.ofSeconds(60), "https://monitor-validation.invalid");
        assertThat(configuration.interval()).isEqualTo(Duration.ofSeconds(60));
        assertThat(configuration.mode().name()).contains("LOCAL_ALERT_ONLY");
        assertThatThrownBy(() -> production(Duration.ofSeconds(59), "https://monitor-validation.invalid")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> production(Duration.ofSeconds(60), "http://127.0.0.1:1234")).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"http://127.0.0.1:1234", "http://[::1]:1234"})
    void acceleratedValidationAcceptsOnlyLiteralLoopback(String origin) {
        assertThat(new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofMillis(100),
            Duration.ofMillis(50), Duration.ofMillis(100), probes(origin)).probes()).hasSize(3);
    }
    @ParameterizedTest @ValueSource(strings = {"http://localhost:1234", "http://127.0.0.2:1234", "https://monitor-validation.invalid"})
    void validationDoesNotExpandScopeThroughDnsAliasesOrNonLoopbackAddresses(String origin) {
        assertThatThrownBy(() -> new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofMillis(100),
            Duration.ofMillis(50), Duration.ofMillis(100), probes(origin))).isInstanceOf(IllegalArgumentException.class);
    }
    @ParameterizedTest @ValueSource(strings = {
        "http://SECRET:VALUE@127.0.0.1/ready", "http://127.0.0.1/ready?token=SECRET",
        "http://127.0.0.1/ready#SECRET", "file:///SECRET", "http://127.0.0.1:0/ready"})
    void accidentalCredentialsQueriesFragmentsAndUnsupportedDestinationsAreRefusedWithoutEcho(String uri) {
        assertThatThrownBy(() -> new MonitorConfiguration.Probe("ready", URI.create(uri), MonitorConfiguration.Expected.HEALTH_UP))
            .isInstanceOf(IllegalArgumentException.class).hasMessageNotContaining("SECRET")
            .hasMessageNotContaining("VALUE").hasMessageNotContaining("token");
    }
    @Test void probesMustCoverTwoHealthChecksAndApplicationWithoutDuplicates() {
        var values = probes("http://127.0.0.1:1234");
        assertThatThrownBy(() -> new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofSeconds(1),
            Duration.ofSeconds(1), Duration.ofSeconds(2), List.of(values.getFirst(), values.getFirst(), values.getLast())))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofSeconds(1),
            Duration.ofSeconds(1), Duration.ofSeconds(2), values.subList(0, 2))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void timeoutsAreBoundedAndCannotOutliveTheConfiguredCompleteRequestBudget() {
        var values = probes("http://127.0.0.1:1234");
        assertThatThrownBy(() -> new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofSeconds(1),
            Duration.ZERO, Duration.ofSeconds(2), values)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofSeconds(1),
            Duration.ofSeconds(5), Duration.ofSeconds(2), values)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofSeconds(1),
            Duration.ofSeconds(1), Duration.ofSeconds(11), values)).isInstanceOf(IllegalArgumentException.class);
    }
}
