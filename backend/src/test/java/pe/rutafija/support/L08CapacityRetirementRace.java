package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.retention.application.RetentionProcedure;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Synthetic committed recovery-evidence fixture ONLY in the authorized CAP25
 * instance. It neither performs recovery nor enrolls/changes an owner's key. */
public final class L08CapacityRetirementRace {
    private static final String CASE = "860c1cdb-b062-469a-9a3f-156ea23bb656";
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final UUID CHALLENGE = reference("retirement-challenge");
    private static final UUID PLAN = reference("retirement-plan");
    private L08CapacityRetirementRace() { }

    public static Map<String,Object> run(ConfigurableApplicationContext app, UUID ownerId,
            String ownerAccessToken, String ownerPassword, Path evidenceDirectory) throws Exception {
        var jdbc = app.getBean(JdbcTemplate.class);
        target(jdbc);
        require("L08_CAP25_01_AUTHORIZED".equals(System.getenv("RF_L08_CAP25_AUTHORIZATION")), "CAP25_RETIREMENT_AUTH_REQUIRED");
        var output = evidenceDirectory.resolve("retiro-concurrencia.json");
        var progress = evidenceDirectory.resolve("retiro-concurrencia-progreso.json");
        if (Files.exists(output)) {
            var prior = JSON.readValue(Files.readAllBytes(output), FixtureJson.OBJECT_TYPE);
            require("PASS_ISOLATED_COMMITTED_RETIREMENT_RACE_SYNTHETIC_ONLY".equals(prior.get("result"))
                && CASE.equals(prior.get("caseId"))&&PLAN.toString().equals(prior.get("planId"))&&CHALLENGE.toString().equals(prior.get("challengeId"))
                && ownerId.toString().equals(prior.get("ownerId")), "CAP25_RETIREMENT_PRIOR_RESULT_REQUIRED");
            require(JSON.valueToTree(prior.get("ledgerBefore")).equals(JSON.valueToTree(ledger(jdbc)))
                && credentialHash(jdbc,ownerId).equals(prior.get("ownerCredentialRowSetSha256Before"))
                && Objects.equals(((Number)prior.get("ownerCredentialCountBefore")).intValue(),credentialCount(jdbc,ownerId)),"CAP25_RETIREMENT_PRIOR_FACTS_MUST_STILL_MATCH");
            verifyCommitted(jdbc); return prior;
        }
        require(!Files.exists(progress), "CAP25_RETIREMENT_SEGMENT_PENDING_KEEP_CASE");
        require(jdbc.queryForObject("select count(*) from super_admin_recovery_challenge where id=?", Integer.class, CHALLENGE) == 0
            && jdbc.queryForObject("select count(*) from retention_plan where id=?", Integer.class, PLAN) == 0, "CAP25_RETIREMENT_NEW_FIXTURE_REFERENCES_REQUIRED");
        var evidence = new LinkedHashMap<String,Object>();
        evidence.put("caseId", CASE); evidence.put("planId", PLAN.toString()); evidence.put("challengeId", CHALLENGE.toString());
        evidence.put("ownerId",ownerId.toString());
        evidence.put("startedAt", Instant.now().toString()); evidence.put("fixtureKind", "SYNTHETIC_CONSUMED_CHALLENGE_NOT_A_RECOVERY_RUN");
        var ledgerBefore = ledger(jdbc); String credentialBefore = credentialHash(jdbc, ownerId);
        evidence.put("ledgerBefore", ledgerBefore);
        int credentialCountBefore=credentialCount(jdbc,ownerId);
        evidence.put("ownerCredentialCountBefore",credentialCountBefore);
        evidence.put("ownerCredentialRowSetSha256Before",credentialBefore);
        evidence.put("ownerKeyEnrolledByThisTest",false);
        // This explicitly synthetic past anchor tests due-time policy, not an
        // assertion that a real recovery happened thirty-one days ago.
        Instant anchor = Instant.now().minus(Duration.ofDays(31));
        jdbc.update("""
            insert into super_admin_recovery_challenge(id,user_id,installation_id,account_reference,requested_email,nonce,expires_at,proof_verified_at,consumed_at,created_at,updated_at)
            values(?,?,?,?,'retention.fixture@rutafija.test',?,?,?, ?,?,?)
            """, CHALLENGE, ownerId, reference("installation"), reference("account-reference"),
            Base64.getUrlEncoder().withoutPadding().encodeToString(reference("nonce").toString().substring(0,32).getBytes(StandardCharsets.US_ASCII)),
            Timestamp.from(anchor.plusSeconds(300)), Timestamp.from(anchor), Timestamp.from(anchor), Timestamp.from(anchor.minusSeconds(60)), Timestamp.from(anchor));
        evidence.put("syntheticPastAnchor", anchor.toString());
        Files.write(progress, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence), StandardOpenOption.CREATE_NEW);
        var procedure = app.getBean(RetentionProcedure.class);
        var plan = authenticated(app, ownerAccessToken, () -> procedure.requestPlatformRecoveryPlan(PLAN,
            List.of(CHALLENGE), reference("purpose"), "SYNTHETIC_CAP25_RETIREMENT_RACE", ownerPassword.toCharArray()));
        evidence.put("selectionSha256", plan.selectionSha256());
        save(progress, evidence);

