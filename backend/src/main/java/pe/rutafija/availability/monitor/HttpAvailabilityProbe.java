package pe.rutafija.availability.monitor;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static pe.rutafija.availability.monitor.ProbeResult.Outcome.*;

/** One GET per sample, no authentication, cookies, redirects, application retries or response-body logging. */
public final class HttpAvailabilityProbe implements AutoCloseable {
    static final int MAX_RESPONSE_BYTES = 16_384;
    private final HttpClient client;
    private final Duration timeout;
    private final List<MonitorConfiguration.Probe> allowedProbes;
    private final ObjectMapper json = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public HttpAvailabilityProbe(MonitorConfiguration configuration) {
        timeout = configuration.requestTimeout();
        allowedProbes = configuration.probes();
        client = HttpClient.newBuilder().connectTimeout(configuration.connectTimeout())
            .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1)
            .proxy(new ProxySelector() {
                @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
                @Override public void connectFailed(URI uri, SocketAddress address, IOException failure) { }
            }).build();
    }

    public ProbeResult sample(MonitorConfiguration.Probe probe) throws InterruptedException {
        if (!allowedProbes.contains(probe)) throw new IllegalArgumentException("UNCONFIGURED_PROBE_REFUSED");
        long start = System.nanoTime();
        var request = HttpRequest.newBuilder(probe.uri()).timeout(timeout)
            .header("Accept", probe.expected() == MonitorConfiguration.Expected.HEALTH_UP
                ? "application/json" : "text/html")
            .header("User-Agent", "RutaFijaAvailabilityMonitor/1.0 (no-authentication)").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = client.sendAsync(request, info -> new BoundedBody());
        try {
            // This deadline includes the complete body, including a stalled stream after response headers.
            var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            var status = response.statusCode();
            if (status != 200) return result(probe, HTTP_STATUS_REJECTED, status, start);
            String type = response.headers().firstValue("Content-Type").orElse("")
                .split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
            if (probe.expected() == MonitorConfiguration.Expected.HEALTH_UP) {
                if (!"application/json".equals(type)) return result(probe, INVALID_CONTENT_TYPE, status, start);
                try {
                    var body = json.readTree(response.body());
                    if (body == null || !body.isObject() || !body.path("status").isTextual()
                        || !"UP".equals(body.path("status").textValue())) {
                        return result(probe, INVALID_HEALTH_PAYLOAD, status, start);
                    }
                } catch (IOException invalid) { return result(probe, INVALID_HEALTH_PAYLOAD, status, start); }
            } else {
                if (!"text/html".equals(type)) return result(probe, INVALID_CONTENT_TYPE, status, start);
                String html = new String(response.body(), StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);
                if (!(html.startsWith("<!doctype html") || html.startsWith("<html")) || !html.contains("</html>")
                    || !html.contains("<rf-root") || !html.contains("</rf-root>")
                    || !(html.contains("type=\"module\"") || html.contains("type='module'")) || !html.contains("<script")) {
                    return result(probe, INVALID_HTML_PAYLOAD, status, start);
                }
            }
            return result(probe, HEALTHY, status, start);
        } catch (TimeoutException expired) {
            pending.cancel(true); return result(probe, TIMEOUT, null, start);
        } catch (ExecutionException failed) {
            for (Throwable cause = failed; cause != null; cause = cause.getCause()) {
                if (cause instanceof BodyLimitExceeded) return result(probe, BODY_TOO_LARGE, null, start);
                if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof TimeoutException) {
                    return result(probe, TIMEOUT, null, start);
                }
            }
            return result(probe, TRANSPORT_ERROR, null, start);
        } catch (InterruptedException cancelled) {
            pending.cancel(true); throw cancelled;
        }
    }

    private static ProbeResult result(MonitorConfiguration.Probe probe, ProbeResult.Outcome outcome,
                                      Integer status, long started) {
        return new ProbeResult(probe.id(), outcome, status,
            TimeUnit.NANOSECONDS.toMillis(Math.max(0, System.nanoTime() - started)));
    }

    @Override public void close() { client.shutdownNow(); }

    private static final class BodyLimitExceeded extends IOException {
        @java.io.Serial
        private static final long serialVersionUID = 3922946286742532050L;
        }

    private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private int count;
        private boolean failed;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription value) { subscription = value; delegate.onSubscribe(value); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (failed) return;
            for (ByteBuffer buffer : buffers) {
                if (buffer.remaining() > MAX_RESPONSE_BYTES - count) {
                    failed = true; subscription.cancel(); delegate.onError(new BodyLimitExceeded()); return;
                }
                count += buffer.remaining();
            }
            delegate.onNext(buffers);
        }
        @Override public void onError(Throwable failure) { if (!failed) delegate.onError(failure); }
        @Override public void onComplete() { if (!failed) delegate.onComplete(); }
    }
}
