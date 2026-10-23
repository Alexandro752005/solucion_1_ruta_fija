package pe.rutafija.availability.monitor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class AvailabilityMonitorCliTest {
    @TempDir Path temporary;
    private final ObjectMapper json = new ObjectMapper();

    @Test void cooperativeStopAfterARealHttpSampleWritesTheFinalJournalWithoutInterruptingItsFileChannel() throws Exception {
        try (var fixture = new MonitorHttpFixture()) {
            var stop = new java.util.concurrent.atomic.AtomicBoolean();
            fixture.application.set(exchange -> {
                MonitorHttpFixture.respond(exchange,200,"text/html","<!doctype html><html><body><rf-root></rf-root><script type=\"module\" src=\"/synthetic.js\"></script></body></html>");
                stop.set(true);
            });
            Path output=temporary.resolve("cooperative-stop");
            var stdout=new ByteArrayOutputStream(); var stderr=new ByteArrayOutputStream();
            int result=AvailabilityMonitorCli.runUntilStopRequested(new String[]{"--mode","validation-loopback",
                "--readiness",fixture.uri("/ready").toString(),"--liveness",fixture.uri("/live").toString(),
                "--application",fixture.uri("/crm").toString(),"--output",output.toString(),"--interval-millis","100"},
                new PrintStream(stdout),new PrintStream(stderr),stop::get);
            assertThat(result).isZero(); assertThat(stderr.size()).isZero();
            Path run; try(var directories=Files.list(output)){run=directories.findFirst().orElseThrow();}
            var records=Files.readAllLines(run.resolve("events.jsonl"));
            assertThat(json.readTree(records.getLast()).path("type").asText()).isEqualTo("MONITOR_STOPPED");
            assertThat(json.readTree(records.getLast()).path("details").path("reason").asText()).isEqualTo("REQUESTED_STOP");
            assertThat(json.readTree(records.getLast()).path("details").path("samples").asInt()).isEqualTo(1);
            assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("MONITOR_JOURNAL_SHA256=");
            assertThat(fixture.count("/ready")).isEqualTo(1); assertThat(fixture.count("/crm")).isEqualTo(1);
        }
    }

    @Test void standaloneJvmObservesRealHttpFailureAndRecoveryWithRealTimestampsAndExplicitAcceleratedScope() throws Exception {
        try (var fixture = new MonitorHttpFixture()) {
            fixture.readiness.set(e -> MonitorHttpFixture.respond(e, fixture.count("/ready") <= 3 ? 503 : 200,
                "application/json", fixture.count("/ready") <= 3 ? "{\"status\":\"DOWN\"}" : "{\"status\":\"UP\"}"));
            Path root = temporary.resolve("runs"); Path console = temporary.resolve("safe-console.txt");
            var command = new ArrayList<String>();
            command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            command.addAll(List.of("-Xms16m", "-Xmx96m", "-cp",
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
                AvailabilityMonitorCli.class.getName(), "--mode", "validation-loopback",
                "--readiness", fixture.uri("/ready").toString(), "--liveness", fixture.uri("/live").toString(),
                "--application", fixture.uri("/crm").toString(), "--output", root.toString(),
                "--interval-millis", "200", "--max-samples", "5"));
            Process child = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(console.toFile()).start();
            try {
                assertThat(child.waitFor(20, TimeUnit.SECONDS)).isTrue();
                assertThat(child.exitValue()).as(Files.readString(console)).isZero();
            } finally { if (child.isAlive()) child.destroyForcibly(); }
            String safeConsole = Files.readString(console);
            assertThat(safeConsole).contains("MONITOR_MODE=VALIDATION_LOOPBACK", "MONITOR_EXECUTION=STOPPED_AFTER_REAL_HTTP_SAMPLES",
                "MONITOR_ALERT_TRANSPORT=LOCAL_TEST_ONLY", "MONITOR_HOST_INDEPENDENCE_AND_24X7_COVERAGE=NOT_ACCREDITED");
            Path run;
            try (var directories = Files.list(root)) { var paths = directories.toList(); assertThat(paths).hasSize(1); run = paths.getFirst(); }
            var records = new ArrayList<JsonNode>();
            for (String line : Files.readAllLines(run.resolve("events.jsonl"))) records.add(json.readTree(line));
            assertThat(records.getFirst().path("details").path("clock").asText()).isEqualTo("REAL_UTC_WALL_CLOCK");
            assertThat(records.getFirst().path("details").path("acceleratedValidation").asBoolean()).isTrue();
            var incidents = records.stream().filter(r -> "INCIDENT_EVENT".equals(r.path("type").asText())).toList();
            assertThat(incidents).hasSize(2);
            assertThat(incidents.getFirst().path("details").path("kind").asText()).isEqualTo("CONFIRMED_FAILURE");
            assertThat(incidents.getLast().path("details").path("kind").asText()).isEqualTo("VERIFIED_RECOVERY");
            assertThat(Instant.parse(incidents.getLast().path("at").asText()))
                .isAfter(Instant.parse(incidents.getFirst().path("at").asText()));
            assertThat(fixture.count("/ready")).isEqualTo(5); assertThat(fixture.count("/live")).isEqualTo(5);
            assertThat(fixture.count("/crm")).isEqualTo(5);
            assertThat(fixture.credentialHeaders).hasValue(0);
        }
    }

    @Test void configurationCheckHasNoFilesystemOrNetworkSideEffects() throws Exception {
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        Path output = temporary.resolve("not-created");
        int result = AvailabilityMonitorCli.run(new String[]{"--mode", "production-cadence",
            "--readiness", "https://synthetic.invalid/ready", "--liveness", "https://synthetic.invalid/live",
            "--application", "https://synthetic.invalid/crm", "--output", output.toString(), "--validate-only"},
            new PrintStream(stdout), new PrintStream(stderr));
        assertThat(result).isZero(); assertThat(output).doesNotExist();
        assertThat(stdout.toString(StandardCharsets.UTF_8)).contains("VALIDATED_NO_HTTP_NO_FILES_NO_DATABASE_NO_ALERTS");
        assertThat(stderr.size()).isZero();
    }

    @Test void invalidConfigurationNeverEchoesAccidentallySuppliedSecretsOrCreatesFiles() throws Exception {
        var stdout = new ByteArrayOutputStream(); var stderr = new ByteArrayOutputStream();
        Path output = temporary.resolve("not-created");
        int result = AvailabilityMonitorCli.run(new String[]{"--mode", "validation-loopback",
            "--readiness", "http://SYNTHETIC_SECRET@127.0.0.1/ready", "--liveness", "http://127.0.0.1/live",
            "--application", "http://127.0.0.1/crm", "--output", output.toString()}, new PrintStream(stdout), new PrintStream(stderr));
        assertThat(result).isEqualTo(2); assertThat(output).doesNotExist(); assertThat(stdout.size()).isZero();
        assertThat(stderr.toString(StandardCharsets.UTF_8)).contains("REFUSED_INVALID_PUBLIC_CONFIGURATION").doesNotContain("SYNTHETIC_SECRET");
    }

    @Test void duplicateOptionsAndAcceleratedProductionCadenceAreRefused() {
        assertThatThrownBy(() -> AvailabilityMonitorCli.Parameters.parse(new String[]{"--mode", "validation-loopback", "--mode", "validation-loopback"}))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AvailabilityMonitorCli.Parameters.parse(new String[]{"--mode", "production-cadence", "--readiness", "https://synthetic.invalid/ready",
            "--liveness", "https://synthetic.invalid/live", "--application", "https://synthetic.invalid/crm", "--output", temporary.toString(), "--interval-millis", "60000"}))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
