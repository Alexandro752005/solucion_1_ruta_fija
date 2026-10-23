package pe.rutafija.pilotquota;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.pilotquota.application.PilotCreationQuotaService;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real PostgreSQL triggers, including inserts that never call the quota service. */
@SpringBootTest
@ActiveProfiles("test")
class PilotCreationQuotaIT extends NativePostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PilotCreationQuotaService quotas;
    private UUID org;

    @BeforeEach
    void prepareOrganization() {
        org = organization();
    }

    @Test
    void superAdminLimitIsOneIncludingInactiveAccounts() {
        insert(UserRole.SUPER_ADMIN, false, null);
        assertThatThrownBy(() -> insert(UserRole.SUPER_ADMIN, true, null))
                .isInstanceOf(DataAccessException.class);
        assertThat(quotas.historicalCreations(UserRole.SUPER_ADMIN)).isEqualTo(1);
    }

    @Test
    void fourAdminCreationsExhaustReplacementEvenAfterDeletionAndDeactivation() {
        UUID first = insert(UserRole.ADMIN, false, org);
        insert(UserRole.ADMIN, false, org);
        insert(UserRole.ADMIN, true, organization());
        insert(UserRole.ADMIN, true, organization());
        jdbc.update("delete from app_user where id = ?", first);
        assertThatThrownBy(() -> insert(UserRole.ADMIN, true, org)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> quotas.requireAvailable(UserRole.ADMIN)).isInstanceOf(ApplicationException.class);
        assertThat(quotas.historicalCreations(UserRole.ADMIN)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'ADMIN'", Long.class)).isEqualTo(3);
    }

    @Test
    void conductorLimitIsTwentyGloballyAcrossOrganizationsAndStates() {
        UUID secondOrg = organization();
        for (int i = 0; i < 20; i++) {
            insert(UserRole.CONDUCTOR, i % 2 == 0, i % 2 == 0 ? org : secondOrg);
        }
        assertThatThrownBy(() -> insert(UserRole.CONDUCTOR, true, secondOrg)).isInstanceOf(DataAccessException.class);
        assertThat(quotas.historicalCreations(UserRole.CONDUCTOR)).isEqualTo(20);
    }

    @Test
    void rollbackReturnsUncommittedSlotAndNeverLeavesLedgerOrphan() {
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            insert(UserRole.ADMIN, true, org);
            assertThat(quotas.historicalCreations(UserRole.ADMIN)).isEqualTo(1);
            status.setRollbackOnly();
        });
        assertThat(quotas.historicalCreations(UserRole.ADMIN)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isZero();
        insert(UserRole.ADMIN, true, org);
        assertThat(quotas.historicalCreations(UserRole.ADMIN)).isEqualTo(1);
    }

    @Test
    void recoveryOfSameAccountDoesNotCreateAnotherIdentityOrConsumeSlot() {
        UUID id = insert(UserRole.SUPER_ADMIN, true, null);
        jdbc.update("""
                update app_user set email = ?, password_hash = ?, email_verified_at = now(),
                    session_version = session_version + 1, updated_at = now() where id = ?
                """, "recovered.synthetic@rutafija.test", "synthetic-recovery-hash", id);
        assertThat(quotas.historicalCreations(UserRole.SUPER_ADMIN)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select session_version from app_user where id = ?", Long.class, id)).isEqualTo(1);
    }

    @Test
    void identityRoleAndOrganizationCannotBeChangedToEvadeHistoricalLimits() {
        UUID id = insert(UserRole.ADMIN, false, org);
        UUID secondOrg = organization();
        assertThatThrownBy(() -> jdbc.update("update app_user set role = 'CONDUCTOR' where id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update app_user set organization_id = ? where id = ?", secondOrg, id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update app_user set id = ? where id = ?", UUID.randomUUID(), id))
                .isInstanceOf(DataAccessException.class);
        assertThat(quotas.historicalCreations(UserRole.ADMIN)).isEqualTo(1);
        assertThat(quotas.historicalCreations(UserRole.CONDUCTOR)).isZero();
    }

    @Test
    void deletedIdentityCannotBeRecreatedAndLedgerCannotBeAlteredByApplicationRole() {
        UUID id = insert(UserRole.ADMIN, true, org);
        jdbc.update("delete from app_user where id = ?", id);
        assertThatThrownBy(() -> insert(id, UserRole.ADMIN, true, org)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("delete from pilot_account_creation where user_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("update pilot_account_creation set role = 'CONDUCTOR' where user_id = ?", id))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("insert into pilot_account_creation values (?, 'ADMIN', now())", UUID.randomUUID()))
                .isInstanceOf(DataAccessException.class);
        assertThat(quotas.historicalCreations(UserRole.ADMIN)).isEqualTo(1);
    }

    @Test
    void concurrentFinalSlotAllowsExactlyOneCreation() throws Exception {
        for (int i = 0; i < 19; i++) insert(UserRole.CONDUCTOR, true, org);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        List<Boolean> outcomes = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var work = (java.util.concurrent.Callable<Boolean>) () -> {
                ready.countDown();
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
                try {
                    insert(UserRole.CONDUCTOR, true, org);
                    return true;
                } catch (DataAccessException exhausted) {
                    return false;
                }
            };
            var first = executor.submit(work);
            var second = executor.submit(work);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            outcomes.add(first.get(15, TimeUnit.SECONDS));
            outcomes.add(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(quotas.historicalCreations(UserRole.CONDUCTOR)).isEqualTo(20);
        assertThat(jdbc.queryForObject("select count(*) from app_user where role = 'CONDUCTOR'", Long.class)).isEqualTo(20);
    }

    @Test
    void aSingleBulkStatementCannotExceedTheHistoricalCap() {
        assertThatThrownBy(() -> jdbc.update("""
                insert into app_user(id, organization_id, email, password_hash, full_name, role,
                    active, created_at, updated_at)
                select gen_random_uuid(), ?, 'synthetic.bulk.' || i || '@rutafija.test',
                       'synthetic-only-hash', 'SYNTHETIC BULK', 'CONDUCTOR', true, now(), now()
                  from generate_series(1, 21) i
                """, org)).isInstanceOf(DataAccessException.class);
        assertThat(quotas.historicalCreations(UserRole.CONDUCTOR)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isZero();
    }

    @Test
    void ignoredConflictingInsertDoesNotConsumeHistoricalCreation() {
        UUID existing = insert(UserRole.ADMIN, true, org);
        int inserted = jdbc.update("""
                insert into app_user(id, organization_id, email, password_hash, full_name, role,
                    active, created_at, updated_at)
                values (?, ?, ?, 'synthetic-only-hash', 'SYNTHETIC IGNORED ACCOUNT', 'ADMIN', true, now(), now())
                on conflict do nothing
                """, UUID.randomUUID(), org, existing + "@rutafija.test");
        assertThat(inserted).isZero();
        assertThat(quotas.historicalCreations(UserRole.ADMIN)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isEqualTo(1);
    }

    @Test
    void preliminaryIdentityDoesNotConsumeConductorCreation() {
        jdbc.update("""
                insert into conductor_onboarding_identity
                  (id, email, password_hash, full_name, created_at, updated_at)
                values (?, ?, 'synthetic-only-hash', 'SYNTHETIC APPLICANT', now(), now())
                """, UUID.randomUUID(), "preliminary.synthetic@rutafija.test");
        assertThat(quotas.historicalCreations(UserRole.CONDUCTOR)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_user", Long.class)).isZero();
    }

    private UUID organization() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into organization(id, legal_name, status, timezone, created_at, updated_at)
                values (?, ?, 'ACTIVE', 'America/Lima', now(), now())
                """, id, "SYNTHETIC QUOTA " + id);
        return id;
    }

    private UUID insert(UserRole role, boolean active, UUID organizationId) {
        UUID id = UUID.randomUUID();
        insert(id, role, active, organizationId);
        return id;
    }

    private void insert(UUID id, UserRole role, boolean active, UUID organizationId) {
        jdbc.update("""
                insert into app_user(id, organization_id, email, password_hash, full_name, role,
                    active, created_at, updated_at)
                values (?, ?, ?, 'synthetic-not-a-login-hash', 'SYNTHETIC QUOTA ACCOUNT', ?, ?, now(), now())
                """, id, organizationId, id + "@rutafija.test", role.name(), active);
    }
}
