package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;

/** Opt-in human rehearsal. Test sources only; inspection never starts Spring or changes data. */
public final class ManualL01HumanTrial {
    static final long CLEANUP_LOCK = 7_518_440_514L;
    static final String APPROVED_SHA = "20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417";
    static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private ManualL01HumanTrial() { }

    public static void main(String[] args) {
        try {
            if (args.length == 1 && "--inspect".equals(args[0])) {
                System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(inspect()));
                return;
            }
            if (args.length == 1 && ("--serve".equals(args[0]) || "--smoke".equals(args[0]))) {
                ManualL01HumanTrialServer.run("--smoke".equals(args[0]));
                return;
            }
            if (args.length == 2 && "--inspect-case".equals(args[0])) {
                System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(inspectCase(UUID.fromString(args[1]))));
                return;
            }
            if (args.length == 3 && "--serve".equals(args[0]) && "--resume-case".equals(args[1])) {
                ManualL01HumanTrialServer.run(false, UUID.fromString(args[2]));
                return;
            }
            if (args.length == 2 && "--complete-approved-local".equals(args[0])) {
                ManualL01HumanTrialServer.completeApprovedLocal(UUID.fromString(args[1]));
                return;
            }
            throw new IllegalStateException("UNKNOWN_MODE");
        } catch (Exception failure) {
            System.err.println("L01_HUMAN_TRIAL=REFUSED_DETAILS_REDACTED");
            if (failure instanceof IllegalStateException && failure.getMessage() != null && failure.getMessage().matches("[A-Z][A-Z0-9_]{0,100}"))
                System.err.println("L01_HUMAN_REASON=" + failure.getMessage());
            System.exit(1);
        }
    }

    static Map<String, Object> configuration() {
        Map<String, Object> values = new HashMap<>();
        NativePostgresTestDatabase.load().registerSpringProperties((key, value) -> values.put(key, value.get()));
        return values;
    }

    static Connection connect(Map<String, Object> values) throws Exception {
        return DriverManager.getConnection((String) values.get("spring.datasource.url"),
                (String) values.get("spring.datasource.username"), (String) values.get("spring.datasource.password"));
    }

    static Map<String, Object> inspect() throws Exception {
        Map<String, Object> values = configuration();
        try (Connection connection = connect(values)) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertDatabase(jdbc);
            Map<String, Object> state = snapshot(jdbc);
            state.put("inspectionAtUtc", Instant.now().toString());
            state.put("readOnly", true);
            state.put("smtpAttempts", 0);
            state.put("springStarted", false);
            state.put("migrationsExecuted", false);
            connection.rollback();
            return state;
        }
    }

    static Map<String, Object> inspectCase(UUID runId) throws Exception {
        try (Connection connection = connect(configuration())) {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            assertDatabase(jdbc);
            Map<String, Object> result = snapshot(jdbc);
            result.put("runId", runId.toString());
            result.put("caseInventory", ManualL01HumanTrialServer.bootstrapInventory(jdbc, runId));
            boolean markerPresent = Files.isRegularFile(ManualL01HumanTrialServer.ACTIVE);
            String marker = markerPresent ? Files.readString(ManualL01HumanTrialServer.ACTIVE).strip() : "";
            Map<String, Object> caseState = Files.isRegularFile(ManualL01HumanTrialServer.STATE)
                    ? JSON.readValue(Files.readAllBytes(ManualL01HumanTrialServer.STATE), Map.class) : Map.of();
            boolean runtimeKeyPresent = Files.exists(ManualL01HumanTrialServer.SECRET);
            String eligibility = ManualL01HumanTrialServer.bootstrapResumeRefusal(runId, marker, caseState,
                    runtimeKeyPresent, (Map<String, Object>) result.get("caseInventory"));
            result.put("markerPresentAndMatches", runId.toString().equals(marker));
            result.put("stateFilePresent", !caseState.isEmpty());
            result.put("privateRuntimeKeyFilePresent", runtimeKeyPresent);
            if (ManualL01RecoveryFixtureRepair.RUN.toString().equals(caseState.get("runId"))) {
                var repairFacts = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc, null, caseState, 0, null).inspect();
                result.put("syntheticRecoveryFixtureRepairReadOnlyFacts", repairFacts);
                result.put("syntheticRecoveryFixtureRepairRequiresServingLock", true);
                UUID segment = caseState.get("recoveryObservationSegmentId") instanceof String value ? UUID.fromString(value) : null;
                result.put("syntheticRecoveryFixtureKeyPreparationReadOnlyFacts",
                        new ManualL01RecoveryFixtureKeyReplacement.JdbcStore(jdbc, null, caseState, 0, segment).inspect());
                result.put("syntheticRecoveryFixtureKeyPreparationRequiresServingLock", true);
                var keyAudits = jdbc.queryForList(ManualL01RecoveryFixtureKeyReplacement.PUBLIC_AUDITS_SQL,
                        ManualL01RecoveryFixtureRepair.OWNER, ManualL01RecoveryFixtureKeyReplacement.ACTION);
                result.put("syntheticRecoveryFixtureKeyPreparationPublicAudits", keyAudits);
                var currentKeys = jdbc.queryForList("select public_key_x509 from super_admin_recovery_credential where id=1 and user_id=?",
                        String.class, ManualL01RecoveryFixtureRepair.OWNER);
                String currentPublic = currentKeys.size() == 1 ? currentKeys.getFirst() : null;
                if (currentPublic != null) result.put("currentRecoveryPublicKeyFingerprintSha256",
                        ManualL01RecoveryFixtureKeyReplacement.fingerprint(currentPublic));
                if (currentPublic != null && keyAudits.size() == 1 && ManualL01RecoveryFixtureKeyReplacement.authorizationValid()) {
                    var verified = ManualL01RecoveryFixtureKeyReplacement.restorePublicReference(keyAudits.getFirst(),
                            ManualL01RecoveryFixtureKeyReplacement.fingerprint(currentPublic), caseState);
                    result.put("syntheticRecoveryFixtureKeyPreparationAuditCorrespondenceVerified", !verified.isEmpty());
                }
            }
            result.put("passwordRecaptureEligibility", eligibility == null ? "ELIGIBLE_SAME_CASE_NO_PREPARED_ACCOUNT" : eligibility);
            result.put("legacyExactFirstRejectionCause", "NOT_RECORDED_CANNOT_RECONSTRUCT");
            if (caseState.get("bootstrapHistory") instanceof List<?> history && !history.isEmpty()
                    && history.getLast() instanceof Map<?, ?> last && last.get("reason") instanceof String reason
                    && reason.matches("[A-Z][A-Z0-9_]{0,100}")) result.put("latestRecordedBootstrapReason", reason);
            boolean blocked = false;
            try { NativePostgresTestDatabase.assertNoHumanTrialMarker(ManualL01HumanTrialServer.ACTIVE); }
            catch (IllegalStateException expected) { blocked = true; }
            result.put("standardCleanupRefusedByMarker", blocked);
            result.put("inspectionAtUtc", Instant.now().toString());
            result.put("readOnly", true);
            result.put("smtpAttempts", 0);
            result.put("writes", 0);
            connection.rollback();
            return result;
        }
    }

    static void assertDatabase(JdbcTemplate jdbc) {
        if (!"ruta_fija_test".equals(jdbc.queryForObject("select current_database()", String.class))
                || !"rf_test".equals(jdbc.queryForObject("select current_user", String.class)))
            throw new IllegalStateException("WRONG_DATABASE_OR_ROLE");
        String version = jdbc.queryForObject("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1", String.class);
        if (!"26".equals(version) || count(jdbc, "select count(*) from flyway_schema_history where not success") != 0)
            throw new IllegalStateException("UNEXPECTED_SCHEMA");
    }

    static Map<String, Object> snapshot(JdbcTemplate jdbc) throws Exception {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("database", "ruta_fija_test");
        state.put("role", "rf_test");
        state.put("schema", "V26");
        Map<String, Long> creations = new LinkedHashMap<>();
        for (String role : List.of("SUPER_ADMIN", "ADMIN", "CONDUCTOR"))
            creations.put(role, count(jdbc, "select count(*) from pilot_account_creation where role=?", role));
        state.put("historicalCreations", creations);
        state.put("remainingSlots", Map.of("SUPER_ADMIN", 1 - creations.get("SUPER_ADMIN"),
                "ADMIN", 4 - creations.get("ADMIN"), "CONDUCTOR", 20 - creations.get("CONDUCTOR")));
        state.put("superAdminAccounts", count(jdbc, "select count(*) from app_user where role='SUPER_ADMIN'"));
        state.put("personIdentityBindings", count(jdbc, "select count(*) from person_identity"));
        state.put("personIdentityKeyRecorded", count(jdbc, "select count(*) from person_identity_hash_key") != 0);
        state.put("recoveryCredentialPresent", count(jdbc, "select count(*) from super_admin_recovery_credential") != 0);
        state.put("eligibleSyntheticPendingAdmins", count(jdbc, """
                select count(*) from app_user u join admin_activation a on a.admin_user_id=u.id
                join organization o on o.id=u.organization_id
                where u.role='ADMIN' and not u.active and u.email like '%@rutafija.test'
                  and u.full_name like 'SYNTHETIC%' and o.status='ACTIVE'
                  and a.status in ('PENDING_CLAIM','IN_PROGRESS')
                """));
        state.put("termsAcceptanceCount", count(jdbc, "select count(*) from admin_terms_acceptance"));
        var documents = jdbc.queryForList("select id::text,version,content_sha256,synthetic from admin_terms_document where document_key='ADMIN_TERMS' order by created_at desc,id desc limit 1");
        state.put("currentTerms", documents.isEmpty() ? Map.of() : documents.getFirst());
        state.put("publishedFileHashMatches", publishedHashMatches());
        state.put("cleanupLockHeld", Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from pg_locks where locktype='advisory' and granted and objsubid=1
                  and database=(select oid from pg_database where datname=current_database())
                  and classid::bigint=(?::bigint >> 32) and objid::bigint=(?::bigint & 4294967295))
                """, Boolean.class, CLEANUP_LOCK, CLEANUP_LOCK)));
        return state;
    }

    static boolean publishedHashMatches() throws Exception {
        byte[] bytes = Files.readAllBytes(ROOT.resolve("docs/legal/publicado/terminos-admin-v1.0.0.md"));
        return APPROVED_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    static long count(JdbcTemplate jdbc, String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }
}
