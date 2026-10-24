package pe.rutafija.availability.monitor;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/** Public probe configuration only: authentication, query strings and redirect following are forbidden. */
public record MonitorConfiguration(Mode mode, Duration interval, Duration connectTimeout,
                                   Duration requestTimeout, List<Probe> probes) {
    public enum Mode { PRODUCTION_CADENCE_LOCAL_ALERT_ONLY, VALIDATION_LOOPBACK }
    public enum Expected { HEALTH_UP, HTML_PAGE }
    public record Probe(String id, URI uri, Expected expected) {
        public Probe {
            Objects.requireNonNull(id); Objects.requireNonNull(uri); Objects.requireNonNull(expected);
            if (!id.matches("[a-z][a-z0-9-]{0,47}")) throw invalid();
            String scheme = uri.getScheme();
            if (!uri.isAbsolute() || uri.getHost() == null || uri.getHost().isBlank()
                || !("https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme))
                || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                || uri.getPort() == 0 || uri.getPort() > 65535
                || uri.getRawPath() == null || !uri.getRawPath().startsWith("/")) throw invalid();
        }
    }

    public MonitorConfiguration {
        Objects.requireNonNull(mode); Objects.requireNonNull(interval);
        Objects.requireNonNull(connectTimeout); Objects.requireNonNull(requestTimeout);
        probes = List.copyOf(probes);
        if (probes.size() != 3 || probes.stream().map(Probe::id).distinct().count() != 3
            || probes.stream().map(Probe::uri).distinct().count() != 3
            || probes.stream().filter(p -> p.expected() == Expected.HEALTH_UP).count() != 2
            || probes.stream().filter(p -> p.expected() == Expected.HTML_PAGE).count() != 1
            || connectTimeout.compareTo(Duration.ofMillis(50)) < 0
            || connectTimeout.compareTo(Duration.ofSeconds(5)) > 0
            || requestTimeout.compareTo(connectTimeout) < 0
            || requestTimeout.compareTo(Duration.ofSeconds(10)) > 0) throw invalid();
        if (mode == Mode.PRODUCTION_CADENCE_LOCAL_ALERT_ONLY) {
            if (!interval.equals(Duration.ofSeconds(60))
                || probes.stream().anyMatch(p -> !"https".equalsIgnoreCase(p.uri().getScheme()))) throw invalid();
        } else {
            if (interval.compareTo(Duration.ofMillis(100)) < 0 || interval.compareTo(Duration.ofSeconds(60)) > 0
                || probes.stream().anyMatch(p -> !literalLoopback(p.uri()))) throw invalid();
        }
    }

    private static boolean literalLoopback(URI uri) {
        // No hostname resolution: localhost aliases or DNS rebinding do not expand validation scope.
        return "127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()) || "::1".equals(uri.getHost());
    }

    private static IllegalArgumentException invalid() {
        // Never include the rejected URI, which could contain accidentally supplied credentials.
        return new IllegalArgumentException("INVALID_PUBLIC_MONITOR_CONFIGURATION");
    }
}
