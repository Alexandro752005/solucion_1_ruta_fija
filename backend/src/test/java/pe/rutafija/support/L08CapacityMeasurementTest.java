package pe.rutafija.support;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/** Pure measurements/guards only; no context, connection, API, account, SMTP or fixture. */
class L08CapacityMeasurementTest {
    @Test void p95UsesNearestRankRatherThanDiscardingSlowTail() {
        var hundred = IntStream.rangeClosed(1, 100).mapToObj(i -> (double)i).toList();
        assertThat(L08CapacityMeasurement.nearestRank(hundred, .95)).isEqualTo(95);
        assertThat(L08CapacityMeasurement.nearestRank(hundred, .99)).isEqualTo(99);
        assertThat(L08CapacityMeasurement.nearestRank(hundred, 1)).isEqualTo(100);
    }

    @Test void smallSamplesKeepExplicitNearestRankAndDoNotClaimPopulationCoverage() {
        assertThat(L08CapacityMeasurement.nearestRank(List.of(11d), .95)).isEqualTo(11);
        assertThat(L08CapacityMeasurement.nearestRank(IntStream.rangeClosed(1, 20).mapToObj(i -> (double)i).toList(), .95)).isEqualTo(19);
    }

    @Test void requiresNonemptyTimingsAndAValidPercentile() {
        assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.nearestRank(null, .95));
        assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.nearestRank(List.of(), .95));
        for (double p : new double[]{0, -1, 1.001, Double.NaN}) {
            assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.nearestRank(List.of(1d), p));
        }
    }

    @Test void rejectsNegativeNonfiniteAndUnsortedDurations() {
        for (var values : List.of(List.of(-1d), List.of(Double.NaN), List.of(Double.POSITIVE_INFINITY), List.of(2d, 1d))) {
            assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.nearestRank(values, .95));
        }
    }

    @Test void reportsHttpDurableAndObserverTimesSeparately() {
        var samples = new ArrayList<Map<String,Object>>();
        for (int i = 1; i <= 100; i++) samples.add(Map.of("httpResponseMs", i * 1d,
                "durableCommitObservedMs", i * 2d, "observerAfterHttpMs", i * 1d));
        var result = L08CapacityMeasurement.metrics(samples);
        assertThat(result).containsEntry("successfulDurableSamples", 100)
                .containsEntry("p95HttpResponseMs", 95d).containsEntry("p95DurableCommitObservedMs", 190d)
                .containsEntry("p95ObserverAfterHttpMs", 95d).containsEntry("maxDurableCommitObservedMs", 200d)
                .containsEntry("percentileMethod", "NEAREST_RANK_CEIL_P_TIMES_N");
        assertThat(result).doesNotContainKeys("networkOnlyMeasured", "serverOnlyMeasured");
    }

    @Test void noSamplesNeverProducesAPercentileOrSuccess() {
        assertThat(L08CapacityMeasurement.metrics(List.of())).containsOnly(Map.entry("successfulDurableSamples", 0));
        assertThat(L08CapacityMeasurement.resultCode(false, false, 25, null)).isEqualTo("FAIL_MEASURED_LOCAL_SYNTHETIC_ONLY");
    }

    @Test void failuresAndInsufficientObservedConcurrencyNeverProducePass() {
        assertThat(L08CapacityMeasurement.resultCode(true, true, 25, "HTTP_TIMEOUT")).isEqualTo("PENDING_FAILURE_KEEP_CASE");
        assertThat(L08CapacityMeasurement.resultCode(true, false, 25, null)).isEqualTo("FAIL_MEASURED_LOCAL_SYNTHETIC_ONLY");
        assertThat(L08CapacityMeasurement.resultCode(true, true, 24, null)).isEqualTo("FAIL_MEASURED_LOCAL_SYNTHETIC_ONLY");
        assertThat(L08CapacityMeasurement.resultCode(false, true, 25, null)).isEqualTo("FAIL_MEASURED_LOCAL_SYNTHETIC_ONLY");
        assertThat(L08CapacityMeasurement.resultCode(true, true, 25, null)).isEqualTo("PASS_MEASURED_LOCAL_SYNTHETIC_ONLY");
    }

    @Test void reconciledCommitsWarmupAndErrorsAreNotFabricatedLatencySamples() {
        var real = Map.<String,Object>of("phase", "measurement", "operation", "CREATE", "success", true, "durableCommitObservedMs", 12d);
        var reconciled = Map.<String,Object>of("phase", "resume-support-not-new-creation-measurement", "operation", "CREATE", "success", true, "durableCommitObservedMs", 1d);
        var warmup = Map.<String,Object>of("phase", "warmup", "operation", "CREATE", "success", true, "durableCommitObservedMs", 1d);
        var rejected = Map.<String,Object>of("phase", "measurement", "operation", "CREATE", "success", false, "durableCommitObservedMs", 1d);
        var responseOnly = Map.<String,Object>of("phase", "measurement", "operation", "CREATE", "success", true, "httpResponseMs", 1d);
        assertThat(L08CapacityMeasurement.measuredSamples(List.of(real,reconciled,warmup,rejected,responseOnly), "CREATE")).containsExactly(real);
        assertThat(L08CapacityMeasurement.measuredSamples(List.of(real), "ACCEPT")).isEmpty();
    }

    @Test void fiveSchedulesNeverOverlapThirtyMinuteServicesForTheSameResources() {
        Instant origin = Instant.parse("2026-10-23T16:00:00Z");
        for (int cycle = 0; cycle < 5; cycle++) {
            Instant begin = L08CapacityMeasurement.scheduledAt(origin, cycle);
            assertThat(begin).isEqualTo(origin.plus(Duration.ofHours(cycle * 2L)));
            if (cycle < 4) assertThat(begin.plusSeconds(1800)).isBefore(L08CapacityMeasurement.scheduledAt(origin, cycle + 1));
        }
    }

    @Test void invalidCyclesCannotInventMoreLoadOutsideThePlannedSeries() {
        Instant origin = Instant.parse("2026-10-23T16:00:00Z");
        for (int cycle : new int[]{-1, 5, 100}) assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.scheduledAt(origin, cycle));
        assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.scheduledAt(null, 0));
    }

    @Test void onlyTheExactNewDatasetIsAccepted() {
        assertThatNoException().isThrownBy(() -> L08CapacityMeasurement.exactTarget("jdbc:postgresql://127.0.0.1:55439/ruta_fija_test"));
        for (String url : List.of("jdbc:postgresql://127.0.0.1:5432/ruta_fija_test", "jdbc:postgresql://127.0.0.1:55438/ruta_fija_test",
                "jdbc:postgresql://localhost:55439/ruta_fija_test", "jdbc:postgresql://127.0.0.1:55439/ruta_fija_piloto",
                "jdbc:postgresql://127.0.0.1:55439/ruta_fija_test?user=rf_test")) {
            assertThatIllegalStateException().isThrownBy(() -> L08CapacityMeasurement.exactTarget(url));
        }
    }
}
