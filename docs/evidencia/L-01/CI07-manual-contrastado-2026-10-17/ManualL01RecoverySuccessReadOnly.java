package pe.rutafija.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** Exact-case SELECT-only archive verification. No Spring start, HTTP, secrets, fixture writes or cleanup. */
public final class ManualL01RecoverySuccessReadOnly {
    static final UUID RUN=UUID.fromString("140c10a1-be0f-4d08-a6d4-c796e2ed107a");
    static final UUID OWNER=UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0");
    static final UUID ADMIN=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
    static final UUID ORG=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
    static final UUID INSTALLATION=UUID.fromString("f0cd888b-57df-4def-9d08-7882ffadebae");
    static final UUID CHALLENGE=UUID.fromString("fa9aa2ae-8e4b-4eab-bbb5-55d86f016036");
    static final String PUBLIC_SHA="5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433";
    static final String ARCHIVE_SHA="e0a986d10e09f8fbb289061753b3e36759b0cae24e53731ede4bba8640d5fffb";
    static final String RECOVERED="sa.recovered.140c10a1@rutafija.test";
    static void require(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
    static long number(Object value){return ((Number)value).longValue();}
    static Instant instant(Object value){return ((Timestamp)value).toInstant();}

    public static void main(String[] args){
        try{
            require(args.length==0,"NO_ARGUMENTS_ACCEPTED");
            Path archive=ManualL01HumanTrial.ROOT.resolve("docs/evidencia/L-01/ensayos-humanos-"+RUN+"/observacion-1792287275662.json");
            byte[] bytes=Files.readAllBytes(archive);
            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            require(ARCHIVE_SHA.equals(hash),"ARCHIVE_SHA_MISMATCH");
            require(Files.readString(Path.of(archive+".sha256")).strip().split("\\s+")[0].equalsIgnoreCase(hash),"SIDECAR_MISMATCH");
            Map<String,Object> state=ManualL01HumanTrial.JSON.readValue(bytes,Map.class);
            require(RUN.toString().equals(state.get("runId"))&&OWNER.toString().equals(state.get("ownerId"))
                &&ADMIN.toString().equals(state.get("adminId"))&&ORG.toString().equals(state.get("organizationId")),"ARCHIVE_CASE_MISMATCH");
            require("PASS_LOCAL_TEMPORARY_COPY_ONLY".equals(state.get("manualTemporaryRecoveryResult"))
                &&ManualL01HumanTrialServer.recoveryEvidenceComplete(state),"ARCHIVED_RECOVERY_PREDICATE_FAILED");
            require("PENDING".equals(state.get("manualSyntheticAdminResult"))&&!state.containsKey("preAcceptanceGate"),"ADMIN_GATE_STATE_UNEXPECTED");
            require(RUN.toString().equals(Files.readString(ManualL01HumanTrialServer.ACTIVE).strip()),"MARKER_MISMATCH");
            try(Connection connection=ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())){
                connection.setReadOnly(true);connection.setAutoCommit(false);
                connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
                var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
                ManualL01HumanTrial.assertDatabase(jdbc);
                require("on".equals(jdbc.queryForObject("show transaction_read_only",String.class)),"READONLY_NOT_EFFECTIVE");
                Map<String,Object> environment=ManualL01HumanTrial.snapshot(jdbc);
                require(Map.of("SUPER_ADMIN",1L,"ADMIN",2L,"CONDUCTOR",0L).equals(environment.get("historicalCreations")),"LEDGER_CHANGED");
                require(Boolean.TRUE.equals(environment.get("cleanupLockHeld"))&&number(environment.get("termsAcceptanceCount"))==1,"PROTECTION_OR_ACCEPTANCE_CHANGED");
                var owner=jdbc.queryForMap("""
                    select id::text,role,active,email_verified_at is not null email_verified,
                      organization_id is null platform_account,email=? recovered_email_matches,session_version
                    from app_user where id=?
                    """,RECOVERED,OWNER);
                require("SUPER_ADMIN".equals(owner.get("role"))&&Boolean.TRUE.equals(owner.get("active"))
                    &&Boolean.TRUE.equals(owner.get("email_verified"))&&Boolean.TRUE.equals(owner.get("platform_account"))
                    &&Boolean.TRUE.equals(owner.get("recovered_email_matches"))&&number(owner.get("session_version"))==1,"RECOVERED_ACCOUNT_STATE_MISMATCH");
                require(ManualL01HumanTrial.count(jdbc,"select count(*) from app_user where email=?","sa.human.140c10a1@rutafija.test")==0,"OLD_ACCOUNT_EMAIL_STILL_PRESENT");
                var challenge=jdbc.queryForMap("""
                    select id::text,user_id::text,installation_id::text,requested_email=? recipient_matches,
                      created_at,expires_at,proof_verified_at,consumed_at,failed_proof_attempts
                    from super_admin_recovery_challenge where id=? and user_id=?
                    """,RECOVERED,CHALLENGE,OWNER);
                require(INSTALLATION.toString().equals(challenge.get("installation_id"))&&Boolean.TRUE.equals(challenge.get("recipient_matches"))
                    &&challenge.get("proof_verified_at")!=null&&challenge.get("consumed_at")!=null
                    &&number(challenge.get("failed_proof_attempts"))==0,"RECOVERY_CHALLENGE_MISMATCH");
                Instant completed=instant(challenge.get("consumed_at"));
                require(instant(challenge.get("proof_verified_at")).isBefore(completed)&&completed.isBefore(instant(challenge.get("expires_at"))),"RECOVERY_ORDER_OR_TTL_MISMATCH");
                var archivedChallenges=(List<Map<String,Object>>)state.get("recoveryChallenges");
                var archived=archivedChallenges.stream().filter(c->CHALLENGE.toString().equals(c.get("id"))).findFirst().orElseThrow();
                Instant archivedConsumed=OffsetDateTime.parse(archived.get("consumed_at").toString()).toInstant();
                // Existing archive serializes java.sql.Timestamp at millisecond precision.
                // Keep the full native instant and compare only at the precision actually published.
                require(completed.truncatedTo(java.time.temporal.ChronoUnit.MILLIS).equals(archivedConsumed),"ARCHIVE_NATIVE_CONSUMPTION_MISMATCH");
                for(String field:List.of("proof_verified_at","expires_at"))
                    require(instant(challenge.get(field)).truncatedTo(java.time.temporal.ChronoUnit.MILLIS)
                        .equals(OffsetDateTime.parse(archived.get(field).toString()).toInstant()),"ARCHIVE_NATIVE_CHALLENGE_TIME_MISMATCH");
                var otp=jdbc.queryForMap("""
                    select id::text,purpose,purpose_reference_id::text,recipient_email=? recipient_matches,
                      consumed_at,otp_hash is null otp_cleared,verification_token_hash is null capability_cleared,
                      failed_attempts,send_at_1,send_at_2,send_at_3
                    from email_otp_challenge where user_id=? and purpose='SUPER_ADMIN_RECOVERY' and purpose_reference_id=?
                    """,RECOVERED,OWNER,CHALLENGE);
                require(Boolean.TRUE.equals(otp.get("recipient_matches"))&&otp.get("consumed_at")!=null
                    &&Boolean.TRUE.equals(otp.get("otp_cleared"))&&Boolean.TRUE.equals(otp.get("capability_cleared"))
                    &&number(otp.get("failed_attempts"))==0,"RECOVERY_OTP_NOT_CONSUMED_OR_CLEARED");
                var audits=jdbc.queryForList("""
                    select id::text,action,occurred_at,metadata_json->>'refreshTokensRevoked' refresh_tokens_revoked
                    from audit_event where user_id=? and action in
                      ('SUPER_ADMIN_RECOVERY_ENROLLED','SUPER_ADMIN_RECOVERY_PROOF_ACCEPTED',
                       'SUPER_ADMIN_RECOVERY_PROOF_REJECTED','SUPER_ADMIN_RECOVERY_EMAIL_VERIFIED',
                       'SUPER_ADMIN_RECOVERY_OTP_SENT','SUPER_ADMIN_RECOVERY_COMPLETED')
                    order by occurred_at,id
                    """,OWNER);
                for(String action:List.of("SUPER_ADMIN_RECOVERY_ENROLLED","SUPER_ADMIN_RECOVERY_PROOF_ACCEPTED",
                    "SUPER_ADMIN_RECOVERY_EMAIL_VERIFIED","SUPER_ADMIN_RECOVERY_COMPLETED"))
                    require(audits.stream().filter(a->action.equals(a.get("action"))).count()==1,"RECOVERY_AUDIT_COUNT_MISMATCH");
                // The approved protocol allows a resend under the 3/60-minute limit.
                // The no-resend rehearsal instruction is checked and reported as a deviation,
                // not silently converted into a new protocol acceptance criterion.
                long otpSendAudits=audits.stream().filter(a->"SUPER_ADMIN_RECOVERY_OTP_SENT".equals(a.get("action"))).count();
                long retainedSendSlots=List.of("send_at_1","send_at_2","send_at_3").stream().filter(k->otp.get(k)!=null).count();
                require(otpSendAudits>=1&&otpSendAudits<=3&&otpSendAudits==retainedSendSlots,"OTP_SEND_AUDIT_OR_LIMIT_MISMATCH");
                require(audits.stream().noneMatch(a->"SUPER_ADMIN_RECOVERY_PROOF_REJECTED".equals(a.get("action"))),"UNEXPECTED_PROOF_REJECTION");
                var completeAudit=audits.stream().filter(a->"SUPER_ADMIN_RECOVERY_COMPLETED".equals(a.get("action"))).findFirst().orElseThrow();
                long revoked=Long.parseLong(completeAudit.get("refresh_tokens_revoked").toString());
                require(revoked>=number(state.get("oldRefreshSessionsChecked")),"AUDIT_REVOKED_FEWER_THAN_OBSERVED");
                var oldRefresh=jdbc.queryForMap("""
                    select count(*) total_before_recovery,count(*) filter(where revoked_at is not null) revoked_before_recovery,
                      count(*) filter(where revoked_at is null) unrevoked_before_recovery
                    from refresh_token where user_id=? and created_at<=?
                    """,OWNER,Timestamp.from(completed));
                require(number(oldRefresh.get("total_before_recovery"))>=number(state.get("oldRefreshSessionsChecked"))
                    &&number(oldRefresh.get("unrevoked_before_recovery"))==0,"OLD_REFRESH_SESSIONS_NOT_REVOKED");
                var credential=jdbc.queryForMap("select installation_id::text,user_id::text,public_key_x509,enrolled_at,updated_at from super_admin_recovery_credential where id=1 and user_id=?",OWNER);
                String key=credential.remove("public_key_x509").toString();
                String publicSha=ManualL01RecoveryFixtureKeyReplacement.fingerprint(key);
                require(PUBLIC_SHA.equals(publicSha)&&completed.equals(instant(credential.get("updated_at"))),"RECOVERY_CREDENTIAL_MISMATCH");
                credential.put("publicKeySha256",publicSha);
                var keyAudits=jdbc.queryForList(ManualL01RecoveryFixtureKeyReplacement.PUBLIC_AUDITS_SQL,OWNER,ManualL01RecoveryFixtureKeyReplacement.ACTION);
                require(keyAudits.size()==1&&!ManualL01RecoveryFixtureKeyReplacement.restorePublicReference(keyAudits.getFirst(),publicSha,state).isEmpty(),"KEY_PREPARATION_AUDIT_MISMATCH");
                require(ManualL01HumanTrial.count(jdbc,"select count(*) from audit_event where user_id=? and action='LOGIN_SUCCESS' and occurred_at>?",OWNER,Timestamp.from(completed))>=1,"RECOVERED_LOGIN_AUDIT_MISSING");
                require(ManualL01HumanTrial.count(jdbc,"select count(*) from admin_terms_acceptance where id=? and admin_user_id=? and organization_id=?",UUID.fromString("c496b63c-735f-48cc-be56-90f7ec1464e1"),ADMIN,ORG)==1,"ACCEPTANCE_CHANGED");
                var result=new LinkedHashMap<String,Object>();
                result.put("inspectionAtUtc",Instant.now().toString());result.put("runId",RUN.toString());
                result.put("environment",environment);result.put("readOnlyEffective",true);result.put("archiveSha256",hash);
                result.put("archiveRecoveryPredicatePass",true);result.put("recoveredAccount",owner);
                result.put("completedChallenge",challenge);result.put("consumedOtpWithoutSecrets",otp);result.put("recoveryAudits",audits);
                result.put("nativeConsumedAtFullPrecision",completed.toString());result.put("archivedConsumedAtPublishedPrecision",archivedConsumed.toString());
                result.put("nativeMinusArchiveNanos",java.time.Duration.between(archivedConsumed,completed).toNanos());
                result.put("challengeTimestampsMatchAtArchiveMillisecondPrecision",true);
                result.put("oldRefreshTokensWithoutValues",oldRefresh);result.put("auditRefreshTokensRevoked",revoked);
                result.put("observedSimulatedOtpEmissions",otpSendAudits);result.put("observedSimulatedResends",otpSendAudits-1);
                result.put("retainedOtpSendSlots",retainedSendSlots);result.put("otpWithinApprovedThreePerHourLimit",true);
                result.put("deviationFromNoResendRehearsalInstruction",otpSendAudits>1);
                result.put("credentialPublicFieldsOnly",credential);result.put("approvedKeyAuditCorrespondenceVerified",true);
                result.put("recoveredLoginAuditPresent",true);result.put("adminAcceptancePreserved",true);
                result.put("emergencyHashComparisonSource","ALREADY_ARCHIVED_IN_PROCESS_OBSERVER_FLAGS_NOT_REPEATED");
                result.put("manualRecoveryResult","PASS_LOCAL_TEMPORARY_COPY_ONLY");result.put("manualAdminResult","PENDING");
                result.put("independentCustodyVerified",false);result.put("scopeChangeApplied",false);result.put("testCleanupReleased",false);
                result.put("applicationWrites",0);result.put("httpCalls",0);result.put("smtpAttempts",0);result.put("privateFilesRead",false);
                connection.rollback();result.put("transactionRolledBack",true);
                System.out.println(ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
            }
        }catch(Exception failure){
            System.err.println("RECOVERY_SUCCESS_READONLY=FAIL_DETAILS_REDACTED");
            if(failure instanceof IllegalStateException&&failure.getMessage()!=null&&failure.getMessage().matches("[A-Z][A-Z0-9_]{0,100}"))
                System.err.println("RECOVERY_SUCCESS_READONLY_REASON="+failure.getMessage());
            System.exit(1);
        }
    }
}
