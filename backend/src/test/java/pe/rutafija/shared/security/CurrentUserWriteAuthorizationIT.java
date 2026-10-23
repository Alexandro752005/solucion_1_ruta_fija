package pe.rutafija.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** Real row-lock races; all identities and requests are synthetic, no mail. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CurrentUserWriteAuthorizationIT extends NativePostgresIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired OrganizationRepository organizations;
    @Autowired AppUserRepository users;

    private Organization organization;
    private AppUser admin;
    private AppUser owner;

    @BeforeEach
    void prepareSyntheticActors() {
        organization = organizations.save(Organization.active(
                "Synthetic authorization " + UUID.randomUUID(), "Synthetic", "America/Lima"));
        admin = AppUser.organizationUser(organization, "synthetic.admin@rutafija.test", "hash",
                "Synthetic Admin", UserRole.ADMIN);
        admin.verifyEmail(Instant.now());
        admin = users.save(admin);
        owner = AppUser.superAdmin("synthetic.owner@rutafija.test", "hash", "Synthetic Owner");
        owner.verifyEmail(Instant.now());
        owner = users.save(owner);
    }

    @Test
    void revokedAdminCannotCreateGroupAfterWaitingForActorLock() throws Exception {
        expectRevokedWhileWaiting(
                "update app_user set active=false, session_version=session_version+1 where id=?",
                admin.getId(), groupRequest(admin));
        assertThat(count("select count(*) from transport_group")).isZero();
        assertThat(count("select count(*) from audit_event where action='GROUP_CREATED'")).isZero();
    }

    @Test
    void changedSessionVersionRejectsStillActiveAdminAfterActorLockWait() throws Exception {
        expectRevokedWhileWaiting(
                "update app_user set session_version=session_version+1 where id=?",
                admin.getId(), groupRequest(admin));
        assertThat(count("select count(*) from transport_group")).isZero();
        assertThat(jdbc.queryForObject("select active from app_user where id=?", Boolean.class, admin.getId()))
                .isTrue();
    }

    @Test
    void disabledOrganizationRejectsAdminAfterOrganizationLockWait() throws Exception {
        expectRevokedWhileWaiting("update organization set status='INACTIVE' where id=?",
                organization.getId(), groupRequest(admin));
        assertThat(count("select count(*) from transport_group")).isZero();
    }

    @Test
    void changedOwnerSessionRejectsOrganizationCreationAfterActorLockWait() throws Exception {
        expectRevokedWhileWaiting("update app_user set session_version=session_version+1 where id=?",
                owner.getId(), post("/api/v1/organizations").with(auth(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"legalName":"Synthetic blocked creation","tradeName":"Synthetic","timezone":"America/Lima"}
                                """));
        assertThat(count("select count(*) from organization")).isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action='ORGANIZATION_CREATED'")).isZero();
    }

    @Test
    void authorizationLocksPreventRevocationUntilAuthorizedWriteCommits() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch requestFinished = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        AtomicInteger writerPid = new AtomicInteger();
        try {
            var writer = executor.submit(() -> new TransactionTemplate(transactionManager).execute(status -> {
                writerPid.set(jdbc.queryForObject("select pg_backend_pid()", Integer.class));
                try {
                    MvcResult response = mvc.perform(groupRequest(admin)).andReturn();
                    requestFinished.countDown();
                    if (!allowCommit.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Synthetic write was not released");
                    }
                    return response.getResponse().getStatus();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(requestFinished.await(10, TimeUnit.SECONDS)).isTrue();
            var revocation = executor.submit(() -> jdbc.update(
                    "update app_user set session_version=session_version+1 where id=?", admin.getId()));
            awaitBlockedBy(writerPid.get());
            assertThat(revocation.isDone()).isFalse();
            allowCommit.countDown();

            assertThat(writer.get(10, TimeUnit.SECONDS)).isEqualTo(201);
            assertThat(revocation.get(10, TimeUnit.SECONDS)).isEqualTo(1);
            assertThat(count("select count(*) from transport_group")).isEqualTo(1);
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void expectRevokedWhileWaiting(String mutationSql, UUID target,
                                           MockHttpServletRequestBuilder request) throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try {
                int blockerPid;
                try (PreparedStatement statement = blocker.prepareStatement("select pg_backend_pid()");
                     ResultSet result = statement.executeQuery()) {
                    result.next();
                    blockerPid = result.getInt(1);
                }
                try (PreparedStatement statement = blocker.prepareStatement(mutationSql)) {
                    statement.setObject(1, target);
                    assertThat(statement.executeUpdate()).isEqualTo(1);
                }
                var pendingRequest = executor.submit(() -> mvc.perform(request).andReturn());
                awaitBlockedBy(blockerPid);
                assertThat(pendingRequest.isDone()).isFalse();
                blocker.commit();

                MvcResult response = pendingRequest.get(10, TimeUnit.SECONDS);
                assertThat(response.getResponse().getStatus()).isEqualTo(401);
                assertThat(response.getResponse().getContentAsString()).contains("AUTH_TOKEN_INVALID");
            } finally {
                blocker.rollback();
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void awaitBlockedBy(int blockerPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Integer blocked = jdbc.queryForObject("""
                    select count(*) from pg_stat_activity
                    where datname=current_database() and usename=current_user
                      and ? = any(pg_blocking_pids(pid))
                    """, Integer.class, blockerPid);
            if (blocked != null && blocked > 0) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("The protected write never waited for the expected PostgreSQL row lock");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private MockHttpServletRequestBuilder groupRequest(AppUser actor) {
        return post("/api/v1/groups").with(auth(actor)).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Synthetic protected group","description":"Authorization race fixture"}
                        """);
    }

    private RequestPostProcessor auth(AppUser actor) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name()))
                .jwt(token -> {
                    token.subject(actor.getId().toString()).claim("role", actor.getRole().name())
                            .claim("email", actor.getEmail()).claim("sessionVersion", actor.getSessionVersion());
                    if (actor.getOrganizationId() != null) {
                        token.claim("organizationId", actor.getOrganizationId().toString());
                    }
                });
    }
}
