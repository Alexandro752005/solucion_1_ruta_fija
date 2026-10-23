package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** Independent read-only postflight of the exact connected fixture. */
public final class L02ClosedCaseVerification {
    public static void main(String[] args)throws Exception{
        if(args.length!=0)throw new IllegalArgumentException("READ_ONLY_NO_ARGUMENTS");
        Path root=ManualL01HumanTrial.ROOT;
        Map<String,Object> state=ManualL01HumanTrial.JSON.readValue(Files.readAllBytes(root.resolve("backend/.local/l02-connected-state.json")),Map.class);
        String run=Objects.toString(state.get("runId"));UUID account=UUID.fromString(Objects.toString(state.get("accountId"))),driver=UUID.fromString(Objects.toString(state.get("driverId")));
        Map<String,Object> result=new LinkedHashMap<>();result.put("runId",run);result.put("observedAt",Instant.now().toString());result.put("readOnly",true);
        try(Connection connection=ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())){
            connection.setReadOnly(true);connection.setAutoCommit(false);JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            require("ruta_fija_test".equals(jdbc.queryForObject("select current_database()",String.class)));require("rf_test".equals(jdbc.queryForObject("select current_user",String.class)));
            result.put("database","ruta_fija_test");result.put("runtimeRole","rf_test");result.put("schemaVersion",jdbc.queryForObject("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1",String.class));
            require(Boolean.TRUE.equals(state.get("flutterPassed"))&&Boolean.TRUE.equals(state.get("crmPassed"))&&Boolean.TRUE.equals(state.get("TEST_CLEANUP_RELEASED")));
            require(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from app_user u join driver d on d.user_id=u.id where u.id=? and d.id=? and not u.active and not d.active and u.role='CONDUCTOR' and u.email like 'conductor.l02.%@rutafija.test')",Boolean.class,account,driver)));
            require(jdbc.queryForObject("select count(*) from refresh_token where user_id=? and revoked_at is null",Integer.class,account)==0);
            require(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and status='AUTHORIZED'",Integer.class,driver)==0);
            require(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and status='REVOKED'",Integer.class,driver)==1);
            require(jdbc.queryForObject("select count(*) from conductor_onboarding_request where linked_user_id=? and linked_driver_id=? and status='APPROVED' and personal_approved_at is not null",Integer.class,account,driver)==1);
            require(!Files.exists(root.resolve("backend/.local/l02-connected.active"))&&!Files.exists(root.resolve("backend/.local/l02-connected.private"))) ;
            require(Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)));jdbc.queryForObject("select pg_advisory_unlock(7518440514)",Boolean.class);
            result.put("accountId",account);result.put("driverId",driver);result.put("fixtureDisabledNotDeleted",true);result.put("temporaryRefreshCredentialsRevoked",true);
            result.put("unitPeriodRevokedHistoryPreserved",true);result.put("personalApprovalPreserved",true);result.put("activeMarkerReleased",true);result.put("advisoryLockFree",true);result.put("privateFixtureSecretsRemoved",true);
            Map<String,Integer> ledger=new LinkedHashMap<>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))ledger.put(role,jdbc.queryForObject("select count(*) from pilot_account_creation where role=?",Integer.class,role));
            require(ledger.equals(state.get("ledgerAfter")));result.put("ledger",ledger);result.put("historicalCreationPreserved",true);result.put("globalCleanupExecuted",false);
            String publicKey=jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1",String.class);
            String fingerprint=ManualL01RecoveryFixtureKeyReplacement.fingerprint(publicKey);require("5569d8c89e2118dede5ca39253a03735f1eb13b88a070beab45b245ce5914433".equals(fingerprint));result.put("L01RecoveryPublicKeySha256",fingerprint);
            result.put("result","PASS_READ_ONLY_EXACT_CASE_CLOSURE");connection.rollback();
        }
        Path output=root.resolve("docs/evidencia/L-02/conectado-"+run+"/verificacion-cierre-V"+result.get("schemaVersion")+".json");Files.write(output,ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);
        System.out.println(ManualL01HumanTrial.JSON.writeValueAsString(result));
    }
    private static void require(boolean condition){if(!condition)throw new IllegalStateException("CLOSED_CASE_POSTCONDITION_REFUSED_KEEP_STATE");}
}
