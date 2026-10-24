package pe.rutafija.retention;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.support.L02PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Real PostgreSQL, synthetic fixture inside one rollback transaction; no global
 * cleanup, persisted account, SMTP, production data or owner credential change.
 * Ordering/stale/replay checks here are not a claim of parallel HTTP-race testing. */
@SpringBootTest(properties={"debug=false","logging.level.root=ERROR","app.retention.technical-enabled=true"})
@ActiveProfiles("test")
class PersonalRetirementIntegrationTest extends L02PostgresIntegrationTest {
    @MockitoBean OtpEmailSender mail;
    private UUID org,foreignOrg,admin,conductor,driver,identity,request,invitation,revision,event,technical,acceptance,document;
    private Instant now,before,ended;
    private int ledgerBefore;
    private String commonHash;
    private record Plan(UUID id,String hash) { }
    @BeforeEach void syntheticRollbackFixture(){
        now=Instant.now().truncatedTo(ChronoUnit.MICROS);before=now.minus(Duration.ofHours(1));ended=now.minus(Duration.ofDays(30));
        technical=jdbc.queryForObject("select id from app_user where role='SUPER_ADMIN' and active and email_verified_at is not null",UUID.class);
        ledgerBefore=jdbc.queryForObject("select count(*) from pilot_account_creation",Integer.class);
        org=UUID.randomUUID();foreignOrg=UUID.randomUUID();admin=UUID.randomUUID();conductor=UUID.randomUUID();driver=UUID.randomUUID();identity=UUID.randomUUID();request=UUID.randomUUID();invitation=UUID.randomUUID();revision=UUID.randomUUID();event=UUID.randomUUID();
        for(UUID o:List.of(org,foreignOrg))jdbc.update("insert into organization(id,legal_name,status,timezone,created_at,updated_at) values(?,?,'ACTIVE','America/Lima',?,?)",o,"SYNTHETIC RETIREMENT "+o,ts(ended),ts(ended));
        account(admin,"ADMIN",true);account(conductor,"CONDUCTOR",false);
        UUID group=UUID.randomUUID();jdbc.update("insert into transport_group(id,organization_id,name,created_at,updated_at) values(?,?,'SYNTHETIC',?,?)",group,org,ts(ended),ts(ended));
        jdbc.update("""
            insert into driver(id,organization_id,user_id,group_id,full_name,phone,document_type,document_number,license_number,active,created_at,updated_at)
            values(?,?,?,?,'SYNTHETIC PERSON','000000000','DNI',?,'SYNTHETIC-LICENSE',false,?,?)
            """,driver,org,conductor,group,"7"+String.format(Locale.ROOT,"%07d",Math.floorMod(UUID.randomUUID().hashCode(),10_000_000)),ts(ended),ts(ended));
        jdbc.update("insert into conductor_onboarding_identity(id,email,password_hash,full_name,phone,email_verified_at,created_at,updated_at) values(?,?,'!SYNTHETIC-NOT-LOGIN','SYNTHETIC PERSON','000000000',?,?,?)",identity,"preliminary."+identity+"@rutafija.test",ts(ended),ts(ended),ts(ended));
        invitation(invitation,org);request(request,invitation,"APPROVED",true);
        jdbc.update("""
            insert into conductor_document_revision(id,request_id,requirement_id,revision_no,fields,expiry_mode,applicability,applicability_condition,source_reference,result,review_reference,observation,reviewer_id,actor_kind,recorded_at,catalog_id,catalog_version,catalog_hash)
            values(?,?,'CON-01',1,'{"documentNumber":"SYNTHETIC-NOT-REAL"}'::jsonb,'NO_EXPIRY','CONFIRMED','SYNTHETIC CONDITION','SYNTHETIC SOURCE','CONFORME','SYNTHETIC CHECK','SYNTHETIC OBSERVATION',?,'ADMIN',?,'L01-CHICLAYO','0.1.0',?)
            """,revision,request,admin,ts(ended),"a".repeat(64));
        jdbc.update("""
            insert into conductor_onboarding_review_event(id,request_id,operation_key,payload_hash,action,actor_kind,actor_id,occurred_at,reason,review_round,resulting_version,catalog_id,catalog_version,catalog_hash)
            values(?,?,'SYNTHETIC-OPERATION',?,'APPROVE','ADMIN',?,?,'SYNTHETIC REASON',1,1,'L01-CHICLAYO','0.1.0',?)
            """,event,request,"b".repeat(64),admin,ts(ended),"a".repeat(64));
        jdbc.update("insert into driver_qualification_control(driver_id,organization_id,suspension_reference,updated_at) values(?,?,'SYNTHETIC REFERENCE',?)",driver,org,ts(ended));
        jdbc.update("""
            insert into qualification_review(id,organization_id,driver_id,scope,requirement_id,fields,expiry_mode,applicability,source_reference,result,review_reference,version,reviewer_id,recorded_at)
            values(?,?,?,'COMPANY','EMP-01','{"holder":"SYNTHETIC HOLDER"}'::jsonb,'NO_EXPIRY','CONFIRMED','SYNTHETIC SOURCE','CONFORME','SYNTHETIC REVIEW',1,?,?)
            """,UUID.randomUUID(),org,driver,admin,ts(ended));
        commonHash=sha(UUID.randomUUID().toString());
        jdbc.update("insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at) values(?,?,'DNI',?,'CONDUCTOR_DOSSIER','SYNTHETIC CHECK',?)",commonHash,conductor,admin,ts(ended));
        jdbc.update("insert into conductor_person_binding(person_hash,identity_id,organization_id,user_id,driver_id,bound_at) values(?,?,?,?,?,?)",commonHash,identity,org,conductor,driver,ts(ended));
        jdbc.update("insert into conductor_common_person_bridge values(?,?)",commonHash,commonHash);
        document=jdbc.queryForObject("select id from admin_terms_document where document_key='ADMIN_TERMS' and version='1.0.0' and not synthetic",UUID.class);
        acceptance=UUID.randomUUID();jdbc.update("insert into admin_terms_acceptance(id,admin_user_id,organization_id,document_id,document_version,content_sha256,accepted_at) select ?,?,?,id,version,content_sha256,? from admin_terms_document where id=?",acceptance,admin,org,ts(ended),document);
        jdbc.update("insert into pilot_lifecycle values('PILOT',?,?,?,?)",ts(ended.minus(Duration.ofDays(7))),ts(ended),technical,ts(ended));
    }
    private void account(UUID id,String role,boolean active){jdbc.update("insert into app_user(id,organization_id,email,password_hash,full_name,phone,role,active,email_verified_at,created_at,updated_at) values(?,?,?,'!SYNTHETIC-NOT-LOGIN','SYNTHETIC PERSON','000000000',?,?,?,?,?)",id,org,"account."+id+"@rutafija.test",role,active,ts(ended),ts(ended),ts(ended));}
    private void invitation(UUID id,UUID owner){jdbc.update("insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at,used_at) values(?,?,?,?,?,?,?,?)",id,owner,admin,"preliminary."+identity+"@rutafija.test",sha(id.toString()),ts(ended.minusSeconds(60)),ts(ended.plusSeconds(3600)),ts(ended));}
    private void request(UUID id,UUID invite,String status,boolean linked){jdbc.update("""
        insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,status,submitted_at,reviewed_by_admin_id,reviewed_at,personal_approved_at,linked_user_id,linked_driver_id,provisioned_at)
        values(?,?,?,?,?,?,?, ?,?,?,?,?,?)
        """,id,identity,org,invite,"SYNTHETIC-"+id,status,ts(ended),admin,ts(ended),linked?ts(ended):null,linked?conductor:null,linked?driver:null,linked?ts(ended):null);}
    private Plan plan(String category,UUID... ids){UUID id=UUID.randomUUID();jdbc.queryForObject("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.class,id,category,org,admin,UUID.randomUUID(),"SYNTHETIC_EXACT_RETIREMENT",array(ids),ts(before));return read(id);}
    private Plan read(UUID id){return new Plan(id,jdbc.queryForObject("select selection_sha256 from retention_plan where id=?",String.class,id));}
    private int execute(Plan p,Instant at){return jdbc.queryForObject("select retention_execute_plan(?,?,?,?)",Integer.class,p.id,p.hash,technical,ts(at));}
    private String executeRejected(Plan p,Instant at){return rejected("select retention_execute_plan(?,?,?,?)",p.id,p.hash,technical,ts(at));}
    private int count(String table,String where,Object... args){return jdbc.queryForObject("select count(*) from "+table+" where "+where,Integer.class,args);}
    private static String array(UUID... ids){return "{"+String.join(",",Arrays.stream(ids).map(UUID::toString).toList())+"}";}
    private static Timestamp ts(Instant at){return Timestamp.from(at);}
    private static String sha(String value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private String rejected(String sql,Object... args){return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)c->{var savepoint=c.setSavepoint();try(var q=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);try{q.execute();throw new AssertionError("SYNTHETIC_EXPECTED_REJECTION");}catch(java.sql.SQLException failure){c.rollback(savepoint);return failure.getSQLState();}}finally{c.releaseSavepoint(savepoint);}});}

