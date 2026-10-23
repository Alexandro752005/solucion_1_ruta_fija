package pe.rutafija.support;
import com.fasterxml.jackson.databind.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Read-only verification of the exact archived L03 technical smoke, no secret columns. */
public final class L03ConnectedPostflight {
    public static void main(String[] args)throws Exception {
        if(args.length!=0)throw new IllegalArgumentException("NO_ARGUMENTS_ALLOWED");
        var json=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        Path stateFile=Path.of(".local/l03-connected-state.json");var state=json.readTree(Files.readAllBytes(stateFile));
        if(!"2e734706-f7ef-4947-b940-1f64fd13f86a".equals(state.path("runId").asText())||!state.path("TEST_CLEANUP_RELEASED").asBoolean())throw new IllegalStateException("EXACT_CLOSED_CASE_REQUIRED");
        var database=NativePostgresTestDatabase.load();database.assertTestRolePermissions();
        try(Connection connection=L03GroupSnapshotConcurrencyTest.connection(database)){
            connection.setAutoCommit(false);connection.setReadOnly(true);var jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            if(!"ruta_fija_test/rf_test".equals(jdbc.queryForObject("select current_database()||'/'||current_user",String.class)))throw new IllegalStateException("TEST_TARGET_REQUIRED");
            UUID vehicle=UUID.fromString(state.path("vehicleId").asText()),route=UUID.fromString(state.path("routeId").asText());
            var unit=jdbc.queryForMap("select id,status,active from vehicle where id=? and organization_id='1a759298-7831-4aaf-9299-c3920a9cc28a'",vehicle);
            var definition=jdbc.queryForMap("select id,active,version,(select count(*) from route_version where route_id=d.id) revisions from route_definition d where id=? and organization_id='1a759298-7831-4aaf-9299-c3920a9cc28a'",route);
            var maintenance=jdbc.queryForList("select id,status,starts_at,planned_ends_at,blocking_ends_at from vehicle_maintenance where vehicle_id=?",vehicle);
            Instant created=Files.readAttributes(stateFile,java.nio.file.attribute.BasicFileAttributes.class).creationTime().toInstant().minusSeconds(1);
            var families=jdbc.queryForList("select family_id,count(*) token_rows,bool_and(revoked_at is not null) all_revoked from refresh_token where user_id='92ebc07c-1319-4850-a734-f792cb299979' and created_at>=? group by family_id",Timestamp.from(created));
            boolean activeMarker=Files.exists(Path.of(".local/l03-connected.active"));boolean lockFree=Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class));if(lockFree)jdbc.queryForObject("select pg_advisory_unlock(7518440514)",Boolean.class);
            boolean passed="INACTIVO".equals(unit.get("status"))&&Boolean.FALSE.equals(unit.get("active"))&&Boolean.FALSE.equals(definition.get("active"))&&((Number)definition.get("revisions")).intValue()==1
                &&maintenance.size()==1&&"CANCELLED".equals(maintenance.getFirst().get("status"))&&families.size()==1&&Boolean.TRUE.equals(families.getFirst().get("all_revoked"))&&!activeMarker&&lockFree;
            var result=new LinkedHashMap<String,Object>();result.put("observedAt",Instant.now().toString());result.put("runId",state.path("runId").asText());result.put("database","ruta_fija_test");result.put("role","rf_test");result.put("readOnly",true);result.put("unit",unit);result.put("route",definition);result.put("maintenance",maintenance);result.put("refreshFamiliesSinceCaseCreation",families);result.put("activeMarker",activeMarker);result.put("protectionLockFree",lockFree);result.put("result",passed?"PASS_EXACT_SELECTIVE_CLOSE":"FAIL_KEEP_HISTORICAL_CASE");
            System.out.println(json.writerWithDefaultPrettyPrinter().writeValueAsString(result));connection.rollback();if(!passed)System.exit(1);
        }
    }
}
