package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.identity.domain.AppUser;

import java.util.UUID;

/**
 * Explicit historical SYNTHETIC approval fixture for existing operation regressions.
 * This is not an alternative provisioning endpoint and is never packaged in runtime.
 * Lifecycle tests must exercise the actual review/provisioning API instead.
 */
public final class ApprovedConductorFixture {
    private ApprovedConductorFixture() { }

    public static UUID recordHistoricalApproval(JdbcTemplate jdbc, AppUser admin, AppUser user, Driver driver) {
        return recordHistoricalApproval(jdbc, admin.getId(), user.getId(), driver.getId());
    }

    public static UUID recordHistoricalApproval(JdbcTemplate jdbc, UUID adminId, UUID userId, UUID driverId) {
        Boolean target = jdbc.queryForObject("""
                select current_database() = 'ruta_fija_test'
                """, Boolean.class);
        if (!Boolean.TRUE.equals(target)) {
            throw new IllegalStateException("Synthetic historical approval is restricted to ruta_fija_test");
        }
        var rows = jdbc.queryForList("""
                select u.organization_id, u.email, u.password_hash, u.full_name, u.phone
                from app_user u join driver d on d.user_id = u.id and d.organization_id = u.organization_id
                join app_user a on a.id = ? and a.organization_id = u.organization_id and a.role = 'ADMIN'
                where u.id = ? and d.id = ? and u.role = 'CONDUCTOR' and lower(u.email) like '%@rutafija.test'
                """, adminId, userId, driverId);
        if (rows.size() != 1) {
            throw new IllegalArgumentException("Fixture requires synthetic CONDUCTOR, linked driver and same-tenant ADMIN");
        }
        var existing = jdbc.queryForList("select id from conductor_onboarding_request where linked_user_id = ? and linked_driver_id = ?",
                UUID.class, userId, driverId);
        if (!existing.isEmpty()) return existing.getFirst();
        var user = rows.getFirst();
        UUID identity = UUID.randomUUID();
        UUID invitation = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_identity
                  (id,email,password_hash,full_name,phone,email_verified_at,created_at,updated_at)
                values (?, ?, ?, ?, ?, now(), now(), now())
                """, identity, user.get("email"), user.get("password_hash"), user.get("full_name"), user.get("phone"));
        jdbc.update("""
                insert into organization_invitation
                  (id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at,used_at)
                values (?, ?, ?, ?, ?, now() - interval '1 hour', now() + interval '1 hour', now())
                """, invitation, user.get("organization_id"), adminId, user.get("email"), "a".repeat(64));
        jdbc.update("""
                insert into conductor_onboarding_request
                  (id,identity_id,organization_id,invitation_id,idempotency_key,status,review_rounds,
                   submitted_at,reviewed_by_admin_id,reviewed_at,personal_approved_at,
                   linked_user_id,linked_driver_id,provisioned_at)
                values (?, ?, ?, ?, ?, 'APPROVED', 1, now(), ?, now(), now(), ?, ?, now())
                """, request, identity, user.get("organization_id"), invitation,
                "SYNTHETIC-HISTORICAL-" + request, adminId, userId, driverId);
        return request;
    }
}
