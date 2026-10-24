package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static pe.rutafija.support.ManualL01HumanTrial.*;

/** REC-CI07-02: exact synthetic fixture only. Never included in production artifacts. */
final class ManualL01RecoveryFixtureRepair {
    static final UUID RUN = UUID.fromString("140c10a1-be0f-4d08-a6d4-c796e2ed107a");
    static final UUID OWNER = UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0");
    static final UUID ORGANIZATION = UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
    static final UUID ADMIN = UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
    static final UUID INSTALLATION = UUID.fromString("f0cd888b-57df-4def-9d08-7882ffadebae");
    static final String PUBLIC_SHA = "5b727e9aa6252667a1cbdc30f7575fb5007f9d8bb159d0e4dd2b5dcb27fbe00c";
    static final String PROPOSAL_SHA = "B1B22C7CF2928397E111EF9F7464248E0C04E1A57A1E60E2C57236ED4A58B4A2";
    static final String APPROVAL_SHA = "EF4E1B5B5C03C448F77B4F2F9B744BC5E710DF75A36902D2FCE91B97EBE9CFC4";
    static final String AUDIT_ACTION = "L01_SYNTHETIC_RECOVERY_FIXTURE_REPAIRED";
    static final String UPDATE_SQL = """
            update super_admin_recovery_credential set emergency_code_hash=?,updated_at=current_timestamp
            where id=1 and user_id=? and installation_id=?
            """;
    static final String AUDIT_SQL = """
            insert into audit_event(id,organization_id,user_id,action,entity_type,entity_id,metadata_json,occurred_at)
            values(?,null,?,?,'L01_SYNTHETIC_FIXTURE',?,?::jsonb,current_timestamp)
            """;
    static final List<String> REQUIRED = List.of("authorizationAndOutputCheck", "exactCaseAndMarker",
            "environmentApproved", "harnessOwnsCleanupLock", "identitiesMatch", "identityReviewed",
            "credentialMatches", "termsAndAcceptancePreserved", "historicalLedgerPreserved",
            "noCompletedRecovery", "noLiveChallenge", "noPriorRepair", "enrollmentObservedOnce",
            "observationHealthy");

    interface ProtectedTerminal {
        void showOnce(char[] fictitiousFixtureCode);
        char[] readHiddenConfirmation();
    }
    interface Authentication {
        void authenticateAndReauthenticate() throws Exception;
        void verifyCurrentSession() throws Exception;
    }
    interface Store {
        Map<String, Object> inspect() throws Exception;
        Map<String, Object> commit(String encodedCode) throws Exception;
    }

    static String refusal(Map<String, Object> facts) {
        for (String key : REQUIRED) if (!Boolean.TRUE.equals(facts.get(key)))
            return "REPAIR_GUARD_" + key.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase(Locale.ROOT);
        return null;
    }

    static void requireReady(Map<String, Object> facts) {
        String refusal = refusal(facts);
        if (refusal != null) throw new IllegalStateException(refusal);
    }

    static Map<String, Object> execute(ProtectedTerminal terminal, Authentication authentication, Store store,
            PasswordEncoder encoder, Consumer<char[]> committedSecret) throws Exception {
        return execute(terminal, authentication, store, encoder, committedSecret, () -> {
            byte[] random = new byte[32];
            new SecureRandom().nextBytes(random);
            try { return Base64.getUrlEncoder().withoutPadding().encodeToString(random).toCharArray(); }
            finally { Arrays.fill(random, (byte) 0); }
        });
    }

