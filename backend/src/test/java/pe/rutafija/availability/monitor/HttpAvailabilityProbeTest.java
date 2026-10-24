package pe.rutafija.availability.monitor;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static pe.rutafija.availability.monitor.ProbeResult.Outcome.*;

class HttpAvailabilityProbeTest {
    private MonitorHttpFixture fixture;
    @BeforeEach void startRealLoopback() throws Exception { fixture = new MonitorHttpFixture(); }
    @AfterEach void stopOnlyFixtureServer() { fixture.close(); }

    @Test void actualHttpHealthAndApplicationChecksUseOneGetEachWithoutCredentials() throws Exception {
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            for (var probe : configuration.probes()) assertThat(http.sample(probe).outcome()).isEqualTo(HEALTHY);
        }
        assertThat(fixture.count("/ready")).isEqualTo(1);
        assertThat(fixture.count("/live")).isEqualTo(1);
        assertThat(fixture.count("/crm")).isEqualTo(1);
        assertThat(fixture.credentialHeaders).hasValue(0);
        assertThat(fixture.nonGetRequests).hasValue(0);
    }
    @Test void one503DoesNotRetryFollowAResponseBodyOrLeakItsContents() throws Exception {
        fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 503, "application/json", "SYNTHETIC_SECRET_NOT_LOGGED"));
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            var result = http.sample(configuration.probes().getFirst());
            assertThat(result.outcome()).isEqualTo(HTTP_STATUS_REJECTED);
            assertThat(result.httpStatus()).isEqualTo(503);
            assertThat(result.toString()).doesNotContain("SYNTHETIC_SECRET_NOT_LOGGED");
        }
        assertThat(fixture.count("/ready")).isEqualTo(1);
    }
    @Test void redirectsAreFailuresAndNeverExpandLoopbackOrContactAnotherDestination() throws Exception {
        fixture.readiness.set(e -> { e.getResponseHeaders().set("Location", fixture.uri("/live").toString());
            MonitorHttpFixture.respond(e, 302, "text/html", "SYNTHETIC REDIRECT"); });
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThat(http.sample(configuration.probes().getFirst()).outcome()).isEqualTo(HTTP_STATUS_REJECTED);
        }
        assertThat(fixture.count("/ready")).isEqualTo(1); assertThat(fixture.count("/live")).isZero();
    }
    @ParameterizedTest @ValueSource(strings = {"NOT JSON", "{}", "{\"status\":\"DOWN\"}", "{\"status\":true}",
        "{\"status\":\"DOWN\",\"status\":\"UP\"}", "{\"status\":\"UP\"} {\"status\":\"DOWN\"}"})
    void invalidOrAmbiguousPayloadCannotBecomeHealthyEvenWhenStatusIs200(String body) throws Exception {
        fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 200, "application/json", body));
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThat(http.sample(configuration.probes().getFirst()).outcome()).isEqualTo(INVALID_HEALTH_PAYLOAD);
        }
        assertThat(fixture.count("/ready")).isEqualTo(1);
    }
    @Test void wrongContentTypeDoesNotMasqueradeAsHealth() throws Exception {
        fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 200, "text/html", "{\"status\":\"UP\"}"));
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThat(http.sample(configuration.probes().getFirst()).outcome()).isEqualTo(INVALID_CONTENT_TYPE);
        }
    }
    @Test void arbitraryTextIsNotApplicationAvailability() throws Exception {
        fixture.application.set(e -> MonitorHttpFixture.respond(e, 200, "text/html", "SYNTHETIC ERROR WITHOUT AN HTML PAGE"));
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThat(http.sample(configuration.probes().getLast()).outcome()).isEqualTo(INVALID_HTML_PAYLOAD);
        }
    }
    @Test void genericMaintenanceHtml200DoesNotMasqueradeAsTheRutaFijaCrmShell() throws Exception {
        fixture.application.set(e -> MonitorHttpFixture.respond(e, 200, "text/html", "<!doctype html><html><body>SYNTHETIC MAINTENANCE</body></html>"));
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThat(http.sample(configuration.probes().getLast()).outcome()).isEqualTo(INVALID_HTML_PAYLOAD);
        }
    }
    @Test void bodyLimitCancelsAnOversizedResponseWithoutPersistingIt() throws Exception {
        fixture.readiness.set(e -> MonitorHttpFixture.respond(e, 200, "application/json", "X".repeat(HttpAvailabilityProbe.MAX_RESPONSE_BYTES + 1)));
        var configuration = fixture.configuration();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThat(http.sample(configuration.probes().getFirst()).outcome()).isEqualTo(BODY_TOO_LARGE);
        }
        assertThat(fixture.count("/ready")).isEqualTo(1);
    }
    @Test void timeoutIncludesWaitingForHeadersAndDoesNotAutomaticallyRetry() throws Exception {
        fixture.readiness.set(e -> { Thread.sleep(700); MonitorHttpFixture.respond(e, 200, "application/json", "{\"status\":\"UP\"}"); });
        var configuration = fixture.configuration(Duration.ofMillis(100), Duration.ofMillis(200));
        try (var http = new HttpAvailabilityProbe(configuration)) {
            var result = http.sample(configuration.probes().getFirst());
            assertThat(result.outcome()).isEqualTo(TIMEOUT); assertThat(result.elapsedMillis()).isLessThan(2500);
        }
        assertThat(fixture.count("/ready")).isEqualTo(1);
    }
    @Test void deadlineAlsoCoversBodyStalledAfterHeaders() throws Exception {
        fixture.readiness.set(e -> {
            e.getResponseHeaders().set("Content-Type", "application/json"); e.sendResponseHeaders(200, 0);
            e.getResponseBody().write('{'); e.getResponseBody().flush(); Thread.sleep(700);
        });
        var configuration = fixture.configuration(Duration.ofMillis(100), Duration.ofMillis(200));
        try (var http = new HttpAvailabilityProbe(configuration)) {
            var result = http.sample(configuration.probes().getFirst());
            assertThat(result.outcome()).isEqualTo(TIMEOUT); assertThat(result.elapsedMillis()).isLessThan(2500);
        }
        assertThat(fixture.count("/ready")).isEqualTo(1);
    }
    @Test void stoppedRealServerIsATransportFailureNotAnInventedHealthyResponse() throws Exception {
        var configuration = fixture.configuration(); fixture.stopServer();
        try (var http = new HttpAvailabilityProbe(configuration)) {
            var result = http.sample(configuration.probes().getFirst());
            assertThat(result.outcome()).isEqualTo(TRANSPORT_ERROR);
            assertThat(result.httpStatus()).isNull();
        }
    }
    @Test void samplingAnUnconfiguredDestinationIsRefusedBeforeAnyRequest() throws Exception {
        var configuration = fixture.configuration();
        var outside = new MonitorConfiguration.Probe("other", fixture.uri("/other"), MonitorConfiguration.Expected.HEALTH_UP);
        try (var http = new HttpAvailabilityProbe(configuration)) {
            assertThatThrownBy(() -> http.sample(outside)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(fixture.count("/other")).isZero();
    }
}
