package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Read-only correspondence check. No Spring bootstrap, migrations, email or cleanup capability. */
public final class ManualOtpEvidenceVerification {
    private static final String RECIPIENT = "sandrito10xyz@gmail.com";
    private static final long CLEANUP_LOCK = 7_518_440_514L;

    private ManualOtpEvidenceVerification() { }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("MANUAL_OTP_EVIDENCE_RESULT=REFUSED_EXPECT_CASE_RUN_OBSERVED_TIME");
            System.exit(2);
        }
        try {
            verifyReadOnly(UUID.fromString(args[0]), UUID.fromString(args[1]), Instant.parse(args[2]));
        } catch (VerificationFailure failure) {
            System.err.println("MANUAL_OTP_EVIDENCE_RESULT=REFUSED_" + failure.code);
            System.exit(1);
        } catch (ManualOtpCaseRecovery.Refused failure) {
            System.err.println("MANUAL_OTP_EVIDENCE_RESULT=REFUSED_" + failure.code);
            System.exit(1);
        } catch (Exception failure) {
            System.err.println("MANUAL_OTP_EVIDENCE_RESULT=FAIL_DETAILS_REDACTED");
            System.exit(1);
        }
    }

    private static void verifyReadOnly(UUID caseRunId, UUID runId, Instant observedConsumption) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        NativePostgresTestDatabase.load().registerSpringProperties((key, value) -> properties.put(key, value.get()));
        try (Connection connection = DriverManager.getConnection((String) properties.get("spring.datasource.url"),
                (String) properties.get("spring.datasource.username"), (String) properties.get("spring.datasource.password"))) {
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setAutoCommit(false);
            JdbcTemplate jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
            var state = ManualOtpCaseRecovery.inspect(jdbc, caseRunId, RECIPIENT);
            var rows = jdbc.query("""
                    select r.id request_id,r.idempotency_key request_key,c.idempotency_key challenge_key,
                           r.status='PENDING_REVIEW' and r.linked_user_id is null and r.linked_driver_id is null
                             and r.provisioned_at is null and r.personal_approved_at is null pending_unlinked,
                           c.otp_hash is null and c.otp_expires_at is null otp_cleared,
                           c.otp_issued_at,c.consumed_at,i.email_verified_at,v.used_at,r.submitted_at
                    from conductor_onboarding_email_challenge c
                    join conductor_onboarding_identity i on i.id=c.identity_id
                    join conductor_onboarding_request r on r.id=c.request_id and r.identity_id=i.id
                      and r.invitation_id=c.invitation_id
                    join organization_invitation v on v.id=r.invitation_id and v.organization_id=r.organization_id
                    where c.identity_id=? and c.invitation_id=? and r.organization_id=?
                      and v.issuer_user_id=? and v.recipient_email=? and i.email=?
                    """, (rs, row) -> new StoredConsumption(rs.getObject("request_id", UUID.class), rs.getString("request_key"),
                    rs.getString("challenge_key"), rs.getBoolean("pending_unlinked"), rs.getBoolean("otp_cleared"),
                    instant(rs.getTimestamp("otp_issued_at")), instant(rs.getTimestamp("consumed_at")),
                    instant(rs.getTimestamp("email_verified_at")), instant(rs.getTimestamp("used_at")), instant(rs.getTimestamp("submitted_at"))),
                    state.challenge().identityId(), state.challenge().invitationId(), state.root().organizationId(), state.root().adminId(),
                    RECIPIENT, RECIPIENT);
            if (rows.size() != 1) throw refused("CURRENT_CONSUMPTION_NOT_UNIQUE");
            int retiredPreviousInvitations = jdbc.queryForObject("""
                    select count(*) from organization_invitation where organization_id=? and issuer_user_id=?
                      and recipient_email=? and id<>? and revoked_at is not null and used_at is null
                    """, Integer.class, state.root().organizationId(), state.root().adminId(), RECIPIENT, state.challenge().invitationId());
            StoredConsumption stored = rows.getFirst();
            validate(state, stored, runId, observedConsumption, retiredPreviousInvitations);
            boolean cleanupLockFree = Boolean.TRUE.equals(jdbc.queryForObject("""
                    select not exists(select 1 from pg_locks
                      where locktype='advisory' and granted and objsubid=1
                        and database=(select oid from pg_database where datname=current_database())
                        and classid::bigint=(?::bigint >> 32) and objid::bigint=(?::bigint & 4294967295))
                    """, Boolean.class, CLEANUP_LOCK, CLEANUP_LOCK));
            System.out.println("MANUAL_OTP_CASE_RUN_ID=" + caseRunId);
            System.out.println("MANUAL_OTP_RUN_ID=" + runId);
            System.out.println("MANUAL_OTP_EVIDENCE_OBSERVED_AT_UTC=" + state.observedAt());
            System.out.println("MANUAL_OTP_REPORTED_CONSUMPTION_OBSERVATION_UTC=" + observedConsumption);
            System.out.println("MANUAL_OTP_STORED_ISSUED_AT_UTC=" + stored.issuedAt);
            System.out.println("MANUAL_OTP_STORED_CONSUMED_AT_UTC=" + stored.consumedAt);
            System.out.println("MANUAL_OTP_REQUEST_ID=" + stored.requestId);
            System.out.println("MANUAL_OTP_OPERATION_KEYS_MATCH=true");
            System.out.println("MANUAL_OTP_PERSISTED_STATE=PENDING_REVIEW_NO_OPERATIONAL_ACCOUNT_OR_DRIVER");
            System.out.println("MANUAL_OTP_CHALLENGE_CONSUMED_AND_OTP_CLEARED=true");
            System.out.println("MANUAL_OTP_EXACT_FIXTURE_CLEANUP=PASS_DISABLED_CAPABILITY_EXPIRED_ACCESS_REVOKED");
            System.out.println("MANUAL_OTP_CASE_OWNED_LEDGER_ROWS=" + state.ownedLedgerRows());
            System.out.println("MANUAL_OTP_ADMIN_HISTORICAL_CREATIONS=" + state.adminCreations());
            System.out.println("MANUAL_OTP_CASE_SEND_HISTORY_ENTRIES=" + state.challenge().sends().size());
            System.out.println("MANUAL_OTP_CASE_SEND_AT_1_UTC=" + state.challenge().send1());
            System.out.println("MANUAL_OTP_CASE_SEND_AT_2_UTC=" + state.challenge().send2());
            System.out.println("MANUAL_OTP_CASE_SEND_AT_3_UTC=" + state.challenge().send3());
            System.out.println("MANUAL_OTP_PREVIOUS_REVOKED_INVITATIONS_PRESERVED=" + retiredPreviousInvitations);
            System.out.println("MANUAL_OTP_CLEANUP_ADVISORY_SCOPE_FREE=" + cleanupLockFree);
            System.out.println("MANUAL_OTP_EVIDENCE_DATABASE=ruta_fija_test; ROLE=rf_test; READ_ONLY=true");
            System.out.println("MANUAL_OTP_EVIDENCE_SMTP_ATTEMPTS=0; CLEANUP_EXECUTED=false; SPRING_STARTED=false; FLYWAY_STARTED=false");
            connection.rollback();
            System.out.println("MANUAL_OTP_EVIDENCE_RESULT=PASS_LOCAL_CORRESPONDENCE_AND_CLEANUP");
        }
    }

    static void validate(ManualOtpCaseRecovery.Snapshot state, StoredConsumption stored, UUID runId,
                         Instant observedConsumption, int retiredPreviousInvitations) {
        String expectedKey = "manual-otp-" + runId;
        if (!expectedKey.equals(stored.requestKey) || !expectedKey.equals(stored.challengeKey))
            throw refused("ATTEMPT_IDENTIFIERS_DO_NOT_MATCH");
        if (!stored.pendingUnlinked || !stored.otpCleared || !state.challenge().consumed() || !state.challenge().verified()
                || !state.challenge().used() || state.challenge().revoked() || state.requests()!=1
                || state.operationalUsers()!=0 || state.unexpectedScopedRows()!=0 || retiredPreviousInvitations<1)
            throw refused("PERSISTED_CONSUMPTION_DOES_NOT_MATCH_REPORT");
        if (stored.issuedAt==null || stored.consumedAt==null || stored.consumedAt.isBefore(stored.issuedAt)
                || !stored.consumedAt.isBefore(stored.issuedAt.plus(Duration.ofMinutes(5)))
                || !stored.consumedAt.equals(stored.verifiedAt) || !stored.consumedAt.equals(stored.usedAt)
                || !stored.consumedAt.equals(stored.submittedAt) || observedConsumption.isBefore(stored.consumedAt))
            throw refused("CONSUMPTION_TIME_CORRESPONDENCE_FAILED");
        if (!"INACTIVE".equals(state.root().status()) || state.root().active() || state.challenge().otpPresent()
                || state.challenge().capabilityLive() || state.activeAccessTokens()!=0 || state.activeInvitations()!=0
                || state.ownedLedgerRows()!=1)
            throw refused("EXACT_CLEANUP_OR_LEDGER_NOT_CONFIRMED");
    }

    private static Instant instant(Timestamp value) { return value==null ? null : value.toInstant(); }
    private static VerificationFailure refused(String code) { return new VerificationFailure(code); }
    static final class VerificationFailure extends RuntimeException {
        final String code;
        private VerificationFailure(String code) { super(null, null, false, false); this.code=code; }
    }
    record StoredConsumption(UUID requestId, String requestKey, String challengeKey, boolean pendingUnlinked,
                             boolean otpCleared, Instant issuedAt, Instant consumedAt, Instant verifiedAt,
                             Instant usedAt, Instant submittedAt) { }
}
