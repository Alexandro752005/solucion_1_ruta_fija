package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Independent read-only check after selective retirement; never a fixture or purge. */
public final class L07ConnectedPostflight {
    private static final String RUN="6b496135-b86b-4c5b-938f-a60ddcfe32db";
    private static void require(boolean value,String code){if(!value)throw new IllegalStateException(code);}
    private static String scalar(Connection c,String sql,Object... args)throws SQLException{
        try(var p=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)p.setObject(i+1,args[i]);try(var r=p.executeQuery()){require(r.next(),"EXPECTED_READ_ROW");return r.getString(1);}}
    }
    private static UUID id(JsonNode s,String field){return UUID.fromString(s.path(field).asText());}
    public static void main(String[] args){try{
        require(args.length==0,"NO_ARGUMENTS_ALLOWED");var json=new ObjectMapper();Path root=Path.of("..").toAbsolutePath().normalize();
        var s=json.readTree(Files.readAllBytes(root.resolve("backend/.local/l07-connected-state.json")));
        require(RUN.equals(s.path("runId").asText()),"EXACT_RUN_REQUIRED");require("PASS_REAL_CRM_FLUTTER_HTTP_POSTGRES_SIMULATED_MAIL".equals(s.path("result").asText()),"CONNECTED_PASS_REQUIRED");
        require(s.path("TEST_CLEANUP_RELEASED").asBoolean()&&s.path("privateFixtureSecretsRemoved").asBoolean(),"SELECTIVE_CLOSURE_REQUIRED");
        for(String step:List.of("CASE_SERVICES","FLUTTER_HOME","FLUTTER_PREFERENCES","CRM_DASHBOARD","CRM_PLATFORM","CRM_INITIAL_EMISSION","NORMAL_EXECUTION","NORMAL_TIME_CORRECTION","CRM_NEXT_EMISSION","FLUTTER_OWN_REPORT","EMISSION_HTTP_RACE","REPORT_SCOPE_DENIALS","SAME_BINARY_RETURN"))require(s.path(step).asBoolean(),"CONNECTED_STAGE_NOT_PASSED_"+step);
        for(String name:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active","l05-connected.active","l06-connected.active","l07-connected.active","l07-connected.private"))require(!Files.exists(root.resolve("backend/.local/"+name)),"PROTECTION_OR_SECRET_STILL_PRESENT");
        for(var name:s.path("diagnosticPrivateFiles"))require(!Files.exists(root.resolve("backend/.local/"+name.asText())),"PRIVATE_CLIENT_DIAGNOSTIC_STILL_PRESENT");
        var database=NativePostgresTestDatabase.load();database.assertTestRolePermissions();
        try(Connection c=ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())){
            c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setReadOnly(true);
            require("ruta_fija_test/rf_test".equals(scalar(c,"select current_database()||'/'||current_user")),"TEST_TARGET_REQUIRED");require("48".equals(scalar(c,"select version from flyway_schema_history where success order by installed_rank desc limit 1")),"V48_REQUIRED");
            UUID account=id(s,"accountId"),driver=id(s,"driverId"),org=id(s,"organizationId"),service=id(s,"serviceId");
            require("1".equals(scalar(c,"select count(*) from app_user u join driver d on d.user_id=u.id where u.id=? and d.id=? and u.organization_id=? and d.organization_id=u.organization_id and u.role='CONDUCTOR' and not u.active and not d.active",account,driver,org)),"EXACT_ACCOUNT_DRIVER_DISABLED_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from refresh_token where user_id=? and revoked_at is null",account)),"CONDUCTOR_SESSIONS_REVOKED_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from driver_unit_period where driver_id=? and status='AUTHORIZED' and revoked_at is null",driver)),"NO_AUTHORIZED_PERIOD_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from assignment where driver_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')",driver)),"NO_LIVE_COMMITMENT_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from vehicle where id=? and organization_id=? and not active",id(s,"vehicleId"),org)),"EXACT_VEHICLE_RETIRED_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from transport_group where id=? and organization_id=? and not active",id(s,"groupId"),org)),"EXACT_GROUP_RETIRED_REQUIRED");
            require("false".equals(scalar(c,"select active::text from route_definition where id=? and organization_id=?",id(s,"routeId"),org)),"EXACT_ROUTE_RETIRED_REQUIRED");
            require("false".equals(scalar(c,"select active::text from service_type where id=? and organization_id=?",id(s,"typeId"),org)),"EXACT_TYPE_RETIRED_REQUIRED");
            for(var family:s.path("browserRefreshFamilyIds"))require("0".equals(scalar(c,"select count(*) from refresh_token where family_id=? and revoked_at is null",UUID.fromString(family.asText()))),"TEST_BROWSER_SESSIONS_REVOKED_REQUIRED");
            var ledger=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))ledger.put(role,Integer.valueOf(scalar(c,"select count(*) from pilot_account_creation where role=?",role)));
            require(ledger.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",6)),"EXACT_HISTORICAL_LEDGER_REQUIRED");require("2".equals(scalar(c,"select count(*) from admin_terms_acceptance")),"ACCEPTANCES_PRESERVED_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from service_execution_segment where service_id=? and driver_id=? and end_kind='COMPLETED' and ended_at is not null",service,driver)),"ONE_COMPLETED_ORIGINAL_SEGMENT_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from service_execution_segment where driver_id=? and ended_at is null",driver)),"NO_OPEN_EXECUTION_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from service_time_correction where id=? and service_id=?",id(s,"correctionId"),service)),"NORMAL_TIME_CORRECTION_REQUIRED");
            require(s.path("firstEmissionSha256").asText().equals(scalar(c,"select content_sha256 from report_emission where id=?",id(s,"currentEmissionId"))),"FIRST_EMISSION_IMMUTABLE_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from report_emission_content c join report_emission_series s on s.id=c.series_id where c.emission_id=? and s.issuer_id=? and s.own_driver_id=? and s.scope='OWN_PARTICIPATION'",id(s,"ownEmissionId"),account,driver)),"OWN_REPORT_OWNERSHIP_REQUIRED");
            require("1".equals(scalar(c,"select count(*) from mobile_preferences where user_id=? and organization_id=? and text_size='LARGE' and not optional_notice_alerts and version>=1",account,org)),"OWN_PREFERENCES_PERSISTED_REQUIRED");
            require("0".equals(scalar(c,"select count(*) from pilot_lifecycle")),"NO_ACTUAL_PILOT_START_RECORDED_REQUIRED");
            var auditChecks=new ArrayList<Map<String,Object>>();
            for(var expected:List.of(
                Map.entry("CONDUCTOR_PERSONAL_APPROVED",id(s,"requestId")),Map.entry("CONDUCTOR_ACCOUNT_PROVISIONED",id(s,"requestId")),
                Map.entry("DRIVER_QUALIFICATION_CHANGED",driver),Map.entry("ASSIGNMENT_MOBILE_CONFIRMATION_REQUESTED",id(s,"currentOfferId")),
                Map.entry("ASSIGNMENT_MOBILE_ACCEPTED",id(s,"currentOfferId")),Map.entry("ASSIGNMENT_MOBILE_STARTED",id(s,"currentOfferId")),
                Map.entry("ASSIGNMENT_MOBILE_COMPLETED",id(s,"currentOfferId")),Map.entry("SERVICE_TIMES_CORRECTED",service),
                Map.entry("MOBILE_PREFERENCES_CHANGED",account),Map.entry("REPORT_EMITTED",id(s,"currentEmissionId")),
                Map.entry("REPORT_EMITTED",id(s,"latestEmissionId")),Map.entry("REPORT_EMITTED",id(s,"ownEmissionId")),
                Map.entry("L07_SYNTHETIC_FIXTURE_DISABLED",account))){
                int count=Integer.parseInt(scalar(c,"select count(*) from audit_event where action=? and entity_id=? and organization_id=? and occurred_at is not null and metadata_json->>'auditOutcome'='SUCCEEDED'",expected.getKey(),expected.getValue(),org));
                require(count>0,"PERSISTED_AUDIT_EVENT_REQUIRED_"+expected.getKey());
                auditChecks.add(Map.of("action",expected.getKey(),"entityId",expected.getValue(),"organizationId",org,"persistedSuccessfulCount",count));
            }
            for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment","incident","announcement","audit_event","report_emission")){
                var originals=s.path("antecedentRowHashes").path(table);require(originals.isObject(),"BASELINE_REQUIRED");var ids=originals.fieldNames();
                while(ids.hasNext()){String ref=ids.next(),key=table.equals("pilot_account_creation")?"user_id":"id";String row=scalar(c,"select to_jsonb(t)::text from "+table+" t where "+key+"=?",UUID.fromString(ref));String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(row.getBytes(StandardCharsets.UTF_8)));require(sha.equals(originals.path(ref).asText()),"ANTECEDENT_ROW_CHANGED_"+table);}
            }
            require("true".equals(scalar(c,"select pg_try_advisory_lock(7518440514)::text")),"CLEANUP_LOCK_MUST_BE_FREE");require("true".equals(scalar(c,"select pg_advisory_unlock(7518440514)::text")),"CLEANUP_LOCK_RELEASE_REQUIRED");c.rollback();
            var result=new LinkedHashMap<String,Object>();result.put("observedAt",Instant.now().toString());result.put("runId",RUN);result.put("database","ruta_fija_test");result.put("role","rf_test");result.put("readOnly",true);result.put("schemaVersion","48");result.put("result","PASS_EXACT_CONNECTED_RETIREMENT");result.put("ledger",ledger);result.put("remainingHistoricalCreations",Map.of("SUPER_ADMIN",0,"ADMIN",1,"CONDUCTOR",14));result.put("termsAcceptances",2);result.put("accountsResourcesRetired",true);result.put("sessionsPeriodsRevokedNoLiveCommitments",true);result.put("reportVersionsAndOriginalExecutionPreserved",true);result.put("antecedentRowsPreserved",true);result.put("privateFixtureSecretsRemoved",true);result.put("TEST_CLEANUP_RELEASED",true);result.put("cleanupLockFree",true);result.put("globalCleanup",false);result.put("pilotEnabled",false);result.put("L08Started",false);
            result.put("recordedAuditEventsChecked",auditChecks);
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }catch(Exception failure){String code=Objects.toString(failure.getMessage(),"");if(!code.matches("[A-Z][A-Z0-9_]{0,120}"))code="DETAILS_REDACTED";System.out.println("{\"result\":\"REFUSED\",\"check\":\""+code+"\",\"causeType\":\""+failure.getClass().getSimpleName()+"\"}");System.exit(1);}}
}
