package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static pe.rutafija.support.ManualL01HumanTrial.*;
import static pe.rutafija.support.ManualL01AutomatedTermsComplement.*;

/** Independent final SELECT-only verification. No Spring, SMTP, mutation or secret column. */
public final class ManualL01LocalClosureReadOnly {
 static String phase="INITIAL";
 public static void main(String[] args) {
  try {
   require(args.length==0,"FINAL_NO_ARGUMENTS");
   Map<String,Object> state=JSON.readValue(Files.readAllBytes(ManualL01HumanTrialServer.STATE),Map.class);
   Map<String,Object> proof=JSON.readValue(Files.readAllBytes(PROOF),Map.class);
   require(validProof(proof),"FINAL_GATE_ARCHIVE_INVALID");
   UUID automated=UUID.fromString((String)proof.get("automatedAdminId"));
   try(Connection conn=connect(configuration())) {
    conn.setReadOnly(true);conn.setAutoCommit(false);conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(conn,true));assertDatabase(jdbc);
    require("on".equals(jdbc.queryForObject("show transaction_read_only",String.class)),"FINAL_EFFECTIVE_READONLY_REQUIRED");
    require(archivedComplementValid(state,jdbc),"FINAL_COMPOSITE_ARCHIVE_NATIVE_MISMATCH");
    Map<String,Object> output=new LinkedHashMap<>();output.put("runId",RUN.toString());
    output.put("observedAtUtc",Instant.now().toString());output.put("environment",snapshot(jdbc));
    output.put("readOnlyEffective",true);output.put("springStarted",false);output.put("migrationsExecuted",false);
    output.put("smtpAttempts",0);output.put("applicationWrites",0);output.put("transactionRolledBack",true);
    require(!Files.exists(ManualL01HumanTrialServer.ACTIVE)&&Boolean.FALSE.equals(snapshot(jdbc).get("cleanupLockHeld")),"FINAL_PROTECTION_NOT_RELEASED");
    require(!Files.exists(PRIVATE),"FINAL_AUTOMATION_PRIVATE_CAPABILITIES_NOT_REMOVED");
    require(count(jdbc,"select count(*) from admin_terms_acceptance")==2,"FINAL_ACCEPTANCE_COUNT");
    phase="OWNER";var owner=jdbc.queryForMap("""
      select id::text,role,active,email_verified_at is not null email_verified,organization_id is null platform_account,
        email='sa.recovered.140c10a1@rutafija.test' recovered_email_matches,session_version from app_user where id=?
      """,OWNER);
    require(Boolean.TRUE.equals(owner.get("active"))&&Boolean.TRUE.equals(owner.get("email_verified"))
      &&Boolean.TRUE.equals(owner.get("recovered_email_matches"))&&((Number)owner.get("session_version")).longValue()==1,"FINAL_OWNER_STATE_CHANGED");
    output.put("owner",owner);
    phase="MANUAL_ADMIN";var manual=jdbc.queryForMap("select id::text,role,active,organization_id::text from app_user where id=?",MANUAL_ADMIN);
    require(Boolean.TRUE.equals(manual.get("active"))&&ORG.toString().equals(manual.get("organization_id"))&&manualAcceptanceIntact(jdbc),"FINAL_MANUAL_ACCEPTANCE_OR_ACCOUNT_CHANGED");
    output.put("manualAdmin",manual);
    phase="AUTOMATED_ADMIN";var extra=jdbc.queryForMap("select id::text,role,active,organization_id::text,session_version from app_user where id=?",automated);
    require(Boolean.FALSE.equals(extra.get("active"))&&ORG.toString().equals(extra.get("organization_id"))
      &&((Number)extra.get("session_version")).longValue()==1,"FINAL_AUTOMATED_ACCOUNT_NOT_CLEANED");
    require(count(jdbc,"select count(*) from refresh_token where user_id=? and revoked_at is null",automated)==0,"FINAL_AUTOMATED_REFRESH_NOT_REVOKED");
    output.put("automatedAdminAfterSelectiveCleanup",extra);
    var acceptances=jdbc.queryForList("""
      select id::text,admin_user_id::text,organization_id::text,document_id::text,document_version,content_sha256,accepted_at
      from admin_terms_acceptance where admin_user_id in (?,?) order by accepted_at
      """,MANUAL_ADMIN,automated);output.put("acceptances",acceptances);
    var activation=jdbc.queryForMap("select id::text,status,password_set_at,email_verified_at,completed_at from admin_activation where admin_user_id=?",automated);
    output.put("automatedActivation",activation);
    phase="AUTOMATED_OTP";var otp=jdbc.queryForMap("""
      select id::text,purpose,consumed_at,otp_hash is null otp_cleared,verification_token_hash is null capability_cleared,
        send_at_1 is not null first_send,send_at_2 is null no_second_send,send_at_3 is null no_third_send,failed_attempts
      from email_otp_challenge where user_id=?
      """,automated);
    // Normal complete/revoke invalidates the current OTP: purpose returns to LOGIN,
    // consumed_at is cleared, send timestamps remain. HTTP/audit retain prior verification.
    require("LOGIN".equals(otp.get("purpose"))&&Boolean.TRUE.equals(otp.get("otp_cleared"))&&Boolean.TRUE.equals(otp.get("capability_cleared"))
      &&Boolean.TRUE.equals(otp.get("no_second_send"))&&Boolean.TRUE.equals(otp.get("no_third_send")),"FINAL_AUTOMATED_OTP_STATE");output.put("automatedOtp",otp);
    phase="RECOVERY_PUBLIC";String publicKey=jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1 and user_id=?",String.class,OWNER);
    String publicSha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(publicKey)));
    require("5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433".equals(publicSha),"FINAL_RECOVERY_PUBLIC_CHANGED");
    output.put("recoveryPublicFingerprintSha256",publicSha);
    var recovered=jdbc.queryForMap("select id::text,user_id::text,proof_verified_at,consumed_at,failed_proof_attempts from super_admin_recovery_challenge where id='fa9aa2ae-8e4b-4eab-bbb5-55d86f016036' and user_id=?",OWNER);
    require(recovered.get("consumed_at")!=null&&recovered.get("proof_verified_at")!=null,"FINAL_RECOVERY_CONSUMPTION_CHANGED");
    output.put("existingRecoveryChallenge",recovered);
    require(count(jdbc,"select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_COMPLETED'",OWNER)==1,"FINAL_RECOVERY_REPEATED");
    output.put("recoveryCompletions",1);output.put("independentCustodyVerified",false);output.put("legitimateAdminAcceptanceVerified",false);
    phase="CI01_REQUEST";output.put("ci01PreservedRequest",jdbc.queryForList("select id::text,status,provisioned_at from conductor_onboarding_request where id='e0f3de05-5b8a-4f26-93d8-89e6ebc693dd'"));
    require(count(jdbc,"select count(*) from conductor_onboarding_request where id='e0f3de05-5b8a-4f26-93d8-89e6ebc693dd' and status='PENDING_REVIEW' and provisioned_at is null")==1,"FINAL_CI01_CASE_CHANGED");
    output.put("result","PASS_FINAL_NATIVE_CORRESPONDENCE_AND_SELECTIVE_CLEANUP");
    conn.rollback();Path file=FOLDER.resolve("contraste-final-nativo-solo-lectura.json");
    Files.write(file,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(output),StandardOpenOption.CREATE_NEW);
    Files.writeString(Path.of(file+".sha256"),sha(file)+"  "+file.getFileName()+System.lineSeparator(),StandardOpenOption.CREATE_NEW);
    System.out.println("FINAL_NATIVE_RESULT=PASS_FINAL_NATIVE_CORRESPONDENCE_AND_SELECTIVE_CLEANUP\nEVIDENCE_SHA256="+sha(file));
   }
  } catch(Exception failure) {
   String reason=failure instanceof IllegalStateException?failure.getMessage():"FINAL_NATIVE_ERROR_REDACTED";
   System.out.println("FINAL_NATIVE_RESULT=PENDING; PHASE="+phase+"; REASON="+(reason!=null&&reason.matches("[A-Z0-9_]{1,120}")?reason:"FINAL_NATIVE_ERROR_REDACTED"));System.exit(1);
  }
 }
}
