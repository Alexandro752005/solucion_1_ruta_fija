package pe.rutafija.onboarding.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConductorOnboardingRepository {

    private final JdbcTemplate jdbc;

    public ConductorOnboardingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<InvitationRow> findActiveInvitation(String email, String codeHash, Instant now) {
        return jdbc.query("""
                select id, organization_id, recipient_email, code_hash, expires_at, revoked_at, used_at
                  from organization_invitation
                 where recipient_email = ?
                   and code_hash = ?
                   and revoked_at is null
                   and used_at is null
                   and expires_at > ?
                   and exists (select 1 from organization where id = organization_id and status = 'ACTIVE')
                 limit 1
                """, ConductorOnboardingRepository::mapInvitation,
                email, codeHash, Timestamp.from(now)).stream().findFirst();
    }

    public Optional<InvitationRow> lockInvitation(UUID invitationId, String email) {
        // Issuance/revocation also lock organization before invitation. Keep that
        // order explicit instead of relying on a join's row-lock order.
        var activeOrganizations = jdbc.query("""
                select org.id from organization org
                 where org.id = (select organization_id from organization_invitation
                                  where id = ? and recipient_email = ?)
                   and org.status = 'ACTIVE'
                 for share
                """, (rs, row) -> rs.getObject("id", UUID.class), invitationId, email);
        if (activeOrganizations.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.query("""
                select invitation.id, invitation.organization_id, invitation.recipient_email,
                       invitation.code_hash, invitation.expires_at, invitation.revoked_at, invitation.used_at
                  from organization_invitation invitation
                  join organization org on org.id = invitation.organization_id
                 where invitation.id = ? and invitation.recipient_email = ? and org.status = 'ACTIVE'
                 for update of invitation
                """, ConductorOnboardingRepository::mapInvitation, invitationId, email).stream().findFirst();
    }

    public boolean markInvitationUsed(UUID invitationId, Instant now) {
        return jdbc.update("""
                update organization_invitation
                   set used_at = ?
                 where id = ?
                   and revoked_at is null
                   and used_at is null
                   and expires_at > ?
                """, Timestamp.from(now), invitationId, Timestamp.from(now)) == 1;
    }

    public Optional<IdentityRow> findIdentityForUpdate(String email) {
        return jdbc.query("""
                select id, email, password_hash, full_name, phone, email_verified_at, created_at, updated_at
                  from conductor_onboarding_identity
                 where email = ?
                 for update
                """, ConductorOnboardingRepository::mapIdentity, email).stream().findFirst();
    }

    public Optional<IdentityRow> findIdentityByIdForUpdate(UUID identityId) {
        return jdbc.query("""
                select id, email, password_hash, full_name, phone, email_verified_at, created_at, updated_at
                  from conductor_onboarding_identity
                 where id = ?
                 for update
                """, ConductorOnboardingRepository::mapIdentity, identityId).stream().findFirst();
    }

    public void markIdentityVerified(UUID identityId, Instant verifiedAt) {
        jdbc.update("""
                update conductor_onboarding_identity
                   set email_verified_at = coalesce(email_verified_at, ?), updated_at = ?
                 where id = ?
                """, Timestamp.from(verifiedAt), Timestamp.from(verifiedAt), identityId);
    }

    public IdentityRow upsertIdentity(IdentityRow identity) {
        return jdbc.queryForObject("""
                insert into conductor_onboarding_identity (
                    id, email, password_hash, full_name, phone, email_verified_at, created_at, updated_at
                ) values (?, ?, ?, ?, ?, null, ?, ?)
                on conflict (email) do update set
                    password_hash = excluded.password_hash,
                    full_name = excluded.full_name,
                    phone = excluded.phone,
                    updated_at = excluded.updated_at
                returning id, email, password_hash, full_name, phone, email_verified_at, created_at, updated_at
                """, ConductorOnboardingRepository::mapIdentity,
                identity.id(), identity.email(), identity.passwordHash(), identity.fullName(), identity.phone(),
                Timestamp.from(identity.createdAt()), Timestamp.from(identity.updatedAt()));
    }

    public Optional<ChallengeRow> findChallengeByIdentityForUpdate(UUID identityId) {
        return jdbc.query("""
                select id, identity_id, invitation_id, idempotency_key, verification_token_hash, otp_hash,
                       otp_issued_at, otp_expires_at, challenge_expires_at, failed_attempts, blocked_until,
                       send_at_1, send_at_2, send_at_3, consumed_at, request_id, created_at, updated_at
                  from conductor_onboarding_email_challenge
                 where identity_id = ?
                 for update
                """, ConductorOnboardingRepository::mapChallenge, identityId).stream().findFirst();
    }

    public Optional<ChallengeRow> findChallengeByTokenForUpdate(String tokenHash) {
        return jdbc.query("""
                select id, identity_id, invitation_id, idempotency_key, verification_token_hash, otp_hash,
                       otp_issued_at, otp_expires_at, challenge_expires_at, failed_attempts, blocked_until,
                       send_at_1, send_at_2, send_at_3, consumed_at, request_id, created_at, updated_at
                  from conductor_onboarding_email_challenge
                 where verification_token_hash = ?
                 for update
                """, ConductorOnboardingRepository::mapChallenge, tokenHash).stream().findFirst();
    }

    public Optional<UUID> findIdentityIdByVerificationToken(String tokenHash) {
        return jdbc.query("""
                select identity_id from conductor_onboarding_email_challenge
                 where verification_token_hash = ?
                """, (rs, row) -> rs.getObject("identity_id", UUID.class), tokenHash).stream().findFirst();
    }

    public void saveChallenge(ChallengeRow row) {
        jdbc.update("""
                insert into conductor_onboarding_email_challenge (
                    id, identity_id, invitation_id, idempotency_key, verification_token_hash, otp_hash,
                    otp_issued_at, otp_expires_at, challenge_expires_at, failed_attempts, blocked_until,
                    send_at_1, send_at_2, send_at_3, consumed_at, request_id, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (identity_id) do update set
                    invitation_id = excluded.invitation_id,
                    idempotency_key = excluded.idempotency_key,
                    verification_token_hash = excluded.verification_token_hash,
                    otp_hash = excluded.otp_hash,
                    otp_issued_at = excluded.otp_issued_at,
                    otp_expires_at = excluded.otp_expires_at,
                    challenge_expires_at = excluded.challenge_expires_at,
                    failed_attempts = excluded.failed_attempts,
                    blocked_until = excluded.blocked_until,
                    send_at_1 = excluded.send_at_1,
                    send_at_2 = excluded.send_at_2,
                    send_at_3 = excluded.send_at_3,
                    consumed_at = excluded.consumed_at,
                    request_id = excluded.request_id,
                    updated_at = excluded.updated_at
                """,
                row.id(), row.identityId(), row.invitationId(), row.idempotencyKey(), row.verificationTokenHash(),
                row.otpHash(), ts(row.otpIssuedAt()), ts(row.otpExpiresAt()), ts(row.challengeExpiresAt()),
                row.failedAttempts(), ts(row.blockedUntil()), ts(row.sendAt1()), ts(row.sendAt2()), ts(row.sendAt3()),
                ts(row.consumedAt()), row.requestId(), Timestamp.from(row.createdAt()), Timestamp.from(row.updatedAt()));
    }

    public Optional<OnboardingRequestRow> findRequestByIdempotency(UUID identityId, String idempotencyKey) {
        return jdbc.query("""
                select id, identity_id, organization_id, invitation_id, idempotency_key, status, submitted_at
                  from conductor_onboarding_request
                 where identity_id = ? and idempotency_key = ?
                """, ConductorOnboardingRepository::mapRequest, identityId, idempotencyKey).stream().findFirst();
    }

    public Optional<OnboardingRequestRow> findRequest(UUID requestId) {
        return jdbc.query("""
                select id, identity_id, organization_id, invitation_id, idempotency_key, status, submitted_at
                  from conductor_onboarding_request
                 where id = ?
                """, ConductorOnboardingRepository::mapRequest, requestId).stream().findFirst();
    }

    public List<RequestSummaryRow> listRequestsForOrganization(UUID organizationId) {
        return jdbc.query("""
                select id, status, review_rounds, submitted_at
                  from conductor_onboarding_request
                 where organization_id = ?
                 order by submitted_at desc, id desc
                 limit 200
                """, (rs, row) -> new RequestSummaryRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getShort("review_rounds"),
                instant(rs, "submitted_at")
        ), organizationId);
    }

    public Optional<ExpedienteRow> findExpediente(UUID requestId, UUID organizationId) {
        return jdbc.query("""
                select request.id, request.status, identity.full_name, identity.email, identity.phone
                  from conductor_onboarding_request request
                  join conductor_onboarding_identity identity on identity.id = request.identity_id
                 where request.id = ?
                   and request.organization_id = ?
                """, (rs, row) -> new ExpedienteRow(
                rs.getObject("id", UUID.class),
                rs.getString("status"),
                rs.getString("full_name"),
                rs.getString("email"),
                rs.getString("phone")
        ), requestId, organizationId).stream().findFirst();
    }

    public Optional<OnboardingRequestRow> findPendingRequest(UUID identityId, UUID organizationId) {
        return jdbc.query("""
                select id, identity_id, organization_id, invitation_id, idempotency_key, status, submitted_at
                  from conductor_onboarding_request
                 where identity_id = ? and organization_id = ?
                   and status in ('PENDING_REVIEW', 'RETURNED')
                 order by submitted_at desc
                 limit 1
                """, ConductorOnboardingRepository::mapRequest, identityId, organizationId).stream().findFirst();
    }

    public OnboardingRequestRow createRequest(OnboardingRequestRow request) {
        return jdbc.queryForObject("""
                insert into conductor_onboarding_request (
                    id, identity_id, organization_id, invitation_id, idempotency_key,
                    status, review_rounds, submitted_at
                ) values (?, ?, ?, ?, ?, 'PENDING_REVIEW', 0, ?)
                returning id, identity_id, organization_id, invitation_id, idempotency_key, status, submitted_at
                """, ConductorOnboardingRepository::mapRequest,
                request.id(), request.identityId(), request.organizationId(), request.invitationId(),
                request.idempotencyKey(), Timestamp.from(request.submittedAt()));
    }

    public Instant lockGuard(String type, String hash, Instant now) {
        jdbc.update("""
                insert into conductor_onboarding_invitation_guard (guard_type, guard_hash, blocked_until, updated_at)
                values (?, ?, null, ?)
                on conflict (guard_type, guard_hash) do nothing
                """, type, hash, Timestamp.from(now));
        return jdbc.queryForObject("""
                select blocked_until from conductor_onboarding_invitation_guard
                 where guard_type = ? and guard_hash = ?
                 for update
                """, (rs, row) -> instantOrNull(rs, "blocked_until"), type, hash);
    }

    public void pruneFailuresAtOrBefore(Instant cutoff) {
        jdbc.update("delete from conductor_onboarding_invitation_failure where failed_at <= ?",
                Timestamp.from(cutoff));
    }

    public void recordFailure(String emailHash, String originHash, Instant now) {
        jdbc.update("""
                insert into conductor_onboarding_invitation_failure (id, identity_email_hash, origin_hash, failed_at)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), emailHash, originHash, Timestamp.from(now));
    }

    public long countEmailFailures(String emailHash, Instant cutoff) {
        return count("select count(*) from conductor_onboarding_invitation_failure where identity_email_hash = ? and failed_at > ?",
                emailHash, cutoff);
    }

    public long countOriginFailures(String originHash, Instant cutoff) {
        return count("select count(*) from conductor_onboarding_invitation_failure where origin_hash = ? and failed_at > ?",
                originHash, cutoff);
    }

    public void blockGuard(String type, String hash, Instant blockedUntil, Instant now) {
        jdbc.update("""
                update conductor_onboarding_invitation_guard
                   set blocked_until = ?, updated_at = ?
                 where guard_type = ? and guard_hash = ?
                """, Timestamp.from(blockedUntil), Timestamp.from(now), type, hash);
    }

    private long count(String sql, String hash, Instant cutoff) {
        Long result = jdbc.queryForObject(sql, Long.class, hash, Timestamp.from(cutoff));
        return result == null ? 0 : result;
    }

    private static InvitationRow mapInvitation(ResultSet rs, int row) throws SQLException {
        return new InvitationRow(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("recipient_email"), rs.getString("code_hash").trim(),
                instant(rs, "expires_at"), instantOrNull(rs, "revoked_at"), instantOrNull(rs, "used_at"));
    }

    private static IdentityRow mapIdentity(ResultSet rs, int row) throws SQLException {
        return new IdentityRow(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("password_hash"),
                rs.getString("full_name"), rs.getString("phone"), instantOrNull(rs, "email_verified_at"),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static ChallengeRow mapChallenge(ResultSet rs, int row) throws SQLException {
        return new ChallengeRow(rs.getObject("id", UUID.class), rs.getObject("identity_id", UUID.class),
                rs.getObject("invitation_id", UUID.class), rs.getString("idempotency_key"),
                rs.getString("verification_token_hash").trim(), rs.getString("otp_hash") == null
                ? null : rs.getString("otp_hash").trim(),
                instantOrNull(rs, "otp_issued_at"), instantOrNull(rs, "otp_expires_at"),
                instant(rs, "challenge_expires_at"), rs.getShort("failed_attempts"),
                instantOrNull(rs, "blocked_until"), instantOrNull(rs, "send_at_1"),
                instantOrNull(rs, "send_at_2"), instantOrNull(rs, "send_at_3"),
                instantOrNull(rs, "consumed_at"),
                rs.getObject("request_id") == null ? null : rs.getObject("request_id", UUID.class),
                instant(rs, "created_at"), instant(rs, "updated_at"));
    }

    private static OnboardingRequestRow mapRequest(ResultSet rs, int row) throws SQLException {
        return new OnboardingRequestRow(rs.getObject("id", UUID.class), rs.getObject("identity_id", UUID.class),
                rs.getObject("organization_id", UUID.class), rs.getObject("invitation_id", UUID.class),
                rs.getString("idempotency_key"), rs.getString("status"), instant(rs, "submitted_at"));
    }

    private static Timestamp ts(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record InvitationRow(UUID id, UUID organizationId, String recipientEmail, String codeHash,
                                Instant expiresAt, Instant revokedAt, Instant usedAt) {
    }

    public record IdentityRow(UUID id, String email, String passwordHash, String fullName, String phone,
                              Instant emailVerifiedAt, Instant createdAt, Instant updatedAt) {
    }

    public record ChallengeRow(UUID id, UUID identityId, UUID invitationId, String idempotencyKey,
                               String verificationTokenHash, String otpHash, Instant otpIssuedAt,
                               Instant otpExpiresAt, Instant challengeExpiresAt, short failedAttempts,
                               Instant blockedUntil, Instant sendAt1, Instant sendAt2, Instant sendAt3,
                               Instant consumedAt, UUID requestId, Instant createdAt, Instant updatedAt) {
    }

    public record OnboardingRequestRow(UUID id, UUID identityId, UUID organizationId, UUID invitationId,
                                       String idempotencyKey, String status, Instant submittedAt) {
    }

    public record RequestSummaryRow(UUID id, String status, short reviewRounds, Instant submittedAt) {
    }

    public record ExpedienteRow(UUID id, String status, String fullName, String email, String phone) {
    }
}
