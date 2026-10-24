package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Authorized synthetic HTTP capacity case. Never creates accounts or writes SQL. */
public final class L08CapacityMeasurement {
    private static final String BASE = "http://127.0.0.1:4314";
    private static final String RUN = "860c1cdb-b062-469a-9a3f-156ea23bb656";
    private static final int CYCLES = 5, ACTORS = 25, BODY_LIMIT = 1_048_576;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final L08CapacityTrial.Prepared prepared;
    private final Path directory, stateFile, journal, resultFile;
    private final Map<String, Object> state = new LinkedHashMap<>();
    private final List<Map<String, Object>> samples = new ArrayList<>();
    private final List<Map<String, Object>> waves = new ArrayList<>();
    private final ThreadLocal<String> currentWave = new ThreadLocal<>();
    private final Map<String, Map<String, Object>> operations = new LinkedHashMap<>();
    private final Map<String, JsonNode> routes = new HashMap<>();
    private final Map<String, JsonNode> types = new HashMap<>();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).build();
    private final AtomicInteger inflight = new AtomicInteger(), maximumInflight = new AtomicInteger();
    private final long startedNanos = System.nanoTime();
    private JdbcTemplate observer;
    private int observerPid;

    private L08CapacityMeasurement(L08CapacityTrial.Prepared prepared, Path directory) {
        this.prepared = Objects.requireNonNull(prepared);
        this.directory = directory.toAbsolutePath().normalize();
        stateFile = this.directory.resolve("medicion-state.json");
        journal = this.directory.resolve("medicion-http.jsonl");
        resultFile = this.directory.resolve("medicion.json");
    }

    /** evidenceDirectory is public/sanitized; tokens remain only in Prepared and request headers. */
    public static Map<String, Object> run(L08CapacityTrial.Prepared prepared, Path evidenceDirectory) throws Exception {
        return new L08CapacityMeasurement(prepared, evidenceDirectory).execute();
    }

    private Map<String, Object> execute() throws Exception {
        validateProfile(prepared);
        String url = prepared.app().getEnvironment().getRequiredProperty("spring.datasource.url");
        exactTarget(url);
        Files.createDirectories(directory);
        require(!Files.exists(resultFile), "MEASUREMENT_ALREADY_ARCHIVED_DO_NOT_REPEAT");
        initialize();
        // Retained outside Hikari: no backend request can borrow this physical connection.
        try (Connection connection = DriverManager.getConnection(url,
                prepared.app().getEnvironment().getRequiredProperty("spring.datasource.username"),
                prepared.app().getEnvironment().getRequiredProperty("spring.datasource.password"))) {
            connection.setReadOnly(true);
            connection.setAutoCommit(true);
            observer = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            observer.setQueryTimeout(15);
            require("ruta_fija_test/55439/rf_test/127.0.0.1".equals(observer.queryForObject(
                    "select current_database()||'/'||inet_server_port()||'/'||current_user||'/'||inet_server_addr()", String.class)), "OBSERVER_EXACT_TARGET_REQUIRED");
            require(!Boolean.TRUE.equals(observer.queryForObject("select rolsuper or rolcreatedb or rolcreaterole from pg_roles where rolname=current_user",Boolean.class)), "RESTRICTED_OBSERVER_ROLE_REQUIRED");
            observerPid = observer.queryForObject("select pg_backend_pid()", Integer.class);
            require("on".equals(observer.queryForObject("show fsync", String.class))
                    && "on".equals(observer.queryForObject("show synchronous_commit", String.class))
                    && "on".equals(observer.queryForObject("show full_page_writes", String.class)),
                    "NORMAL_DURABILITY_SETTINGS_REQUIRED");
            state.put("observerBackendPid", observerPid);
            state.put("durabilitySettings", Map.of("fsync", "on", "synchronous_commit", "on", "full_page_writes", "on"));
            state.put("connectionObservation", "RETAINED_DRIVER_MANAGER_CONNECTION_OUTSIDE_BACKEND_POOL_READ_ONLY_AUTOCOMMIT");
            persist();
            try {
                prepareCatalogs();
                wave("warmup", null, -1, -1);
                for (int cycle = 0; cycle < CYCLES; cycle++) {
                    for (int slot = 0; slot < 5; slot++) wave("create", "CREATE", cycle, slot);
                    wave("accept", "ACCEPT", cycle, -1);
                }
                Map<String, Object> result = summarize(null);
                JSON.writerWithDefaultPrettyPrinter().writeValue(resultFile.toFile(), result);
                return result;
            } catch (Exception failure) {
                state.put("result", "PENDING_KEEP_DATASET_AND_REFERENCES");
                state.put("failureCode", safe(failure));
                persist();
                Path failureEvidence = directory.resolve("medicion-fallo-" + Instant.now().toEpochMilli() + ".json");
                JSON.writerWithDefaultPrettyPrinter().writeValue(failureEvidence.toFile(), summarize(safe(failure)));
                throw new IllegalStateException("CAPACITY_MEASUREMENT_FAILED_KEEP_CASE");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void initialize() throws Exception {
        if (Files.exists(stateFile)) {
            state.putAll(JSON.readValue(Files.readAllBytes(stateFile), Map.class));
            require(RUN.equals(state.get("runId")), "SAME_CAPACITY_CASE_REQUIRED");
            if (Files.exists(journal)) {
                for (String line : Files.readAllLines(journal, StandardCharsets.UTF_8)) {
                    require(!line.isBlank(), "INTACT_APPEND_ONLY_SAMPLE_JOURNAL_REQUIRED");
                    samples.add(JSON.readValue(line, Map.class));
                }
                require(samples.size() >= ((Number) state.getOrDefault("sampleCount", 0)).intValue(), "SAMPLE_JOURNAL_MUST_PRESERVE_PRIOR_SAMPLES");
            } else if (state.get("samples") instanceof List<?> saved) for (Object item : saved) samples.add((Map<String, Object>) item);
            state.remove("samples");
            if (state.get("waves") instanceof List<?> saved) for (Object item : saved) waves.add((Map<String, Object>) item);
            if (state.get("operations") instanceof Map<?, ?> saved) saved.forEach((key, value) -> operations.put(key.toString(), (Map<String, Object>) value));
            maximumInflight.set(((Number) state.getOrDefault("maxObservedHttpInflight", 0)).intValue());
            require(state.get("participants").equals(participantReferences()), "EXACT_PREPARED_IDENTITIES_REQUIRED");
            state.put("resumeCount", ((Number) state.getOrDefault("resumeCount", 0)).intValue() + 1);
            // No uncertain POST is automatically repeated. Completed mutations are reconciled by SELECT below.
        } else {
            require(!Files.exists(journal), "UNCLAIMED_MEASUREMENT_JOURNAL_REQUIRES_REVIEW");
            state.put("runId", RUN);
            state.put("createdAt", Instant.now().toString());
            state.put("scheduleOrigin", Instant.now().plus(Duration.ofDays(1)).truncatedTo(ChronoUnit.SECONDS).toString());
            state.put("participants", participantReferences());
            state.put("result", "EN_CURSO_NOT_MEASURED");
            state.put("configuredActors", ACTORS);
            state.put("resumeCount", 0);
        }
        persist();
    }

    private List<Map<String, String>> participantReferences() {
        var out = new ArrayList<Map<String, String>>();
        for (var person : allActors()) {
            var row = new LinkedHashMap<String, String>();
            row.put("role", person.role()); row.put("accountId", person.accountId());
            if (person.organizationId() != null) row.put("organizationId", person.organizationId());
            if (person.driverId() != null) row.put("driverId", person.driverId());
            if (person.vehicleId() != null) row.put("vehicleId", person.vehicleId());
            out.add(row);
        }
        return out;
    }

    private List<L08CapacityTrial.Participant> allActors() {
        var actors = new ArrayList<L08CapacityTrial.Participant>();
        actors.add(prepared.owner()); actors.addAll(prepared.admins()); actors.addAll(prepared.conductors());
        return actors;
    }

    private void prepareCatalogs() throws Exception {
        for (var admin : prepared.admins()) {
            String org = admin.organizationId();
            String routeKey = key("route:" + org), typeKey = key("type:" + org);
            JsonNode route = recoverCatalog("/routes/operations/" + routeKey, admin);
            if (route == null) route = request("preparation", "ROUTE", "route:" + org, admin, "/routes", "POST", Map.of(
                    "name", "SYNTHETIC CAP25 " + org.substring(0, 8), "operationKey", routeKey,
                    "reason", "SYNTHETIC_CAPACITY_NO_PHYSICAL_GEOMETRY", "stops", List.of(
                            Map.of("key", "start", "kind", "START", "name", "SYNTHETIC origin"),
                            Map.of("key", "middle", "kind", "INTERMEDIATE", "name", "SYNTHETIC middle"),
                            Map.of("key", "end", "kind", "END", "name", "SYNTHETIC terminal"))), 200, null, null);
            routes.put(org, route);
            JsonNode type = recoverCatalog("/service-types/operations/" + typeKey, admin);
            if (type == null) type = request("preparation", "TYPE", "type:" + org, admin, "/service-types", "POST", Map.of(
                    "name", "SYNTHETIC CAP25 " + org.substring(0, 8), "operationKey", typeKey,
                    "reason", "SYNTHETIC_CAPACITY", "beforeMinutes", 0, "afterMinutes", 0), 200, null, null);
            types.put(org, type);
        }
    }

    private JsonNode recoverCatalog(String path, L08CapacityTrial.Participant admin) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(BASE + "/api/v1" + path))
                .timeout(Duration.ofSeconds(20)).header("Authorization", "Bearer " + admin.token()).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (var body = response.body()) {
            byte[] bytes = body.readNBytes(BODY_LIMIT + 1);
            require(bytes.length <= BODY_LIMIT, "CATALOG_RESPONSE_BOUND_EXCEEDED");
            if (response.statusCode() == 404) return null;
            require(response.statusCode() == 200, "CATALOG_RECONCILIATION_FAILED");
            return JSON.readTree(bytes);
        }
    }

    private void wave(String phase, String mutation, int cycle, int slot) throws Exception {
        String wave = phase + ":" + cycle + ":" + slot;
        int samplesBefore;
        synchronized (this) { samplesBefore = samples.size(); }
        Instant waveStartedAt = Instant.now();
        CountDownLatch ready = new CountDownLatch(ACTORS), start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(ACTORS)) {
            var jobs = new ArrayList<java.util.concurrent.Future<?>>();
            for (var actor : allActors()) jobs.add(executor.submit(() -> {
                currentWave.set(wave);
                ready.countDown();
                try {
                    require(start.await(30, TimeUnit.SECONDS), "WAVE_START_TIMEOUT");
                    if ("CREATE".equals(mutation) && "ADMIN".equals(actor.role())) {
                        List<L08CapacityTrial.Participant> own = prepared.conductors().stream()
                                .filter(c -> c.organizationId().equals(actor.organizationId())).toList();
                        create(actor, own.get(slot), cycle, wave);
                    } else if ("ACCEPT".equals(mutation) && "CONDUCTOR".equals(actor.role())) accept(actor, cycle, wave);
                    else read(actor, phase.equals("warmup") ? "warmup" : "measurement-support", wave);
                } catch (Exception failure) { throw new IllegalStateException(safe(failure)); }
                finally { currentWave.remove(); }
            }));
            require(ready.await(30, TimeUnit.SECONDS), "ALL_25_ACTORS_READY_REQUIRED");
            start.countDown();
            boolean failed = false;
            for (var job : jobs) try { job.get(90, TimeUnit.SECONDS); } catch (Exception failure) { failed = true; }
            synchronized (this) {
                var submitted = samples.subList(samplesBefore, samples.size());
                var observation = new LinkedHashMap<String,Object>();
                observation.put("wave", wave); observation.put("phase", phase); observation.put("startedAt", waveStartedAt.toString());
                observation.put("finishedAt", Instant.now().toString()); observation.put("actorsReleasedTogether", ACTORS);
                observation.put("observedHttpSamples", submitted.size()); observation.put("failed", failed);
                observation.put("peakHttpInflight", submitted.stream().mapToInt(s -> ((Number)s.getOrDefault("activeHttpAtStart",0)).intValue()).max().orElse(0));
                observation.put("writes", submitted.stream().filter(s -> "CREATE".equals(s.get("operation")) || "ACCEPT".equals(s.get("operation"))).count());
                waves.add(observation); state.put("lastCompletedOrAttemptedWave", wave); persist();
            }
            require(!failed, "CAPACITY_WAVE_FAILED_KEEP_CASE");
        } finally { start.countDown(); }
    }

    private void read(L08CapacityTrial.Participant actor, String phase, String wave) throws Exception {
        String path = switch (actor.role()) {
            case "SUPER_ADMIN" -> "/organizations?size=5";
            case "ADMIN" -> "/assignments?size=5";
            case "CONDUCTOR" -> "/mobile/assignments?size=5";
            default -> throw new IllegalStateException("EXACT_ROLE_REQUIRED");
        };
        request(phase, "READ", wave + ":" + actor.accountId(), actor, path, "GET", null, 200, null, null);
    }

    private void create(L08CapacityTrial.Participant admin, L08CapacityTrial.Participant driver, int cycle, String wave) throws Exception {
        String operation = operation(cycle, driver.driverId()), idempotency = key(operation);
        Map<String, Object> op = operationState(operation);
        synchronized (observer) {
            var committed = observer.queryForList("select id::text,service_id::text,driver_id::text,vehicle_id::text,version,status from assignment where organization_id=? and idempotency_key=?",
                    UUID.fromString(driver.organizationId()), idempotency);
            require(committed.size() <= 1, "AMBIGUOUS_CREATION_KEY");
            if (!committed.isEmpty()) {
                var row = committed.getFirst();
                require(driver.driverId().equals(row.get("driver_id")) && driver.vehicleId().equals(row.get("vehicle_id")), "RECOVERED_OFFER_RESOURCES_MUST_MATCH");
                synchronized (this) { op.put("offerId", row.get("id")); op.put("serviceId", row.get("service_id")); op.putIfAbsent("version", row.get("version")); op.put("creationRecoveredBySelect", true); persist(); }
                // Preserve historical measured samples; a recovered result never manufactures a timing sample.
                read(admin, "resume-support-not-new-creation-measurement", wave);
                return;
            }
        }
        synchronized (this) {
            require(!op.containsKey("createIntent"), "UNCONFIRMED_CREATION_REQUIRES_RECONCILIATION_NOT_RESEND");
            op.put("createIntent", Instant.now().toString()); persist();
        }
        JsonNode route = routes.get(driver.organizationId()), type = types.get(driver.organizationId());
        Instant scheduled = scheduledAt(Instant.parse(state.get("scheduleOrigin").toString()), cycle);
        Map<String, Object> payload = L08ConnectedTrial.serviceOfferPayload(route, route.path("id").asText(), route.path("currentVersionId").asText(),
                type.path("currentVersionId").asText(), driver.driverId(), driver.vehicleId(), scheduled);
        JsonNode response = request("measurement", "CREATE", operation, admin, "/assignments", "POST", payload, 201, idempotency,
                result -> observeCommit(driver, idempotency, null, result, false));
        synchronized (this) {
            op.put("offerId", response.path("id").asText()); op.put("serviceId", response.path("serviceId").asText());
            op.put("version", response.path("version").asLong()); op.put("scheduledAt", scheduled.toString()); op.put("createCommitted", true); persist();
        }
    }

    private void accept(L08CapacityTrial.Participant driver, int cycle, String wave) throws Exception {
        String operation = operation(cycle, driver.driverId());
        Map<String, Object> op = operationState(operation);
        require(op.containsKey("offerId") && op.containsKey("version"), "EXACT_CREATED_OFFER_REQUIRED");
        UUID event = UUID.nameUUIDFromBytes((RUN + ":accept:" + operation).getBytes(StandardCharsets.US_ASCII));
        synchronized (observer) {
            int received = observer.queryForObject("select count(*) from mobile_command_receipt where event_id=? and result_status='APPLIED' and command_type='ASSIGNMENT_ACCEPT' and driver_id=? and assignment_id=? and organization_id=?",
                    Integer.class, event, UUID.fromString(driver.driverId()), UUID.fromString(op.get("offerId").toString()), UUID.fromString(driver.organizationId()));
            if (received == 1) {
                synchronized (this) { op.put("acceptanceRecoveredBySelect", true); persist(); }
                read(driver, "resume-support-not-new-acceptance-measurement", wave); return;
            }
        }
        Instant occurredAt;
        synchronized (this) {
            require(!op.containsKey("acceptIntent"), "UNCONFIRMED_ACCEPTANCE_REQUIRES_RECONCILIATION_NOT_RESEND");
            occurredAt = Instant.now(); op.put("acceptIntent", occurredAt.toString()); op.put("clientEventId", event.toString()); persist();
        }
        request("measurement", "ACCEPT", operation, driver, "/mobile/assignments/" + op.get("offerId") + "/accept", "POST",
                Map.of("clientEventId", event, "version", ((Number) op.get("version")).longValue(), "occurredAt", occurredAt), 200, null,
                response -> observeCommit(driver, key(operation), event, response, true));
        synchronized (this) { op.put("acceptCommitted", true); persist(); }
    }

    private Map<String, Object> observeCommit(L08CapacityTrial.Participant driver, String key, UUID event, JsonNode response, boolean accepted) {
        synchronized (observer) {
            var rows = observer.queryForList("select a.id::text as offer_id,a.service_id::text as service_id,a.status,a.created_at,a.accepted_at,s.status as service_status,clock_timestamp() as observed_at "
                            + "from assignment a join scheduled_service s on s.id=a.service_id and s.organization_id=a.organization_id and s.current_offer_id=a.id "
                            + "where a.id=? and a.organization_id=? and a.driver_id=? and a.vehicle_id=? and a.idempotency_key=?",
                    UUID.fromString(response.path("id").asText()), UUID.fromString(driver.organizationId()), UUID.fromString(driver.driverId()), UUID.fromString(driver.vehicleId()), key);
            require(rows.size() == 1, "COMMIT_NOT_VISIBLE_TO_INDEPENDENT_CONNECTION");
            var row = rows.getFirst();
            require(response.path("serviceId").asText().equals(row.get("service_id")), "COMMITTED_SERVICE_REFERENCE_REQUIRED");
            require((accepted ? "SCHEDULED" : "PENDING_RESPONSE").equals(row.get("status"))
                    && (accepted ? "CONFIRMED" : "PENDING_RESPONSE").equals(row.get("service_status")), "EXACT_DURABLE_SERVER_STATE_REQUIRED");
            var out = new LinkedHashMap<String, Object>();
            out.put("offerId", row.get("offer_id")); out.put("serviceId", row.get("service_id"));
            out.put("offerStatus", row.get("status")); out.put("serviceStatus", row.get("service_status"));
            out.put("serverCreatedAt", instantText(row.get("created_at"))); out.put("observerServerAt", instantText(row.get("observed_at")));
            if (accepted) {
                require(row.get("accepted_at") != null, "SERVER_ACCEPTANCE_TIME_REQUIRED");
                require(observer.queryForObject("select count(*) from mobile_command_receipt where event_id=? and assignment_id=? and driver_id=? and organization_id=? and command_type='ASSIGNMENT_ACCEPT' and result_status='APPLIED' and http_status=200",
                        Integer.class, event, UUID.fromString(response.path("id").asText()), UUID.fromString(driver.driverId()), UUID.fromString(driver.organizationId())) == 1, "DURABLE_ACCEPTANCE_RECEIPT_REQUIRED");
                out.put("serverAcceptedAt", instantText(row.get("accepted_at"))); out.put("clientEventId", event.toString()); out.put("receiptApplied", true);
            }
            out.put("observerBackendPid", observerPid); out.put("durableCommitVisible", true);
            return out;
        }
    }

    @FunctionalInterface private interface DurableCheck { Map<String, Object> verify(JsonNode response); }

    private JsonNode request(String phase, String operation, String reference, L08CapacityTrial.Participant actor, String path, String method,
                             Object payload, int expectedStatus, String idempotencyKey, DurableCheck durable) throws Exception {
        long begin = System.nanoTime();
        var sample = new LinkedHashMap<String, Object>();
        sample.put("sampleId", UUID.randomUUID().toString()); sample.put("phase", phase); sample.put("operation", operation);
        if (currentWave.get() != null) sample.put("wave", currentWave.get());
        sample.put("reference", reference); sample.put("role", actor.role()); sample.put("accountId", actor.accountId());
        if (actor.organizationId() != null) sample.put("organizationId", actor.organizationId());
        sample.put("method", method); sample.put("path", path); sample.put("startedAt", Instant.now().toString());
        sample.put("startOffsetNs", begin - startedNanos); sample.put("httpStatus", 0); sample.put("success", false);
        long responseAt = 0;
        boolean counted = false;
        try {
            var builder = HttpRequest.newBuilder(URI.create(BASE + "/api/v1" + path)).timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + actor.token()).header("Accept", "application/json");
            if (idempotencyKey != null) builder.header("Idempotency-Key", idempotencyKey);
            if (payload == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
            else builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(payload)));
            int active = inflight.incrementAndGet(); counted = true; maximumInflight.accumulateAndGet(active, Math::max);
            sample.put("activeHttpAtStart", active);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (var stream = response.body()) { bytes = stream.readNBytes(BODY_LIMIT + 1); }
            responseAt = System.nanoTime(); inflight.decrementAndGet(); counted = false;
            sample.put("responseAt", Instant.now().toString()); sample.put("httpStatus", response.statusCode());
            sample.put("httpResponseMs", millis(responseAt - begin)); sample.put("responseBytes", bytes.length);
            require(bytes.length <= BODY_LIMIT, "HTTP_RESPONSE_BOUND_EXCEEDED");
            sample.put("responseSha256", hash(bytes));
            JsonNode body = bytes.length == 0 ? JSON.createObjectNode() : JSON.readTree(bytes);
            if (response.statusCode() != expectedStatus) {
                String serverCode = body.path("code").asText(body.path("error").path("code").asText("UNCLASSIFIED_HTTP_REJECTION"));
                sample.put("serverErrorCode", serverCode.matches("[A-Z][A-Z0-9_]{0,99}") ? serverCode : "REDACTED_UNCLASSIFIED_ERROR");
                throw new IllegalStateException("UNEXPECTED_HTTP_STATUS");
            }
            if (durable != null) {
                sample.put("durableObservation", durable.verify(body));
                long durableAt = System.nanoTime();
                sample.put("durableCommitObservedMs", millis(durableAt - begin));
                sample.put("observerAfterHttpMs", millis(durableAt - responseAt));
                sample.put("durableObservedAt", Instant.now().toString());
            }
            sample.put("success", true);
            return body;
        } catch (Exception failure) {
            sample.put("failureCode", safe(failure));
            if (responseAt == 0) sample.put("failedAfterMs", millis(System.nanoTime() - begin));
            throw failure;
        } finally {
            if (counted) inflight.decrementAndGet();
            append(sample);
        }
    }

    private synchronized Map<String, Object> operationState(String reference) throws Exception {
        Map<String, Object> op = operations.computeIfAbsent(reference, ignored -> new LinkedHashMap<>());
        persist(); return op;
    }

    private synchronized void append(Map<String, Object> sample) throws Exception {
        samples.add(sample);
        Files.writeString(journal, JSON.writeValueAsString(sample) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        persist();
    }

    private synchronized void persist() throws Exception {
        state.put("operations", operations); state.put("sampleCount", samples.size()); state.put("waves",waves); state.put("maxObservedHttpInflight", maximumInflight.get());
        state.put("updatedAt", Instant.now().toString());
        Path pending = stateFile.resolveSibling(stateFile.getFileName() + ".pending");
        JSON.writerWithDefaultPrettyPrinter().writeValue(pending.toFile(), state);
        Files.move(pending, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private synchronized Map<String, Object> summarize(String failure) throws Exception {
        var out = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> creates = measuredSamples(samples,"CREATE"), accepts = measuredSamples(samples,"ACCEPT");
        long errors = samples.stream().filter(s -> !Boolean.TRUE.equals(s.get("success"))).count();
        var createMetrics = metrics(creates);
        var acceptMetrics = metrics(accepts);
        boolean complete = creates.size() == 100 && accepts.size() == 100 && errors == 0 && failure == null;
        boolean fast = complete && ((Number) createMetrics.get("p95DurableCommitObservedMs")).doubleValue() <= 2000
                && ((Number) acceptMetrics.get("p95DurableCommitObservedMs")).doubleValue() <= 2000;
        out.put("schemaVersion", "1.0.0"); out.put("runId", RUN); out.put("recordedAt", Instant.now().toString());
        out.put("result", resultCode(complete,fast,maximumInflight.get(),failure));
        out.put("measured", complete); out.put("p95ThresholdMs", 2000); out.put("p95BothOperationsPass", fast);
        out.put("configuredConcurrentActors", ACTORS); out.put("maxObservedHttpInflight", maximumInflight.get());
        out.put("actors", participantReferences()); out.put("cycles", CYCLES); out.put("errorSamples", errors);
        out.put("waves",List.copyOf(waves));
        out.put("create", createMetrics); out.put("accept", acceptMetrics);
        out.put("warmupSamples", samples.stream().filter(s -> "warmup".equals(s.get("phase"))).count());
        out.put("supportAndPreparationAreExcludedFromWritePercentiles", true);
        out.put("allSamples", List.copyOf(samples)); out.put("observerBackendPid", observerPid);
        out.put("environment", environment());
        out.put("timingDefinition", "HTTP includes client serialization, local loopback, server transaction and complete bounded body read; durable timing additionally includes independent observer queue and SELECT.");
        out.put("networkOnlyMeasured", false); out.put("serverOnlyMeasured", false);
        out.put("harnessOverhead", "25 workers, one independent observer connection, bounded HTTP response hashing and append-only evidence writes share this PC; durability timing includes observer queue, while evidence archival occurs after timing.");
        out.put("mail", "SIMULATED"); out.put("gps", "SIMULATED_NOT_USED_BY_CREATION_ACCEPTANCE");
        out.put("limits", List.of("25 synthetic actors on one Windows PC, not 25 people/devices", "No restart, power-loss, production, independent monitor or backup claim", "Warmup, support reads, preparation, failures and reconciled commits excluded from successful write latency percentiles; failures remain visible", "No SQL mutations or automatic uncertain HTTP retries"));
        out.put("resumed", ((Number) state.getOrDefault("resumeCount", 0)).intValue() > 0);
        if (failure != null) out.put("failureCode", failure);
        return out;
    }

    private Map<String, Object> environment() {
        var env = prepared.app().getEnvironment();
        var out = new LinkedHashMap<String, Object>();
        out.put("backend", BASE); out.put("database", "ruta_fija_test@127.0.0.1:55439");
        out.put("javaVersion", System.getProperty("java.version")); out.put("os", System.getProperty("os.name"));
        out.put("availableProcessors", Runtime.getRuntime().availableProcessors()); out.put("jvmMaxHeapBytes", Runtime.getRuntime().maxMemory());
        out.put("backendHikariMaximumPoolSize", env.getProperty("spring.datasource.hikari.maximum-pool-size", "DEFAULT_LIBRARY_VALUE_NOT_INFERRED"));
        var source = prepared.app().getBean(javax.sql.DataSource.class);
        if (source instanceof com.zaxxer.hikari.HikariDataSource pool) {
            out.put("backendHikariMaximumPoolSize", pool.getMaximumPoolSize());
            out.put("backendHikariMinimumIdle", pool.getMinimumIdle());
            out.put("backendHikariConnectionTimeoutMs", pool.getConnectionTimeout());
        }
        out.put("workers", ACTORS); out.put("observerConnectionsOutsidePool", 1); out.put("httpConnectTimeoutSeconds", 5); out.put("httpRequestTimeoutSeconds", 30);
        out.put("durabilitySettings", state.get("durabilitySettings"));
        return out;
    }

    static List<Map<String, Object>> measuredSamples(List<Map<String,Object>> samples,String operation) {
        return samples.stream().filter(s -> "measurement".equals(s.get("phase")) && operation.equals(s.get("operation"))
                && Boolean.TRUE.equals(s.get("success")) && s.containsKey("durableCommitObservedMs")).toList();
    }

    static String resultCode(boolean complete,boolean p95Pass,int peak,String failure) {
        if (failure != null) return "PENDING_FAILURE_KEEP_CASE";
        return complete && p95Pass && peak == ACTORS ? "PASS_MEASURED_LOCAL_SYNTHETIC_ONLY" : "FAIL_MEASURED_LOCAL_SYNTHETIC_ONLY";
    }

    static Map<String, Object> metrics(List<Map<String, Object>> values) {
        var out = new LinkedHashMap<String, Object>(); out.put("successfulDurableSamples", values.size());
        if (values.isEmpty()) return out;
        for (String column : List.of("httpResponseMs", "durableCommitObservedMs", "observerAfterHttpMs")) {
            List<Double> sorted = values.stream().map(row -> ((Number) row.get(column)).doubleValue()).sorted().toList();
            String suffix = switch (column) { case "httpResponseMs" -> "HttpResponseMs"; case "durableCommitObservedMs" -> "DurableCommitObservedMs"; default -> "ObserverAfterHttpMs"; };
            out.put("p50" + suffix, nearestRank(sorted, 0.50)); out.put("p95" + suffix, nearestRank(sorted, 0.95));
            out.put("p99" + suffix, nearestRank(sorted, 0.99)); out.put("max" + suffix, sorted.getLast());
        }
        out.put("percentileMethod", "NEAREST_RANK_CEIL_P_TIMES_N"); return out;
    }

    static double nearestRank(List<Double> sorted, double percentile) {
        require(sorted != null && !sorted.isEmpty() && percentile > 0 && percentile <= 1, "VALID_NONEMPTY_PERCENTILE_REQUIRED");
        require(sorted.stream().allMatch(v -> v != null && Double.isFinite(v) && v >= 0), "FINITE_NONNEGATIVE_TIMINGS_REQUIRED");
        for (int i = 1; i < sorted.size(); i++) require(sorted.get(i - 1) <= sorted.get(i), "SORTED_TIMINGS_REQUIRED");
        return sorted.get(Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1));
    }

    static Instant scheduledAt(Instant origin, int cycle) {
        require(origin != null && cycle >= 0 && cycle < CYCLES, "VALID_MEASURED_CYCLE_REQUIRED");
        return origin.plus(Duration.ofHours(2L * cycle));
    }

    static void exactTarget(String url) {
        require("jdbc:postgresql://127.0.0.1:55439/ruta_fija_test".equals(url), "EXACT_AUTHORIZED_CAPACITY_TARGET_REQUIRED");
    }

    static void validateProfile(L08CapacityTrial.Prepared prepared) {
        require(prepared != null && prepared.owner() != null && "SUPER_ADMIN".equals(prepared.owner().role())
                && prepared.admins().size() == 4 && prepared.conductors().size() == 20, "EXACT_1_4_20_PROFILE_REQUIRED");
        var accounts = new HashSet<String>(); accounts.add(prepared.owner().accountId());
        var orgs = new HashSet<String>(); var drivers = new HashSet<String>(); var units = new HashSet<String>();
        for (var admin : prepared.admins()) require("ADMIN".equals(admin.role()) && nonblank(admin.token())
                && accounts.add(admin.accountId()) && nonblank(admin.organizationId()) && orgs.add(admin.organizationId()), "FOUR_DISTINCT_ORG_ADMIN_IDENTITIES_REQUIRED");
        for (var conductor : prepared.conductors()) require("CONDUCTOR".equals(conductor.role()) && nonblank(conductor.token())
                && accounts.add(conductor.accountId()) && orgs.contains(conductor.organizationId())
                && drivers.add(conductor.driverId()) && nonblank(conductor.driverId())
                && units.add(conductor.vehicleId()) && nonblank(conductor.vehicleId()), "TWENTY_DISTINCT_QUALIFIED_RESOURCES_REQUIRED");
        for (String org : orgs) require(prepared.conductors().stream().filter(c -> org.equals(c.organizationId())).count() == 5, "FIVE_CONDUCTORS_PER_ORGANIZATION_REQUIRED");
        require(nonblank(prepared.owner().token()) && accounts.size() == ACTORS, "EXACT_DISTINCT_25_ACTORS_REQUIRED");
    }

    private static boolean nonblank(String value) { return value != null && !value.isBlank(); }
    private static String instantText(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant().toString();
        if (value instanceof java.time.OffsetDateTime offset) return offset.toInstant().toString();
        if (value instanceof Instant instant) return instant.toString();
        throw new IllegalStateException("EXPLICIT_SERVER_TIMESTAMP_REQUIRED");
    }
    private static String operation(int cycle, String driverId) { return "cycle:" + cycle + ":driver:" + driverId; }
    private static String key(String suffix) { return "CAP25:" + UUID.nameUUIDFromBytes((RUN + ":" + suffix).getBytes(StandardCharsets.US_ASCII)); }
    private static double millis(long nanos) { return nanos / 1_000_000.0; }
    private static String hash(byte[] bytes) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static String safe(Exception failure) { String message = failure.getMessage(); return message != null && message.matches("[A-Z][A-Z0-9_]{0,99}") ? message : failure instanceof java.net.http.HttpTimeoutException ? "HTTP_TIMEOUT" : "DETAILS_REDACTED"; }
    private static void require(boolean test, String code) { if (!test) throw new IllegalStateException(code); }
}
