import pe.rutafija.backup.AuthenticatedBackupArchive;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import com.sun.net.httpserver.HttpServer;

/** Local continuity adapter. No dependencies, key/password logging, migrations or shared restore. */
public final class L08ContinuityTools {
    public static void main(String[] args) {
        byte[] key = null, plaintext = null;
        Process child = null;
        ExecutorService worker = Executors.newFixedThreadPool(2);
        int code = 1;
        try {
            if (Runtime.version().feature() != 21) throw new IllegalStateException("JAVA21_REQUIRED");
            if (args.length == 3 && args[0].equals("http-fixture")) {
                HttpServer api = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[1])), 0);
                HttpServer web = HttpServer.create(new InetSocketAddress("127.0.0.1", Integer.parseInt(args[2])), 0);
                api.createContext("/actuator/health", exchange -> {
                    byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
                });
                web.createContext("/actuator/health", exchange -> {
                    byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
                });
                web.createContext("/", exchange -> {
                    byte[] body = "<html><body>RF_L08_SYNTHETIC_PROXY</body></html>".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
                    exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
                });
                api.start(); web.start(); System.out.println("SYNTHETIC_HTTP_FIXTURE_READY");
                Thread.currentThread().join(); return;
            }
            key = Base64.getDecoder().decode(Objects.requireNonNull(System.getenv("RF_L08_BACKUP_KEY_BASE64")));
            if (key.length != 32) throw new IllegalArgumentException("KEY_LENGTH");
            var crypto = new AuthenticatedBackupArchive();
            if (args.length == 2 && args[0].equals("verify")) {
                try (var input = Files.newInputStream(Path.of(args[1]))) { crypto.verify(input, key, true); }
                System.out.println("GCM_AUTHENTICATED_POSTGRES_ARCHIVE"); code = 0;
            } else if (args.length == 8 && args[0].equals("backup")) {
                String tool = args[1], host = args[2], port = args[3], database = args[4], user = args[5];
                Path output = Path.of(args[6]);
                boolean shared = port.equals("5432") && database.equals("ruta_fija_test") && user.equals("rf_migrator");
                boolean synthetic = port.equals("55441") && database.equals("l08_ops_source") && user.equals("l08_ops_owner");
                if (!host.equals("127.0.0.1") || !(shared || synthetic) || !args[7].equals("READ_ONLY_DUMP")) throw new IllegalStateException("SOURCE_REFUSED");
                if (!Path.of(tool).getFileName().toString().equalsIgnoreCase("pg_dump.exe")) throw new IllegalArgumentException("TOOL_REFUSED");
                child = new ProcessBuilder(tool, "--host="+host, "--port="+port, "--username="+user, "--dbname="+database,
                        "--format=custom", "--no-password", "--lock-wait-timeout=5s", "--no-owner", "--no-acl").start();
                Process owned = child;
                Future<?> drain = worker.submit(() -> { try (var error = owned.getErrorStream()) { error.transferTo(OutputStream.nullOutputStream()); } catch (IOException ignored) {} });
                final byte[] encryptionKey = key;
                Future<?> encrypted = worker.submit(() -> {
                    try (var input = owned.getInputStream(); var file = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) { crypto.encrypt(input, file, encryptionKey); }
                    catch (Exception failure) { throw new IllegalStateException("ENCRYPTION_FAILED"); }
                });
                encrypted.get(15, TimeUnit.MINUTES);
                if (!child.waitFor(10, TimeUnit.SECONDS) || child.exitValue()!=0) throw new IOException("DUMP_FAILED");
                drain.get(5, TimeUnit.SECONDS);
                try (var input = Files.newInputStream(output)) { crypto.verify(input, key, true); }
                System.out.println("GCM_ENCRYPTED_DUMP_VERIFIED"); code = 0;
            } else if (args.length == 5 && args[0].equals("restore-rehearsal")) {
                if (!args[3].equals("55441") || !args[4].equals("l08_ops_restore") ||
                    !Path.of(args[1]).getFileName().toString().equalsIgnoreCase("pg_restore.exe")) throw new IllegalStateException("RESTORE_REFUSED");
                try (var input = Files.newInputStream(Path.of(args[2]))) { plaintext = crypto.authenticatedPostgresBytes(input,key,64*1024*1024); }
                child = new ProcessBuilder(args[1], "--host=127.0.0.1", "--port=55441", "--username=l08_ops_owner",
                    "--dbname=l08_ops_restore", "--no-owner", "--no-acl", "--exit-on-error", "--single-transaction", "--no-password").start();
                Process owned = child;
                worker.submit(() -> { try (var error = owned.getErrorStream()) { error.transferTo(OutputStream.nullOutputStream()); } catch (IOException ignored) {} });
                worker.submit(() -> { try (var output = owned.getInputStream()) { output.transferTo(OutputStream.nullOutputStream()); } catch (IOException ignored) {} });
                try (var input = child.getOutputStream()) { input.write(plaintext); }
                if (!child.waitFor(15, TimeUnit.MINUTES) || child.exitValue()!=0) throw new IOException("RESTORE_FAILED");
                System.out.println("ISOLATED_SYNTHETIC_RESTORE_SUCCEEDED"); code = 0;
            } else throw new IllegalArgumentException("ARGUMENTS_REFUSED");
        } catch (Exception failure) {
            System.out.println("CONTINUITY_OPERATION_REJECTED_"+failure.getClass().getSimpleName());
        } finally {
            if (child != null && child.isAlive()) child.destroyForcibly();
            if (key != null) Arrays.fill(key,(byte)0);
            if (plaintext != null) Arrays.fill(plaintext,(byte)0);
            worker.shutdownNow();
        }
        System.exit(code);
    }
}