        // A locked inventory row must reject the entire first attempt before
        // any retirement or execution is committed.
        try (var blocker = app.getBean(DataSource.class).getConnection()) {
            blocker.setAutoCommit(false);
            try (var q = blocker.prepareStatement("select id from super_admin_recovery_challenge where id=? for update")) {
                q.setObject(1, CHALLENGE); try (var rows = q.executeQuery()) { require(rows.next(), "CAP25_FIXTURE_ROW_REQUIRED"); }
            }
            boolean rejectedForInventoryLock = false;
            try { authenticated(app, ownerAccessToken, () -> procedure.execute(PLAN, plan.selectionSha256(), ownerPassword.toCharArray())); }
            catch (RuntimeException failure) { rejectedForInventoryLock = "55P03".equals(sqlState(failure)); }
            finally { blocker.rollback(); }
            require(rejectedForInventoryLock, "CAP25_INVENTORY_NOWAIT_REJECTION_REQUIRED");
        }
        require(jdbc.queryForObject("select count(*) from retention_execution where plan_id=?", Integer.class, PLAN) == 0
            && jdbc.queryForObject("select count(*) from super_admin_recovery_challenge where id=?", Integer.class, CHALLENGE) == 1,
            "CAP25_LOCK_REJECTION_MUST_PRESERVE_RESOURCE");
        evidence.put("inventoryNowaitRejectedWithSqlState", "55P03"); save(progress, evidence);

