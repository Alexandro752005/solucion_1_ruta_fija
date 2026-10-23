package pe.rutafija.retention;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager;
import pe.rutafija.retention.application.RetentionProcedure;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.terms.application.AdminTermsService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Bean security unit tests with synthetic unpersisted actors and a JDBC double; SQL is tested separately. */
class RetentionProcedureSecurityTest {
    private static final String PLAN_LOCK="select pg_advisory_xact_lock(hashtextextended('ruta_fija.retention.execute:' || ?::text,0) | '-9223372036854775808'::bigint)";
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final CurrentUserService users=mock(CurrentUserService.class);
    private final PersonIdentityGateAuthorizationManager identity=mock(PersonIdentityGateAuthorizationManager.class);
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(4);
    private final AuditService audit=mock(AuditService.class);
    private final Instant now=Instant.parse("2026-10-21T15:00:00Z");
    private RetentionProcedure service(boolean enabled){return new RetentionProcedure(jdbc,users,encoder,mock(LoginRateLimiter.class),audit,Clock.fixed(now,ZoneOffset.UTC),identity,mock(AdminTermsService.class),enabled);}
    private void owner(){var actor=AppUser.superAdmin("unpersisted.synthetic@rutafija.test",encoder.encode("Synthetic-retention-unit-only"),"SYNTHETIC_NOT_PERSISTED");when(users.requireSuperAdminActor()).thenReturn(actor);when(identity.allowsUser(actor.getId())).thenReturn(true);}
    @Test void disabledTechnicalProcedureNeverCallsDatabaseAndClearsInput(){char[] password="Synthetic-password".toCharArray();assertThatThrownBy(()->service(false).execute(UUID.randomUUID(),"a".repeat(64),password)).isInstanceOf(ApplicationException.class);assertThat(password).containsOnly('\0');verifyNoInteractions(jdbc,users);}
    @Test void wrongReauthenticationNeverExecutesSqlAndClearsInput(){owner();char[] password="Synthetic-wrong-password".toCharArray();assertThatThrownBy(()->service(true).execute(UUID.randomUUID(),"a".repeat(64),password)).isInstanceOf(ApplicationException.class);assertThat(password).containsOnly('\0');verifyNoInteractions(jdbc);}
    @Test void successfulSyntheticReauthenticationUsesExactHashAndClearsInput(){
        owner();UUID id=UUID.randomUUID();String sha="a".repeat(64);
        when(jdbc.queryForObject(eq("select exists(select 1 from retention_execution where plan_id=?)"),eq(Boolean.class),eq(id))).thenReturn(false);
        when(jdbc.queryForObject(eq("select retention_execute_plan(?,?,?,?)"),eq(Integer.class),eq(id),eq(sha),any(UUID.class),any(Timestamp.class))).thenReturn(1);
        when(jdbc.queryForObject(eq("select executed_at from retention_execution where plan_id=?"),eq(Timestamp.class),eq(id))).thenReturn(Timestamp.from(now));
        char[] password="Synthetic-retention-unit-only".toCharArray();var result=service(true).execute(id,sha,password);assertThat(result.retiredCount()).isEqualTo(1);assertThat(result.replay()).isFalse();assertThat(password).containsOnly('\0');
        var order=inOrder(jdbc,audit);
        order.verify(jdbc).queryForObject(PLAN_LOCK,Object.class,id);
        order.verify(jdbc).queryForObject("select exists(select 1 from retention_execution where plan_id=?)",Boolean.class,id);
        order.verify(jdbc).queryForObject(eq("select retention_execute_plan(?,?,?,?)"),eq(Integer.class),eq(id),eq(sha),any(UUID.class),eq(Timestamp.from(now)));
        order.verify(jdbc).queryForObject("select executed_at from retention_execution where plan_id=?",Timestamp.class,id);
        order.verify(audit).record(any(AppUser.class),eq("RETENTION_PLAN_EXECUTED"),eq("RETENTION_PLAN"),eq(id),eq(Map.of("planId",id,"retiredCount",1)));
        order.verifyNoMoreInteractions();
    }
    @Test void replayIsReadAfterLockAndDoesNotDuplicateTheExecutionAudit(){
        owner();UUID id=UUID.randomUUID();String sha="b".repeat(64);Instant original=now.minusSeconds(20);
        when(jdbc.queryForObject("select exists(select 1 from retention_execution where plan_id=?)",Boolean.class,id)).thenReturn(true);
        when(jdbc.queryForObject(eq("select retention_execute_plan(?,?,?,?)"),eq(Integer.class),eq(id),eq(sha),any(UUID.class),any(Timestamp.class))).thenReturn(2);
        when(jdbc.queryForObject("select executed_at from retention_execution where plan_id=?",Timestamp.class,id)).thenReturn(Timestamp.from(original));
        char[] password="Synthetic-retention-unit-only".toCharArray();var result=service(true).execute(id,sha,password);
        assertThat(result.replay()).isTrue();assertThat(result.retiredCount()).isEqualTo(2);assertThat(result.executedAt()).isEqualTo(original);assertThat(password).containsOnly('\0');
        var order=inOrder(jdbc);
        order.verify(jdbc).queryForObject(PLAN_LOCK,Object.class,id);
        order.verify(jdbc).queryForObject("select exists(select 1 from retention_execution where plan_id=?)",Boolean.class,id);
        order.verify(jdbc).queryForObject(eq("select retention_execute_plan(?,?,?,?)"),eq(Integer.class),eq(id),eq(sha),any(UUID.class),eq(Timestamp.from(now)));
        order.verify(jdbc).queryForObject("select executed_at from retention_execution where plan_id=?",Timestamp.class,id);
        order.verifyNoMoreInteractions();verifyNoInteractions(audit);
    }
    @Test void lockFailureNeverChecksReplayOrExecutesAndStillClearsPassword(){
        owner();UUID id=UUID.randomUUID();
        when(jdbc.queryForObject(PLAN_LOCK,Object.class,id)).thenThrow(new QueryTimeoutException("SYNTHETIC_LOCK_TIMEOUT"));
        char[] password="Synthetic-retention-unit-only".toCharArray();
        assertThatThrownBy(()->service(true).execute(id,"c".repeat(64),password)).isInstanceOf(QueryTimeoutException.class);
        assertThat(password).containsOnly('\0');verify(jdbc).queryForObject(PLAN_LOCK,Object.class,id);verifyNoMoreInteractions(jdbc);verifyNoInteractions(audit);
    }
    @Test void invalidApprovedHashDoesNotAcquireAnyLockAndClearsPassword(){
        owner();char[] password="Synthetic-retention-unit-only".toCharArray();
        assertThatThrownBy(()->service(true).execute(UUID.randomUUID(),"NOT_AN_APPROVED_HASH",password)).isInstanceOf(ApplicationException.class);
        assertThat(password).containsOnly('\0');verifyNoInteractions(jdbc,audit);
    }
    @Test void disabledDelegatedPreparationDoesNotReadOrWriteAnySelection(){
        char[] password="Synthetic-password".toCharArray();
        assertThatThrownBy(()->service(false).requestDelegatedPersonalPlan(UUID.randomUUID(),UUID.randomUUID(),password)).isInstanceOf(ApplicationException.class);
        assertThat(password).containsOnly('\0');verifyNoInteractions(jdbc,users);
    }
    @Test void delegatedPreparationRequiresSuccessfulOwnerReauthenticationBeforeSql(){
        owner();char[] password="Synthetic-wrong-password".toCharArray();
        assertThatThrownBy(()->service(true).requestDelegatedPersonalPlan(UUID.randomUUID(),UUID.randomUUID(),password)).isInstanceOf(ApplicationException.class);
        assertThat(password).containsOnly('\0');verifyNoInteractions(jdbc);
    }
    @Test void personalSelectionWithoutCurrentTermsIsDeniedAndClearsPassword(){
        var organization=pe.rutafija.organization.domain.Organization.active("SYNTHETIC UNPERSISTED","SYNTHETIC","America/Lima");
        var admin=AppUser.organizationUser(organization,"synthetic.unpersisted@rutafija.test",encoder.encode("Synthetic-retention-unit-only"),"SYNTHETIC",pe.rutafija.identity.domain.UserRole.ADMIN);
        when(users.requireTenantActor()).thenReturn(admin);when(identity.allowsUser(admin.getId())).thenReturn(true);
        char[] password="Synthetic-retention-unit-only".toCharArray();
        assertThatThrownBy(()->service(true).delegatePersonalSelection(UUID.randomUUID(),RetentionProcedure.Category.DOCUMENTARY_CASE,List.of(UUID.randomUUID()),UUID.randomUUID(),"SYNTHETIC_PRE_CLOSURE",password)).isInstanceOf(ApplicationException.class);
        assertThat(password).containsOnly('\0');verifyNoInteractions(jdbc);
    }
}
