package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Exact synthetic manual case only. Inspection never starts Spring/Flyway/SMTP. */
final class ManualOtpCaseRecovery {
    private ManualOtpCaseRecovery() { }

    static void inspectReadOnly(UUID caseRunId, String recipient) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        NativePostgresTestDatabase.load().registerSpringProperties((key, value) -> properties.put(key, value.get()));
        try (Connection connection = DriverManager.getConnection(
                (String) properties.get("spring.datasource.url"),
                (String) properties.get("spring.datasource.username"),
                (String) properties.get("spring.datasource.password"))) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            Snapshot snapshot = inspect(jdbc, caseRunId, recipient);
            snapshot.printSanitized();
            connection.rollback();
            System.out.println("MANUAL_OTP_CASE_INSPECTION=READ_ONLY_NO_SPRING_NO_FLYWAY_NO_SMTP");
        }
    }

    static Snapshot inspect(JdbcTemplate jdbc, UUID caseRunId, String recipient) {
        if (!"ruta_fija_test".equals(jdbc.queryForObject("select current_database()", String.class))
                || !"rf_test".equals(jdbc.queryForObject("select current_user", String.class)))
            throw refused("DATABASE_TARGET_OR_ROLE_REJECTED");
        var roots = jdbc.query("""
                select o.id organization_id,a.id admin_id,o.status,a.active
                from organization o join app_user a on a.organization_id=o.id
                where o.legal_name=? and o.trade_name='SYNTHETIC OTP ONLY' and o.timezone='America/Lima'
                  and a.email=? and a.full_name='SYNTHETIC MANUAL ADMIN' and a.role='ADMIN'
                """, (rs, row) -> new Root(rs.getObject("organization_id", UUID.class),
                rs.getObject("admin_id", UUID.class), rs.getString("status"), rs.getBoolean("active")),
                "SYNTHETIC MANUAL OTP " + caseRunId, "manual-otp-admin." + caseRunId + "@rutafija.test");
        if (roots.size() != 1) throw refused("EXACT_SYNTHETIC_CASE_NOT_FOUND");
        Root root = roots.getFirst();
        int unexpected = count(jdbc, "select count(*) from app_user where organization_id=? and id<>?", root.organizationId, root.adminId)
                + count(jdbc, "select count(*) from driver where organization_id=?", root.organizationId)
                + count(jdbc, "select count(*) from transport_group where organization_id=?", root.organizationId)
                + count(jdbc, "select count(*) from vehicle where organization_id=?", root.organizationId)
                + count(jdbc, "select count(*) from organization_invitation where organization_id=? and (issuer_user_id<>? or recipient_email<>?)",
                        root.organizationId, root.adminId, recipient);
        var challenges = jdbc.query("""
                select c.identity_id,c.invitation_id,c.send_at_1,c.send_at_2,c.send_at_3,c.failed_attempts,
                       c.blocked_until,c.otp_hash is not null otp_present,
                       c.challenge_expires_at>clock_timestamp() capability_live,
                       c.consumed_at is not null consumed,i.email_verified_at is not null verified,
                       v.revoked_at is not null revoked,v.used_at is not null used,
                       i.full_name='SYNTHETIC MANUAL APPLICANT' and i.phone is null synthetic_identity
                from conductor_onboarding_email_challenge c
                join conductor_onboarding_identity i on i.id=c.identity_id
                join organization_invitation v on v.id=c.invitation_id
                where v.organization_id=? and v.issuer_user_id=? and v.recipient_email=? and i.email=?
                """, (rs, row) -> new Challenge(rs.getObject("identity_id", UUID.class), rs.getObject("invitation_id", UUID.class),
                instant(rs.getTimestamp("send_at_1")), instant(rs.getTimestamp("send_at_2")), instant(rs.getTimestamp("send_at_3")),
                rs.getInt("failed_attempts"), instant(rs.getTimestamp("blocked_until")), rs.getBoolean("otp_present"),
                rs.getBoolean("capability_live"), rs.getBoolean("consumed"), rs.getBoolean("verified"),
                rs.getBoolean("revoked"), rs.getBoolean("used"), rs.getBoolean("synthetic_identity")),
                root.organizationId, root.adminId, recipient, recipient);
        if (challenges.size() != 1) throw refused("EXACT_CASE_CHALLENGE_NOT_FOUND");
        Challenge challenge = challenges.getFirst();
        Instant now = jdbc.queryForObject("select clock_timestamp()", Timestamp.class).toInstant();
        return new Snapshot(caseRunId, root, challenge, now, unexpected,
                count(jdbc, "select count(*) from pilot_account_creation where role='ADMIN'"),
                count(jdbc, "select count(*) from pilot_account_creation where user_id=? and role='ADMIN'", root.adminId),
                count(jdbc, "select count(*) from conductor_onboarding_request where identity_id=? or organization_id=?", challenge.identityId, root.organizationId),
                count(jdbc, "select count(*) from app_user where lower(email)=?", recipient),
                count(jdbc, "select count(*) from conductor_onboarding_access_token where identity_id=? and revoked_at is null and expires_at>clock_timestamp()", challenge.identityId),
                count(jdbc, "select count(*) from organization_invitation where organization_id=? and revoked_at is null and used_at is null and expires_at>clock_timestamp()", root.organizationId),
                count(jdbc, "select count(*) from organization_invitation where recipient_email=? and organization_id<>? and revoked_at is null and used_at is null and expires_at>clock_timestamp()", recipient, root.organizationId));
    }

    static Snapshot prepareContinuation(JdbcTemplate jdbc, UUID caseRunId, String recipient,
                                        UUID newInvitationId, String newCodeHash, Instant now) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw refused("TRANSACTION_REQUIRED");
        Snapshot prior = inspect(jdbc, caseRunId, recipient);
        jdbc.queryForObject("select id from organization where id=? for update", UUID.class, prior.root.organizationId);
        jdbc.queryForObject("select id from app_user where id=? and organization_id=? for update", UUID.class,
                prior.root.adminId, prior.root.organizationId);
        jdbc.queryForObject("select id from conductor_onboarding_identity where id=? for update", UUID.class, prior.challenge.identityId);
        jdbc.queryForObject("select identity_id from conductor_onboarding_email_challenge where identity_id=? for update", UUID.class,
                prior.challenge.identityId);
        prior = inspect(jdbc, caseRunId, recipient);
        prior.requireContinuable();
        if (jdbc.update("update organization set status='ACTIVE',updated_at=current_timestamp where id=? and status='INACTIVE'",
                prior.root.organizationId) != 1) throw refused("CASE_CONCURRENT_CHANGE");
        jdbc.update("""
                insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at)
                values (?,?,?,?,?,?,?)
                """, newInvitationId, prior.root.organizationId, prior.root.adminId, recipient, newCodeHash,
                Timestamp.from(now), Timestamp.from(now.plus(Duration.ofHours(120))));
        // The former invitation stays revoked. Identity/challenge/counters/ledger are untouched.
        return prior;
    }

    static int count(JdbcTemplate jdbc, String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Refused refused(String code) { return new Refused(code); }
    static final class Refused extends RuntimeException {
        final String code;
        private Refused(String code) { super(null, null, false, false); this.code = code; }
    }
    record Root(UUID organizationId, UUID adminId, String status, boolean active) { }
    record Challenge(UUID identityId, UUID invitationId, Instant send1, Instant send2, Instant send3,
                     int failures, Instant blockedUntil, boolean otpPresent, boolean capabilityLive,
                     boolean consumed, boolean verified, boolean revoked, boolean used, boolean syntheticIdentity) {
        List<Instant> sends() { return java.util.stream.Stream.of(send1, send2, send3).filter(java.util.Objects::nonNull).toList(); }
    }
    record Snapshot(UUID caseRunId, Root root, Challenge challenge, Instant observedAt,
                    int unexpectedScopedRows, int adminCreations, int ownedLedgerRows, int requests,
                    int operationalUsers, int activeAccessTokens, int activeInvitations, int foreignActiveInvitations) {
        int recentSends() { return (int) challenge.sends().stream().filter(t -> t.isAfter(observedAt.minus(Duration.ofMinutes(60)))).count(); }
        void requireContinuable() {
            if (!"INACTIVE".equals(root.status) || root.active || unexpectedScopedRows != 0 || ownedLedgerRows != 1
                    || !challenge.syntheticIdentity || challenge.verified || challenge.consumed || challenge.used
                    || !challenge.revoked || challenge.otpPresent || challenge.capabilityLive
                    || requests != 0 || operationalUsers != 0 || activeAccessTokens != 0
                    || activeInvitations != 0 || foreignActiveInvitations != 0 || adminCreations > 4)
                throw refused("EXACT_CASE_NOT_SAFELY_CONTINUABLE");
            if (challenge.failures != 0 || challenge.blockedUntil != null)
                throw refused("CASE_OTP_FAILURE_OR_BLOCK_REQUIRES_REVIEW");
            if (recentSends() >= 3) throw refused("SEND_WINDOW_EXHAUSTED_WAIT_NO_RESET");
        }
        void printSanitized() {
            System.out.println("MANUAL_OTP_CASE_RUN_ID=" + caseRunId);
            System.out.println("MANUAL_OTP_CASE_OBSERVED_AT_UTC=" + observedAt);
            System.out.println("MANUAL_OTP_CASE_ORGANIZATION=" + root.status);
            System.out.println("MANUAL_OTP_CASE_ADMIN_ACTIVE=" + root.active);
            System.out.println("MANUAL_OTP_CASE_OWNED_LEDGER_ROWS=" + ownedLedgerRows);
            System.out.println("MANUAL_OTP_ADMIN_HISTORICAL_CREATIONS=" + adminCreations);
            System.out.println("MANUAL_OTP_ADMIN_HISTORICAL_SLOTS_REMAINING=" + Math.max(0, 4 - adminCreations));
            System.out.println("MANUAL_OTP_CASE_SEND_AT_1_UTC=" + challenge.send1);
            System.out.println("MANUAL_OTP_CASE_SEND_AT_2_UTC=" + challenge.send2);
            System.out.println("MANUAL_OTP_CASE_SEND_AT_3_UTC=" + challenge.send3);
            System.out.println("MANUAL_OTP_CASE_SENDS_IN_LAST_60_MINUTES=" + recentSends());
            System.out.println("MANUAL_OTP_CASE_SEND_SLOTS_AVAILABLE=" + Math.max(0, 3 - recentSends()));
            System.out.println("MANUAL_OTP_CASE_FAILED_HTTP_VERIFICATIONS=" + challenge.failures);
            System.out.println("MANUAL_OTP_CASE_OTP_PRESENT=" + challenge.otpPresent);
            System.out.println("MANUAL_OTP_CASE_CAPABILITY_LIVE=" + challenge.capabilityLive);
            System.out.println("MANUAL_OTP_CASE_EMAIL_VERIFIED=" + challenge.verified);
            System.out.println("MANUAL_OTP_CASE_CHALLENGE_CONSUMED=" + challenge.consumed);
            System.out.println("MANUAL_OTP_CASE_CURRENT_INVITATION_REVOKED=" + challenge.revoked);
            System.out.println("MANUAL_OTP_CASE_CURRENT_INVITATION_USED=" + challenge.used);
            System.out.println("MANUAL_OTP_CASE_OPERATIONAL_USERS=" + operationalUsers);
            System.out.println("MANUAL_OTP_CASE_REQUESTS=" + requests);
            System.out.println("MANUAL_OTP_CASE_ACTIVE_ACCESS_TOKENS=" + activeAccessTokens);
            System.out.println("MANUAL_OTP_CASE_UNEXPECTED_SCOPED_ROWS=" + unexpectedScopedRows);
            try { requireContinuable(); System.out.println("MANUAL_OTP_CASE_CONTINUATION_PREFLIGHT=PASS_SAME_IDENTITY_NO_NEW_ADMIN"); }
            catch (Refused refusal) { System.out.println("MANUAL_OTP_CASE_CONTINUATION_PREFLIGHT=REFUSED_" + refusal.code); }
        }
    }
}
