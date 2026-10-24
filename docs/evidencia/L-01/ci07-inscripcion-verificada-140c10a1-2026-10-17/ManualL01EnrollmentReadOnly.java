package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.UUID;

/** One-off SELECT-only inspection; no enrollment, HTTP, SMTP, or fixture changes. */
public final class ManualL01EnrollmentReadOnly {
    public static void main(String[] args) {
        try {
            UUID run = UUID.fromString(args[0]);
            if (!run.toString().equals("140c10a1-be0f-4d08-a6d4-c796e2ed107a"))
                throw new IllegalStateException("EXACT_CASE_REQUIRED");
            var state = ManualL01HumanTrial.JSON.readTree(Files.readAllBytes(ManualL01HumanTrialServer.STATE));
            if (!run.toString().equals(state.path("runId").asText())
                    || !run.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip())
                    || !"ruta_fija_test".equals(state.path("database").asText()))
                throw new IllegalStateException("STATE_OR_MARKER_MISMATCH");
            UUID owner = UUID.fromString(state.path("ownerId").asText());
            UUID admin = UUID.fromString(state.path("adminId").asText());
            UUID org = UUID.fromString(state.path("organizationId").asText());
            if (!owner.toString().equals("088518c1-821c-4316-9f87-090f51ac44e0"))
                throw new IllegalStateException("OWNER_MISMATCH");
            try (Connection connection = ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())) {
                connection.setReadOnly(true);
                connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                var jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
                ManualL01HumanTrial.assertDatabase(jdbc);
                String prefix = run.toString().substring(0, 8);
                if (ManualL01HumanTrial.count(jdbc, """
                        select count(*) from app_user where id=? and role='SUPER_ADMIN'
                        and organization_id is null and email=?
                        and full_name='SYNTHETIC HUMAN TRIAL OWNER' and active
                        and email_verified_at is not null
                        """, owner, "sa.human." + prefix + "@rutafija.test") != 1
                        || ManualL01HumanTrial.count(jdbc, """
                        select count(*) from app_user u join admin_activation a on a.admin_user_id=u.id
                        where u.id=? and u.organization_id=? and u.role='ADMIN'
                        and a.created_by_super_admin_id=? and u.full_name='SYNTHETIC HUMAN ADMIN'
                        """, admin, org, owner) != 1)
                    throw new IllegalStateException("EXACT_SYNTHETIC_IDENTITIES_REQUIRED");
                var credentials = jdbc.queryForList("""
                        select id, installation_id::text, user_id::text, public_key_x509, enrolled_at
                        from super_admin_recovery_credential where id=1 and user_id=?
                        """, owner);
                if (credentials.size() != 1 || state.path("enrollmentResponseCount").asInt() != 1)
                    throw new IllegalStateException("SINGLE_ENROLLMENT_REQUIRED");
                var credential = credentials.getFirst();
                byte[] publicKeyBytes = Base64.getDecoder().decode(credential.get("public_key_x509").toString());
                String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(publicKeyBytes));
                if (!fingerprint.equalsIgnoreCase(state.path("publicKeyFingerprintSha256").asText()))
                    throw new IllegalStateException("PUBLIC_FINGERPRINT_MISMATCH");
                var enrollmentAudit = jdbc.queryForList("""
                        select id::text, user_id::text, action, occurred_at
                        from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_ENROLLED'
                        order by occurred_at, id
                        """, owner);
                if (enrollmentAudit.size() != 1)
                    throw new IllegalStateException("SINGLE_ENROLLMENT_AUDIT_REQUIRED");
                var safeCredential = new LinkedHashMap<String, Object>();
                for (String field : new String[]{"id", "installation_id", "user_id", "enrolled_at"})
                    safeCredential.put(field, credential.get(field));
                safeCredential.put("publicKeyFingerprintSha256", fingerprint);
                safeCredential.put("matchesObservedHttpEnrollmentFingerprint", true);
                var result = new LinkedHashMap<String, Object>();
                result.put("inspectionAtUtc", Instant.now().toString());
                result.put("runId", run.toString());
                result.put("environment", ManualL01HumanTrial.snapshot(jdbc));
                result.put("credential", safeCredential);
                result.put("enrollmentAudit", enrollmentAudit);
                result.put("enrollmentResponseCount", state.path("enrollmentResponseCount").asInt());
                result.put("ownerSessionVersion", jdbc.queryForObject("select session_version from app_user where id=?", Long.class, owner));
                result.put("sessionVersionBeforeRecovery", state.path("sessionVersionBeforeRecovery").asLong());
                result.put("recoveryChallengeCountForCase", ManualL01HumanTrial.count(jdbc,
                        "select count(*) from super_admin_recovery_challenge where user_id=?", owner));
                result.put("recoveryCompletedAuditCount", ManualL01HumanTrial.count(jdbc,
                        "select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_COMPLETED'", owner));
                result.put("termsAcceptanceCountForCase", ManualL01HumanTrial.count(jdbc,
                        "select count(*) from admin_terms_acceptance where admin_user_id=? and organization_id=?", admin, org));
                result.put("existingManualAdminResult", state.path("manualSyntheticAdminResult").asText());
                result.put("existingManualRecoveryResult", state.path("manualTemporaryRecoveryResult").asText());
                result.put("preAcceptanceGateRecorded", state.has("preAcceptanceGate"));
                result.put("cleanupMarkerMatches", true);
                result.put("readOnly", true);
                result.put("databaseWrites", 0);
                result.put("httpRequests", 0);
                result.put("smtpAttempts", 0);
                result.put("additionalAccountsCreated", 0);
                result.put("privateKeyFilesRead", 0);
                result.put("emergencyCodeHashesSelected", false);
                connection.rollback();
                System.out.println(ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            }
        } catch (Exception failure) {
            System.err.println("ENROLLMENT_INSPECTION=REFUSED_DETAILS_REDACTED");
            System.exit(1);
        }
    }
}
