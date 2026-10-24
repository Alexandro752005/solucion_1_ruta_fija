package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** SELECT-only reconciliation after the exact FIS02 harness has retired its case. */
public final class L08PhysicalFinalPostflight {
    private static final String RUN = "3f0ba758-6eb9-4d2f-b948-dd1c7de3c430";
    public static void main(String[] args) {
        String phase = "EXACT_RETIRED_STATE";
        try {
            if (args.length != 0) throw new IllegalStateException();
            Path root = L08AvailabilityRehearsal.repositoryRoot(), local = root.resolve("backend/.local");
            var json = new ObjectMapper();
            var state = json.readTree(Files.readAllBytes(local.resolve("l08-fis02-state.json")));
            require(RUN.equals(state.path("runId").asText()) && state.path("TEST_CLEANUP_RELEASED").asBoolean());
            require(state.path("selectiveCleanup").asText().startsWith("PASS_"));
            require(!Files.exists(local.resolve("l08-connected.active")) && !Files.exists(local.resolve("l08-fis02.private")));
            var config = new HashMap<String,Object>();
            NativePostgresTestDatabase.load().registerSpringProperties((k,v) -> config.put(k,v.get()));
            require("jdbc:postgresql://127.0.0.1:5432/ruta_fija_test".equals(config.get("spring.datasource.url")));
            var properties = new Properties();
            properties.setProperty("user", "rf_test");
            properties.setProperty("password", config.get("spring.datasource.password").toString());
            properties.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=15000");
            config.clear();
            var result = new LinkedHashMap<String,Object>();
            result.put("runId", RUN); result.put("observedAt", Instant.now().toString());
            try (var c = DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/ruta_fija_test", properties)) {
                c.setReadOnly(true); phase = "TARGET_LOCK_AND_LEDGER";
                try (var q = c.createStatement(); var r = q.executeQuery("select current_database(),current_user,inet_server_port(),current_setting('transaction_read_only'),pg_try_advisory_lock(7518440514)")) {
                    require(r.next() && "ruta_fija_test".equals(r.getString(1)) && "rf_test".equals(r.getString(2)) && r.getInt(3)==5432 && "on".equals(r.getString(4)) && r.getBoolean(5));
                }
                var ledger = new TreeMap<String,Integer>();
                try (var q=c.createStatement(); var r=q.executeQuery("select role,count(*) from pilot_account_creation group by role")) { while(r.next()) ledger.put(r.getString(1),r.getInt(2)); }
                require(ledger.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",8)));
                result.put("historicalLedger",ledger); require(count(c,"select count(*) from flyway_schema_history where not success")==0); require(count(c,"select count(*) from flyway_schema_history where version='50' and success")==1); require(count(c,"select count(*) from flyway_schema_history where success")==51); result.put("flywaySchemaVersion",50); result.put("flywaySuccessfulMigrations",51);
                require(count(c,"select count(*) from admin_terms_acceptance")==2);
                result.put("termsAcceptances",2);
                UUID account=id(state,"accountId"), driver=id(state,"driverId"), vehicle=id(state,"vehicleId"), group=id(state,"groupId"), service=id(state,"serviceId"), offer=id(state,"offerId");
                phase="EXACT_DISABLED_RESOURCES_AND_SESSIONS";
                for (var item : Map.of("app_user",account,"driver",driver,"vehicle",vehicle,"transport_group",group).entrySet())
                    require(count(c,"select count(*) from "+item.getKey()+" where id=? and organization_id=? and not active",item.getValue(),L08ConnectedTrial.ORG)==1);
                require(count(c,"select count(*) from refresh_token where user_id=? and revoked_at is null",account)==0);
                require(count(c,"select count(*) from driver_unit_period where driver_id=? and status='AUTHORIZED'",driver)==0);
                require(count(c,"select count(*) from assignment where driver_id=? and status in ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')",driver)==0);
                require(count(c,"select count(*) from service_execution_segment where driver_id=? and ended_at is null",driver)==0);
                require(count(c,"select count(*) from driver_current_location where driver_id=?",driver)==0);
                require(count(c,"select count(*) from scheduled_service where id=? and status='COMPLETED'",service)==1);
                require(count(c,"select count(*) from assignment where id=? and service_id=? and driver_id=? and status='COMPLETED'",offer,service,driver)==1);
                long points=count(c,"select count(*) from service_gps_point p join service_execution_segment s on s.id=p.segment_id where s.service_id=? and p.driver_id=?",service,driver);
                require(points>0 && points==state.path("persistedCaseGpsPointCount").asLong());
                result.put("gpsHistoryPointCount",points);
                phase="ALL_ORIGINAL_BASELINE_ROWS";
                JsonNode baseline=state.path("antecedentRowHashes");
                for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment","incident","announcement","audit_event","report_emission")) {
                    String key=table.equals("pilot_account_creation")?"user_id":"id";
                    var observed=new TreeMap<String,String>();
                    try(var q=c.createStatement();var rows=q.executeQuery("select "+key+"::text,encode(sha256(convert_to(to_jsonb(t)::text,'UTF8')),'hex') from "+table+" t")) {
                        while(rows.next()) if(baseline.path(table).has(rows.getString(1))) observed.put(rows.getString(1),rows.getString(2));
                    }
                    require(json.valueToTree(observed).equals(baseline.path(table)));
                }
            }
            result.put("accountDriverVehicleGroupDisabled",true); result.put("allCaseRefreshTokensRevoked",true);
            result.put("noAuthorizedCaseUnitPeriods",true); result.put("noOpenCaseCommitmentsOrSegments",true);
            result.put("currentLocationRows",0); result.put("caseAdvisoryLockFree",true);
            result.put("markerAndPrivateFixtureAbsent",true); result.put("originalBaselineRowsPreserved",true);
            result.put("databaseWrites",0); result.put("coordinatesOrSecretsRecorded",false);
            result.put("TEST_CLEANUP_RELEASED",true); result.put("pilotEnabled",false);
            result.put("result","PASS_EXACT_FIS02_SELECTIVE_RETIREMENT_AND_PRIOR_ROWS_PRESERVED");
            Path file=root.resolve("docs/evidencia/L-08/incremento-5-candidato-final/hygiene/fis02-postflight-"+UUID.randomUUID()+".json");
            Files.write(file,json.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);
            System.out.println(json.writeValueAsString(result));
        } catch(Exception failure) {
            System.out.println("L08_FIS02_POSTFLIGHT=STOPPED_KEEP_STATE; PHASE="+phase+"; TYPE="+failure.getClass().getSimpleName());
            System.exit(1);
        }
    }
    private static UUID id(JsonNode state,String key) { return UUID.fromString(state.path(key).asText()); }
    private static long count(Connection c,String sql,Object... args)throws SQLException {
        try(var q=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]); try(var r=q.executeQuery()){require(r.next());return r.getLong(1);} }
    }
    private static void require(boolean value) { if(!value)throw new IllegalStateException(); }
}
