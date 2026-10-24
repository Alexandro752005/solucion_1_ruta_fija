package pe.rutafija.qualification;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.retention.application.*;
import pe.rutafija.shared.exception.ApplicationException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated rollback organizations only. Never select historical shared-organization events. */
@TestPropertySource(properties="app.retention.technical-enabled=true")
class RetentionIntegrationTest extends ServiceExecutionIntegrationTest {
    @Autowired RetentionProcedure procedure;
    @Autowired JwtDecoder decoder;
    private AppUser technical;
    private final String syntheticPassword="Synthetic-Retention-Password-2026";
    @AfterEach void clearTechnicalContext(){SecurityContextHolder.clearContext();}
    private AppUser technical(){
        if(technical==null){
            // Public reference only. Never change the closed fixture's password, key or identity.
            technical=users.findById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow();
        }return technical;
    }
    private void context(AppUser a){var token=decoder.decode(jwt.issueAccessToken(a));SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(token,List.of(new SimpleGrantedAuthority("ROLE_"+a.getRole()))));}
    private UUID event(UUID org,Instant at){UUID id=UUID.randomUUID();jdbc.update("insert into audit_event(id,organization_id,user_id,action,entity_type,entity_id,metadata_json,occurred_at) values(?,?,null,'SYNTHETIC_RETENTION','SYNTHETIC',?,'{}'::jsonb,?)",id,org,UUID.randomUUID(),ts(at));return id;}
    private String[] plan(UUID resource,UUID org,String category){UUID id=UUID.randomUUID();jdbc.queryForObject("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.class,id,category,org,admin.getId(),UUID.randomUUID(),"SYNTHETIC_EXACT_CASE","{"+resource+"}",ts(initial));return new String[]{id.toString(),jdbc.queryForObject("select selection_sha256 from retention_plan where id=?",String.class,id)};}
    private int execute(String[] p,Instant at){return jdbc.queryForObject("select retention_execute_plan(?,?,?,?)",Integer.class,UUID.fromString(p[0]),p[1],technical().getId(),ts(at));}
    private boolean exists(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from audit_event where id=?)",Boolean.class,id));}
    @Test void retentionCalendarSqlMatchesLimaJavaAndExactReadDeadline()throws Exception{
        var at=Instant.parse("2026-09-01T04:59:59.123Z");assertThat(jdbc.queryForObject("select retention_audit_due(?)",Timestamp.class,ts(at)).toInstant()).isEqualTo(RetentionCalendar.auditDue(at));
        assertThat(jdbc.queryForObject("select retention_audit_visible(?,?,?)",Boolean.class,UUID.randomUUID(),ts(at),ts(RetentionCalendar.auditDue(at)))).isFalse();
        UUID old=event(organization.getId(),initial.atZone(RetentionCalendar.AUDIT_ZONE).minusMonths(7).toInstant());
        mvc.perform(get("/api/v1/audit-events/"+old).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());assertThat(exists(old)).isTrue();
        var page=json(mvc.perform(get("/api/v1/audit-events?action=SYNTHETIC_RETENTION").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));assertThat(page.path("items")).isEmpty();
    }
    @Test void retentionExactPlanPurgesOnlyExpiredSelectedRowsAndReplaysOnce(){
        UUID old=event(organization.getId(),initial.atZone(RetentionCalendar.AUDIT_ZONE).minusMonths(7).toInstant()),keep=event(organization.getId(),initial),foreign=event(otherOrganization.getId(),initial.atZone(RetentionCalendar.AUDIT_ZONE).minusMonths(7).toInstant());
        var p=plan(old,organization.getId(),"OPERATIONAL_AUDIT");assertThat(execute(p,initial)).isEqualTo(1);assertThat(execute(p,initial.plusSeconds(1))).isEqualTo(1);assertThat(exists(old)).isFalse();assertThat(exists(keep)).isTrue();assertThat(exists(foreign)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from retention_execution where plan_id=?",Integer.class,UUID.fromString(p[0]))).isEqualTo(1);
    }
    @Test void retentionNonexpiredWrongHashForeignScopeAndUnapprovedDirectDeletesAreRejected(){
        UUID recent=event(organization.getId(),initial),foreign=event(otherOrganization.getId(),initial.minus(Duration.ofDays(300)));var p=plan(recent,organization.getId(),"OPERATIONAL_AUDIT");
        assertThat(rejectedSqlState("select retention_execute_plan(?,?,?,?)",UUID.fromString(p[0]),p[1],technical().getId(),ts(initial))).isEqualTo("23514");
        assertThat(rejectedSqlState("select retention_execute_plan(?,?,?,?)",UUID.fromString(p[0]),"a".repeat(64),technical().getId(),ts(initial))).isEqualTo("23514");
        assertThat(rejectedSqlState("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.randomUUID(),"OPERATIONAL_AUDIT",organization.getId(),admin.getId(),UUID.randomUUID(),"SYNTHETIC_EXACT_CASE","{"+foreign+"}",ts(initial))).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from audit_event where id=?",recent)).isEqualTo("23514");
        assertThat(rejectedSqlState("update audit_event set action='SYNTHETIC_CHANGE' where id=?",recent)).isEqualTo("23514");
        assertThat(rejectedSqlState("insert into retention_execution_authority values(?,?,?)",UUID.fromString(p[0]),1,1)).isEqualTo("42501");
    }
    @Test void retentionHoldIsExactApprovedBoundedAndNeverMakesOrdinaryReadVisible()throws Exception{
        Instant at=initial.atZone(RetentionCalendar.AUDIT_ZONE).minusMonths(6).minusDays(1).toInstant(),due=RetentionCalendar.auditDue(at);UUID id=event(organization.getId(),at),hold=UUID.randomUUID();
        jdbc.queryForObject("select retention_request_hold(?,?,?,?,?,?,?)",UUID.class,hold,id,admin.getId(),UUID.randomUUID(),"SYNTHETIC_OPEN_CASE",ts(due.plus(Duration.ofDays(10))),ts(initial));
        jdbc.queryForObject("select retention_approve_hold(?,?,?)",UUID.class,hold,technical().getId(),ts(initial));
        mvc.perform(get("/api/v1/audit-events/"+id).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        var p=plan(id,organization.getId(),"OPERATIONAL_AUDIT");assertThat(rejectedSqlState("select retention_execute_plan(?,?,?,?)",UUID.fromString(p[0]),p[1],technical().getId(),ts(initial))).isEqualTo("23514");
        assertThat(execute(p,due.plus(Duration.ofDays(10)))).isEqualTo(1);assertThat(exists(id)).isFalse();
    }
    @Test void retentionHoldRejectsMoreThan30DaysAndRepeatedRenewal(){
        Instant at=initial.atZone(RetentionCalendar.AUDIT_ZONE).minusMonths(6).toInstant(),due=RetentionCalendar.auditDue(at);UUID id=event(organization.getId(),at);
        assertThat(rejectedSqlState("select retention_request_hold(?,?,?,?,?,?,?)",UUID.randomUUID(),id,admin.getId(),UUID.randomUUID(),"SYNTHETIC_OPEN_CASE",ts(due.plus(Duration.ofDays(30)).plusSeconds(1)),ts(initial))).isEqualTo("23514");
        jdbc.queryForObject("select retention_request_hold(?,?,?,?,?,?,?)",UUID.class,UUID.randomUUID(),id,admin.getId(),UUID.randomUUID(),"SYNTHETIC_OPEN_CASE",ts(due.plus(Duration.ofDays(30))),ts(initial));
        assertThat(rejectedSqlState("select retention_request_hold(?,?,?,?,?,?,?)",UUID.randomUUID(),id,admin.getId(),UUID.randomUUID(),"SYNTHETIC_OPEN_CASE",ts(due.plus(Duration.ofDays(29))),ts(initial))).isEqualTo("23505");
    }
    @Test void retentionHoldApprovedAfterAPlanMakesTheOriginalSelectionStale(){
        Instant at=initial.atZone(RetentionCalendar.AUDIT_ZONE).minusMonths(6).minusDays(1).toInstant(),due=RetentionCalendar.auditDue(at);UUID id=event(organization.getId(),at);var p=plan(id,organization.getId(),"OPERATIONAL_AUDIT");UUID hold=UUID.randomUUID();
        jdbc.queryForObject("select retention_request_hold(?,?,?,?,?,?,?)",UUID.class,hold,id,admin.getId(),UUID.randomUUID(),"SYNTHETIC_OPEN_CASE",ts(due.plus(Duration.ofDays(10))),ts(initial));jdbc.queryForObject("select retention_approve_hold(?,?,?)",UUID.class,hold,technical().getId(),ts(initial));
        assertThat(rejectedSqlState("select retention_execute_plan(?,?,?,?)",UUID.fromString(p[0]),p[1],technical().getId(),ts(due.plus(Duration.ofDays(11))))).isEqualTo("23514");assertThat(exists(id)).isTrue();
    }
    @Test void retentionCopiesInheritExpiryAndRestoreAccessRequiresWithdrawalReapplied(){
        UUID source=event(organization.getId(),initial),copy=UUID.randomUUID(),owner=technical().getId();
        jdbc.queryForObject("select retention_register_copy(?,?,?,?,?,?,?)",UUID.class,copy,"OPERATIONAL_AUDIT",source,UUID.randomUUID(),owner,ts(initial),true);
        assertThat(jdbc.queryForObject("select expires_at from retention_controlled_copy where id=?",Timestamp.class,copy).toInstant()).isEqualTo(RetentionCalendar.auditDue(initial));
        assertThat(jdbc.queryForObject("select retention_copy_restore_ready(?,?)",Boolean.class,copy,ts(initial))).isFalse();
        jdbc.queryForObject("select retention_attest_copy(?,?,?,?,?,?)",UUID.class,UUID.randomUUID(),copy,"REAPPLIED_BEFORE_ACCESS",UUID.randomUUID(),owner,ts(initial));assertThat(jdbc.queryForObject("select retention_copy_restore_ready(?,?)",Boolean.class,copy,ts(initial))).isTrue();
        assertThat(jdbc.queryForObject("select retention_copy_restore_ready(?,?)",Boolean.class,copy,ts(RetentionCalendar.auditDue(initial)))).isFalse();
        jdbc.queryForObject("select retention_attest_copy(?,?,?,?,?,?)",UUID.class,UUID.randomUUID(),copy,"RETIRED",UUID.randomUUID(),owner,ts(initial));assertThat(jdbc.queryForObject("select retention_copy_restore_ready(?,?)",Boolean.class,copy,ts(initial))).isFalse();
    }
    @Test void retentionReportContentExpiresWithoutDeletingImmutableReference()throws Exception{
        prepared();var a=started();clock.set(initial.plusSeconds(30));command(a,"complete",command(a,false)).andExpect(status().isOk());
        String from=Instant.parse(a.path("scheduledAt").asText()).atZone(RetentionCalendar.AUDIT_ZONE).toLocalDate().toString();
        var report=json(adminPost("/api/v1/reports/services/emissions",Map.of("operationKey",UUID.randomUUID(),"from",from,"to",from)).andExpect(status().isOk()));UUID id=UUID.fromString(report.path("id").asText());
        jdbc.update("insert into pilot_lifecycle values('PILOT',?,?,?,?)",ts(initial.minus(Duration.ofDays(100))),ts(initial.minus(Duration.ofDays(93))),technical().getId(),ts(initial));
        mvc.perform(get("/api/v1/reports/services/emissions/"+id).header("Authorization","Bearer "+adminToken)).andExpect(status().isGone());
        var p=plan(id,organization.getId(),"REPORT_EMISSION");assertThat(execute(p,initial)).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from report_emission_content where emission_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select content_sha256 from report_emission where id=?",String.class,id)).isEqualTo(report.path("contentSha256").asText());assertThat(jdbc.queryForObject("select count(*) from report_emission_retirement where emission_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void retentionTechnicalServiceRequiresAuthenticatedRoleReauthenticationAndClearsPassword(){
        UUID old=event(organization.getId(),initial.minus(Duration.ofDays(300)));context(admin);var plan=procedure.requestPlan(UUID.randomUUID(),RetentionProcedure.Category.OPERATIONAL_AUDIT,List.of(old),UUID.randomUUID(),"SYNTHETIC_EXACT_CASE","Synthetic-L02-Password-2026".toCharArray());
        char[] wrongRole=syntheticPassword.toCharArray();assertThatThrownBy(()->procedure.execute(plan.id(),plan.selectionSha256(),wrongRole)).isInstanceOf(ApplicationException.class);assertThat(wrongRole).containsOnly('\0');
        context(technical());char[] wrongPassword="Synthetic-wrong-password".toCharArray();assertThatThrownBy(()->procedure.execute(plan.id(),plan.selectionSha256(),wrongPassword)).isInstanceOf(ApplicationException.class);assertThat(wrongPassword).containsOnly('\0');
        // Successful reauthentication uses an isolated unit fixture, not the unknown owner secret.
        assertThat(exists(old)).isTrue();
    }
    @Test void retentionBoundedVolumeRetiresOneThousandExactSyntheticEventsAndNoOthers(){
        var selected=new ArrayList<UUID>();Instant expired=initial.minus(Duration.ofDays(300));
        jdbc.batchUpdate("insert into audit_event(id,organization_id,user_id,action,entity_type,metadata_json,occurred_at) values(?,?,null,'SYNTHETIC_RETENTION_VOLUME','SYNTHETIC','{}'::jsonb,?)",new org.springframework.jdbc.core.BatchPreparedStatementSetter(){
            @Override public int getBatchSize(){return 1000;}
            @Override public void setValues(java.sql.PreparedStatement p,int i)throws java.sql.SQLException{UUID id=UUID.randomUUID();selected.add(id);p.setObject(1,id);p.setObject(2,organization.getId());p.setTimestamp(3,ts(expired));}
        });
        UUID keep=event(organization.getId(),initial),plan=UUID.randomUUID();String ids="{"+String.join(",",selected.stream().map(UUID::toString).toList())+"}";
        jdbc.queryForObject("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.class,plan,"OPERATIONAL_AUDIT",organization.getId(),admin.getId(),UUID.randomUUID(),"SYNTHETIC_VOLUME_BOUNDARY",ids,ts(initial));
        String sha=jdbc.queryForObject("select selection_sha256 from retention_plan where id=?",String.class,plan);
        assertThat(execute(new String[]{plan.toString(),sha},initial)).isEqualTo(1000);assertThat(exists(keep)).isTrue();assertThat(jdbc.queryForObject("select count(*) from audit_event where organization_id=? and action='SYNTHETIC_RETENTION_VOLUME'",Integer.class,organization.getId())).isZero();
        String tooMany="{"+String.join(",",java.util.stream.IntStream.range(0,1001).mapToObj(i->UUID.randomUUID().toString()).toList())+"}";
        assertThat(rejectedSqlState("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.randomUUID(),"OPERATIONAL_AUDIT",organization.getId(),admin.getId(),UUID.randomUUID(),"SYNTHETIC_VOLUME_BOUNDARY",tooMany,ts(initial))).isEqualTo("23514");
    }
    private static Timestamp ts(Instant at){return Timestamp.from(at);}
    protected String rejectedSqlState(String sql,Object... args){
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->{
            var savepoint=c.setSavepoint();
            try(var p=c.prepareStatement(sql)){
                for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);
                try{p.execute();throw new AssertionError("SYNTHETIC_EXPECTED_DATABASE_REJECTION");}
                catch(java.sql.SQLException failure){c.rollback(savepoint);return failure.getSQLState();}
            }finally{c.releaseSavepoint(savepoint);}
        });
    }
}
