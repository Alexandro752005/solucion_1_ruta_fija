package pe.rutafija.availability.monitor;

import pe.rutafija.availability.domain.AvailabilityPolicy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Separate-process coordinator, independent of Spring; no service restart or external notification authority. */
public final class AvailabilityMonitor {
    public enum TimeBasis { REAL_UTC_WALL_CLOCK, SYNTHETIC_TEST_CLOCK }
    public record Sample(long sequence, Instant startedAt, Instant observedAt, List<ProbeResult> probes,
                         boolean allHealthy, List<AvailabilityPolicy.Event> events) { }
    private final MonitorConfiguration configuration;
    private final HttpAvailabilityProbe http;
    private final AvailabilityPolicy policy;
    private final MonitorJournal journal;
    private final LocalAlertAdapter alerts;
    private final Clock clock;
    private final TimeBasis timeBasis;
    private Instant previousObservation;
    private long sequence;
    private boolean started, stopped;

    public AvailabilityMonitor(MonitorConfiguration configuration, HttpAvailabilityProbe http,
                               AvailabilityPolicy policy, MonitorJournal journal, LocalAlertAdapter alerts,
                               Clock clock, TimeBasis timeBasis) {
        this.configuration = configuration; this.http = http; this.policy = policy;
        this.journal = journal; this.alerts = alerts; this.clock = clock; this.timeBasis = timeBasis;
    }

    public synchronized void start() throws IOException {
        if (started) throw new IllegalStateException("MONITOR_ALREADY_STARTED");
        var fields = new LinkedHashMap<String, Object>();
        fields.put("mode", configuration.mode().name()); fields.put("intervalMillis", configuration.interval().toMillis());
        fields.put("connectTimeoutMillis", configuration.connectTimeout().toMillis());
        fields.put("completeResponseTimeoutMillis", configuration.requestTimeout().toMillis());
        fields.put("clock", timeBasis.name());
        fields.put("acceleratedValidation", configuration.mode() == MonitorConfiguration.Mode.VALIDATION_LOOPBACK
            && configuration.interval().toSeconds() < 60);
        fields.put("stateContinuity", "NEW_PROCESS_NO_PRIOR_INCIDENT_STATE_OR_COVERAGE_ACCREDITED");
        fields.put("hostIndependenceAccredited", false); fields.put("externalAlertDeliveryAccredited", false);
        fields.put("human24x7CoverageAccredited", false); fields.put("automaticRestartsEnabled", false);
        fields.put("probes", configuration.probes().stream().map(p -> Map.of("id", p.id(), "expected", p.expected().name(),
            "publicEndpointSha256", LocalAlertAdapter.sha256(p.uri().toASCIIString().getBytes(StandardCharsets.UTF_8)))).toList());
        journal.append("MONITOR_STARTED", clock.instant(), fields);
        started = true;
    }

    public synchronized Sample sampleOnce() throws IOException, InterruptedException {
        if (!started || stopped) throw new IllegalStateException("MONITOR_NOT_RUNNING");
        Instant begin = clock.instant();
        if (previousObservation != null && !begin.isAfter(previousObservation)) {
            journal.append("CLOCK_ORDER_REFUSED", begin, Map.of("sampleNotApplied", true, "requestsSent", 0));
            throw new IllegalStateException("NON_MONOTONIC_OBSERVATION_TIME");
        }
        var results = new ArrayList<ProbeResult>();
        for (var definition : configuration.probes()) results.add(http.sample(definition));
        Instant observed = clock.instant();
        if (observed.isBefore(begin) || previousObservation != null && !observed.isAfter(previousObservation)) {
            journal.append("CLOCK_ORDER_REFUSED", observed, Map.of("sampleNotApplied", true));
            throw new IllegalStateException("NON_MONOTONIC_OBSERVATION_TIME");
        }
        boolean healthy = results.stream().allMatch(ProbeResult::healthy);
        var events = policy.observe(observed, healthy);
        previousObservation = observed;
        var sample = new Sample(++sequence, begin, observed, List.copyOf(results), healthy, events);
        journal.append("HTTP_SAMPLE", observed, Map.of("sampleSequence", sequence, "startedAt", begin.toString(),
            "allRequiredProbesHealthy", healthy, "probes", results));
        for (var event : events) {
            journal.append("INCIDENT_EVENT", observed, MonitorJournal.eventFields(event));
            var receipt = alerts.record(event);
            journal.append("LOCAL_TEST_ALERT", observed, Map.of("sampleSequence", sequence,
                "incidentId", event.incidentId().toString(), "kind", event.kind().name(),
                "reference", receipt.reference(), "sha256", receipt.sha256(), "result", receipt.result(),
                "externalDeliveryAccredited", false));
        }
        journal.heartbeat(observed, sequence, healthy, policy.incidentOpen(), policy.suspectedFailure());
        return sample;
    }

    public synchronized void stop(String safeReason) throws IOException {
        if (!started || stopped) return;
        if (!List.of("REQUESTED_STOP", "SAMPLE_LIMIT_REACHED", "INTERRUPTED", "SAFE_FAILURE").contains(safeReason)) {
            throw new IllegalArgumentException("UNKNOWN_STOP_REASON");
        }
        journal.append("MONITOR_STOPPED", clock.instant(), Map.of("reason", safeReason, "samples", sequence,
            "incidentStillOpen", policy.incidentOpen(), "coverageAfterStopAccredited", false));
        stopped = true;
    }
}
