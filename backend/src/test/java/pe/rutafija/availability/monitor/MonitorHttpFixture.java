package pe.rutafija.availability.monitor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Real loopback HTTP; response content is explicitly synthetic and never touches the application or database. */
final class MonitorHttpFixture implements AutoCloseable {
    @FunctionalInterface interface Response { void respond(HttpExchange exchange) throws Exception; }
    final HttpServer server;
    final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    final AtomicInteger credentialHeaders = new AtomicInteger(), nonGetRequests = new AtomicInteger();
    final AtomicReference<Response> readiness = new AtomicReference<>(e -> respond(e, 200, "application/json", "{\"status\":\"UP\"}"));
    final AtomicReference<Response> liveness = new AtomicReference<>(e -> respond(e, 200, "application/json", "{\"status\":\"UP\"}"));
    final AtomicReference<Response> application = new AtomicReference<>(e -> respond(e, 200, "text/html; charset=utf-8",
        "<!doctype html><html><body><rf-root>SYNTHETIC CRM</rf-root><script type=\"module\" src=\"/synthetic.js\"></script></body></html>"));
    private boolean stopped;

    MonitorHttpFixture() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            counts.computeIfAbsent(exchange.getRequestURI().getPath(), ignored -> new AtomicInteger()).incrementAndGet();
            if (exchange.getRequestHeaders().containsKey("Authorization") || exchange.getRequestHeaders().containsKey("Cookie")) credentialHeaders.incrementAndGet();
            if (!"GET".equals(exchange.getRequestMethod())) nonGetRequests.incrementAndGet();
            try {
                switch (exchange.getRequestURI().getPath()) {
                    case "/ready" -> readiness.get().respond(exchange);
                    case "/live" -> liveness.get().respond(exchange);
                    case "/crm" -> application.get().respond(exchange);
                    default -> respond(exchange, 404, "application/json", "{\"status\":\"DOWN\"}");
                }
            } catch (Exception ignored) {
                // Client cancellation of intentionally delayed synthetic responses must not print exception data.
            } finally { exchange.close(); }
        });
        server.setExecutor(executor);
        server.start();
    }

    URI uri(String path) { return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path); }
    int count(String path) { return counts.getOrDefault(path, new AtomicInteger()).get(); }
    MonitorConfiguration configuration() { return configuration(Duration.ofSeconds(1), Duration.ofSeconds(2)); }
    MonitorConfiguration configuration(Duration connect, Duration request) {
        return new MonitorConfiguration(MonitorConfiguration.Mode.VALIDATION_LOOPBACK, Duration.ofMillis(100), connect, request,
            List.of(new MonitorConfiguration.Probe("api-readiness", uri("/ready"), MonitorConfiguration.Expected.HEALTH_UP),
                new MonitorConfiguration.Probe("api-liveness", uri("/live"), MonitorConfiguration.Expected.HEALTH_UP),
                new MonitorConfiguration.Probe("crm-application", uri("/crm"), MonitorConfiguration.Expected.HTML_PAGE)));
    }
    static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
    }
    void stopServer() { if (!stopped) { server.stop(0); stopped = true; } }
    @Override public void close() { stopServer(); executor.shutdownNow(); }
}
