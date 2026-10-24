package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** Independent read-only observation of the two-account authorized L05 case after selective closure. */
public final class L05ConnectedPostflight {
    private static void require(boolean value,String code){if(!value)throw new IllegalStateException(code);}
    public static void main(String[] args){try{
        require(args.length==0,"NO_ARGUMENTS_ALLOWED");var json=new ObjectMapper();
        Path root=Path.of("..").toAbsolutePath().normalize();
        var s=json.readTree(Files.readAllBytes(root.resolve("backend/.local/l05-connected-state.json")));
        require("844b3bf6-f2fa-4666-bd0e-b1f7a1188696".equals(s.path("runId").asText()),"EXACT_RUN_REQUIRED");
        require(s.path("TEST_CLEANUP_RELEASED").asBoolean()&&s.path("privateFixtureSecretsRemoved").asBoolean(),"CLOSURE_REQUIRED");
        require("PASS_REAL_CRM_FLUTTER_HTTP_POSTGRES_SIMULATED_GPS_MAIL".equals(s.path("result").asText()),"CONNECTED_PASS_REQUIRED");
        var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();
        var families=new TreeSet<UUID>();Path folder=root.resolve("docs/evidencia/L-05/conectado-"+s.path("runId").asText());
        try(var files=Files.list(folder)){for(Path p:files.filter(p->p.getFileName().toString().startsWith("observacion-")).toList()){
            var observation=json.readTree(Files.readAllBytes(p));if(observation.hasNonNull("browserRefreshFamilyId"))families.add(UUID.fromString(observation.path("browserRefreshFamilyId").asText()));
        }}
        try(Connection c=connection(db)){
            c.setAutoCommit(false);c.setReadOnly(true);
            require("ruta_fija_test/rf_test".equals(scalar(c,"select current_database()||'/'||current_user")),"TEST_TARGET_REQUIRED");
            require("42".equals(scalar(c,"select version from flyway_schema_history where success order by installed_rank desc limit 1")),"V42_REQUIRED");
            require(s.path("participants").size()==2&&s.path("caseServices").size()==4,"EXACT_CASE_RESOURCES_REQUIRED");
            var accounts=new ArrayList<UUID>();
            for(JsonNode person:s.path("participants")){
                UUID account=UUID.fromString(person.path("accountId").asText()),driver=UUID.fromString(person.path("driverId").asText());accounts.add(account);
                require("1".equals(scalar(c,"select count(*) from app_user u join driver d on d.user_id=u.id where u.id='"+account+"' and d.id='"+driver+"' and u.role='CONDUCTOR' and not u.active and not d.active and u.organization_id='"+UUID.fromString(s.path("organizationId").asText())+"'")),"EXACT_ACCOUNT_DISABLED_REQUIRED");
                require("0".equals(scalar(c,"select count(*) from refresh_token where user_id='"+account+"' and revoked_at is null")),"CONDUCTOR_SESSIONS_REVOKED_REQUIRED");
                require("0".equals(scalar(c,"select count(*) from driver_unit_period where driver_id='"+driver+"' and status='AUTHORIZED' and revoked_at is null")),"NO_AUTHORIZED_PERIOD_REQUIRED");
                for(String table:List.of("vehicle","transport_group")){
                    UUID id=UUID.fromString(person.path(table.equals("vehicle")?"vehicleId":"groupId").asText());
                    require("1".equals(scalar(c,"select count(*) from "+table+" where id='"+id+"' and not active")),"EXACT_RESOURCE_DISABLED_REQUIRED");
                }
            }
            for(JsonNode service:s.path("caseServices")){
                UUID id=UUID.fromString(service.asText());
                require("1".equals(scalar(c,"select count(*) from scheduled_service where id='"+id+"' and status in ('COMPLETED','CANCELLED')")),"TERMINAL_SERVICE_REQUIRED");
                require("0".equals(scalar(c,"select count(*) from service_execution_segment where service_id='"+id+"' and ended_at is null")),"NO_OPEN_SEGMENT_REQUIRED");
                require("0".equals(scalar(c,"select count(*) from assignment where service_id='"+id+"' and status in ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')")),"NO_LIVE_CASE_RESERVATION_REQUIRED");
            }
            for(String table:List.of("route_definition","service_type")){
                UUID id=UUID.fromString(s.path(table.equals("route_definition")?"routeId":"serviceTypeId").asText());
                require("1".equals(scalar(c,"select count(*) from "+table+" where id='"+id+"' and not active")),"CATALOG_RETIRED_REQUIRED");
            }
            for(UUID family:families)require("0".equals(scalar(c,"select count(*) from refresh_token where family_id='"+family+"' and revoked_at is null")),"TEST_ADMIN_SESSION_REVOKED_REQUIRED");
            var ledger=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))ledger.put(role,Integer.valueOf(scalar(c,"select count(*) from pilot_account_creation where role='"+role+"'")));
            require(ledger.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",4)),"EXACT_HISTORICAL_LEDGER_REQUIRED");
            require("2".equals(scalar(c,"select count(*) from admin_terms_acceptance")),"ACCEPTANCES_PRESERVED_REQUIRED");
            for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment")){
                var original=s.path("antecedentRowHashes").path(table);require(original.isObject(),"BASELINE_REQUIRED");
                var ids=original.fieldNames();while(ids.hasNext()){
                    String id=ids.next();UUID.fromString(id);String key=table.equals("pilot_account_creation")?"user_id":"id";
                    String row=scalar(c,"select to_jsonb(t)::text from "+table+" t where "+key+"='"+id+"'");
                    String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(row.getBytes(StandardCharsets.UTF_8)));
                    require(hash.equals(original.path(id).asText()),"ANTECEDENT_ROW_CHANGED");
                }
            }
            for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active","l05-connected.active","l05-connected.private"))require(!Files.exists(root.resolve("backend/.local/"+marker)),"PROTECTION_OR_SECRET_STILL_PRESENT");
            require("true".equals(scalar(c,"select pg_try_advisory_lock(7518440514)")),"CLEANUP_LOCK_MUST_BE_FREE");scalar(c,"select pg_advisory_unlock(7518440514)");c.rollback();
            var result=new LinkedHashMap<String,Object>();result.put("observedAt",Instant.now().toString());result.put("runId",s.path("runId").asText());result.put("database","ruta_fija_test");result.put("role","rf_test");result.put("schemaVersion","42");result.put("readOnly",true);
            result.put("result","PASS_EXACT_CONNECTED_RETIREMENT");result.put("newAccounts",accounts);result.put("accountsAndResourcesDisabled",true);result.put("noOpenSegmentsOrLiveReservations",true);result.put("allConductorRefreshRevoked",true);result.put("adminTestRefreshFamiliesRevoked",families);result.put("antecedentRowsPreserved",true);result.put("ledger",ledger);result.put("remainingHistoricalCreations",Map.of("SUPER_ADMIN",0,"ADMIN",1,"CONDUCTOR",16));result.put("termsAcceptances",2);result.put("TEST_CLEANUP_RELEASED",true);result.put("privateFixtureSecretsRemoved",true);result.put("historyAndCountersRetained",true);result.put("globalCleanup",false);
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }catch(Exception failure){String code=Objects.toString(failure.getMessage(),"");if(!code.matches("[A-Z_]+"))code="DETAILS_REDACTED";System.out.println("{\"result\":\"REFUSED\",\"check\":\""+code+"\"}");System.exit(1);}}
}
