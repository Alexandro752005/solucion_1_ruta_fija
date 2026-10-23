package pe.rutafija.availability.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import pe.rutafija.availability.domain.AvailabilityPolicy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/** HTTP sockets are real; clocks explicitly injected here are synthetic and do not prove production detection times. */
class AvailabilityMonitorHttpTest {
    @TempDir Path output;
    private MonitorHttpFixture fixture;
    private final ObjectMapper json = new ObjectMapper();
    private final Instant start = Instant.parse("2026-10-21T00:00:00Z");
    @BeforeEach void startRealHttpOnly() throws Exception { fixture = new MonitorHttpFixture(); }
    @AfterEach void stopOnlyFixture() { fixture.close(); }

    @Test void sustainedHttpFailureAndReturnProduceOrderedDurableEventsAndOnlyLocalAlerts() throws Exception {
        var clock = new MutableClock(start);
        var configuration = fixture.configuration();
        var run = UUID.randomUUID();
        try (var journal = new MonitorJournal(output, run); var http = new HttpAvailabilityProbe(configuration)) {
            var policy = new AvailabilityPolicy();
            var monitor = monitor(configuration, http, policy, journal, clock);
            monitor.start(); assertThat(monitor.sampleOnce().allHealthy()).isTrue();
            fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 503, "application/json", "{\"status\":\"DOWN\",\"detail\":\"SYNTHETIC_SECRET_NEVER_RECORDED\"}"));
            clock.at(start.plusSeconds(60)); assertThat(monitor.sampleOnce().events()).isEmpty();
            clock.at(start.plusSeconds(120)); assertThat(monitor.sampleOnce().events()).isEmpty();
            clock.at(start.plusSeconds(180));
            var failure = monitor.sampleOnce().events().getFirst();
            assertThat(failure.kind()).isEqualTo(AvailabilityPolicy.Kind.CONFIRMED_FAILURE);
            assertThat(failure.detectionSeconds()).isEqualTo(120);
            fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 200, "application/json", "{\"status\":\"UP\"}"));
            clock.at(start.plusSeconds(240)); assertThat(monitor.sampleOnce().events()).isEmpty();
            clock.at(start.plusSeconds(300));
            var recovery = monitor.sampleOnce().events().getFirst();
            assertThat(recovery.kind()).isEqualTo(AvailabilityPolicy.Kind.VERIFIED_RECOVERY);
            assertThat(recovery.incidentId()).isEqualTo(failure.incidentId());
            assertThat(recovery.recoverySeconds()).isEqualTo(120);
            monitor.stop("SAMPLE_LIMIT_REACHED");
            assertThat(journal.sha256()).matches("[a-f0-9]{64}");
            var records = records(journal.journal());
            assertThat(records).hasSize(12); // start, six HTTP samples, two incidents, two local receipts, stop
            for (int index = 0; index < records.size(); index++) {
                assertThat(records.get(index).path("journalSequence").asLong()).isEqualTo(index + 1);
                assertThat(records.get(index).path("runId").asText()).isEqualTo(run.toString());
            }
            assertThat(records.getFirst().path("details").path("clock").asText()).isEqualTo("SYNTHETIC_TEST_CLOCK");
            assertThat(records.getFirst().path("details").path("acceleratedValidation").asBoolean()).isTrue();
            var heartbeat = json.readTree(Files.readAllBytes(journal.directory().resolve("heartbeat.json")));
            assertThat(heartbeat.path("sampleSequence").asLong()).isEqualTo(6);
            assertThat(heartbeat.path("incidentOpen").asBoolean()).isFalse();
            assertThat(heartbeat.path("externalCoverageAccredited").asBoolean()).isFalse();
            String text = Files.readString(journal.journal());
            assertThat(text).doesNotContain("SYNTHETIC_SECRET_NEVER_RECORDED", "127.0.0.1", "http://", "Authorization");
            try (var alerts = Files.list(journal.directory().resolve("local-test-alerts"))) {
                var files = alerts.toList(); assertThat(files).hasSize(2);
                for (var file : files) {
                    var alert = json.readTree(Files.readAllBytes(file));
                    assertThat(alert.path("transport").asText()).isEqualTo("LOCAL_TEST_ONLY");
                    assertThat(alert.path("externalDeliveryAccredited").asBoolean()).isFalse();
                }
            }
        }
        assertThat(fixture.count("/ready")).isEqualTo(6); assertThat(fixture.count("/live")).isEqualTo(6);
        assertThat(fixture.count("/crm")).isEqualTo(6);
    }

    @Test void applicationFailureIsNotHiddenByHealthyApiAndRepeatedFailuresDoNotDuplicateAlerts() throws Exception {
        var clock = new MutableClock(start); var configuration = fixture.configuration();
        fixture.application.set(e -> MonitorHttpFixture.respond(e, 503, "text/html", "SYNTHETIC DOWN"));
        try (var journal = new MonitorJournal(output, UUID.randomUUID()); var http = new HttpAvailabilityProbe(configuration)) {
            var policy = new AvailabilityPolicy(); var monitor = monitor(configuration, http, policy, journal, clock); monitor.start();
            for (int i = 0; i < 5; i++) {
                clock.at(start.plusSeconds(i * 60L)); var sample = monitor.sampleOnce();
                assertThat(sample.allHealthy()).isFalse();
                assertThat(sample.events()).hasSize(i == 2 ? 1 : 0);
            }
            assertThat(policy.incidentOpen()).isTrue(); monitor.stop("REQUESTED_STOP");
            assertThat(records(journal.journal()).stream().filter(r -> "LOCAL_TEST_ALERT".equals(r.path("type").asText()))).hasSize(1);
            assertThat(records(journal.journal()).getLast().path("details").path("incidentStillOpen").asBoolean()).isTrue();
        }
        assertThat(fixture.count("/crm")).isEqualTo(5);
    }

    @Test void duplicateOrRegressedObservationTimeStopsBeforeAnotherRequestWithoutResettingState() throws Exception {
        var clock = new MutableClock(start); var configuration = fixture.configuration();
        fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 503, "application/json", "{}"));
        try (var journal = new MonitorJournal(output, UUID.randomUUID()); var http = new HttpAvailabilityProbe(configuration)) {
            var policy = new AvailabilityPolicy(); var monitor = monitor(configuration, http, policy, journal, clock); monitor.start();
            monitor.sampleOnce(); assertThat(policy.suspectedFailure()).isTrue();
            assertThatThrownBy(monitor::sampleOnce).isInstanceOf(IllegalStateException.class).hasMessage("NON_MONOTONIC_OBSERVATION_TIME");
            clock.at(start.minusSeconds(1));
            assertThatThrownBy(monitor::sampleOnce).isInstanceOf(IllegalStateException.class);
            assertThat(policy.suspectedFailure()).isTrue();
            assertThat(fixture.count("/ready")).isEqualTo(1);
            monitor.stop("SAFE_FAILURE");
        }
    }

    @Test void localAdapterIsIdempotentButDoesNotClaimAnyExternalNotification() throws Exception {
        var policy = new AvailabilityPolicy(); policy.observe(start, false); policy.observe(start.plusSeconds(60), false);
        var event = policy.observe(start.plusSeconds(120), false).getFirst();
        var local = new LocalAlertAdapter(output);
        var first = local.record(event); var duplicate = local.record(event);
        assertThat(first.reference()).isEqualTo(duplicate.reference()); assertThat(first.sha256()).isEqualTo(duplicate.sha256());
        assertThat(first.result()).isEqualTo("RECORDED_LOCAL_ONLY_NOT_EXTERNALLY_DELIVERED");
        assertThat(duplicate.result()).isEqualTo("ALREADY_RECORDED_LOCAL_ONLY");
        try (var files = Files.list(output.resolve("local-test-alerts"))) { assertThat(files).hasSize(1); }
    }

    @Test void existingRunCannotBeOverwrittenOrUsedToInventIncidentContinuity() throws Exception {
        UUID run = UUID.randomUUID();
        try (var journal = new MonitorJournal(output, run)) { journal.append("SYNTHETIC_EXISTING", start, java.util.Map.of()); }
        Path file = output.resolve("monitor-" + run).resolve("events.jsonl"); String before = Files.readString(file);
        assertThatThrownBy(() -> new MonitorJournal(output, run)).isInstanceOf(java.nio.file.FileAlreadyExistsException.class);
        assertThat(Files.readString(file)).isEqualTo(before);
    }

    private AvailabilityMonitor monitor(MonitorConfiguration configuration, HttpAvailabilityProbe http,
                                        AvailabilityPolicy policy, MonitorJournal journal, Clock clock) throws Exception {
        return new AvailabilityMonitor(configuration, http, policy, journal, new LocalAlertAdapter(journal.directory()),
            clock, AvailabilityMonitor.TimeBasis.SYNTHETIC_TEST_CLOCK);
    }
    private List<JsonNode> records(Path file) throws Exception {
        var result = new java.util.ArrayList<JsonNode>();
        for (String line : Files.readAllLines(file)) result.add(json.readTree(line));
        return result;
    }
    private static final class MutableClock extends Clock {
        private Instant value; MutableClock(Instant value) { this.value = value; }
        void at(Instant next) { value = next; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return value; }
    }
}
