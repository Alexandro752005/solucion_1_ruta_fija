package pe.rutafija.invitation.infrastructure;

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
public class InvitationJdbcRepository {

    private final JdbcTemplate jdbcTemplate;

    public InvitationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lockOrganization(UUID organizationId) {
        jdbcTemplate.queryForObject(
                "select id from organization where id = ? for update",
                UUID.class,
                organizationId
        );
    }

    public int revokePriorInvitations(UUID organizationId, String recipientEmail, Instant revokedAt) {
        return jdbcTemplate.update("""
                update organization_invitation
                   set revoked_at = ?
                 where organization_id = ?
                   and recipient_email = ?
                   and revoked_at is null
                   and used_at is null
                """, Timestamp.from(revokedAt), organizationId, recipientEmail);
    }

    public boolean insertInvitation(InvitationRow invitation) {
        List<UUID> inserted = jdbcTemplate.query("""
                insert into organization_invitation (
                    id, organization_id, issuer_user_id, recipient_email, code_hash,
                    issued_at, expires_at, revoked_at, used_at
                ) values (?, ?, ?, ?, ?, ?, ?, null, null)
                on conflict do nothing
                returning id
                """, (rs, row) -> rs.getObject("id", UUID.class),
                invitation.id(),
                invitation.organizationId(),
                invitation.issuerUserId(),
                invitation.recipientEmail(),
                invitation.codeHash(),
                Timestamp.from(invitation.issuedAt()),
                Timestamp.from(invitation.expiresAt())
        );
        return !inserted.isEmpty();
    }

    public List<InvitationRow> listTenantInvitations(UUID organizationId, int limit, int offset) {
        return jdbcTemplate.query("""
                select id, organization_id, issuer_user_id, recipient_email, code_hash,
                       issued_at, expires_at, revoked_at, used_at
                  from organization_invitation
                 where organization_id = ?
                 order by issued_at desc, id desc
                 limit ? offset ?
                """, InvitationJdbcRepository::mapInvitation,
                organizationId,
                limit,
                offset
        );
    }

    public long countTenantInvitations(UUID organizationId) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from organization_invitation where organization_id = ?",
                Long.class,
                organizationId
        );
        return count == null ? 0 : count;
    }

    public Optional<InvitationRow> findTenantInvitationForUpdate(UUID organizationId, UUID invitationId) {
        List<InvitationRow> rows = jdbcTemplate.query("""
                select id, organization_id, issuer_user_id, recipient_email, code_hash,
                       issued_at, expires_at, revoked_at, used_at
                  from organization_invitation
                 where organization_id = ? and id = ?
                 for update
                """, InvitationJdbcRepository::mapInvitation, organizationId, invitationId);
        return rows.stream().findFirst();
    }

    public boolean revokeInvitation(UUID organizationId, UUID invitationId, Instant revokedAt) {
        return jdbcTemplate.update("""
                update organization_invitation
                   set revoked_at = ?
                 where organization_id = ?
                   and id = ?
                   and revoked_at is null
                   and used_at is null
                """, Timestamp.from(revokedAt), organizationId, invitationId) == 1;
    }

    public Optional<InvitationMatch> findActiveInvitation(String recipientEmail, String codeHash, Instant now) {
        List<InvitationMatch> rows = jdbcTemplate.query("""
                select id, code_hash
                  from organization_invitation
                 where recipient_email = ?
                   and code_hash = ?
                   and revoked_at is null
                   and used_at is null
                   and expires_at > ?
                 limit 1
                """, (rs, row) -> new InvitationMatch(
                rs.getObject("id", UUID.class),
                rs.getString("code_hash").trim()
        ), recipientEmail, codeHash, Timestamp.from(now));
        return rows.stream().findFirst();
    }

    public Instant lockAccountGuard(UUID userId, Instant now) {
        jdbcTemplate.update("""
                insert into invitation_validation_account_guard (user_id, blocked_until, updated_at)
                values (?, null, ?)
                on conflict (user_id) do nothing
                """, userId, Timestamp.from(now));
        return jdbcTemplate.queryForObject("""
                select blocked_until
                  from invitation_validation_account_guard
                 where user_id = ?
                 for update
                """, (rs, row) -> instantOrNull(rs, "blocked_until"), userId);
    }

    public Instant lockOriginGuard(String originHash, Instant now) {
        jdbcTemplate.update("""
                insert into invitation_validation_origin_guard (origin_hash, paused_until, updated_at)
                values (?, null, ?)
                on conflict (origin_hash) do nothing
                """, originHash, Timestamp.from(now));
        return jdbcTemplate.queryForObject("""
                select paused_until
                  from invitation_validation_origin_guard
                 where origin_hash = ?
                 for update
                """, (rs, row) -> instantOrNull(rs, "paused_until"), originHash);
    }

    public void pruneFailuresAtOrBefore(Instant cutoff) {
        jdbcTemplate.update(
                "delete from invitation_validation_failure where failed_at <= ?",
                Timestamp.from(cutoff)
        );
    }

    public void recordFailure(UUID userId, String originHash, Instant failedAt) {
        jdbcTemplate.update("""
                insert into invitation_validation_failure (id, user_id, origin_hash, failed_at)
                values (?, ?, ?, ?)
                """, UUID.randomUUID(), userId, originHash, Timestamp.from(failedAt));
    }

    public long countFailuresForAccountSince(UUID userId, Instant cutoff) {
        return count("select count(*) from invitation_validation_failure where user_id = ? and failed_at > ?",
                userId, Timestamp.from(cutoff));
    }

    public long countFailuresForOriginSince(String originHash, Instant cutoff) {
        return count("select count(*) from invitation_validation_failure where origin_hash = ? and failed_at > ?",
                originHash, Timestamp.from(cutoff));
    }

    public void blockAccount(UUID userId, Instant blockedUntil, Instant updatedAt) {
        jdbcTemplate.update("""
                update invitation_validation_account_guard
                   set blocked_until = ?, updated_at = ?
                 where user_id = ?
                """, Timestamp.from(blockedUntil), Timestamp.from(updatedAt), userId);
    }

    public void pauseOrigin(String originHash, Instant pausedUntil, Instant updatedAt) {
        jdbcTemplate.update("""
                update invitation_validation_origin_guard
                   set paused_until = ?, updated_at = ?
                 where origin_hash = ?
                """, Timestamp.from(pausedUntil), Timestamp.from(updatedAt), originHash);
    }

    private long count(String sql, Object first, Object second) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class, first, second);
        return count == null ? 0 : count;
    }

    private static InvitationRow mapInvitation(ResultSet rs, int row) throws SQLException {
        return new InvitationRow(
                rs.getObject("id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("issuer_user_id", UUID.class),
                rs.getString("recipient_email"),
                rs.getString("code_hash").trim(),
                instant(rs, "issued_at"),
                instant(rs, "expires_at"),
                instantOrNull(rs, "revoked_at"),
                instantOrNull(rs, "used_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getTimestamp(column).toInstant();
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    public record InvitationRow(
            UUID id,
            UUID organizationId,
            UUID issuerUserId,
            String recipientEmail,
            String codeHash,
            Instant issuedAt,
            Instant expiresAt,
            Instant revokedAt,
            Instant usedAt
    ) {
    }

    public record InvitationMatch(UUID id, String codeHash) {
    }
}
