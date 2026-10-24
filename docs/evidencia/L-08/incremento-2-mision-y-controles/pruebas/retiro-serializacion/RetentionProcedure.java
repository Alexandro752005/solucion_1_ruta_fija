package pe.rutafija.retention.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.domain.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Local technical procedure. There is deliberately no normal HTTP purge endpoint or timer. */
@Service
public class RetentionProcedure {
    public enum Category {
        OPERATIONAL_AUDIT, REPORT_EMISSION, DOCUMENTARY_CASE, ADMIN_CONDUCTOR_PII,
        PERSON_HMAC_LINKS, TERMS_ACCEPTANCE, RECOVERY_CASE_MINIMUM
    }
    public record Plan(UUID id,Category category,UUID organizationId,String selectionSha256,int itemCount,String policyVersion) { }
    public record Result(UUID planId,int retiredCount,Instant executedAt,boolean replay) { }
    private final JdbcTemplate jdbc;private final CurrentUserService users;private final PasswordEncoder passwords;
    private final LoginRateLimiter limiter;private final AuditService audit;private final Clock clock;
    private final pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager identity;
    private final pe.rutafija.terms.application.AdminTermsService terms;
    private final boolean technicalEnabled;
    public RetentionProcedure(JdbcTemplate jdbc,CurrentUserService users,PasswordEncoder passwords,
        LoginRateLimiter limiter,AuditService audit,Clock clock,
        pe.rutafija.personidentity.application.PersonIdentityGateAuthorizationManager identity,
        pe.rutafija.terms.application.AdminTermsService terms,@Value("${app.retention.technical-enabled:false}") boolean technicalEnabled){
        this.jdbc=jdbc;this.users=users;this.passwords=passwords;this.limiter=limiter;
        this.audit=audit;this.clock=clock;this.technicalEnabled=technicalEnabled;
        this.identity=identity;this.terms=terms;
    }
    private AppUser admin(){var a=users.requireTenantActor();if(a.getRole()!=UserRole.ADMIN||!identity.allowsUser(a.getId())||!terms.hasCurrentAcceptanceForCurrentActor())throw denied();return a;}
    private AppUser technical(char[] password){
        try{
            if(!technicalEnabled)throw denied();
            var a=users.requireSuperAdminActor();
            if(!identity.allowsUser(a.getId()))throw denied();
            String key="retention-technical:"+a.getId();limiter.checkAndRecordAttempt(key);
            if(password==null||!passwords.matches(new String(password),a.getPasswordHash()))throw new InvalidCredentialsException();
            limiter.recordSuccess(key);return a;
        }finally{if(password!=null)Arrays.fill(password,'\0');}
    }
    private void reauthenticateAdmin(AppUser a,char[] password){
        try{
            String key="retention-request:"+a.getId();limiter.checkAndRecordAttempt(key);
            if(password==null||!passwords.matches(new String(password),a.getPasswordHash()))throw new InvalidCredentialsException();
            limiter.recordSuccess(key);
        }finally{if(password!=null)Arrays.fill(password,'\0');}
    }
    private AppUser requester(char[] password){try{var a=admin();reauthenticateAdmin(a,password);return a;}finally{if(password!=null)Arrays.fill(password,'\0');}}
    @Transactional public UUID requestHold(UUID requestId,UUID eventId,UUID caseReference,String reasonCode,Instant until,char[] password){
        var a=requester(password);code(reasonCode);Objects.requireNonNull(until);
        UUID id=jdbc.queryForObject("select retention_request_hold(?,?,?,?,?,?,?)",UUID.class,requestId,eventId,a.getId(),caseReference,reasonCode,ts(until),ts(clock.instant()));
        audit.record(a,"RETENTION_HOLD_REQUESTED","AUDIT_EVENT",eventId,Map.of("requestId",id,"caseId",caseReference,"expiresAt",until));return id;
    }
    @Transactional public UUID approveHold(UUID requestId,char[] password){
        var a=technical(password);UUID id=jdbc.queryForObject("select retention_approve_hold(?,?,?)",UUID.class,requestId,a.getId(),ts(clock.instant()));
        // Minimal platform decision only; neither underlying tenant content nor its personal data is loaded.
        audit.record(a,"RETENTION_HOLD_APPROVED","RETENTION_HOLD",id,Map.of("requestId",id));return id;
    }
    @Transactional public Plan requestPlan(UUID planId,Category category,List<UUID> resourceIds,UUID caseReference,String reasonCode,char[] password){
        var a=requester(password);return prepare(a,planId,category,a.getOrganizationId(),resourceIds,caseReference,reasonCode);
    }
    /** ADMIN identifies exact resources before deactivation; no dossier content is returned. */
    @Transactional public UUID delegatePersonalSelection(UUID delegationId,Category category,List<UUID> resourceIds,UUID caseReference,String reasonCode,char[] password){
        var a=requester(password);personal(category);code(reasonCode);Objects.requireNonNull(delegationId);Objects.requireNonNull(caseReference);
        String selected=selection(resourceIds);
        UUID id=jdbc.queryForObject("select retention_delegate_personal_selection(?,?,?,?,?,?,?::uuid[],?)",UUID.class,
            delegationId,category.name(),a.getOrganizationId(),a.getId(),caseReference,reasonCode,selected,ts(clock.instant()));
        audit.record(a,"RETENTION_PERSONAL_SELECTION_DELEGATED","RETENTION_DELEGATION",id,Map.of("delegationId",id,"category",category.name(),"itemCount",resourceIds.size(),"caseId",caseReference));
        return id;
    }
    /** Technical actor prepares only a previously delegated selection after ADMIN's closure. */
    @Transactional public Plan requestDelegatedPersonalPlan(UUID planId,UUID delegationId,char[] password){
        var a=technical(password);Objects.requireNonNull(planId);Objects.requireNonNull(delegationId);
        jdbc.queryForObject("select retention_prepare_delegated_personal_plan(?,?,?,?)",UUID.class,planId,delegationId,a.getId(),ts(clock.instant()));
        var plan=readPlan(planId);
        audit.record(a,"RETENTION_DELEGATED_PLAN_PREPARED","RETENTION_PLAN",planId,Map.of("planId",planId,"delegationId",delegationId,"category",plan.category().name(),"itemCount",plan.itemCount()));
        return plan;
    }
    @Transactional public Plan requestPlatformAuditPlan(UUID planId,List<UUID> resourceIds,UUID caseReference,String reasonCode,char[] password){
        var a=technical(password);return prepare(a,planId,Category.OPERATIONAL_AUDIT,null,resourceIds,caseReference,reasonCode);
    }
    /** Consumed recovery evidence only. This never selects the owner's enrolled credential or custody. */
    @Transactional public Plan requestPlatformRecoveryPlan(UUID planId,List<UUID> resourceIds,UUID caseReference,String reasonCode,char[] password){
        var a=technical(password);return prepare(a,planId,Category.RECOVERY_CASE_MINIMUM,null,resourceIds,caseReference,reasonCode);
    }
    @Transactional public UUID requestPersonalHold(UUID requestId,Category category,UUID resourceId,UUID caseReference,String purposeCode,Instant until,char[] password){
        var a=requester(password);code(purposeCode);Objects.requireNonNull(category);Objects.requireNonNull(until);
        personal(category);
        UUID id=jdbc.queryForObject("select retention_request_personal_hold(?,?,?,?,?,?,?,?,?)",UUID.class,
            requestId,category.name(),resourceId,a.getOrganizationId(),a.getId(),caseReference,purposeCode,ts(until),ts(clock.instant()));
        audit.record(a,"RETENTION_PERSONAL_HOLD_REQUESTED","RETENTION_HOLD",id,Map.of("requestId",id,"caseId",caseReference,"category",category.name()));return id;
    }
    @Transactional public UUID approvePersonalHold(UUID requestId,char[] password){
        var a=technical(password);UUID id=jdbc.queryForObject("select retention_approve_personal_hold(?,?,?)",UUID.class,requestId,a.getId(),ts(clock.instant()));
        audit.record(a,"RETENTION_PERSONAL_HOLD_APPROVED","RETENTION_HOLD",id,Map.of("requestId",id));return id;
    }
    private Plan prepare(AppUser a,UUID planId,Category category,UUID organization,List<UUID> ids,UUID caseReference,String reasonCode){
        code(reasonCode);Objects.requireNonNull(category);Objects.requireNonNull(caseReference);Objects.requireNonNull(planId);
        String array=selection(ids);
        jdbc.queryForObject("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.class,planId,category.name(),organization,a.getId(),caseReference,reasonCode,array,ts(clock.instant()));
        var plan=readPlan(planId);audit.record(a,"RETENTION_PLAN_REQUESTED","RETENTION_PLAN",planId,Map.of("planId",planId,"itemCount",plan.itemCount(),"caseId",caseReference));return plan;
    }
    @Transactional public Result execute(UUID planId,String approvedSelectionSha256,char[] password){
        var a=technical(password);
        if(approvedSelectionSha256==null||!approvedSelectionSha256.matches("[a-f0-9]{64}"))throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,"Confirma la huella exacta del plan.");
        // Serialize Java callers before reading replay state, without requiring
        // UPDATE on immutable retention_plan. This transaction-scoped, negative
        // key cannot equal the positive fixture guard 7518440514. A hash collision
        // only serializes additional plans. Direct SQL callers retain their own
        // function-level FOR UPDATE protocol; this is not a new SQL-client gate.
        jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended('ruta_fija.retention.execute:' || ?::text,0) | '-9223372036854775808'::bigint)",Object.class,Objects.requireNonNull(planId));
        boolean replay=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from retention_execution where plan_id=?)",Boolean.class,planId));
        int count=Objects.requireNonNull(jdbc.queryForObject("select retention_execute_plan(?,?,?,?)",Integer.class,planId,approvedSelectionSha256,a.getId(),ts(clock.instant())));
        Instant at=Objects.requireNonNull(jdbc.queryForObject("select executed_at from retention_execution where plan_id=?",Timestamp.class,planId)).toInstant();
        if(!replay)audit.record(a,"RETENTION_PLAN_EXECUTED","RETENTION_PLAN",planId,Map.of("planId",planId,"retiredCount",count));
        return new Result(planId,count,at,replay);
    }
    /** Caller sees only its exact submitted selection; technical execution does not expose audit bodies. */
    private Plan readPlan(UUID id){return jdbc.queryForObject("select * from retention_plan where id=?",(r,n)->new Plan(r.getObject("id",UUID.class),Category.valueOf(r.getString("category")),r.getObject("organization_id",UUID.class),r.getString("selection_sha256"),r.getInt("item_count"),r.getString("policy_version")),id);}
    @Transactional(readOnly=true) public List<Map<String,Object>> policyInventory(){
        users.requireSuperAdminActor();
        return jdbc.queryForList("""
            select c.category,coalesce(p.policy_version,c.policy_version) policy_version,c.anchor,c.duration_description,
                   coalesce(p.execution_capability,c.execution_capability) execution_capability,
                   c.execution_capability predecessor_capability,p.limitation_code
              from retention_policy_catalog c left join retention_personal_capability p on p.category=c.category
             order by c.category
            """);
    }
    @Transactional public UUID registerSyntheticCopy(UUID copyId,Category category,UUID sourceId,UUID mediumReference,char[] password){
        var a=technical(password);return jdbc.queryForObject("select retention_register_copy(?,?,?,?,?,?,true)",UUID.class,copyId,category.name(),sourceId,mediumReference,a.getId(),ts(clock.instant()));
    }
    @Transactional public UUID attestSyntheticCopy(UUID copyId,String action,UUID procedureReference,char[] password){
        var a=technical(password);
        if(!Set.of("RETIRED","REAPPLIED_BEFORE_ACCESS").contains(action))throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,"Accion de copia no permitida.");
        Boolean synthetic=jdbc.queryForObject("select synthetic from retention_controlled_copy where id=?",Boolean.class,copyId);
        if(!Boolean.TRUE.equals(synthetic))throw denied();
        return jdbc.queryForObject("select retention_attest_copy(?,?,?,?,?,?)",UUID.class,UUID.randomUUID(),copyId,action,procedureReference,a.getId(),ts(clock.instant()));
    }
    @Transactional(readOnly=true) public boolean syntheticRestoreReady(UUID copyId){
        users.requireSuperAdminActor();return Boolean.TRUE.equals(jdbc.queryForObject("select retention_copy_restore_ready(?,?)",Boolean.class,copyId,ts(clock.instant())));
    }
    private static void code(String value){if(value==null||!value.matches("[A-Z][A-Z0-9_]{2,59}"))throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,"Usa un motivo codificado sin datos personales.");}
    private static void personal(Category category){
        if(category==null||category==Category.OPERATIONAL_AUDIT||category==Category.REPORT_EMISSION)
            throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,"La categoría conserva su procedimiento específico.");
    }
    private static String selection(List<UUID> ids){
        if(ids==null||ids.isEmpty()||ids.size()>1000||ids.stream().anyMatch(Objects::isNull)||new HashSet<>(ids).size()!=ids.size())
            throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,"Selecciona de 1 a 1000 referencias distintas.");
        return "{"+String.join(",",ids.stream().map(UUID::toString).sorted().toList())+"}";
    }
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
    private static ApplicationException denied(){return new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Procedimiento tecnico restringido no autorizado.");}
}
