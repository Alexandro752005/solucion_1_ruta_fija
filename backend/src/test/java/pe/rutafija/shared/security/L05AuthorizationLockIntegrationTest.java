package pe.rutafija.shared.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.operation.application.OrganizationSchedulingLock;
import pe.rutafija.support.L02PostgresIntegrationTest;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

/** Two real transactions, locks/read-only business data; no fixture creation, commit or global cleanup. */
@SpringBootTest(properties={"debug=false","logging.level.root=ERROR"}) @ActiveProfiles("test")
class L05AuthorizationLockIntegrationTest extends L02PostgresIntegrationTest {
    @Autowired CurrentUserProvider actors;
    @Autowired OrganizationSchedulingLock scheduling;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean OtpEmailSender mail;

    @Test void sameTenantAuthorizedWritesSerializeWithoutOrganizationLockUpgradeDeadlock()throws Exception {
        // Only read/lock the existing synthetic reviewer. Do not reopen a human case or create an identity/token/session.
        var rows=jdbc.queryForList("""
            select u.id,u.organization_id,u.session_version from app_user u join organization o on o.id=u.organization_id
             where u.id=? and u.role='ADMIN' and u.active and u.email_verified_at is not null
              and u.email like '%@rutafija.test' and o.status='ACTIVE'
            """,UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979"));
        assertThat(rows).hasSize(1);var row=rows.getFirst();UUID org=(UUID)row.get("organization_id");
        var auth=Jwt.withTokenValue("SYNTHETIC_TEST_CONTEXT_NOT_A_BEARER_CREDENTIAL").header("alg","none")
            .subject(row.get("id").toString()).claim("organizationId",org.toString()).claim("role","ADMIN")
            .claim("sessionVersion",((Number)row.get("session_version")).longValue()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        var firstReady=new CountDownLatch(1);var secondReady=new CountDownLatch(1);var secondPid=new AtomicInteger();
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            Future<String> first=executor.submit(()->invoke(auth,org,()->{
                firstReady.countDown();await(secondReady);long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4);
                while(System.nanoTime()<until) {
                    Boolean waiting=jdbc.queryForObject("select coalesce((select wait_event_type='Lock' from pg_stat_activity where pid=?),false)",Boolean.class,secondPid.get());
                    if(Boolean.TRUE.equals(waiting))return;
                    try{Thread.sleep(10);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("RACE_INTERRUPTED");}
                }
                throw new IllegalStateException("SECOND_TRANSACTION_DID_NOT_REACH_LOCK_WAIT");
            },null));
            if(!firstReady.await(5,TimeUnit.SECONDS))assertThat(first.get(1,TimeUnit.SECONDS)).isEqualTo("ACTOR_LOCK_REACHED");
            Future<String> second=executor.submit(()->invoke(auth,org,null,()->{secondPid.set(jdbc.queryForObject("select pg_backend_pid()",Integer.class));secondReady.countDown();}));
            assertThat(first.get(12,TimeUnit.SECONDS)).isEqualTo("AUTHORIZED_AND_SCHEDULING_LOCKED");
            assertThat(second.get(12,TimeUnit.SECONDS)).isEqualTo("AUTHORIZED_AND_SCHEDULING_LOCKED");
        } finally {executor.shutdownNow();assertThat(executor.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
        verifyNoInteractions(mail);
    }
    private String invoke(Jwt auth,UUID org,Runnable afterActor,Runnable beforeActor) {
        var tx=new TransactionTemplate(transactions);tx.setTimeout(10);
        try {
            return tx.execute(status->{
                status.setRollbackOnly();SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(auth,List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))));
                try {
                    jdbc.queryForObject("select set_config('lock_timeout','6s',true)",String.class);
                    if(beforeActor!=null)beforeActor.run();actors.requireCurrentUser();if(afterActor!=null)afterActor.run();
                    scheduling.acquire(org);actors.requireCurrentUser();return "AUTHORIZED_AND_SCHEDULING_LOCKED";
                } finally {SecurityContextHolder.clearContext();}
            });
        } catch(Exception failure) {
            Throwable root=failure;for(int n=0;n<12&&root.getCause()!=null;n++)root=root.getCause();
            return root instanceof SQLException sql?"SQLSTATE_"+sql.getSQLState():"REFUSED_"+root.getClass().getSimpleName();
        }
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(5,TimeUnit.SECONDS))throw new IllegalStateException("RACE_LATCH_TIMEOUT");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("RACE_INTERRUPTED");}}
}
