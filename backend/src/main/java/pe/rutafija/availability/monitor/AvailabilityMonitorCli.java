package pe.rutafija.availability.monitor;

import pe.rutafija.availability.domain.AvailabilityPolicy;

import java.io.PrintStream;
import java.net.URI;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Standalone JVM entry point. Never bootstraps Spring, connects to a database or sends external alerts. */
public final class AvailabilityMonitorCli {
    private AvailabilityMonitorCli() { }

    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) System.exit(status);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        return runUntilStopRequested(args, out, err, () -> false);
    }

    /** Embedders request a graceful stop between samples, without interrupting a durable journal write. */
    public static int runUntilStopRequested(String[] args, PrintStream out, PrintStream err, BooleanSupplier requestedStop) {
        Parameters parameters;
        try { parameters = Parameters.parse(args); }
        catch (RuntimeException refused) {
            // Arguments may contain accidentally supplied secrets: never print them or exception text.
            err.println("MONITOR_EXECUTION=REFUSED_INVALID_PUBLIC_CONFIGURATION");
            return 2;
        }
        if (parameters.validateOnly()) {
            out.println("MONITOR_CONFIGURATION=VALIDATED_NO_HTTP_NO_FILES_NO_DATABASE_NO_ALERTS");
            out.println("MONITOR_ALERT_TRANSPORT=LOCAL_TEST_ONLY");
            return 0;
        }
        var stop = new AtomicBoolean();
        var finished = new CountDownLatch(1);
        var stopSignal = new CountDownLatch(1);
        Thread hook = new Thread(() -> {
            // Interrupting an active FileChannel can truncate the final journal.
            // Wake the cadence wait instead; bounded HTTP probes finish normally.
            stop.set(true); stopSignal.countDown();
            try { finished.await(35, TimeUnit.SECONDS); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }, "ruta-fija-monitor-safe-stop");
        Runtime.getRuntime().addShutdownHook(hook);
        try (var journal = new MonitorJournal(parameters.outputRoot(), UUID.randomUUID());
             var http = new HttpAvailabilityProbe(parameters.configuration())) {
            var monitor = new AvailabilityMonitor(parameters.configuration(), http, new AvailabilityPolicy(), journal,
                new LocalAlertAdapter(journal.directory()), Clock.systemUTC(), AvailabilityMonitor.TimeBasis.REAL_UTC_WALL_CLOCK);
            monitor.start();
            out.println("MONITOR_RUN_ID=" + journal.runId());
            out.println("MONITOR_MODE=" + parameters.configuration().mode());
            out.println("MONITOR_JOURNAL=" + journal.journal());
            out.println("MONITOR_ALERT_TRANSPORT=LOCAL_TEST_ONLY");
            out.println("MONITOR_HOST_INDEPENDENCE_AND_24X7_COVERAGE=NOT_ACCREDITED");
            String reason = "SAFE_FAILURE";
            long completed = 0;
            try {
                long next = System.nanoTime();
                long interval = parameters.configuration().interval().toNanos();
                while (!stop.get() && !requestedStop.getAsBoolean()) {
                    var sample = monitor.sampleOnce();
                    completed++;
                    out.println("MONITOR_SAMPLE=" + sample.sequence() + " ALL_REQUIRED_HEALTHY=" + sample.allHealthy());
                    if (parameters.maxSamples() != 0 && completed >= parameters.maxSamples()) {
                        reason = "SAMPLE_LIMIT_REACHED"; break;
                    }
                    next += interval;
                    long now = System.nanoTime();
                    if (next <= now) {
                        // Skip missed slots rather than retrying samples in a catch-up loop.
                        long skipped = (now - next) / interval + 1;
                        next += skipped * interval;
                        journal.append("CADENCE_OVERRUN", Clock.systemUTC().instant(),
                            Map.of("skippedSlots", skipped, "catchUpRequests", 0));
                    }
                    stopSignal.await(Math.max(0, next - System.nanoTime()), TimeUnit.NANOSECONDS);
                }
                if (stop.get() || requestedStop.getAsBoolean()) reason = "REQUESTED_STOP";
            } catch (InterruptedException interrupted) {
                reason = "INTERRUPTED";
                // Temporarily clear interruption so the durable final journal can be written.
                Thread.interrupted();
            } finally {
                monitor.stop(reason);
            }
            out.println(completed > 0 ? "MONITOR_EXECUTION=STOPPED_AFTER_REAL_HTTP_SAMPLES"
                : "MONITOR_EXECUTION=STOPPED_WITHOUT_COMPLETED_HTTP_SAMPLE");
            out.println("MONITOR_JOURNAL_SHA256=" + journal.sha256());
            return 0;
        } catch (Exception failure) {
            err.println("MONITOR_EXECUTION=STOPPED_SAFE_FAILURE_KEEP_CASE_AND_LOGS");
            return 1;
        } finally {
            finished.countDown();
            try { Runtime.getRuntime().removeShutdownHook(hook); }
            catch (IllegalStateException shuttingDown) { /* Hook is already awaiting the completed final record. */ }
        }
    }

    record Parameters(MonitorConfiguration configuration, Path outputRoot, int maxSamples, boolean validateOnly) {
        static Parameters parse(String[] args) {
            var values = new LinkedHashMap<String, String>();
            Set<String> allowed = Set.of("--mode", "--readiness", "--liveness", "--application", "--output",
                "--interval-millis", "--connect-timeout-millis", "--request-timeout-millis", "--max-samples");
            boolean validateOnly = false;
            for (int i = 0; i < args.length; i++) {
                if ("--validate-only".equals(args[i])) {
                    if (validateOnly) throw new IllegalArgumentException();
                    validateOnly = true; continue;
                }
                if (!allowed.contains(args[i]) || i + 1 >= args.length || values.putIfAbsent(args[i], args[++i]) != null) {
                    throw new IllegalArgumentException();
                }
            }
            var mode = switch (required(values, "--mode")) {
                case "validation-loopback" -> MonitorConfiguration.Mode.VALIDATION_LOOPBACK;
                case "production-cadence" -> MonitorConfiguration.Mode.PRODUCTION_CADENCE_LOCAL_ALERT_ONLY;
                default -> throw new IllegalArgumentException();
            };
            if (mode == MonitorConfiguration.Mode.PRODUCTION_CADENCE_LOCAL_ALERT_ONLY
                && values.containsKey("--interval-millis")) throw new IllegalArgumentException();
            var configuration = new MonitorConfiguration(mode,
                Duration.ofMillis(Long.parseLong(values.getOrDefault("--interval-millis", "60000"))),
                Duration.ofMillis(Long.parseLong(values.getOrDefault("--connect-timeout-millis", "2000"))),
                Duration.ofMillis(Long.parseLong(values.getOrDefault("--request-timeout-millis", "5000"))),
                List.of(new MonitorConfiguration.Probe("api-readiness", URI.create(required(values, "--readiness")), MonitorConfiguration.Expected.HEALTH_UP),
                    new MonitorConfiguration.Probe("api-liveness", URI.create(required(values, "--liveness")), MonitorConfiguration.Expected.HEALTH_UP),
                    new MonitorConfiguration.Probe("crm-application", URI.create(required(values, "--application")), MonitorConfiguration.Expected.HTML_PAGE)));
            int max = Integer.parseInt(values.getOrDefault("--max-samples", "0"));
            if (max < 0 || max > 1_000_000) throw new IllegalArgumentException();
            return new Parameters(configuration, Path.of(required(values, "--output")), max, validateOnly);
        }
        private static String required(Map<String, String> values, String key) {
            String value = values.get(key);
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return value;
        }
    }
}
