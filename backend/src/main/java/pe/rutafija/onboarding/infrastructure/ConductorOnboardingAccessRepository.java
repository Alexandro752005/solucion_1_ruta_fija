package pe.rutafija.onboarding.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stores applicant credentials independently from operational users and sessions. */
@Repository
public class ConductorOnboardingAccessRepository {

    private final JdbcTemplate jdbc;

    public ConductorOnboardingAccessRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ApplicantCredential> findCredential(String email) {
        // Explicit provisioning is the only link. Matching a legacy user's email
        // never turns an independent preliminary identity into an account.
        var identityId = jdbc.query("""
                select identity.id
                  from conductor_onboarding_identity identity
                  left join conductor_onboarding_request request
                    on request.identity_id = identity.id and request.linked_user_id is not null
                  left join app_user account on account.id = request.linked_user_id
                 where (account.id is not null and account.email = ?)
                    or (request.linked_user_id is null and identity.email = ?
                        and not exists (select 1 from app_user existing where existing.email = ?))
                """, (rs, row) -> rs.getObject("id", UUID.class), email, email, email).stream().findFirst();
        if (identityId.isEmpty()) return Optional.empty();
        // Provisioning takes the same identity lock; recovery takes the account
        // lock. Re-read after both locks so token snapshots cannot be stale.
        jdbc.queryForObject("select id from conductor_onboarding_identity where id = ? for update",
                UUID.class, identityId.get());
        jdbc.query("""
                select account.id from app_user account
                  join conductor_onboarding_request request on request.linked_user_id = account.id
                 where request.identity_id = ? for update of account
                """, (rs, row) -> rs.getObject("id", UUID.class), identityId.get());
        return jdbc.query("""
                select identity.id,
                       case when account.id is null then identity.password_hash else account.password_hash end as password_hash,
                       case when account.id is null then identity.email_verified_at is not null
                            else account.email_verified_at is not null end as email_verified,
                       (account.id is null or (account.active and account.role = 'CONDUCTOR'
                            and org.status = 'ACTIVE')) as account_allowed,
                       account.id as linked_user_id, account.session_version as linked_session_version,
                       exists (select 1 from conductor_onboarding_request request
                                where request.identity_id = identity.id) as has_request
                  from conductor_onboarding_identity identity
                  left join conductor_onboarding_request provision
                    on provision.identity_id = identity.id and provision.linked_user_id is not null
                  left join app_user account on account.id = provision.linked_user_id
                  left join organization org on org.id = account.organization_id
                 where identity.id = ? and (
                       (account.id is not null and account.email = ?)
                    or (provision.linked_user_id is null and identity.email = ?
                        and not exists (select 1 from app_user existing where existing.email = ?)))
                """, (rs, row) -> new ApplicantCredential(
                rs.getObject("id", UUID.class), rs.getString("password_hash"),
                rs.getBoolean("email_verified"), rs.getBoolean("has_request"), rs.getBoolean("account_allowed"),
                rs.getObject("linked_user_id", UUID.class), rs.getObject("linked_session_version", Long.class)
        ), identityId.get(), email, email, email).stream().findFirst();
    }

    public void createToken(ApplicantCredential credential, String tokenHash, Instant createdAt, Instant expiresAt) {
        jdbc.update("""
                insert into conductor_onboarding_access_token
                    (id, identity_id, token_hash, created_at, expires_at, linked_user_id, linked_session_version)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), credential.identityId(), tokenHash,
                Timestamp.from(createdAt), Timestamp.from(expiresAt), credential.linkedUserId(), credential.linkedSessionVersion());
    }

    public Optional<UUID> findIdentityForActiveToken(String tokenHash, Instant now) {
        return jdbc.query("""
                select token.identity_id
                  from conductor_onboarding_access_token token
                  join conductor_onboarding_identity identity on identity.id = token.identity_id
                  left join conductor_onboarding_request provision
                    on provision.identity_id = identity.id and provision.linked_user_id is not null
                  left join app_user account on account.id = provision.linked_user_id
                  left join organization org on org.id = account.organization_id
                 where token.token_hash = ?
                   and token.revoked_at is null
                   and token.expires_at > ?
                   and ((provision.linked_user_id is null and token.linked_user_id is null
                         and identity.email_verified_at is not null)
                     or (account.id = token.linked_user_id
                         and account.session_version = token.linked_session_version
                         and account.active and account.role = 'CONDUCTOR'
                         and account.email_verified_at is not null and org.status = 'ACTIVE'))
                """, (rs, row) -> rs.getObject("identity_id", UUID.class),
                tokenHash, Timestamp.from(now)).stream().findFirst();
    }

    /** Called after identity/request locks; account precedes token to match recovery/deactivation. */
    public boolean lockAccountAndTokenForMutation(UUID identityId, String tokenHash) {
        jdbc.query("""
                select account.id from app_user account
                  join conductor_onboarding_request request on request.linked_user_id = account.id
                 where request.identity_id = ? for share of account
                """, (rs, row) -> rs.getObject("id", UUID.class), identityId);
        return !jdbc.query("""
                select id from conductor_onboarding_access_token
                 where identity_id = ? and token_hash = ? for share
                """, (rs, row) -> rs.getObject("id", UUID.class), identityId, tokenHash).isEmpty();
    }

    public List<ApplicantRequest> listRequests(UUID identityId) {
        return jdbc.query("""
                select id, organization_id, status, review_rounds, submitted_at
                  from conductor_onboarding_request
                 where identity_id = ?
                 order by submitted_at desc, id desc
                 limit 200
                """, (rs, row) -> new ApplicantRequest(
                rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getString("status"), rs.getShort("review_rounds"),
                rs.getTimestamp("submitted_at").toInstant()
        ), identityId);
    }

    public void revokeToken(String tokenHash, Instant now) {
        jdbc.update("""
                update conductor_onboarding_access_token
                   set revoked_at = greatest(?, created_at)
                 where token_hash = ? and revoked_at is null
                """, Timestamp.from(now), tokenHash);
    }

    public record ApplicantCredential(UUID identityId, String passwordHash, boolean emailVerified,
                                      boolean hasRequest, boolean accountAllowed,
                                      UUID linkedUserId, Long linkedSessionVersion) {
    }

    public record ApplicantRequest(UUID requestId, UUID organizationId, String status, short reviewRounds,
                                   Instant submittedAt) {
    }
}
