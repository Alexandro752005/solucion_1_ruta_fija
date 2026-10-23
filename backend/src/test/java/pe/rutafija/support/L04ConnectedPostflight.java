package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** Read-only verification of the single explicitly authorized L04 connected case. */
public final class L04ConnectedPostflight {
    public static void main(String[] args){try{
        if(args.length!=0)throw new IllegalStateException();var json=new ObjectMapper();var db=NativePostgresTestDatabase.load();
        db.assertTestRolePermissions();Path root=Path.of("..").toAbsolutePath().normalize();
        var s=json.readTree(Files.readAllBytes(root.resolve("backend/.local/l04-connected-state.json")));
        UUID run=UUID.fromString(s.path("runId").asText()),org=UUID.fromString(s.path("organizationId").asText());
        UUID account=UUID.fromString(s.path("accountId").asText()),driver=UUID.fromString(s.path("driverId").asText()),service=UUID.fromString(s.path("serviceId").asText());
        if(!s.path("TEST_CLEANUP_RELEASED").asBoolean()||!"PASS_REAL_CRM_BROWSER_FLUTTER_HTTP_POSTGRES_SIMULATED_MAIL".equals(s.path("result").asText()))throw new IllegalStateException();
        var folder=root.resolve("docs/evidencia/L-04/conectado-"+run);var families=new TreeSet<UUID>();
        try(var paths=Files.list(folder)){for(Path p:paths.filter(p->p.getFileName().toString().startsWith("observacion-")).toList()){
            var observed=json.readTree(Files.readAllBytes(p));if(observed.hasNonNull("browserRefreshFamilyId"))families.add(UUID.fromString(observed.path("browserRefreshFamilyId").asText()));
        }}
        try(Connection c=connection(db)){
            c.setAutoCommit(false);c.setReadOnly(true);require("ruta_fija_test/rf_test",scalar(c,"select current_database()||'/'||current_user"));
            require("35",scalar(c,"select version from flyway_schema_history where success order by installed_rank desc limit 1"));
            require("1",scalar(c,"select count(*) from app_user where id='"+account+"' and organization_id='"+org+"' and role='CONDUCTOR' and not active"));
            require("1",scalar(c,"select count(*) from driver where id='"+driver+"' and user_id='"+account+"' and organization_id='"+org+"' and not active"));
            require("0",scalar(c,"select count(*) from refresh_token where user_id='"+account+"' and revoked_at is null"));
            require("0",scalar(c,"select count(*) from driver_unit_period where driver_id='"+driver+"' and status='AUTHORIZED' and revoked_at is null"));
            require("0",scalar(c,"select count(*) from assignment where service_id='"+service+"' and status in ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')"));
            require("2",scalar(c,"select count(*) from assignment where service_id='"+service+"'"));
            require("1",scalar(c,"select count(*) from scheduled_service where id='"+service+"' and status='CANCELLED'"));
            require("1",scalar(c,"select count(*) from vehicle where id='"+UUID.fromString(s.path("vehicleId").asText())+"' and not active"));
            require("1",scalar(c,"select count(*) from transport_group where id='"+UUID.fromString(s.path("groupId").asText())+"' and not active"));
            require("1",scalar(c,"select count(*) from route_definition where id='"+UUID.fromString(s.path("routeId").asText())+"' and not active"));
            require("1",scalar(c,"select count(*) from service_type where id='"+UUID.fromString(s.path("serviceTypeId").asText())+"' and not active"));
            if(families.size()!=3)throw new IllegalStateException();
            for(UUID family:families)require("0",scalar(c,"select count(*) from refresh_token where family_id='"+family+"' and revoked_at is null"));
            require("1",scalar(c,"select count(*) from pilot_account_creation where role='SUPER_ADMIN'"));require("3",scalar(c,"select count(*) from pilot_account_creation where role='ADMIN'"));require("2",scalar(c,"select count(*) from pilot_account_creation where role='CONDUCTOR'"));
            require("2",scalar(c,"select count(*) from admin_terms_acceptance"));
            String originalLedger=L04DatabasePreflight.digestRows(c,"select user_id,role,created_at from pilot_account_creation where user_id<>'"+account+"' order by user_id");
            require("6f71d5455a50172f6e144b11611b51dbfcf0b389d3b43d7c026dc9d051ef8abf",originalLedger);
            for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active"))if(Files.exists(root.resolve("backend/.local/"+marker)))throw new IllegalStateException();
            if(Files.exists(root.resolve("backend/.local/l04-connected.private")))throw new IllegalStateException();
            require("true",scalar(c,"select pg_try_advisory_lock(7518440514)"));scalar(c,"select pg_advisory_unlock(7518440514)");c.rollback();
            var result=new LinkedHashMap<String,Object>();result.put("observedAt",Instant.now().toString());result.put("database","ruta_fija_test");result.put("role","rf_test");
            result.put("runId",run);result.put("accountId",account);result.put("driverId",driver);result.put("serviceId",service);result.put("readOnly",true);
            result.put("accountAndResourcesDisabled",true);result.put("liveReservations",0);result.put("conductorRefreshRevoked",true);result.put("adminTestRefreshFamiliesRevoked",families);
            result.put("privateFixtureSecretsRemoved",true);result.put("ledger",Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",2));result.put("termsAcceptances",2);
            result.put("TEST_CLEANUP_RELEASED",true);result.put("historyAndCountersRetained",true);result.put("result","PASS_EXACT_CONNECTED_RETIREMENT");
            result.put("priorLedgerSha256ExcludingAuthorizedNewAccount",originalLedger);
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        }
    }catch(Exception error){System.out.println("{\"result\":\"REFUSED_DETAILS_REDACTED\"}");System.exit(1);}}
    private static void require(String expected,String actual){if(!expected.equals(actual))throw new IllegalStateException();}
}
