package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** Read-only inspection of the exact two technical race cases; never repairs or removes data. */
public final class L04MarginPostflight {
    public static void main(String[] args) {
        try {
            if(args.length!=0)throw new IllegalStateException();
            var json=new ObjectMapper();var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();
            var checks=new ArrayList<Map<String,Object>>();
            try(Connection c=connection(db)) {
                c.setAutoCommit(false);c.setReadOnly(true);
                if(!"ruta_fija_test/rf_test".equals(scalar(c,"select current_database()||'/'||current_user")))throw new IllegalStateException();
                if(!Set.of("34","35").contains(scalar(c,"select version from flyway_schema_history where success order by installed_rank desc limit 1")))throw new IllegalStateException();
                Path folder=Path.of("../docs/evidencia/L-04/incremento-2-margenes/concurrencia").normalize();
                UUID laterOffer=null,laterService=null;
                Path laterFolder=Path.of("../docs/evidencia/L-04/incremento-3-servicios/concurrencia").normalize();
                if(Files.isDirectory(laterFolder))try(var laterPaths=Files.list(laterFolder)){
                    var laterFiles=laterPaths.filter(x->x.getFileName().toString().endsWith(".json")).toList();
                    if(laterFiles.size()!=1)throw new IllegalStateException();
                    var later=json.readTree(Files.readAllBytes(laterFiles.getFirst()));
                    if(!"PASS_PG_SINGLE_LIVE_OFFER".equals(later.path("result").asText()))throw new IllegalStateException();
                    laterOffer=UUID.fromString(later.path("offerId").asText());laterService=UUID.fromString(later.path("serviceId").asText());
                }
                try(var paths=Files.list(folder)) {
                    for(Path p:paths.filter(x->x.getFileName().toString().endsWith(".json")).sorted().toList()) {
                        var fixture=json.readTree(Files.readAllBytes(p));
                        if(!"PASS_PG_BUFFER_EXCLUSION_AND_BOUNDARY".equals(fixture.path("result").asText()))throw new IllegalStateException();
                        UUID type=UUID.fromString(fixture.path("typeId").asText()),version=UUID.fromString(fixture.path("typeVersionId").asText());
                        boolean retired="false".equals(scalar(c,"select active from service_type where id='"+type+"'"));
                        int revisions=Integer.parseInt(scalar(c,"select count(*) from service_type_version where id='"+version+"' and type_id='"+type+"'"));
                        int receipts=Integer.parseInt(scalar(c,"select count(*) from service_type_operation where type_id='"+type+"' and action='TECHNICAL_RETIRE'"));
                        int assignments=Integer.parseInt(scalar(c,"select count(*) from assignment where service_type_version_id='"+version+"'"));
                        int laterReferences=laterOffer==null?0:Integer.parseInt(scalar(c,"select count(*) from assignment where id='"+laterOffer+"' and service_id='"+laterService+"' and service_type_version_id='"+version+"' and status='CANCELLED' and accepted_at is null"));
                        if(!retired||revisions!=1||receipts!=1||assignments!=laterReferences)throw new IllegalStateException();
                        checks.add(Map.of("runId",fixture.path("runId").asText(),"typeId",type.toString(),"typeRetired",retired,"immutableRevisionsRetained",revisions,"technicalRetirementReceipts",receipts,"remainingAssignments",assignments,"exactLaterTechnicalHistoricalReferences",laterReferences));
                    }
                }
                if(checks.size()!=2)throw new IllegalStateException();
                for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active"))if(Files.exists(Path.of(".local",marker)))throw new IllegalStateException();
                boolean available="true".equals(scalar(c,"select pg_try_advisory_lock(7518440514)"));if(!available)throw new IllegalStateException();
                scalar(c,"select pg_advisory_unlock(7518440514)");c.rollback();
                System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(Map.of("observedAt",Instant.now().toString(),"database","ruta_fija_test","role","rf_test","readOnly",true,"cases",checks,"activeMarkers",false,"cleanupProtectionLockFree",true,"result","PASS_EXACT_TECHNICAL_RETIREMENT")));
            }
        } catch(Exception failure) {System.out.println("{\"result\":\"REFUSED_DETAILS_REDACTED\"}");System.exit(1);}
    }
}
