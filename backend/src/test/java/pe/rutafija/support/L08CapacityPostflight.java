package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.security.MessageDigest;

/** Independent SELECT-only postflight, after the capacity JVM has exited. */
public final class L08CapacityPostflight {
    private static final String RUN="860c1cdb-b062-469a-9a3f-156ea23bb656";
    private static final ObjectMapper JSON=new ObjectMapper();
    public static void main(String[] args){String phase="EXACT_AUTHORIZATION";try{
        require(args.length==0&&"L08_CAP25_01_AUTHORIZED".equals(System.getenv("RF_L08_CAP25_AUTHORIZATION")),"EXACT_CAP25_AUTH_REQUIRED");
        String url="jdbc:postgresql://127.0.0.1:55439/ruta_fija_test";require(url.equals(System.getenv("RF_L08_CAP25_URL")),"EXACT_CAP25_URL_REQUIRED");
        Path root=Path.of(System.getenv("RF_L08_CAP25_ROOT")).toRealPath(),folder=root.resolve("docs/evidencia/L-08/cap25-"+RUN),privateRoot=root.resolve("backend/.local/l08-cap25-"+RUN);
        require(!Files.exists(privateRoot.resolve("dataset.active")),"DATASET_MARKER_MUST_BE_RELEASED");
        var prepared=JSON.readTree(Files.readAllBytes(folder.resolve("preparacion.json")));
        var measured=JSON.readTree(Files.readAllBytes(folder.resolve("medicion.json")));
        var race=JSON.readTree(Files.readAllBytes(folder.resolve("retiro-concurrencia.json")));
        require(RUN.equals(prepared.path("runId").asText())&&prepared.path("retired").asBoolean()&&prepared.path("TEST_CLEANUP_RELEASED").asBoolean(),"SAME_RETIRED_DATASET_REQUIRED");
        require("PASS_MEASURED_LOCAL_SYNTHETIC_ONLY".equals(measured.path("result").asText())&&measured.path("maxObservedHttpInflight").asInt()==25&&measured.path("errorSamples").asInt()==0,"MEASURED_25_NO_ERROR_RESULT_REQUIRED");
        require(RUN.equals(race.path("caseId").asText())&&"PASS_ISOLATED_COMMITTED_RETIREMENT_RACE_SYNTHETIC_ONLY".equals(race.path("result").asText()),"SAME_COMMITTED_RACE_REQUIRED");
        var p=new Properties();p.setProperty("user","rf_test");p.setProperty("password",System.getenv("RF_L08_CAP25_TEST_PASSWORD"));p.setProperty("connectTimeout","5");p.setProperty("socketTimeout","15");p.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=15000");
        try(var c=DriverManager.getConnection(url,p)){
            c.setReadOnly(true);c.setAutoCommit(false);phase="EXACT_READ_ONLY_DATABASE_AND_FREE_CASE_LOCK";
            require("ruta_fija_test|55439|rf_test|127.0.0.1|on".equals(text(c,"select current_database()||'|'||inet_server_port()||'|'||current_user||'|'||host(inet_server_addr())||'|'||current_setting('transaction_read_only')")),"POSTFLIGHT_EXACT_TARGET_READ_ONLY_REQUIRED");
            require(bool(c,"select pg_try_advisory_xact_lock(7518440514)"),"CAP25_ADVISORY_LOCK_MUST_BE_FREE");
            phase="EXACT_DATASET_RETIREMENT_AND_HISTORY";
            require(count(c,"select count(*) from app_user")==25&&count(c,"select count(*) from app_user where active")==0,"EXACT_25_INACTIVE_ACCOUNTS_REQUIRED");
            require(count(c,"select count(*) from pilot_account_creation where role='SUPER_ADMIN'")==1&&count(c,"select count(*) from pilot_account_creation where role='ADMIN'")==4&&count(c,"select count(*) from pilot_account_creation where role='CONDUCTOR'")==20,"LEDGER_1_4_20_REQUIRED");
            require(count(c,"select count(*) from refresh_token where revoked_at is null")==0&&count(c,"select count(*) from pilot_lifecycle")==0,"NO_ACTIVE_SESSION_OR_REAL_PILOT_REQUIRED");
            for(String table:List.of("driver","vehicle","transport_group"))require(count(c,"select count(*) from "+table)==20&&count(c,"select count(*) from "+table+" where active")==0,"EXACT_20_DISABLED_FLEET_RESOURCES_REQUIRED");
            require(count(c,"select count(*) from driver_unit_period where status='AUTHORIZED'")==0,"NO_AUTHORIZED_UNIT_PERIOD_REQUIRED");
            require(count(c,"select count(*) from assignment")==100&&count(c,"select count(*) from scheduled_service")==100&&count(c,"select count(*) from assignment where status<>'CANCELLED'")==0&&count(c,"select count(*) from scheduled_service where status<>'CANCELLED'")==0,"EXACT_MEASURED_HISTORY_CANCELLED_NOT_DELETED_REQUIRED");
            require(count(c,"select count(*) from service_execution_segment")==0,"NO_PHYSICAL_SERVICE_CLAIM");
            require(count(c,"select count(*) from admin_terms_acceptance where document_version='1.0.0' and content_sha256='20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417'")==4,"EXACT_FOUR_SYNTHETIC_ACCEPTANCES_PRESERVED");
            var report=new LinkedHashMap<String,Object>();
            for(String op:List.of("CREATE","ACCEPT")){
                var values=new ArrayList<Double>();var refs=new HashSet<String>();
                for(var sample:measured.path("allSamples"))if("measurement".equals(sample.path("phase").asText())&&op.equals(sample.path("operation").asText())&&sample.path("success").asBoolean()){
                    require(sample.path("durableObservation").path("durableCommitVisible").asBoolean(),"EVERY_MEASURED_WRITE_HAS_COMMIT_OBSERVATION");
                    values.add(sample.path("durableCommitObservedMs").asDouble());require(refs.add(sample.path("reference").asText()),"UNIQUE_MEASURED_WRITE_REFERENCES_REQUIRED");
                    UUID offer=UUID.fromString(sample.path("durableObservation").path("offerId").asText());
                    require(number(c,"select count(*) from assignment where id=? and status='CANCELLED'",offer)==1,"MEASURED_OFFER_PRESERVED_AFTER_RETIREMENT");
                    if(op.equals("ACCEPT"))require(number(c,"select count(*) from mobile_command_receipt where event_id=? and assignment_id=? and command_type='ASSIGNMENT_ACCEPT' and result_status='APPLIED'",UUID.fromString(sample.path("durableObservation").path("clientEventId").asText()),offer)==1,"MEASURED_ACCEPTANCE_RECEIPT_PRESERVED");
                }
                require(values.size()==100,"EXACT_100_MEASURED_WRITES_REQUIRED");Collections.sort(values);double p95=values.get(94);
                require(p95<=2000&&Double.compare(p95,measured.path(op.equals("CREATE")?"create":"accept").path("p95DurableCommitObservedMs").asDouble())==0,"RECOMPUTED_P95_MUST_MATCH_ARCHIVE_AND_THRESHOLD");
                report.put(op,Map.of("samples",100,"p95DurableCommitObservedMs",p95));
            }
            UUID plan=UUID.fromString(race.path("planId").asText()),challenge=UUID.fromString(race.path("challengeId").asText());
            require(number(c,"select count(*) from retention_execution where plan_id=?",plan)==1&&number(c,"select count(*) from retention_personal_retirement where plan_id=? and resource_id=?",plan,challenge)==1&&number(c,"select count(*) from audit_event where action='RETENTION_PLAN_EXECUTED' and entity_id=?",plan)==1&&number(c,"select count(*) from super_admin_recovery_challenge where id=?",challenge)==0,"EXACT_ONE_COMMITTED_RETIREMENT_AUDIT_AND_TOMBSTONE_REQUIRED");
            report.put("runId",RUN);report.put("observedAt",Instant.now().toString());report.put("result","PASS_READ_ONLY_CAP25_POSTFLIGHT_LEDGER_HISTORY_RETIREMENT_AND_MEASUREMENTS");
            report.put("ledger",Map.of("SUPER_ADMIN",1,"ADMIN",4,"CONDUCTOR",20));report.put("accountsDisabled",25);report.put("allRefreshTokensRevoked",true);report.put("acceptancesPreserved",4);report.put("offersAndServicesPreserved",100);report.put("retirementExecutionAuditAndTombstone",1);report.put("advisoryGuardFree",true);report.put("markerAbsent",true);report.put("TEST_CLEANUP_RELEASED",true);report.put("databaseWrites",0);report.put("source5432Accessed",false);report.put("restore55438Accessed",false);report.put("realParticipantsOrDevicesAccredited",false);report.put("businessRtoOrRpoAccredited",false);report.put("originalMeasurementSha256",sha(folder.resolve("medicion.json")));report.put("originalRaceSha256",sha(folder.resolve("retiro-concurrencia.json")));
            Path output=folder.resolve("postflight-solo-lectura.json");Files.write(output,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(report),StandardOpenOption.CREATE_NEW);
            c.rollback();System.out.println("CAP25_POSTFLIGHT="+report.get("result"));System.out.println("EVIDENCE_FILE="+root.relativize(output));System.out.println("EVIDENCE_SHA256="+sha(output));
        }finally{p.clear();}
    }catch(Exception failure){System.out.println("CAP25_POSTFLIGHT=STOPPED_KEEP_CASE; PHASE="+phase+"; CHECK="+safe(failure));System.exit(1);}}
    private static long number(Connection c,String sql,Object... args)throws Exception{try(var q=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);try(var r=q.executeQuery()){r.next();return r.getLong(1);}}}
    private static long count(Connection c,String sql)throws Exception{return number(c,sql);}
    private static boolean bool(Connection c,String sql)throws Exception{try(var q=c.createStatement();var r=q.executeQuery(sql)){r.next();return r.getBoolean(1);}}
    private static String text(Connection c,String sql)throws Exception{try(var q=c.createStatement();var r=q.executeQuery(sql)){r.next();return r.getString(1);}}
    private static String sha(Path p)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(p)));}
    private static String safe(Exception f){return f.getMessage()!=null&&f.getMessage().matches("[A-Z][A-Z0-9_]{2,100}")?f.getMessage():f.getClass().getSimpleName();}
    private static void require(boolean b,String code){if(!b)throw new IllegalStateException(code);}
}