        var transaction = new TransactionTemplate(app.getBean(PlatformTransactionManager.class));
        transaction.setTimeout(20);
        var firstExecuted = new CountDownLatch(1); var releaseFirstCommit = new CountDownLatch(1);
        var secondEntering = new CountDownLatch(1); var firstPid = new AtomicInteger(); var secondPid = new AtomicInteger();
        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> authenticated(app, ownerAccessToken, () -> transaction.execute(status -> {
                firstPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                var result = procedure.execute(PLAN, plan.selectionSha256(), ownerPassword.toCharArray());
                firstExecuted.countDown(); await(releaseFirstCommit); return result;
            })));
            require(firstExecuted.await(10, TimeUnit.SECONDS), "CAP25_FIRST_RETIREMENT_DID_NOT_REACH_COMMIT_BARRIER");
            var second = workers.submit(() -> authenticated(app, ownerAccessToken, () -> transaction.execute(status -> {
                secondPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class)); secondEntering.countDown();
                return procedure.execute(PLAN, plan.selectionSha256(), ownerPassword.toCharArray());
            })));
            require(secondEntering.await(5, TimeUnit.SECONDS), "CAP25_SECOND_RETIREMENT_NOT_STARTED");
            boolean blocked = false; long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < deadline && !second.isDone()) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("select ? = any(pg_blocking_pids(?))", Boolean.class, firstPid.get(), secondPid.get()));
                if (blocked) break; Thread.sleep(25);
            }
            require(blocked && !second.isDone(), "CAP25_REAL_TRANSACTION_WAIT_NOT_OBSERVED");
            evidence.put("secondConnectionBlockedByFirstVerified", true); save(progress, evidence);
            releaseFirstCommit.countDown();
            var initial = first.get(10, TimeUnit.SECONDS); var replay = second.get(10, TimeUnit.SECONDS);
            require(initial != null && replay != null && !initial.replay() && replay.replay()
                && initial.retiredCount() == 1 && replay.retiredCount() == 1 && initial.executedAt().equals(replay.executedAt()),
                "CAP25_ONE_EXECUTION_AND_ONE_REPLAY_REQUIRED");
            evidence.put("firstReplay", initial.replay()); evidence.put("secondReplay", replay.replay());
            evidence.put("executedAt", initial.executedAt().toString());
        } finally {
            releaseFirstCommit.countDown(); workers.shutdownNow();
            require(workers.awaitTermination(10, TimeUnit.SECONDS), "CAP25_RETIREMENT_WORKERS_NOT_STOPPED");
        }
        verifyCommitted(jdbc);
        require(ledgerBefore.equals(ledger(jdbc)), "CAP25_RETIREMENT_LEDGER_CHANGED");
        require(Objects.equals(credentialBefore, credentialHash(jdbc, ownerId)), "CAP25_OWNER_CREDENTIAL_CHANGED");
        require(credentialCountBefore==credentialCount(jdbc,ownerId),"CAP25_OWNER_CREDENTIAL_COUNT_CHANGED");
        evidence.put("ownerCredentialCountAfter",credentialCountBefore);
        evidence.put("ownerCredentialClaimLimit",credentialCountBefore==0?"ABSENCE_OF_ENROLLED_CREDENTIAL_PRESERVED_NOT_A_KEY_CONSERVATION_TEST":"EXISTING_SYNTHETIC_CREDENTIAL_ROWSET_UNCHANGED");
        evidence.put("oneCommittedExecution", true); evidence.put("oneRetirementTombstone", true);
        evidence.put("oneJavaExecutionAudit", true); evidence.put("consumedFixtureRemoved", true);
        evidence.put("ownerCredentialUnchanged", true); evidence.put("ledgerUnchanged", true);
        evidence.put("authenticationAndReauthentication", "NORMAL_JWT_DECODER_AND_RETENTION_PROCEDURE_SYNTHETIC_ACCOUNT");
        evidence.put("humanRecoveryOrIndependentCustodyAccredited", false);
        evidence.put("childWriteSerializationAllPurposesAccredited", false);
        evidence.put("finishedAt", Instant.now().toString());
        evidence.put("result", "PASS_ISOLATED_COMMITTED_RETIREMENT_RACE_SYNTHETIC_ONLY");
        Files.write(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence), StandardOpenOption.CREATE_NEW);
        return evidence;
    }

    private static void target(JdbcTemplate jdbc) {
        var db = jdbc.queryForMap("select current_database() db,current_user principal,inet_server_port() port,host(inet_server_addr()) address");
        require("ruta_fija_test".equals(db.get("db")) && "rf_test".equals(db.get("principal"))
            && Integer.valueOf(55439).equals(db.get("port")) && "127.0.0.1".equals(db.get("address")), "CAP25_RETIREMENT_EXACT_TARGET_REQUIRED");
    }
    private static void verifyCommitted(JdbcTemplate jdbc) {
        require(jdbc.queryForObject("select count(*) from retention_execution where plan_id=?", Integer.class, PLAN) == 1
            && jdbc.queryForObject("select count(*) from retention_personal_retirement where plan_id=? and resource_id=?", Integer.class, PLAN, CHALLENGE) == 1
            && jdbc.queryForObject("select count(*) from audit_event where action='RETENTION_PLAN_EXECUTED' and entity_id=?", Integer.class, PLAN) == 1
            && jdbc.queryForObject("select count(*) from super_admin_recovery_challenge where id=?", Integer.class, CHALLENGE) == 0,
            "CAP25_COMMITTED_RETIREMENT_EXACT_FACTS_REQUIRED");
    }
    private static <T> T authenticated(ConfigurableApplicationContext app, String token, Supplier<T> action) {
        var jwt = app.getBean(JwtDecoder.class).decode(token);
        var previous = SecurityContextHolder.getContext(); var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role")))));
        SecurityContextHolder.setContext(context);
        try { return action.get(); } finally { SecurityContextHolder.setContext(previous); }
    }
    private static List<Map<String,Object>> ledger(JdbcTemplate jdbc) { return jdbc.queryForList("select role,count(*) creations from pilot_account_creation group by role order by role"); }
    private static String credentialHash(JdbcTemplate jdbc, UUID owner) {
        return jdbc.queryForObject("select encode(sha256(convert_to(coalesce(string_agg(to_jsonb(c)::text,'' order by user_id),''),'UTF8')),'hex') from super_admin_recovery_credential c where user_id=?", String.class, owner);
    }
    private static int credentialCount(JdbcTemplate jdbc,UUID owner){return jdbc.queryForObject("select count(*) from super_admin_recovery_credential where user_id=?",Integer.class,owner);}
    private static UUID reference(String suffix) { return UUID.nameUUIDFromBytes((CASE + ':' + suffix).getBytes(StandardCharsets.UTF_8)); }
    private static void save(Path path, Map<String,Object> value) throws Exception { Files.write(path, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(value), StandardOpenOption.TRUNCATE_EXISTING); }
    private static String sqlState(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) if (cause instanceof SQLException sql) return sql.getSQLState();
        return "NONE";
    }
    private static void await(CountDownLatch latch) {
        try { require(latch.await(15, TimeUnit.SECONDS), "CAP25_COMMIT_BARRIER_TIMEOUT"); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException("CAP25_COMMIT_BARRIER_INTERRUPTED"); }
    }
    private static void require(boolean value, String reason) { if (!value) throw new IllegalStateException(reason); }
}