    @Test void documentaryRetirementHonorsExactDeadlineAndScrubsStructuredHistoryWithoutDeletingItsIds(){
        Plan p=plan("DOCUMENTARY_CASE",request);assertThat(executeRejected(p,now.minusNanos(1000))).isEqualTo("23514");
        assertThat(execute(p,now)).isEqualTo(1);assertThat(execute(p,now.plusSeconds(1))).isEqualTo(1);
        var d=jdbc.queryForMap("select fields::text fields,revision_no,result,source_reference,review_reference,observation from conductor_document_revision where id=?",revision);
        assertThat(d.get("fields")).isEqualTo("{}");assertThat(d.get("revision_no")).isEqualTo(1L);assertThat(d.get("result")).isEqualTo("CONFORME");
        assertThat(d.get("source_reference")).isNull();assertThat(d.get("review_reference")).isNull();assertThat(d.get("observation")).isNull();
        assertThat(jdbc.queryForObject("select reason from conductor_onboarding_review_event where id=?",String.class,event)).isNull();
        assertThat(jdbc.queryForObject("select full_name from conductor_onboarding_identity where id=?",String.class,identity)).isEqualTo("RETIRED");
        assertThat(jdbc.queryForObject("select recipient_email from organization_invitation where id=?",String.class,invitation)).endsWith("@invalid.example");
        assertThat(jdbc.queryForObject("select fields::text from qualification_review where driver_id=?",String.class,driver)).isEqualTo("{}");
        assertThat(count("retention_execution","plan_id=?",p.id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pilot_account_creation",Integer.class)).isEqualTo(ledgerBefore+2);
    }
    @Test void rawDocumentaryIdentityAndTermsMutationsCannotMintAnAuthorityOrUseASessionFlag(){
        jdbc.queryForObject("select set_config('ruta_fija.retention_bypass','true',true)",String.class);
        assertThat(rejected("update conductor_document_revision set fields='{}'::jsonb where id=?",revision)).isEqualTo("23514");
        assertThat(rejected("delete from person_identity where user_id=?",conductor)).isEqualTo("23514");
        assertThat(rejected("delete from admin_terms_acceptance where id=?",acceptance)).isEqualTo("23514");
        assertThat(rejected("select retention_personal_fingerprint(?,?,?)","DOCUMENTARY_CASE",request,org)).isEqualTo("42501");
        assertThat(rejected("insert into retention_personal_row_authority values(?,'conductor_document_revision',?,'UPDATE',1,1)",UUID.randomUUID(),revision.toString())).isEqualTo("42501");
    }
    @Test void documentChangeAfterApprovalMakesThePlanStaleAtomically(){
        Plan p=plan("DOCUMENTARY_CASE",request);jdbc.update("update conductor_onboarding_identity set phone='111111111' where id=?",identity);
        assertThat(executeRejected(p,now)).isEqualTo("23514");assertThat(count("retention_execution","plan_id=?",p.id)).isZero();
        assertThat(jdbc.queryForObject("select fields::text from conductor_document_revision where id=?",String.class,revision)).contains("SYNTHETIC-NOT-REAL");
    }
    @Test void documentarySelectionRejectsIncompleteSharedIdentityButAllowsAllSameTenantRequests(){
        UUID otherRequest=UUID.randomUUID(),otherInvite=UUID.randomUUID();invitation(otherInvite,org);request(otherRequest,otherInvite,"REJECTED",false);
        assertThat(rejected("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.randomUUID(),"DOCUMENTARY_CASE",org,admin,UUID.randomUUID(),"SYNTHETIC_SHARED_IDENTITY",array(request),ts(before))).isEqualTo("23514");
        Plan complete=plan("DOCUMENTARY_CASE",request,otherRequest);assertThat(execute(complete,now)).isEqualTo(2);
    }
    @Test void aNewUnselectedRequestAfterPreparationIsRejectedRatherThanPurgedByInference(){
        Plan p=plan("DOCUMENTARY_CASE",request);UUID id=UUID.randomUUID(),invite=UUID.randomUUID();invitation(invite,org);request(id,invite,"REJECTED",false);
        assertThat(executeRejected(p,now)).isEqualTo("23514");assertThat(count("retention_execution","plan_id=?",p.id)).isZero();
    }
    @Test void foreignOrganizationSelectionAndUnapprovedOwnerCustodyAreRejected(){
        assertThat(rejected("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.randomUUID(),"DOCUMENTARY_CASE",foreignOrg,admin,UUID.randomUUID(),"SYNTHETIC_FOREIGN_SCOPE",array(request),ts(before))).isEqualTo("42501");
        assertThat(rejected("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.randomUUID(),"ADMIN_CONDUCTOR_PII",null,technical,UUID.randomUUID(),"SYNTHETIC_OWNER_EXCLUDED",array(technical),ts(before))).isEqualTo("23514");
    }
    @Test void activeAccountCannotBeRetiredAndDeactivationAfterPreparationRequiresAFreshFingerprint(){
        Plan p=plan("ADMIN_CONDUCTOR_PII",admin);assertThat(executeRejected(p,now)).isEqualTo("23514");
        jdbc.update("update app_user set active=false where id=?",admin);assertThat(executeRejected(p,now)).isEqualTo("23514");
    }
    @Test void adminDelegatesBeforeDeactivationAndTechnicalActorPreparesAfterwardWithoutReactivation(){
        UUID delegation=UUID.randomUUID();jdbc.queryForObject("select retention_delegate_personal_selection(?,?,?,?,?,?,?::uuid[],?)",UUID.class,delegation,"ADMIN_CONDUCTOR_PII",org,admin,UUID.randomUUID(),"SYNTHETIC_PRE_CLOSURE_DELEGATION",array(admin),ts(before));
        jdbc.update("update app_user set active=false where id=?",admin);
        UUID plan=UUID.randomUUID();jdbc.queryForObject("select retention_prepare_delegated_personal_plan(?,?,?,?)",UUID.class,plan,delegation,technical,ts(before.plusSeconds(1)));
        assertThat(execute(read(plan),now)).isEqualTo(1);assertThat(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,admin)).isFalse();
        assertThat(jdbc.queryForObject("select email from app_user where id=?",String.class,admin)).endsWith("@invalid.example");
        assertThat(rejected("update app_user set active=true where id=?",admin)).isEqualTo("23514");
        assertThat(count("admin_terms_acceptance","id=?",acceptance)).isEqualTo(1);
    }
    @Test void accountRetirementPreservesLedgerAndOtherPurposeTermsUntilTheirOwnDeadline(){
        Plan p=plan("ADMIN_CONDUCTOR_PII",conductor);assertThat(execute(p,now)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select document_number from driver where id=?",String.class,driver)).startsWith("RETIRED-");
        assertThat(jdbc.queryForObject("select password_hash from app_user where id=?",String.class,conductor)).startsWith("!RETIRED-");
        assertThat(count("pilot_account_creation","user_id=?",conductor)).isEqualTo(1);assertThat(count("admin_terms_acceptance","id=?",acceptance)).isEqualTo(1);
    }
    @Test void hmacRetirementRemovesOnlySelectedPersonalLinksAndLeavesGlobalKeyUntouched(){
        String key=jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1",String.class);
        Plan p=plan("PERSON_HMAC_LINKS",conductor);assertThat(executeRejected(p,now.minusNanos(1000))).isEqualTo("23514");assertThat(execute(p,now)).isEqualTo(1);
        assertThat(count("person_identity","user_id=?",conductor)).isZero();assertThat(count("conductor_person_binding","identity_id=?",identity)).isZero();
        assertThat(count("conductor_common_person_bridge","person_hash=?",commonHash)).isZero();
        assertThat(jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1",String.class)).isEqualTo(key);
        assertThat(rejected("insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at) values(?,?,'DNI',?,'CONDUCTOR_DOSSIER','SYNTHETIC REPOPULATION',?)",commonHash,conductor,admin,ts(now))).isEqualTo("23514");
    }
    @Test void acceptanceRetirementUses90DaysAndNeverChangesPublishedDocument(){
        UUID delegation=UUID.randomUUID();jdbc.queryForObject("select retention_delegate_personal_selection(?,?,?,?,?,?,?::uuid[],?)",UUID.class,delegation,"TERMS_ACCEPTANCE",org,admin,UUID.randomUUID(),"SYNTHETIC_TERMS_RETIREMENT",array(acceptance),ts(before));
        jdbc.update("update app_user set active=false where id=?",admin);UUID id=UUID.randomUUID();jdbc.queryForObject("select retention_prepare_delegated_personal_plan(?,?,?,?)",UUID.class,id,delegation,technical,ts(before));Plan p=read(id);
        Instant due=ended.plus(Duration.ofDays(90));assertThat(executeRejected(p,due.minusNanos(1000))).isEqualTo("23514");assertThat(execute(p,due)).isEqualTo(1);assertThat(count("admin_terms_acceptance","id=?",acceptance)).isZero();
        assertThat(jdbc.queryForObject("select content_sha256 from admin_terms_document where id=?",String.class,document)).isEqualTo("20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417");
    }
    private UUID recovery(String status,Instant finish){UUID id=UUID.randomUUID();jdbc.update("""
        insert into conductor_recovery_request(id,organization_id,conductor_user_id,authorized_by_admin_id,previous_email,requested_email,evidence_method,evidence_reference,reason,expediente_reviewed,independent_evidence_confirmed,password_reset_required,status,completed_at,created_at,updated_at)
        values(?,?,?,?,'previous.synthetic@rutafija.test','new.synthetic@rutafija.test','IN_PERSON','SYNTHETIC CHECK','SYNTHETIC REASON',true,true,true,?,?,?,?)
        """,id,org,conductor,admin,status,finish==null?null:ts(finish),ts(ended.minusSeconds(3600)),ts(ended));return id;}
    @Test void conductorRecoveryEvidenceExpiresFromActualFinishAndUnknownAnchorCannotBeInferred(){
        UUID id=recovery("COMPLETED",ended);Plan p=plan("RECOVERY_CASE_MINIMUM",id);assertThat(executeRejected(p,now.minusNanos(1000))).isEqualTo("23514");assertThat(execute(p,now)).isEqualTo(1);assertThat(count("conductor_recovery_request","id=?",id)).isZero();
        UUID pending=recovery("OTP_PENDING",null);assertThat(rejected("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.randomUUID(),"RECOVERY_CASE_MINIMUM",org,admin,UUID.randomUUID(),"SYNTHETIC_UNKNOWN_ANCHOR",array(pending),ts(before))).isEqualTo("23514");
    }
    @Test void cancellationRecordsTheActualTransitionAndDoesNotInventAnEarlierFinish(){
        UUID id=recovery("OTP_PENDING",null);jdbc.update("update conductor_recovery_request set status='CANCELLED' where id=?",id);
        Instant recorded=jdbc.queryForObject("select cancelled_at from conductor_recovery_request where id=?",Timestamp.class,id).toInstant();assertThat(recorded).isNotNull();assertThat(recorded).isAfter(ended);
        assertThat(rejected("update conductor_recovery_request set cancelled_at=created_at where id=?",id)).isEqualTo("23514");
    }
    @Test void consumedOwnerChallengeCanRetireWithoutDeletingOrRotatingEnrolledCredential(){
        String credentialBefore=jdbc.queryForObject("select encode(sha256(convert_to(to_jsonb(c)::text,'UTF8')),'hex') from super_admin_recovery_credential c where user_id=?",String.class,technical);
        UUID challenge=UUID.randomUUID();jdbc.update("""
            insert into super_admin_recovery_challenge(id,user_id,installation_id,account_reference,requested_email,nonce,expires_at,proof_verified_at,consumed_at,created_at,updated_at)
            values(?,?,?,?,'owner.synthetic@rutafija.test',?,?,?, ?,?,?)
            """,challenge,technical,UUID.randomUUID(),UUID.randomUUID(),Base64.getUrlEncoder().withoutPadding().encodeToString(UUID.randomUUID().toString().substring(0,32).getBytes(java.nio.charset.StandardCharsets.US_ASCII)),ts(ended.plusSeconds(300)),ts(ended),ts(ended),ts(ended.minusSeconds(60)),ts(ended));
        UUID id=UUID.randomUUID();jdbc.queryForObject("select retention_prepare_plan(?,?,?,?,?,?,?::uuid[],?)",UUID.class,id,"RECOVERY_CASE_MINIMUM",null,technical,UUID.randomUUID(),"SYNTHETIC_CONSUMED_OWNER_CASE",array(challenge),ts(before));
        Plan p=read(id);assertThat(executeRejected(p,now.minusNanos(1000))).isEqualTo("23514");assertThat(execute(p,now)).isEqualTo(1);assertThat(count("super_admin_recovery_challenge","id=?",challenge)).isZero();
        assertThat(jdbc.queryForObject("select encode(sha256(convert_to(to_jsonb(c)::text,'UTF8')),'hex') from super_admin_recovery_credential c where user_id=?",String.class,technical)).isEqualTo(credentialBefore);
    }
    @Test void approvedExactPurposeHoldChangesDueWithoutInventingAUniversalMaximum(){
        UUID hold=UUID.randomUUID();Instant until=now.plus(Duration.ofDays(45));Plan stale=plan("DOCUMENTARY_CASE",request);
        jdbc.queryForObject("select retention_request_personal_hold(?,?,?,?,?,?,?,?,?)",UUID.class,hold,"DOCUMENTARY_CASE",request,org,admin,UUID.randomUUID(),"SYNTHETIC_EXACT_EXTERNAL_CASE",ts(until),ts(before));
        jdbc.queryForObject("select retention_approve_personal_hold(?,?,?)",UUID.class,hold,technical,ts(before));assertThat(executeRejected(stale,until)).isEqualTo("23514");
        Plan p=plan("DOCUMENTARY_CASE",request);assertThat(executeRejected(p,until.minusNanos(1000))).isEqualTo("23514");assertThat(execute(p,until)).isEqualTo(1);
    }
    @Test void personalCopyKeepsItsOriginalExpiryAndRequiresRetirementBeforeAnyLateRestore(){
        UUID copy=UUID.randomUUID();jdbc.queryForObject("select retention_register_copy(?,?,?,?,?,?,?)",UUID.class,copy,"DOCUMENTARY_CASE",request,UUID.randomUUID(),technical,ts(before),true);
        assertThat(jdbc.queryForObject("select expires_at from retention_controlled_copy where id=?",Timestamp.class,copy).toInstant()).isEqualTo(now);
        assertThat(jdbc.queryForObject("select retention_copy_restore_ready(?,?)",Boolean.class,copy,ts(now))).isFalse();
    }
    @Test void successorInventoryConservesTheOriginalUnimplementedCatalogAsHistory(){
        assertThat(jdbc.queryForObject("select count(*) from retention_personal_capability where execution_capability='EXACT_PLAN'",Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select execution_capability from retention_policy_catalog where category='DOCUMENTARY_CASE'",String.class)).isEqualTo("INVENTORIED_NOT_IMPLEMENTED");
    }
}
