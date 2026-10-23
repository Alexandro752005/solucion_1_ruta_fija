package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.JwtService;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

import static pe.rutafija.support.ManualL01HumanTrial.*;

/** Exact, opt-in REC-CI03-01. Real HTTP/PostgreSQL; only mail is simulated. Not a human trial. */
final class ManualL01AutomatedTermsComplement {
    static final UUID RUN = UUID.fromString("140c10a1-be0f-4d08-a6d4-c796e2ed107a");
    static final UUID OWNER = UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0");
    static final UUID ORG = UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
    static final UUID MANUAL_ADMIN = UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
    static final UUID MANUAL_ACCEPTANCE = UUID.fromString("c496b63c-735f-48cc-be56-90f7ec1464e1");
    static final String EMAIL = "admin.auto.gate.140c10a1@rutafija.test";
    static final String RESULT = "PASS_SC03_MANUAL_ACCEPTANCE_WITH_AUTOMATED_GATE";
    static final String REASON = "Falta la aceptacion expresa de los terminos aprobados actualmente vigentes.";
    static final String ADENDA = "docs/contratos/SC-L01-LOCAL-01-adenda-v1.1.0.md";
    static final String ADENDA_SHA = "b25e7f1e34a50c20daa6bdcfa21a33382b0a7d3b478068f3ea0003ed4ce8cc1b";
    static final String HUMAN_ADMIN = "docs/evidencia/L-01/ensayos-humanos-" + RUN + "/observacion-1792227077200.json";
    static final String HUMAN_ADMIN_SHA = "9319f045f65fd4ee61b9a07edec3000e5ea6ac0e75618d99e2c4896843b8a719";
    static final String HUMAN_RECOVERY = "docs/evidencia/L-01/ensayos-humanos-" + RUN + "/observacion-1792287275662.json";
    static final String HUMAN_RECOVERY_SHA = "e0a986d10e09f8fbb289061753b3e36759b0cae24e53731ede4bba8640d5fffb";
    static final Path FOLDER = ROOT.resolve("docs/evidencia/L-01/REC-CI03-01-automatizado-2026-10-17");
    static final Path JOURNAL = FOLDER.resolve("ejecucion-reanudable.json");
    static final Path PROOF = FOLDER.resolve("gate-http-postgresql.json");
    static final Path PRIVATE = ROOT.resolve("backend/.local/l01-ci03-automated-capabilities.private.json");
    final ManualL01HumanTrialServer trial;
    final JdbcTemplate jdbc;
    final Map<String,Object> proof;
    final Map<String,Object> secrets;
    final List<Map<String,Object>> steps;
    String ownerToken;

    ManualL01AutomatedTermsComplement(ManualL01HumanTrialServer trial) throws Exception {
        this.trial=trial; jdbc=trial.jdbc;
        proof=Files.exists(JOURNAL)?JSON.readValue(Files.readAllBytes(JOURNAL),FixtureJson.OBJECT_TYPE):new LinkedHashMap<>();
        secrets=Files.exists(PRIVATE)?JSON.readValue(Files.readAllBytes(PRIVATE),FixtureJson.OBJECT_TYPE):new HashMap<>();
        steps=FixtureJson.objects(proof.computeIfAbsent("steps",k->new ArrayList<>())); proof.put("steps",steps);
    }

