import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Private one-phone transport to the existing local backend. Never stores credentials. */
public class RutaFijaMobileLocalBridge {
    private static final Set<String> HOP = Set.of("host", "connection", "content-length", "transfer-encoding",
            "upgrade", "http2-settings", "proxy-connection", "keep-alive", "te", "trailer");

    static boolean allowed(String method, String path) {
        if (!Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS").contains(method)
                || path.contains("%") || path.contains("//") || path.contains("/../") || path.contains("/./")
                || path.endsWith("/..") || path.endsWith("/.")) return false;
        return ("GET".equals(method) && path.equals("/actuator/health"))
                || path.startsWith("/api/v1/mobile/")
                || ("POST".equals(method) && Set.of("/api/v1/onboarding/requests", "/api/v1/onboarding/otp/verify",
                        "/api/v1/onboarding/otp/resend").contains(path))
                || ("POST".equals(method) && path.startsWith("/api/v1/auth/conductor-recovery/"));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !args[4].equals("4311")) throw new IllegalArgumentException("EXACT_LOCAL_BRIDGE_ARGUMENTS_REQUIRED");
        for (String address : List.of(args[0], args[1])) {
            if (!address.matches("[0-9.]+") || !(InetAddress.getByName(address) instanceof Inet4Address)
                    || !InetAddress.getByName(address).isSiteLocalAddress()) throw new IllegalArgumentException("PRIVATE_IPV4_REQUIRED");
        }
        if (args[0].equals(args[1])) throw new IllegalArgumentException("DISTINCT_PC_AND_PHONE_REQUIRED");
        var backend = ProcessHandle.of(Long.parseLong(args[2])).orElseThrow();
        var expectedStart = Instant.parse(args[3]);
        if (backend.info().startInstant().orElseThrow().toEpochMilli() != expectedStart.toEpochMilli())
            throw new IllegalStateException("BACKEND_PROCESS_IDENTITY_MISMATCH");
        var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
        var executor = Executors.newVirtualThreadPerTaskExecutor();
        var server = HttpServer.create(new InetSocketAddress(args[0], 4311), 16);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                if (!exchange.getRemoteAddress().getAddress().getHostAddress().equals(args[1])) {
                    reply(exchange, 403, "ONE_AUTHORIZED_PHONE_ONLY".getBytes()); return;
                }
                String path = exchange.getRequestURI().getRawPath();
                if (!allowed(exchange.getRequestMethod(), path)) { reply(exchange, 404, new byte[0]); return; }
                byte[] body = exchange.getRequestBody().readNBytes(1048577);
                if (body.length > 1048576) { reply(exchange, 413, new byte[0]); return; }
                var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8080" + exchange.getRequestURI())).timeout(Duration.ofSeconds(30));
                exchange.getRequestHeaders().forEach((name, values) -> {
                    if (!HOP.contains(name.toLowerCase(Locale.ROOT))) values.forEach(value -> request.header(name, value));
                });
                var response = client.send(request.method(exchange.getRequestMethod(), HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
                response.headers().map().forEach((name, values) -> {
                    if (!HOP.contains(name.toLowerCase(Locale.ROOT))) exchange.getResponseHeaders().put(name, values);
                });
                reply(exchange, response.statusCode(), response.body());
                System.out.println(Instant.now() + " PHONE_FORWARD method=" + exchange.getRequestMethod() + " path=" + path + " status=" + response.statusCode());
            } catch (Exception failure) {
                try { reply(exchange, 502, "LOCAL_BACKEND_UNAVAILABLE".getBytes()); } catch (Exception ignored) { }
            } finally { exchange.close(); }
        });
        server.start();
        System.out.println("LOCAL_MOBILE_BRIDGE=READY port=4311 upstream=127.0.0.1:8080");
        while (backend.isAlive() && backend.info().startInstant().filter(t -> t.toEpochMilli() == expectedStart.toEpochMilli()).isPresent()) Thread.sleep(500);
        server.stop(0); executor.shutdownNow();
        System.out.println("LOCAL_MOBILE_BRIDGE=STOPPED_BACKEND_EXITED");
    }

    private static void reply(HttpExchange exchange, int status, byte[] bytes) throws Exception {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) exchange.getResponseBody().write(bytes);
    }
}