    static Map<String, Object> execute(ProtectedTerminal terminal, Authentication authentication, Store store,
            PasswordEncoder encoder, Consumer<char[]> committedSecret, Supplier<char[]> generator) throws Exception {
        requireReady(store.inspect());
        authentication.authenticateAndReauthenticate();
        char[] code = generator.get(), confirmation = null;
        try {
            if (!validCode(code)) throw new IllegalStateException("REPAIR_GENERATED_FORMAT_INVALID");
            terminal.showOnce(code);
            confirmation = terminal.readHiddenConfirmation();
            if (!sameCode(code, confirmation)) throw new IllegalStateException("REPAIR_CONFIRMATION_REJECTED_NO_WRITE");
            authentication.verifyCurrentSession();
            requireReady(store.inspect());
            String encoded = encoder.encode(CharBuffer.wrap(code));
            Map<String, Object> result = store.commit(encoded);
            committedSecret.accept(code);
            return result;
        } finally {
            if (code != null) Arrays.fill(code, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    static boolean validCode(char[] code) {
        if (code == null || code.length != 43) return false;
        for (char c : code) if (!(c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'
                || c >= '0' && c <= '9' || c == '_' || c == '-')) return false;
        return true;
    }

    static boolean sameCode(char[] expected, char[] actual) {
        if (!validCode(expected) || !validCode(actual)) return false;
        byte[] a = new byte[43], b = new byte[43];
        try {
            for (int i = 0; i < 43; i++) { a[i] = (byte) expected[i]; b[i] = (byte) actual[i]; }
            return MessageDigest.isEqual(a, b);
        } finally { Arrays.fill(a, (byte) 0); Arrays.fill(b, (byte) 0); }
    }

    static void lockHistoricalCreationCounters(JdbcTemplate jdbc) {
        // Reuse V22/V23 insertion locks; rf_test deliberately has SELECT-only ledger privileges.
        for (int role = 1; role <= 3; role++) jdbc.queryForList("select pg_advisory_xact_lock(751844,?)", role);
    }

    static boolean authorizationValid() throws Exception {
        byte[] bytes = Files.readAllBytes(ROOT.resolve("docs/operacion/L-01-REC-CI07-02-autorizacion-2026-10-17.json"));
        if (!APPROVAL_SHA.equalsIgnoreCase(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))) return false;
        if (!PROPOSAL_SHA.equalsIgnoreCase(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                Files.readAllBytes(ROOT.resolve("docs/operacion/L-01-REC-CI07-02-reparacion-sintetica-propuesta.md")))))) return false;
        var a = JSON.readTree(bytes);
        return a.path("authorized").asBoolean() && a.path("operatorTerminalBOutputCheckPassed").asBoolean()
                && RUN.toString().equals(a.path("runId").asText())
                && a.path("maximumSuccessfulFixtureRepairs").asInt() == 1
                && a.path("hiddenHumanConfirmationBeforeWriteRequired").asBoolean();
    }

    static class JdbcStore implements Store {
        private final JdbcTemplate jdbc;
        private final PlatformTransactionManager transactions;
        private final Map<String, Object> state;
        private final int cleanupLockPid;
        private final UUID segment;

        JdbcStore(JdbcTemplate jdbc, PlatformTransactionManager transactions, Map<String, Object> state,
                int cleanupLockPid, UUID segment) {
            this.jdbc = jdbc; this.transactions = transactions; this.state = state;
            this.cleanupLockPid = cleanupLockPid; this.segment = segment;
        }

