package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** Exact read-only check of the accountless V35 race; does not retire or repair anything. */
public final class L04ServicePostflight {
    public static void main(String[] args){try{
        if(args.length!=0)throw new IllegalStateException();var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();var json=new ObjectMapper();
        Path folder=Path.of("../docs/evidencia/L-04/incremento-3-servicios/concurrencia").normalize();
        List<Path> files;try(var paths=Files.list(folder)){files=paths.filter(p->p.toString().endsWith(".json")).toList();}
        if(files.size()!=1)throw new IllegalStateException();var fixture=json.readTree(Files.readAllBytes(files.getFirst()));
        if(!"PASS_PG_SINGLE_LIVE_OFFER".equals(fixture.path("result").asText()))throw new IllegalStateException();
        UUID service=UUID.fromString(fixture.path("serviceId").asText()),offer=UUID.fromString(fixture.path("offerId").asText());
        UUID org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a"),group=UUID.fromString(fixture.path("groupId").asText());
        try(Connection c=connection(db)){
            c.setAutoCommit(false);c.setReadOnly(true);
            require("ruta_fija_test/rf_test",scalar(c,"select current_database()||'/'||current_user"));
            require("35",scalar(c,"select version from flyway_schema_history where success order by installed_rank desc limit 1"));
            require("1",scalar(c,"select count(*) from scheduled_service where id='"+service+"' and organization_id='"+org+"' and status='CANCELLED' and cancellation_reason='SYNTHETIC TECHNICAL RETIRE' and cancelled_at is not null"));
            require("1",scalar(c,"select count(*) from assignment where id='"+offer+"' and service_id='"+service+"' and status='CANCELLED' and accepted_at is null"));
            require("0",scalar(c,"select count(*) from assignment where service_id='"+service+"' and status in ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')"));
            require("1",scalar(c,"select count(*) from scheduled_service_operation where service_id='"+service+"' and action='CANCEL_SERVICE' and reason='SYNTHETIC TECHNICAL RETIRE'"));
            require("1",scalar(c,"select count(*) from transport_group where id='"+group+"' and organization_id='"+org+"' and not active"));
            for(var d:fixture.path("driverIds")){UUID id=UUID.fromString(d.asText());require("1",scalar(c,"select count(*) from driver where id='"+id+"' and organization_id='"+org+"' and group_id='"+group+"' and user_id is null and not active and availability_status='NO_DISPONIBLE'"));}
            for(var v:fixture.path("vehicleIds")){UUID id=UUID.fromString(v.asText());require("1",scalar(c,"select count(*) from vehicle where id='"+id+"' and organization_id='"+org+"' and not active and status='INACTIVO'"));}
            for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active"))if(Files.exists(Path.of(".local",marker)))throw new IllegalStateException();
            require("true",scalar(c,"select pg_try_advisory_lock(7518440514)"));scalar(c,"select pg_advisory_unlock(7518440514)");c.rollback();
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("observedAt",Instant.now().toString(),"database","ruta_fija_test","role","rf_test","readOnly",true,
                "serviceId",service,"offerId",offer,"liveReservations",0,"inactiveAccountlessResourcesRetained",true,"historyAndReceiptRetained",true,"result","PASS_EXACT_TECHNICAL_RETIREMENT")));
        }
    }catch(Exception error){System.out.println("{\"result\":\"REFUSED_DETAILS_REDACTED\"}");System.exit(1);}}
    private static void require(String expected,String actual){if(!expected.equals(actual))throw new IllegalStateException();}
}
