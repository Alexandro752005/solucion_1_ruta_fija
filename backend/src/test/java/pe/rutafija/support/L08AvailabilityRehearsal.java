package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import pe.rutafija.availability.monitor.AvailabilityMonitorCli;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/** Actual API-process outage/return, separate monitor JVM, real test PostgreSQL; never writes business data. */
public final class L08AvailabilityRehearsal {
    static final String RUN_ID = "7a84a97e-79b8-4b3a-b316-545e6b954d55";
    private static final long CLEANUP_LOCK = 7_518_440_514L;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String API = "http://127.0.0.1:" + L08ReadOnlyHealthServer.PORT;
    private final Map<String, Object> evidence = new LinkedHashMap<>();
    private final Map<String, Object> configuration = new LinkedHashMap<>();
    private Process api, monitor;
    private HttpServer crm;
    private ExecutorService crmWorkers;
    private Connection guard;
    private boolean lockOwned;
    private Path monitorRun;
    private String phase = "ARGUMENTS";

    private L08AvailabilityRehearsal() { }

    public static void main(String[] args) {
        if (args.length == 2 && "--owned-monitor-child".equals(args[0])) {
            runOwnedMonitor(args[1]); return;
        }
        if (args.length != 1 || !"--execute".equals(args[0])) {
            System.out.println("L08_AVAILABILITY_REHEARSAL=REFUSED_EXPLICIT_EXECUTE_REQUIRED"); System.exit(2);
        }
        var rehearsal = new L08AvailabilityRehearsal();
        boolean passed = false;
        try { rehearsal.execute(); passed = true; }
        catch (Exception failure) {
            rehearsal.evidence.put("failurePhase", rehearsal.phase);
            rehearsal.evidence.put("failureType", failure.getClass().getSimpleName());
            Throwable cause = failure;
            String sqlState = "NONE";
            for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
                if (cause instanceof java.sql.SQLException sql && sql.getSQLState() != null && sql.getSQLState().matches("[A-Z0-9]{5}"))
                    sqlState = sql.getSQLState();
            }
            rehearsal.evidence.put("failureSqlState", sqlState);
            for (var frame : failure.getStackTrace()) {
                if (frame.getClassName().equals(L08AvailabilityRehearsal.class.getName())
                    || frame.getClassName().equals(L08ReadOnlyHealthServer.class.getName())) {
                    rehearsal.evidence.put("failureSource", frame.getFileName() + ":" + frame.getLineNumber()); break;
                }
            }
        } finally {
            boolean cleanup = rehearsal.cleanup();
            rehearsal.evidence.put("ownedProcessesAndServersStopped", cleanup);
            rehearsal.evidence.put("finishedAt", Instant.now().toString());
            rehearsal.evidence.put("result", passed && cleanup
                ? "PASS_REAL_API_PROCESS_OUTAGE_AND_RETURN_LOCAL_MONITOR_ONLY"
                : "STOPPED_KEEP_CASE_AND_DIAGNOSE_DETAILS_REDACTED");
            // This diagnostic is intentionally available before a case directory exists.
            // Do not emit exception messages, SQL, environment values, classpaths or a raw stack trace.
            System.out.println("L08_AVAILABILITY_RUN_ID=" + RUN_ID);
            for (String field : List.of("failurePhase", "failureType", "failureSqlState", "failureSource")) {
                Object value = rehearsal.evidence.get(field);
                if (value != null) System.out.println("L08_AVAILABILITY_" + field.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT)
                    + "=" + (value.toString().matches("[A-Za-z0-9_$.:_-]{1,160}") ? value : "REDACTED"));
            }
            try {
                Path directory = runDirectory();
                if (Files.isDirectory(directory)) {
                    Path result = directory.resolve("result.json"); writeNewJson(result, rehearsal.evidence);
                    System.out.println("L08_AVAILABILITY_EVIDENCE=" + result);
                    System.out.println("L08_AVAILABILITY_EVIDENCE_SHA256=" + sha256(result));
                }
                System.out.println("L08_AVAILABILITY_REHEARSAL=" + rehearsal.evidence.get("result"));
            } catch (Exception failure) { passed = false; System.out.println("L08_AVAILABILITY_REHEARSAL=EVIDENCE_WRITE_REFUSED_KEEP_STATE"); }
            if (!passed || !cleanup) System.exit(1);
        }
    }

    private void execute() throws Exception {
        evidence.put("runId", RUN_ID); evidence.put("startedAt", Instant.now().toString());
        evidence.put("scope", "REAL_OWNED_API_PROCESS_HEALTH_ONLY_NOT_FULL_BUSINESS_OPERATIONS");
        evidence.put("database", "ruta_fija_test"); evidence.put("role", "rf_test");
        evidence.put("clock", "REAL_UTC_WALL_CLOCK"); evidence.put("monitorIntervalMillis", 1000);
        evidence.put("acceleratedValidation", true); evidence.put("hostIndependenceAccredited", false);
        evidence.put("externalAlertDeliveryAccredited", false); evidence.put("human24x7CoverageAccredited", false);
        evidence.put("actualPostgresServiceStopped", false); evidence.put("accountsCreated", 0);
        evidence.put("smtpAttempts", 0); evidence.put("globalCleanupExecuted", false); evidence.put("migrationsExecuted", false);
        phase = "RESOLVE_EXPECTED_WORKSPACE";
        Path root = repositoryRoot(); Path directory = runDirectory();
        phase = "CHECK_CASE_ABSENCE";
        if (Files.exists(directory)) throw new IllegalStateException("EXISTING_CASE_MUST_BE_PRESERVED");
        phase = "CHECK_ACTIVE_CASE_MARKERS";
        assertNoActiveCases(root);
        phase = "CHECK_API_PORT_AVAILABLE";
        requireFreeApiPort();
        phase = "CHECK_CRM_BUILD";
        Path build = root.resolve("frontend/dist/ruta-fija-crm/browser").toRealPath();
        if (!build.startsWith(root.resolve("frontend/dist").toRealPath()) || !Files.isRegularFile(build.resolve("index.html")))
            throw new IllegalStateException();
        String index = Files.readString(build.resolve("index.html"), StandardCharsets.UTF_8);
        if (!index.contains("<rf-root") || !index.contains("</rf-root>")) throw new IllegalStateException();
        evidence.put("crmIndexSha256", sha256(build.resolve("index.html")));
        phase = "CHECK_COMPILED_CLASS_RESOURCES";
        evidence.put("apiMainClassSha256", classSha256("pe/rutafija/RutaFijaApplication.class"));
        evidence.put("monitorMainClassSha256", classSha256("pe/rutafija/availability/monitor/AvailabilityMonitorCli.class"));
        phase = "READ_ONLY_TEST_LOAD_CONFIG";
        NativePostgresTestDatabase database = NativePostgresTestDatabase.load();
        phase = "READ_ONLY_TEST_ROLE_PERMISSIONS";
        database.assertTestRolePermissions();
        database.registerSpringProperties((key, value) -> configuration.put(key, value.get()));
        var credentials = new Properties(); credentials.put("user", configuration.get("spring.datasource.username"));
        credentials.put("password", configuration.get("spring.datasource.password")); credentials.put("connectTimeout", "5");
        credentials.put("socketTimeout", "10");
        phase = "TEST_CONNECTION_OPEN";
        guard = DriverManager.getConnection(configuration.get("spring.datasource.url").toString(), credentials);
        guard.setReadOnly(true);
        phase = "TEST_SCHEMA_AND_ROLE";
        try (var query = guard.createStatement()) {
            query.setQueryTimeout(10);
            try (var result = query.executeQuery("""
                select current_database(), current_user,
                    (select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1),
                    (select count(*) from flyway_schema_history where not success),
                    (select count(*) from flyway_schema_history where success and version is not null),
                    (select count(distinct version) from flyway_schema_history where success and version is not null),
                    (select count(*) from flyway_schema_history where success and version is null and script='R__native_test_role_privileges.sql'),
                    (select count(*) from flyway_schema_history)
                """)) {
                if (!result.next() || !"ruta_fija_test".equals(result.getString(1)) || !"rf_test".equals(result.getString(2))
                    || !"48".equals(result.getString(3)) || result.getInt(4) != 0 || result.getInt(5) != 48
                    || result.getInt(6) != 48 || result.getInt(7) < 1) throw new IllegalStateException();
                // validateCount=49 describes 48 versioned scripts plus the resolved repeatable.
                // The history intentionally retains every earlier application of that repeatable.
                evidence.put("schemaHistory", Map.of("latestVersion", "48", "failedMigrations", 0,
                    "successfulVersionedMigrations", result.getInt(5), "distinctSuccessfulVersions", result.getInt(6),
                    "historicalTestPrivilegeRepeatableApplications", result.getInt(7), "totalHistoricalRows", result.getInt(8)));
            }
            phase = "TEST_CLEANUP_GUARD_LOCK";
            try (var result = query.executeQuery("select pg_try_advisory_lock(" + CLEANUP_LOCK + ")")) {
                lockOwned = result.next() && result.getBoolean(1); if (!lockOwned) throw new IllegalStateException();
            }
        }
        phase = "RECHECK_ACTIVE_CASE_MARKERS";
        assertNoActiveCases(root);
        phase = "READ_ONLY_SNAPSHOT";
        Map<String, Object> before = snapshot(); evidence.put("before", before);
        phase = "EXACT_HISTORICAL_COUNTS";
        if (!Map.of("SUPER_ADMIN", 1, "ADMIN", 3, "CONDUCTOR", 6).equals(before.get("ledger"))
                || !Integer.valueOf(2).equals(before.get("termsAcceptanceCount"))) throw new IllegalStateException();
        phase = "CREATE_CASE_DIRECTORY_AFTER_PREFLIGHT";
        Files.createDirectory(directory);
        writeNewJson(directory.resolve("authorization.json"), Map.of("runId", RUN_ID,
            "authorization", "READ_ONLY_OWNED_API_OUTAGE_RETURN_TEST_ONLY", "at", Instant.now().toString()));
        phase = "CRM_ACTUAL_BUILD_LOOPBACK";
        startCrm(build);
        int crmPort = crm.getAddress().getPort(); evidence.put("crmLoopbackPort", crmPort);
        phase = "START_INITIAL_ACTUAL_API";
        api = startApi(1); waitForApiReady(1, Duration.ofSeconds(120));
        evidence.put("initialApi", JSON.readTree(Files.readAllBytes(directory.resolve("api-ready-1.json"))));
        phase = "START_SEPARATE_MONITOR_JVM";
        monitor = startChild(L08AvailabilityRehearsal.class.getName(), List.of("--owned-monitor-child", Integer.toString(crmPort)));
        waitForHealthyMonitor(Duration.ofSeconds(20));
        evidence.put("initialHealthyObservedAt", Instant.now().toString());
        phase = "CONTROLLED_OWNED_API_STOP";
        Instant outageRequested = Instant.now(); evidence.put("ownedApiStopCommandIssuedAt", outageRequested.toString());
        requestOwnedStop(api, "STOP_OWNED_API", Duration.ofSeconds(25)); api = null;
        requireFreeApiPort(); evidence.put("ownedApiStoppedAt", Instant.now().toString());
        phase = "WAIT_REAL_MONITOR_CONFIRMED_FAILURE";
        JsonNode failure = waitForIncident("CONFIRMED_FAILURE", null, Duration.ofSeconds(25));
        evidence.put("confirmedFailure", failure);
        long detectionFromCommand = Duration.between(outageRequested,
            Instant.parse(failure.path("details").path("confirmedAt").asText())).toMillis();
        if (detectionFromCommand < 0 || detectionFromCommand > Duration.ofMinutes(5).toMillis()) throw new IllegalStateException();
        evidence.put("stopCommandToConfirmedDetectionMillis", detectionFromCommand);
        evidence.put("detectionOriginLimit", "STOP_COMMAND_IS_CONTROLLED_EVENT_BOUNDARY_NOT_UNOBSERVED_PRODUCTION_OUTAGE_ONSET");
        phase = "RESTART_SAME_OWNED_API_BINARY";
        requireFreeApiPort(); api = startApi(2); waitForApiReady(2, Duration.ofSeconds(120));
        evidence.put("returnedApi", JSON.readTree(Files.readAllBytes(directory.resolve("api-ready-2.json"))));
        phase = "WAIT_REAL_MONITOR_VERIFIED_RECOVERY";
        JsonNode recovery = waitForIncident("VERIFIED_RECOVERY", failure.path("details").path("incidentId").asText(), Duration.ofSeconds(25));
        evidence.put("verifiedRecovery", recovery);
        long millis = Duration.between(Instant.parse(failure.path("details").path("confirmedAt").asText()),
            Instant.parse(recovery.path("details").path("observedAt").asText())).toMillis();
        evidence.put("confirmedDetectionToVerifiedHealthRecoveryMillis", millis);
        if (millis < 0 || millis > Duration.ofMinutes(30).toMillis()
                || !recovery.path("details").path("recoveryWithinThirtyMinutes").asBoolean()
                || !failure.path("details").path("detectionWithinFiveMinutes").asBoolean()) throw new IllegalStateException();
        phase = "NORMAL_MONITOR_STOP_AND_ARCHIVE";
        requestOwnedStop(monitor, "STOP_OWNED_MONITOR", Duration.ofSeconds(25)); monitor = null;
        List<JsonNode> records = records();
        if (records.isEmpty() || !"MONITOR_STOPPED".equals(records.getLast().path("type").asText())) throw new IllegalStateException();
        JsonNode downSample = sampleForEvent(records, failure), restoredSample = sampleForEvent(records, recovery);
        requireExpectedProbes(downSample, false); requireExpectedProbes(restoredSample, true);
        evidence.put("confirmedFailureHttpSample", downSample); evidence.put("verifiedRecoveryHttpSample", restoredSample);
        evidence.put("monitorRunId", monitorRun.getFileName().toString().substring("monitor-".length()));
        evidence.put("monitorJournalRelativePath", runDirectory().relativize(monitorRun.resolve("events.jsonl")).toString().replace('\\', '/'));
        evidence.put("monitorJournalSha256", sha256(monitorRun.resolve("events.jsonl")));
        evidence.put("monitorJournalRecords", records.size());
        long alerts = records.stream().filter(record -> "LOCAL_TEST_ALERT".equals(record.path("type").asText())).count();
        if (alerts != 2) throw new IllegalStateException(); evidence.put("localOnlyAlertReceipts", alerts);
        phase = "READ_ONLY_PRESERVATION_CHECK";
        Map<String, Object> after = snapshot(); evidence.put("after", after);
        if (!before.equals(after)) throw new IllegalStateException("DATABASE_PRESERVATION_MISMATCH");
        evidence.put("databaseRowsLedgerAndAcceptancesPreserved", true);
        evidence.put("testCleanup", "NO_FIXTURES_CREATED_NO_DATABASE_CLEANUP_REQUIRED");
        evidence.put("limitations", List.of("Same Windows PC: no independent host", "Local alert files only: no external delivery or acknowledgement",
            "One-second accelerated cadence: not production 60-second coverage", "Health restoration only: no business-operation acceptance",
            "Controlled own API process interruption; shared PostgreSQL service never stopped", "No backup/restore/RPO/24x7 evidence"));
        phase = "COMPLETED_AWAITING_RESOURCE_CLOSURE";
    }

    private Map<String, Object> snapshot() throws Exception {
        phase = "SNAPSHOT_LEDGER_AND_ACCEPTANCE_COUNTS";
        var result = new LinkedHashMap<String, Object>();
        var ledger = new LinkedHashMap<String, Integer>();
        try (var query = guard.createStatement()) {
            query.setQueryTimeout(20);
            for (String role : List.of("SUPER_ADMIN", "ADMIN", "CONDUCTOR")) {
                try (var row = query.executeQuery("select count(*) from pilot_account_creation where role='" + role + "'")) {
                    if (!row.next()) throw new IllegalStateException(); ledger.put(role, row.getInt(1));
                }
            }
            try (var row = query.executeQuery("select count(*) from admin_terms_acceptance")) {
                if (!row.next()) throw new IllegalStateException(); result.put("termsAcceptanceCount", row.getInt(1));
            }
        }
        result.put("ledger", ledger);
        phase = "SNAPSHOT_PUBLIC_TABLE_LIST";
        var tables = new ArrayList<String>();
        try (var query = guard.createStatement(); var rows = query.executeQuery("select tablename from pg_tables where schemaname='public' order by tablename")) {
            while (rows.next()) { String table = rows.getString(1); if (!table.matches("[a-z][a-z0-9_]*")) throw new IllegalStateException(); tables.add(table); }
        }
        var hashes = new TreeMap<String, String>();
        // Private retention authority tables are intentionally unreadable to rf_test.
        // The validated migrator may inspect their hashes through a separate read-only
        // connection; the actual application and cleanup guard retain rf_test permissions.
        var auditCredentials = new Properties();
        auditCredentials.put("user", configuration.get("RF_TEST_MIGRATOR_USERNAME"));
        auditCredentials.put("password", configuration.get("RF_TEST_MIGRATOR_PASSWORD"));
        auditCredentials.put("connectTimeout", "5"); auditCredentials.put("socketTimeout", "20");
        phase = "OPEN_READ_ONLY_TEST_INTEGRITY_CONNECTION";
        try (var audit = DriverManager.getConnection(configuration.get("RF_TEST_MIGRATOR_URL").toString(), auditCredentials)) {
            audit.setReadOnly(true);
            try (var query = audit.createStatement(); var row = query.executeQuery("select current_database(),current_user")) {
                if (!row.next() || !"ruta_fija_test".equals(row.getString(1)) || !"rf_migrator".equals(row.getString(2)))
                    throw new IllegalStateException();
            }
            for (String table : tables) {
                phase = "SNAPSHOT_TABLE_" + table.toUpperCase(Locale.ROOT);
                hashes.put(table, L07DatabasePreflight.digestRows(audit,
                    "select row_to_json(t)::text from public." + table + " t order by row_to_json(t)::text"));
            }
        }
        evidence.put("integrityInspectionRole", "rf_migrator_READ_ONLY_SEPARATE_FROM_APPLICATION");
        result.put("publicTableRowsSha256", hashes); return result;
    }

    private Process startApi(int ordinal) throws Exception {
        return startChild(L08ReadOnlyHealthServer.class.getName(), List.of("--owned-read-only-health-server",
            runDirectory().resolve("api-ready-" + ordinal + ".json").toString()));
    }

    private Process startChild(String main, List<String> arguments) throws Exception {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        String classpath = Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
            .map(entry -> Path.of(entry).toAbsolutePath().normalize().toString()).collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        command.addAll(List.of("-Xms32m", "-Xmx256m", "-cp", classpath, main)); command.addAll(arguments);
        var builder = new ProcessBuilder(command); builder.directory(repositoryRoot().resolve("backend").toFile());
        // Children inherit only the exact validated test configuration, never generic datasource/mail/Java options.
        var environment = builder.environment(); environment.clear();
        for (String name : List.of("SystemRoot", "WINDIR", "TEMP", "TMP")) {
            String value = System.getenv(name); if (value != null) environment.put(name, value);
        }
        for (String name : List.of("RF_TEST_DATASOURCE_URL", "RF_TEST_DATASOURCE_USERNAME", "RF_TEST_DATASOURCE_PASSWORD",
                "RF_TEST_MIGRATOR_URL", "RF_TEST_MIGRATOR_USERNAME", "RF_TEST_MIGRATOR_PASSWORD")) {
            Object value = configuration.get(name); if (value == null || value.toString().isBlank()) throw new IllegalStateException();
            environment.put(name, value.toString());
        }
        environment.put("RF_L08_AVAILABILITY_REHEARSAL_RUN_ID", RUN_ID);
        environment.put("RF_L08_HEALTH_SERVER_AUTHORIZATION", "READ_ONLY_HEALTH_REAL_POSTGRES_TEST");
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD); builder.redirectError(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    private void waitForApiReady(int ordinal, Duration limit) throws Exception {
        Path ready = runDirectory().resolve("api-ready-" + ordinal + ".json");
        Instant deadline = Instant.now().plus(limit);
        try (var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build()) {
            while (Instant.now().isBefore(deadline)) {
                if (!api.isAlive()) throw new IllegalStateException("OWNED_API_EXITED_BEFORE_READINESS");
                if (Files.exists(ready)) {
                    var response = http.send(HttpRequest.newBuilder(URI.create(API + "/actuator/health/readiness")).timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200 && "UP".equals(JSON.readTree(response.body()).path("status").asText())) return;
                }
                Thread.sleep(200);
            }
        }
        throw new IllegalStateException("OWNED_API_READINESS_TIMEOUT");
    }

    private void waitForHealthyMonitor(Duration limit) throws Exception {
        Instant deadline = Instant.now().plus(limit);
        while (Instant.now().isBefore(deadline)) {
            if (!monitor.isAlive()) throw new IllegalStateException("OWNED_MONITOR_EXITED");
            Path root = runDirectory().resolve("monitor");
            if (Files.isDirectory(root)) try (var directories = Files.list(root)) {
                var runs = directories.filter(Files::isDirectory).toList();
                if (runs.size() > 1) throw new IllegalStateException();
                if (runs.size() == 1) monitorRun = runs.getFirst();
            }
            if (monitorRun != null && Files.isRegularFile(monitorRun.resolve("heartbeat.json"))) {
                var heartbeat = JSON.readTree(Files.readAllBytes(monitorRun.resolve("heartbeat.json")));
                if (heartbeat.path("sampleSequence").asLong() >= 2 && heartbeat.path("allRequiredProbesHealthy").asBoolean()
                    && !heartbeat.path("incidentOpen").asBoolean() && !heartbeat.path("suspectedFailure").asBoolean()) return;
            }
            Thread.sleep(100);
        }
        throw new IllegalStateException("INITIAL_MONITOR_HEALTH_TIMEOUT");
    }

    private JsonNode waitForIncident(String kind, String incident, Duration limit) throws Exception {
        Instant deadline = Instant.now().plus(limit);
        while (Instant.now().isBefore(deadline)) {
            if (!monitor.isAlive()) throw new IllegalStateException("OWNED_MONITOR_EXITED");
            for (JsonNode record : records()) if ("INCIDENT_EVENT".equals(record.path("type").asText())
                && kind.equals(record.path("details").path("kind").asText())
                && (incident == null || incident.equals(record.path("details").path("incidentId").asText()))) return record;
            Thread.sleep(100);
        }
        throw new IllegalStateException("MONITOR_EXPECTED_INCIDENT_TIMEOUT");
    }

    private List<JsonNode> records() throws Exception {
        if (monitorRun == null || !Files.isRegularFile(monitorRun.resolve("events.jsonl"))) return List.of();
        String text = Files.readString(monitorRun.resolve("events.jsonl"), StandardCharsets.UTF_8);
        var records = new ArrayList<JsonNode>(); int start = 0, newline;
        while ((newline = text.indexOf('\n', start)) >= 0) {
            String line = text.substring(start, newline); if (!line.isBlank()) records.add(JSON.readTree(line)); start = newline + 1;
        }
        return records; // Ignore an in-progress final append; never invent a partial record.
    }

    private static JsonNode sampleForEvent(List<JsonNode> records, JsonNode event) {
        return records.stream().filter(record -> "HTTP_SAMPLE".equals(record.path("type").asText())
            && event.path("at").asText().equals(record.path("at").asText())).findFirst().orElseThrow();
    }

    private static void requireExpectedProbes(JsonNode sample, boolean recovered) {
        var probes = new HashMap<String, JsonNode>();
        for (JsonNode probe : sample.path("details").path("probes")) probes.put(probe.path("probeId").asText(), probe);
        if (!probes.keySet().equals(Set.of("api-readiness", "api-liveness", "crm-application"))
            || !"HEALTHY".equals(probes.get("crm-application").path("outcome").asText())) throw new IllegalStateException();
        for (String name : List.of("api-readiness", "api-liveness")) {
            String outcome = probes.get(name).path("outcome").asText();
            if (recovered ? !"HEALTHY".equals(outcome) : !Set.of("TRANSPORT_ERROR", "TIMEOUT").contains(outcome))
                throw new IllegalStateException("ACTUAL_OWNED_API_TRANSPORT_TRANSITION_REQUIRED");
        }
    }

    private void startCrm(Path build) throws Exception {
        crm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        crmWorkers = Executors.newFixedThreadPool(2); crm.setExecutor(crmWorkers);
        crm.createContext("/", exchange -> {
            try {
                if (!"GET".equals(exchange.getRequestMethod()) || exchange.getRequestURI().getRawQuery() != null) {
                    exchange.sendResponseHeaders(404, -1); return;
                }
                String relative = exchange.getRequestURI().getPath();
                Path file = build.resolve(relative.equals("/") ? "index.html" : relative.substring(1)).normalize();
                if (!file.startsWith(build) || !Files.isRegularFile(file) || !file.toRealPath().startsWith(build)) {
                    exchange.sendResponseHeaders(404, -1); return;
                }
                String type = file.toString().endsWith(".html") ? "text/html; charset=utf-8"
                    : file.toString().endsWith(".js") ? "text/javascript" : file.toString().endsWith(".css") ? "text/css" : "application/octet-stream";
                exchange.getResponseHeaders().set("Content-Type", type); exchange.getResponseHeaders().set("Cache-Control", "no-store");
                exchange.sendResponseHeaders(200, Files.size(file)); try (var input = Files.newInputStream(file)) { input.transferTo(exchange.getResponseBody()); }
            } finally { exchange.close(); }
        });
        crm.start();
    }

    private static void runOwnedMonitor(String crmPort) {
        try {
            if (!RUN_ID.equals(System.getenv("RF_L08_AVAILABILITY_REHEARSAL_RUN_ID"))
                    || !Files.isRegularFile(runDirectory().resolve("authorization.json"))) throw new IllegalStateException();
            int port = Integer.parseInt(crmPort); if (port < 1 || port > 65535) throw new IllegalStateException();
            var arguments = new String[]{"--mode", "validation-loopback", "--readiness", API + "/actuator/health/readiness",
                "--liveness", API + "/actuator/health/liveness", "--application", "http://127.0.0.1:" + port + "/",
                "--output", runDirectory().resolve("monitor").toString(), "--interval-millis", "1000", "--max-samples", "240"};
            var stopRequested = new java.util.concurrent.atomic.AtomicBoolean();
            var exit = new java.util.concurrent.atomic.AtomicInteger(-1);
            Thread owned = new Thread(() -> exit.set(AvailabilityMonitorCli.runUntilStopRequested(
                arguments, System.out, System.err, stopRequested::get)), "l08-owned-real-monitor-cli"); owned.start();
            try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                if (!"STOP_OWNED_MONITOR".equals(input.readLine())) throw new IllegalStateException();
            } finally { stopRequested.set(true); owned.join(20_000); }
            if (owned.isAlive() || exit.get() != 0) throw new IllegalStateException();
        } catch (Exception failure) { System.exit(1); }
    }

    private boolean cleanup() {
        boolean result = true;
        if (monitor != null) { try { requestOwnedStop(monitor, "STOP_OWNED_MONITOR", Duration.ofSeconds(25)); }
            catch (Exception failure) { result = false; monitor.destroyForcibly(); } }
        if (api != null) { try { requestOwnedStop(api, "STOP_OWNED_API", Duration.ofSeconds(25)); }
            catch (Exception failure) { result = false; api.destroyForcibly(); } }
        if (crm != null) crm.stop(0);
        if (crmWorkers != null) { crmWorkers.shutdownNow(); try { if (!crmWorkers.awaitTermination(5, TimeUnit.SECONDS)) result = false; }
            catch (InterruptedException failure) { Thread.currentThread().interrupt(); result = false; } }
        if (guard != null) { try {
            if (lockOwned) try (var query = guard.createStatement(); var row = query.executeQuery("select pg_advisory_unlock(" + CLEANUP_LOCK + ")")) {
                if (!row.next() || !row.getBoolean(1)) result = false;
            }
            guard.close();
        } catch (Exception failure) { result = false; } }
        evidence.put("cleanupProtectionReleased", result && lockOwned);
        return result;
    }

    private static void requestOwnedStop(Process process, String command, Duration timeout) throws Exception {
        if (!process.isAlive()) throw new IllegalStateException("OWNED_PROCESS_ALREADY_EXITED");
        process.getOutputStream().write((command + "\n").getBytes(StandardCharsets.UTF_8)); process.getOutputStream().flush();
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS) || process.exitValue() != 0) throw new IllegalStateException("OWNED_PROCESS_STOP_FAILED");
    }

    private static void assertNoActiveCases(Path root) throws Exception {
        Path local = root.resolve("backend/.local");
        if (Files.isDirectory(local)) try (var files = Files.list(local)) {
            if (files.anyMatch(path -> path.getFileName().toString().endsWith(".active"))) throw new IllegalStateException("ACTIVE_CASE_MUST_BE_PRESERVED");
        }
    }

    private static void requireFreeApiPort() throws Exception {
        try (var port = new ServerSocket()) { port.setReuseAddress(false); port.bind(new InetSocketAddress("127.0.0.1", L08ReadOnlyHealthServer.PORT)); }
    }

    static Path repositoryRoot() throws Exception {
        Path current = Path.of(System.getProperty("user.dir")).toRealPath();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("src/main/java/pe/rutafija"))) current = current.getParent();
        if (!Files.isRegularFile(current.resolve("backend/pom.xml")) || !Files.isRegularFile(current.resolve("mobile/pubspec.yaml")))
            throw new IllegalStateException("EXPECTED_WORKSPACE_REQUIRED");
        return current;
    }

    static Path runDirectory() throws Exception { return repositoryRoot().resolve("backend/target/l08-availability-rehearsal-" + RUN_ID); }

    static void writeNewJson(Path file, Map<String, ?> value) throws Exception {
        if (Files.exists(file)) throw new IllegalStateException("EXISTING_EVIDENCE_MUST_BE_PRESERVED");
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value);
        try (var channel = java.nio.channels.FileChannel.open(temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes); while (buffer.hasRemaining()) channel.write(buffer); channel.force(true);
        }
        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
    }

    private static String classSha256(String resource) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = L08AvailabilityRehearsal.class.getClassLoader().getResourceAsStream(resource)) {
            if (input == null) throw new IllegalStateException(); input.transferTo(new java.security.DigestOutputStream(java.io.OutputStream.nullOutputStream(), digest));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    static String sha256(Path file) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(file); var hashed = new java.security.DigestInputStream(input, digest)) { hashed.transferTo(java.io.OutputStream.nullOutputStream()); }
        return HexFormat.of().formatHex(digest.digest());
    }
}