        @Override public Map<String, Object> inspect() throws Exception {
            assertDatabase(jdbc);
            Map<String, Object> facts = new LinkedHashMap<>();
            facts.put("authorizationAndOutputCheck", authorizationValid());
            facts.put("exactCaseAndMarker", RUN.toString().equals(state.get("runId"))
                    && OWNER.toString().equals(state.get("ownerId"))
                    && ADMIN.toString().equals(state.get("adminId"))
                    && ORGANIZATION.toString().equals(state.get("organizationId"))
                    && "ruta_fija_test".equals(state.get("database"))
                    && RUN.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip()));
            facts.put("environmentApproved", true);
            facts.put("harnessOwnsCleanupLock", cleanupLockPid > 0 && Boolean.TRUE.equals(jdbc.queryForObject("""
                    select exists(select 1 from pg_locks where locktype='advisory' and granted and objsubid=1
                      and pid=? and database=(select oid from pg_database where datname=current_database())
                      and classid::bigint=(?::bigint >> 32) and objid::bigint=(?::bigint & 4294967295))
                    """, Boolean.class, cleanupLockPid, CLEANUP_LOCK, CLEANUP_LOCK)));
            facts.put("identitiesMatch", count(jdbc, """
                    select count(*) from app_user where id=? and role='SUPER_ADMIN' and active
                      and organization_id is null and email='sa.human.140c10a1@rutafija.test'
                      and email_verified_at is not null and full_name='SYNTHETIC HUMAN TRIAL OWNER' and session_version=0
                    """, OWNER) == 1 && count(jdbc, """
                    select count(*) from app_user u join admin_activation a on a.admin_user_id=u.id
                      join organization o on o.id=u.organization_id where u.id=? and u.organization_id=?
                      and u.role='ADMIN' and u.active and u.full_name='SYNTHETIC HUMAN ADMIN'
                      and u.email='admin.human.140c10a1@rutafija.test' and a.created_by_super_admin_id=?
                      and a.status='COMPLETED' and o.status='ACTIVE'
                    """, ADMIN, ORGANIZATION, OWNER) == 1);
            facts.put("identityReviewed", count(jdbc, "select count(*) from person_identity where user_id in (?,?)", OWNER, ADMIN) == 2);
            var credentials = jdbc.queryForList("select installation_id::text,user_id::text,public_key_x509 from super_admin_recovery_credential where id=1");
            boolean credentialMatches = credentials.size() == 1
                    && INSTALLATION.toString().equals(credentials.getFirst().get("installation_id"))
                    && OWNER.toString().equals(credentials.getFirst().get("user_id"));
            if (credentialMatches) credentialMatches = PUBLIC_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Base64.getDecoder().decode(credentials.getFirst().get("public_key_x509").toString()))));
            facts.put("credentialMatches", credentialMatches && PUBLIC_SHA.equals(state.get("publicKeyFingerprintSha256")));
            facts.put("termsAndAcceptancePreserved", publishedHashMatches() && count(jdbc, """
                    select count(*) from admin_terms_acceptance a join admin_terms_document d on d.id=a.document_id
                    where a.id='c496b63c-735f-48cc-be56-90f7ec1464e1' and a.admin_user_id=? and a.organization_id=?
                      and a.document_version='1.0.0' and d.version='1.0.0' and d.document_key='ADMIN_TERMS' and not d.synthetic
                      and a.content_sha256=? and d.content_sha256=a.content_sha256
                    """, ADMIN, ORGANIZATION, APPROVED_SHA) == 1);
            facts.put("historicalLedgerPreserved", count(jdbc, "select count(*) from pilot_account_creation where role='SUPER_ADMIN'") == 1
                    && count(jdbc, "select count(*) from pilot_account_creation where role='ADMIN'") == 2
                    && count(jdbc, "select count(*) from pilot_account_creation where role='CONDUCTOR'") == 0);
            facts.put("noCompletedRecovery", count(jdbc, "select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_COMPLETED'", OWNER) == 0
                    && count(jdbc, "select count(*) from super_admin_recovery_challenge where user_id=? and (consumed_at is not null or proof_verified_at is not null)", OWNER) == 0);
            facts.put("noLiveChallenge", count(jdbc, "select count(*) from super_admin_recovery_challenge where user_id=? and consumed_at is null and expires_at>current_timestamp", OWNER) == 0);
            facts.put("noPriorRepair", count(jdbc, "select count(*) from audit_event where user_id=? and action=?", OWNER, AUDIT_ACTION) == 0);
            facts.put("enrollmentObservedOnce", ((Number) state.getOrDefault("enrollmentResponseCount", 0)).intValue() == 1
                    && ((Number) state.getOrDefault("recoveryCompletionResponseCount", 0)).intValue() == 0
                    && count(jdbc, "select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_ENROLLED'", OWNER) == 1);
            facts.put("observationHealthy", !Boolean.TRUE.equals(state.get("observationIncomplete")));
            return facts;
        }

        @Override public Map<String, Object> commit(String encodedCode) throws Exception {
            if (transactions == null || segment == null) throw new IllegalStateException("REPAIR_TRANSACTION_CONTEXT_REQUIRED");
            TransactionTemplate tx = new TransactionTemplate(transactions);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            tx.setTimeout(10);
            return Objects.requireNonNull(tx.execute(status -> {
                try {
                    lockHistoricalCreationCounters(jdbc);
                    jdbc.queryForList("select id from app_user where id=? for update", OWNER);
                    jdbc.queryForList("select id from super_admin_recovery_credential where id=1 for update");
                    jdbc.queryForList("select id from app_user where id=? for share", ADMIN);
                    jdbc.queryForList("select id from organization where id=? for share", ORGANIZATION);
                    requireReady(inspect());
                    UUID audit = UUID.randomUUID();
                    if (jdbc.update(UPDATE_SQL, encodedCode, OWNER, INSTALLATION) != 1)
                        throw new IllegalStateException("REPAIR_UPDATE_REFUSED");
                    Map<String, Object> metadata = new LinkedHashMap<>();
                    metadata.put("proposalId", "REC-CI07-02"); metadata.put("caseRunId", RUN.toString());
                    metadata.put("proposalSha256", PROPOSAL_SHA); metadata.put("reason", "EMERGENCY_CODE_DELIVERY_NOT_ACCREDITED");
                    metadata.put("syntheticFixtureCodeRepairCount", 1); metadata.put("publicKeyFingerprintSha256", PUBLIC_SHA);
                    metadata.put("recoveryObservationSegmentId", segment.toString());
                    metadata.put("originalEmergencyCodeDeliveryVerified", false);
                    if (jdbc.update(AUDIT_SQL, audit, OWNER, AUDIT_ACTION, INSTALLATION, JSON.writeValueAsString(metadata)) != 1)
                        throw new IllegalStateException("REPAIR_AUDIT_REFUSED");
                    Timestamp timestamp = jdbc.queryForObject("select occurred_at from audit_event where id=?", Timestamp.class, audit);
                    return Map.<String, Object>of("syntheticFixtureCodeRepairCount", 1, "fixtureRepairAuditId", audit.toString(),
                            "fixtureRepairedAtUtc", Objects.requireNonNull(timestamp).toInstant().toString(),
                            "fixtureRepairProposalId", "REC-CI07-02", "fixtureRepairProposalSha256", PROPOSAL_SHA,
                            "fixtureRepairHiddenConfirmationBeforeWrite", true,
                            "fixtureRepairOriginalDeliveryVerified", false);
                } catch (Exception refused) {
                    status.setRollbackOnly();
                    if (refused instanceof IllegalStateException known) throw known;
                    throw new IllegalStateException("REPAIR_TRANSACTION_REFUSED_DETAILS_REDACTED");
                }
            }));
        }
    }
}