    static void validateApprovedCase(UUID runId) throws Exception {
        require(RUN.equals(runId),"AUTOGATE_ONLY_APPROVED_RUN");
        require(ADENDA_SHA.equals(sha(ROOT.resolve(ADENDA))),"AUTOGATE_APPROVED_ADENDA_CHANGED");
        require(HUMAN_ADMIN_SHA.equals(sha(ROOT.resolve(HUMAN_ADMIN))),"AUTOGATE_HUMAN_ACCEPTANCE_ARCHIVE_CHANGED");
        require(HUMAN_RECOVERY_SHA.equals(sha(ROOT.resolve(HUMAN_RECOVERY))),"AUTOGATE_HUMAN_RECOVERY_ARCHIVE_CHANGED");
        var recovery=JSON.readValue(Files.readAllBytes(ROOT.resolve(HUMAN_RECOVERY)),FixtureJson.OBJECT_TYPE);
        require("PASS_LOCAL_TEMPORARY_COPY_ONLY".equals(recovery.get("manualTemporaryRecoveryResult"))
                && ManualL01HumanTrialServer.recoveryEvidenceComplete(recovery),"AUTOGATE_EXISTING_RECOVERY_NOT_ACCREDITED");
        require(Files.isRegularFile(ManualL01HumanTrialServer.ACTIVE)
                &&RUN.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip()),"AUTOGATE_PROTECTION_REQUIRED");
    }

    void execute() throws Exception {
        assertDatabase(jdbc);
        require(OWNER.toString().equals(trial.text("ownerId"))&&ORG.toString().equals(trial.text("organizationId")),"AUTOGATE_CASE_BINDING");
        require(ManualL01HumanTrialServer.recoveryEvidenceComplete(trial.evidence),"AUTOGATE_NO_RECOVERY_REPEAT");
        require(count(jdbc,"select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_COMPLETED'",OWNER)==1,"AUTOGATE_RECOVERY_COUNT");
        require(manualAcceptanceIntact(jdbc),"AUTOGATE_MANUAL_ACCEPTANCE_MUST_REMAIN");
        proof.putIfAbsent("runId",RUN.toString()); proof.putIfAbsent("startedAtUtc",Instant.now().toString());
        proof.put("executionOrigin","AUTOMATED_SYNTHETIC_ONLY"); proof.put("humanInterventionRepeated",false);
        proof.put("database","ruta_fija_test");proof.put("databaseRole","rf_test");proof.put("schema","V26");
        proof.put("mail","SIMULATED_ONLY");proof.put("externalSmtpAttempts",0);proof.put("resendAttempts",0);
        proof.put("realHttpServer",true);proof.put("mockMvcOrMockAuthentication",false);
        proof.put("superAdminCredentialSource","REAL_SIGNED_TOKEN_FROM_EXISTING_TEST_JWT_SERVICE_FOR_EXACT_SYNTHETIC_OWNER");
        proof.put("normalAuthorizationFiltersAndBothGatesEnabled",true);
        proof.put("ownerId",OWNER.toString());proof.put("organizationId",ORG.toString());
        proof.put("manualAdminId",MANUAL_ADMIN.toString());proof.put("manualAcceptanceId",MANUAL_ACCEPTANCE.toString());
        proof.put("manualAcceptanceEvidence",Map.of("path",HUMAN_ADMIN,"sha256",HUMAN_ADMIN_SHA));
        proof.put("manualRecoveryEvidence",Map.of("path",HUMAN_RECOVERY,"sha256",HUMAN_RECOVERY_SHA));
        proof.put("approvedAdenda",Map.of("path",ADENDA,"sha256",ADENDA_SHA));
        proof.putIfAbsent("before",snapshot(jdbc)); checkpoint();
        var owner=trial.context.getBean(AppUserRepository.class).findOneById(OWNER).orElseThrow();
        require(owner.isActive()&&owner.isEmailVerified()&&owner.getRole().name().equals("SUPER_ADMIN"),"AUTOGATE_OWNER_NOT_ELIGIBLE");
        ownerToken=trial.context.getBean(JwtService.class).issueAccessToken(owner);
        try {
            if ("PASS_AUTOMATED_REAL_HTTP_POSTGRESQL_SIMULATED_MAIL".equals(proof.get("gateResult"))) {
                UUID existing=UUID.fromString(String.valueOf(proof.get("automatedAdminId")));
                require(existing.toString().equals(secrets.get("adminId"))&&nativeOrderingValid(jdbc,existing)
                        &&historical(jdbc,"ADMIN")==3&&Boolean.TRUE.equals(proof.get("negativeThenAcceptanceThenCompletionServerOrderVerified")),
                        "AUTOGATE_RESUME_MUST_PRESERVE_SAME_COMPLETED_CASE");
                proof.put("resumedAfterAuxiliaryCleanupExpectation",true);
                proof.put("noLifecycleOtpOrAcceptanceRepeatedDuringResume",true);checkpoint();
            } else runLifecycle();
            archiveProof();
            cleanupSyntheticComplement();
            proof.put("afterSelectiveCleanup",snapshot(jdbc));
            proof.put("manualAcceptancePreserved",manualAcceptanceIntact(jdbc));
            proof.put("SC03LocalResult",RESULT); proof.put("result","PASS_AUTOMATED_SYNTHETIC_GATE_ONLY");
            archiveProof();
            trial.evidence.put("automatedGateComplement",Map.of("path",ROOT.relativize(PROOF).toString().replace('\\','/'),"sha256",sha(PROOF),"result",RESULT));
            trial.evidence.put("SC03LocalResult",RESULT);
            trial.evidence.put("SC03ManualAcceptanceOrigin","HUMAN_SYNTHETIC_EXISTING_UNCHANGED");
            trial.evidence.put("SC03GateOrigin","AUTOMATED_SYNTHETIC_SEPARATE_ACCOUNT");
            trial.persist();
        } catch(Exception failure) {
            proof.put("result","PENDING_SAME_CASE_PROTECTED");
            proof.put("lastFailureAtUtc",Instant.now().toString());
            String reason=failure instanceof IllegalStateException?failure.getMessage():"AUTOGATE_INTERNAL_FAILURE_REDACTED";
            proof.put("lastFailureReason",reason!=null&&reason.matches("[A-Z0-9_]{1,120}")?reason:"AUTOGATE_INTERNAL_FAILURE_REDACTED");
            checkpoint(); throw new IllegalStateException(String.valueOf(proof.get("lastFailureReason")));
        } finally {ownerToken=null;secrets.clear();trial.mailbox.remove(EMAIL);}
    }

    void runLifecycle() throws Exception {
        UUID admin;
        if(!secrets.containsKey("adminId")) {
            require(count(jdbc,"select count(*) from app_user where email=?",EMAIL)==0,"AUTOGATE_EXISTING_CASE_REQUIRES_RECOVERY_NOT_NEW_ACCOUNT");
            require(historical(jdbc,"ADMIN")==2&&historical(jdbc,"SUPER_ADMIN")==1&&historical(jdbc,"CONDUCTOR")==0,"AUTOGATE_EXPECTED_TWO_REMAINING_ADMIN_SLOTS");
            require(!Boolean.TRUE.equals(proof.get("creationRequestStarted")),"AUTOGATE_UNCERTAIN_CREATE_RECOVER_SAME_CASE");
            secrets.put("password","Synthetic-Auto-"+UUID.randomUUID());saveSecrets();
            proof.put("creationRequestStarted",true);checkpoint();
            var created=call("CREATE_INACTIVE_ADMIN","POST","/api/v1/platform/admins",Map.of(
                    "organizationId",ORG,"email",EMAIL,"fullName","SYNTHETIC AUTOMATED GATE ONLY",
                    "reason","REC-CI03-01 AUTOMATED SYNTHETIC ONLY; approved SC-03 1.1.0; not a human acceptance",
                    "identityReview",Map.of("documentType","DNI","documentNumber","40000002","method","IN_PERSON",
                            "reference","SYNTHETIC-REC-CI03-01-NO-REAL-PERSON","originalReviewed",true,"identityConfirmed",true)),ownerHeaders(),200);
            require("PENDING_CLAIM".equals(created.path("status").asText()),"AUTOGATE_EXPECTED_INACTIVE_CREATION");
            secrets.put("adminId",created.path("adminUserId").asText());secrets.put("credential",created.path("credential").asText());
            secrets.put("activationId",created.path("activationId").asText());saveSecrets();
        }
        admin=UUID.fromString(String.valueOf(secrets.get("adminId")));proof.put("automatedAdminId",admin.toString());
        proof.put("activationId",secrets.get("activationId"));checkpoint();
        require(count(jdbc,"select count(*) from app_user where id=? and email=? and role='ADMIN' and organization_id=? and full_name='SYNTHETIC AUTOMATED GATE ONLY'",admin,EMAIL,ORG)==1,"AUTOGATE_SYNTHETIC_ACCOUNT_BINDING");
        require(historical(jdbc,"ADMIN")==3,"AUTOGATE_EXACTLY_ONE_ADDITIONAL_CREATION");
        if(!secrets.containsKey("activationToken")) {
            var claimed=call("CLAIM_AND_SET_PASSWORD","POST","/api/v1/admin-activation/claim",Map.of("credential",secrets.get("credential"),"newPassword",secrets.get("password")),Map.of(),200);
            secrets.put("activationToken",claimed.path("activationToken").asText());secrets.remove("credential");saveSecrets();
        }
        JsonNode status=call("SCOPED_STATUS","GET","/api/v1/admin-activation/status",null,scopeHeaders(),200);
        if(!status.path("emailVerified").asBoolean()) {
            require(!Boolean.TRUE.equals(proof.get("otpBeginAttempted")),"AUTOGATE_OTP_ALREADY_REQUESTED_RECOVER_WITHOUT_RESEND");
            proof.put("otpBeginAttempted",true);checkpoint();
            var ticket=call("BEGIN_SIMULATED_OTP","POST","/api/v1/admin-activation/otp/begin",null,scopeHeaders(),200);
            var mail=trial.mailbox.remove(EMAIL);
            require(mail!=null&&Instant.now().isBefore(mail.expiresAt()),"AUTOGATE_SIMULATED_MAIL_NOT_AVAILABLE");
            status=call("VERIFY_SIMULATED_OTP","POST","/api/v1/admin-activation/otp/verify",Map.of("verificationToken",ticket.path("verificationToken").asText(),"code",mail.code()),scopeHeaders(),200);
        }
        if(!proof.containsKey("negativeGate")) {
            require(status.path("passwordSet").asBoolean()&&status.path("emailVerified").asBoolean()
                    &&status.path("identityVerified").asBoolean()&&!status.path("termsAccepted").asBoolean()
                    &&"IN_PROGRESS".equals(status.path("status").asText()),"AUTOGATE_VALID_SCOPED_PRECONDITIONS_REQUIRED");
            proof.put("beforeAcceptanceNative",nativeActivation(admin));checkpoint();
            var rejected=call("NEGATIVE_TERMS_GATE","POST","/api/v1/admin-activation/complete",null,scopeHeaders(),409);
            require(termsSpecificRejection(409,rejected.path("code").asText(),rejected.path("message").asText()),"AUTOGATE_MUST_REJECT_SPECIFICALLY_FOR_PENDING_TERMS");
            require(count(jdbc,"select count(*) from admin_terms_acceptance where admin_user_id=?",admin)==0,"AUTOGATE_PRE_ACCEPTANCE_ROW_MUST_BE_ABSENT");
            proof.put("negativeGate",Map.of("httpStatus",409,"code",rejected.path("code").asText(),"reason",rejected.path("message").asText(),
                    "serverTimestamp",rejected.path("timestamp").asText(),"correlationId",rejected.path("correlationId").asText(),"validActivationCapability",true));checkpoint();
        }
        if(count(jdbc,"select count(*) from admin_terms_acceptance where admin_user_id=?",admin)==0) {
            var terms=call("READ_PUBLISHED_TERMS","GET","/api/v1/admin-activation/terms",null,scopeHeaders(),200);
            require("1.0.0".equals(terms.path("version").asText())&&APPROVED_SHA.equals(terms.path("contentSha256").asText())
                    &&!terms.path("synthetic").asBoolean(true)&&!terms.path("accepted").asBoolean(),"AUTOGATE_EXACT_PUBLISHED_DOCUMENT_REQUIRED");
            call("AUTOMATED_EXPLICIT_ACCEPTANCE","POST","/api/v1/admin-activation/terms",Map.of("version","1.0.0","contentSha256",APPROVED_SHA,"accepted",true),scopeHeaders(),200);
            proof.put("automatedAcceptanceOrigin","AUTOMATED_SYNTHETIC_ONLY_NOT_THE_HUMAN_OPERATOR");checkpoint();
        }
        if(!proof.containsKey("positiveGate")) {
            var completed=call("POSITIVE_SAME_OPERATION","POST","/api/v1/admin-activation/complete",null,scopeHeaders(),200);
            require("COMPLETED".equals(completed.path("status").asText()),"AUTOGATE_COMPLETION_REQUIRED");
            proof.put("positiveGate",Map.of("httpStatus",200,"status","COMPLETED","sameMethodAndPath",true));checkpoint();
        }
        proof.put("afterAcceptanceNative",nativeActivation(admin));
        require(nativeOrderingValid(jdbc,admin),"AUTOGATE_NATIVE_ORDER_OR_AUDIT_INVALID");
        var times=jdbc.queryForMap("select t.accepted_at,a.completed_at from admin_terms_acceptance t join admin_activation a on a.admin_user_id=t.admin_user_id where t.admin_user_id=?",admin);
        Instant rejectedAt=Instant.parse(String.valueOf(((Map<?,?>)proof.get("negativeGate")).get("serverTimestamp")));
        require(rejectedAt.isBefore(((java.sql.Timestamp)times.get("accepted_at")).toInstant())
                &&((java.sql.Timestamp)times.get("accepted_at")).toInstant().isBefore(((java.sql.Timestamp)times.get("completed_at")).toInstant()),"AUTOGATE_NEGATIVE_ACCEPTANCE_POSITIVE_ORDER_INVALID");
        proof.put("negativeThenAcceptanceThenCompletionServerOrderVerified",true);
        if(!Boolean.TRUE.equals(proof.get("ordinaryLoginAndGroupsAllowed"))) {
            var login=call("ORDINARY_LOGIN","POST","/api/v1/auth/login",Map.of("email",EMAIL,"password",secrets.get("password")),Map.of(),200);
            String access=login.path("accessToken").asText();require(!access.isBlank(),"AUTOGATE_LOGIN_MUST_ISSUE_ACCESS");
            secrets.put("accessToken",access);saveSecrets();
            call("ORDINARY_GROUPS_ALLOWED","GET","/api/v1/groups",null,Map.of("Authorization","Bearer "+access),200);
            proof.put("ordinaryLoginAndGroupsAllowed",true);checkpoint();
        }
        require(manualAcceptanceIntact(jdbc),"AUTOGATE_MANUAL_ACCEPTANCE_CHANGED");
        if(!Boolean.TRUE.equals(proof.get("automatedOriginAudited"))) {
            var org=trial.context.getBean(OrganizationRepository.class).findById(ORG).orElseThrow();
            trial.context.getBean(AuditService.class).recordSystem(org,"L01_AUTOMATED_SYNTHETIC_GATE_VERIFIED","APP_USER",admin,
                    Map.of("executionOrigin","AUTOMATED_SYNTHETIC_ONLY","runId",RUN.toString(),"adenda","SC-L01-LOCAL-01 1.1.0",
                            "manualAcceptanceIdPreserved",MANUAL_ACCEPTANCE.toString(),"negativeHttp",409,"positiveHttp",200));
            proof.put("automatedOriginAudited",true);checkpoint();
        }
        proof.put("nativeAutomatedOriginAudits",jdbc.queryForList("select id::text,entity_id::text,occurred_at,action,metadata_json::text metadata from audit_event where entity_id=? and action='L01_AUTOMATED_SYNTHETIC_GATE_VERIFIED'",admin));
        proof.put("gateResult","PASS_AUTOMATED_REAL_HTTP_POSTGRESQL_SIMULATED_MAIL");
        proof.put("beforeSelectiveCleanup",snapshot(jdbc));checkpoint();
    }

    void cleanupSyntheticComplement() throws Exception {
        UUID admin=UUID.fromString(String.valueOf(proof.get("automatedAdminId")));
        if(Boolean.TRUE.equals(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,admin)))
            call("SELECTIVE_REVOKE_AUTOMATED_ADMIN","POST","/api/v1/platform/admins/"+admin+"/revoke",Map.of("reason","REC-CI03-01 completed; selective synthetic fixture cleanup, keep acceptance and historical quota"),ownerHeaders(),200);
        if(secrets.get("accessToken") instanceof String access) {
            // Current JwtAuthenticationConverter removes authorities for inactive/stale accounts.
            // A valid signed token therefore reaches the access denied handler (403 FORBIDDEN_ROLE).
            var refused=call("REVOKED_AUTOMATED_SESSION_DENIED","GET","/api/v1/groups",null,Map.of("Authorization","Bearer "+access),403);
            require("FORBIDDEN_ROLE".equals(refused.path("code").asText()),"AUTOGATE_REVOKED_SESSION_WRONG_DENIAL");
        }
        require(count(jdbc,"select count(*) from app_user where id=? and not active",admin)==1
                &&count(jdbc,"select count(*) from refresh_token where user_id=? and revoked_at is null",admin)==0,"AUTOGATE_SELECTIVE_CLEANUP_FAILED");
        require(historical(jdbc,"ADMIN")==3&&historical(jdbc,"SUPER_ADMIN")==1&&historical(jdbc,"CONDUCTOR")==0,"AUTOGATE_LEDGER_MUST_BE_PRESERVED");
        proof.put("selectiveCleanup",Map.of("result","PASS_ADDED_SYNTHETIC_ADMIN_DISABLED_SESSIONS_REVOKED","acceptancePreserved",true,
                "historicalLedgerPreserved",true,"manualAdminAndOwnerUnchanged",true,"globalCleanupExecuted",false));
        archiveProof();Files.deleteIfExists(PRIVATE);
    }

    JsonNode call(String name,String method,String path,Object body,Map<String,String> extra,int expected) throws Exception {
        var headers=new HashMap<>(extra);headers.put("Origin",ManualL01HumanTrialServer.BASE);
        String correlation=UUID.randomUUID().toString();headers.put("X-Correlation-Id",correlation);
        headers.put("User-Agent","RutaFija-L01-Automated-Synthetic-Gate/1.0");
        Instant sent=Instant.now();
        HttpResponse<byte[]> response=trial.request(method,path,body==null?new byte[0]:JSON.writeValueAsBytes(body),headers);
        JsonNode parsed=response.body().length==0?JSON.createObjectNode():JSON.readTree(response.body());
        var step=new LinkedHashMap<String,Object>();step.put("sequence",steps.size()+1);step.put("name",name);
        step.put("method",method);step.put("path",path);step.put("requestCorrelationId",correlation);
        step.put("sentAtUtc",sent.toString());step.put("receivedAtUtc",Instant.now().toString());step.put("httpStatus",response.statusCode());
        if(response.statusCode()>=400){step.put("serverCode",parsed.path("code").asText());step.put("serverTimestamp",parsed.path("timestamp").asText());}
        step.put("responseBodySha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(response.body())));
        steps.add(step);checkpoint();
        require(response.statusCode()==expected,"AUTOGATE_HTTP_"+name+"_EXPECTED_"+expected+"_ACTUAL_"+response.statusCode());
        return parsed;
    }

    Map<String,String> ownerHeaders(){return Map.of("Authorization","Bearer "+ownerToken);}
    Map<String,String> scopeHeaders(){return Map.of("X-Admin-Activation-Token",String.valueOf(secrets.get("activationToken")));}
    List<Map<String,Object>> nativeActivation(UUID admin){return jdbc.queryForList("""
        select u.id::text account_id,u.organization_id::text organization_id,u.active,a.id::text activation_id,a.status,
          a.password_set_at,a.email_verified_at,a.completed_at,t.id::text acceptance_id,t.document_id::text document_id,
          t.document_version,t.content_sha256,t.accepted_at,
          exists(select 1 from person_identity p where p.user_id=u.id) identity_verified
        from app_user u join admin_activation a on a.admin_user_id=u.id
        left join admin_terms_acceptance t on t.admin_user_id=u.id where u.id=?
        """,admin);}

    void checkpoint() throws Exception {Files.createDirectories(FOLDER);ManualL01HumanTrialServer.writePublicStateAtomically(JOURNAL,proof);}
    void saveSecrets() throws Exception {
        Path temp=PRIVATE.resolveSibling(PRIVATE.getFileName()+".tmp");
        ManualL01HumanTrialServer.writeRestricted(temp,JSON.writeValueAsBytes(secrets));
        Files.move(temp,PRIVATE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);
    }
    void archiveProof() throws Exception {
        proof.put("observedAtUtc",Instant.now().toString());checkpoint();
        ManualL01HumanTrialServer.writePublicStateAtomically(PROOF,proof);
        Files.writeString(Path.of(PROOF+".sha256"),sha(PROOF)+"  "+PROOF.getFileName()+System.lineSeparator());
    }

    static boolean termsSpecificRejection(int status,String code,String message){return status==409&&"RESOURCE_CONFLICT".equals(code)&&REASON.equals(message);}
    static boolean manualAcceptanceIntact(JdbcTemplate jdbc){return count(jdbc,"""
        select count(*) from admin_terms_acceptance where id=? and admin_user_id=? and organization_id=?
        and document_version='1.0.0' and content_sha256=? and date_trunc('milliseconds',accepted_at)='2026-10-17T08:39:10.296Z'::timestamptz
        """,MANUAL_ACCEPTANCE,MANUAL_ADMIN,ORG,APPROVED_SHA)==1;}
    static boolean nativeOrderingValid(JdbcTemplate jdbc,UUID admin){return count(jdbc,"""
        select count(*) from admin_terms_acceptance t join admin_activation a on a.admin_user_id=t.admin_user_id
        join app_user u on u.id=t.admin_user_id join admin_terms_document d on d.id=t.document_id
        where t.admin_user_id=? and t.organization_id=? and t.document_version='1.0.0' and t.content_sha256=?
        and not d.synthetic and d.content_sha256=t.content_sha256 and a.status='COMPLETED'
        and a.password_set_at<=a.email_verified_at and a.email_verified_at<t.accepted_at and t.accepted_at<a.completed_at
        and exists(select 1 from audit_event ev where ev.user_id=u.id and ev.entity_id=d.id and ev.action='ADMIN_ACTIVATION_TERMS_ACCEPTED')
        """,admin,ORG,APPROVED_SHA)==1;}
    static long historical(JdbcTemplate jdbc,String role){return count(jdbc,"select count(*) from pilot_account_creation where role=?",role);}
    static boolean archivedComplementValid(Map<String,Object> state,JdbcTemplate jdbc) throws Exception {
        if(!(state.get("automatedGateComplement") instanceof Map<?,?> ref)||!RESULT.equals(ref.get("result"))
                ||!sha(PROOF).equals(ref.get("sha256"))||!ADENDA_SHA.equals(sha(ROOT.resolve(ADENDA))))return false;
        Map<String,Object> p=JSON.readValue(Files.readAllBytes(PROOF),FixtureJson.OBJECT_TYPE);
        if(!validProof(p)||!manualAcceptanceIntact(jdbc)||!nativeOrderingValid(jdbc,UUID.fromString((String)p.get("automatedAdminId"))))return false;
        return historical(jdbc,"ADMIN")==3&&historical(jdbc,"SUPER_ADMIN")==1&&historical(jdbc,"CONDUCTOR")==0
                &&HUMAN_ADMIN_SHA.equals(sha(ROOT.resolve(HUMAN_ADMIN)))&&HUMAN_RECOVERY_SHA.equals(sha(ROOT.resolve(HUMAN_RECOVERY)));
    }
    static boolean validProof(Map<String,Object> p){
        if(!RUN.toString().equals(p.get("runId"))||!RESULT.equals(p.get("SC03LocalResult"))||!"AUTOMATED_SYNTHETIC_ONLY".equals(p.get("executionOrigin"))
                ||!Boolean.TRUE.equals(p.get("ordinaryLoginAndGroupsAllowed"))||!Boolean.TRUE.equals(p.get("manualAcceptancePreserved"))
                ||!Boolean.TRUE.equals(p.get("negativeThenAcceptanceThenCompletionServerOrderVerified"))
                ||!(p.get("negativeGate") instanceof Map<?,?> n)||!(p.get("positiveGate") instanceof Map<?,?> y))return false;
        return n.get("httpStatus") instanceof Number status && termsSpecificRejection(status.intValue(),String.valueOf(n.get("code")),String.valueOf(n.get("reason")))
                &&Integer.valueOf(200).equals(y.get("httpStatus"))&&Boolean.TRUE.equals(y.get("sameMethodAndPath"));
    }
    static void verifyReleaseAndArchive() throws Exception {
        Map<String,Object> inspected=inspect();
        require(!Files.exists(ManualL01HumanTrialServer.ACTIVE)&&Boolean.FALSE.equals(inspected.get("cleanupLockHeld")),"AUTOGATE_CLEANUP_PROTECTION_NOT_RELEASED");
        var ledger=(Map<?,?>)inspected.get("historicalCreations");
        require(((Number)ledger.get("SUPER_ADMIN")).longValue()==1&&((Number)ledger.get("ADMIN")).longValue()==3&&((Number)ledger.get("CONDUCTOR")).longValue()==0,"AUTOGATE_POST_EXIT_LEDGER_CHANGED");
        require(!Files.exists(PRIVATE),"AUTOGATE_PRIVATE_AUTOMATION_CAPABILITIES_NOT_REMOVED");
        inspected.put("runId",RUN.toString());inspected.put("TEST_CLEANUP_RELEASED",true);
        inspected.put("gateEvidence",ROOT.relativize(PROOF).toString().replace('\\','/'));inspected.put("gateEvidenceSha256",sha(PROOF));
        inspected.put("globalCleanupExecuted",false);inspected.put("privateAutomationCapabilitiesRemoved",true);
        Path file=FOLDER.resolve("salida-y-protecciones.json");ManualL01HumanTrialServer.writePublicStateAtomically(file,inspected);
        Files.writeString(Path.of(file+".sha256"),sha(file)+"  "+file.getFileName()+System.lineSeparator());
        System.out.println("SC03_LOCAL_RESULT="+RESULT+"\nTEST_CLEANUP_RELEASED=true\nEVIDENCE_FILE="+ROOT.relativize(PROOF)+"\nEVIDENCE_SHA256="+sha(PROOF));
    }
    static String sha(Path p) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    static void require(boolean condition,String reason){if(!condition)throw new IllegalStateException(reason);}
}
