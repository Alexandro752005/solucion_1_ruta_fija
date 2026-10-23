package pe.rutafija.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import java.nio.file.*;
import java.sql.Connection;
import java.time.Instant;
import java.util.*;

/** Selective recovery for the two technical resources left by the failed
 * concurrency-test cleanup. No account, documentary history or ledger deletion. */
public final class L02TechnicalFixtureRecovery {
    public static void main(String[] args)throws Exception{
        boolean cleanup=args.length==2&&"--cleanup-exact".equals(args[0]);
        if(args.length!=0&&!cleanup)throw new IllegalArgumentException("INSPECT_OR_EXACT_ID_REQUIRED");
        try(Connection connection=ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration())){
            connection.setReadOnly(!cleanup);connection.setAutoCommit(false);
            JdbcTemplate jdbc=new JdbcTemplate(new SingleConnectionDataSource(connection,true));
            if(!"ruta_fija_test".equals(jdbc.queryForObject("select current_database()",String.class))||!"rf_test".equals(jdbc.queryForObject("select current_user",String.class)))throw new IllegalStateException("TEST_TARGET_REQUIRED");
            if(!Boolean.TRUE.equals(jdbc.queryForObject("select pg_try_advisory_xact_lock(7518440514)",Boolean.class)))throw new IllegalStateException("ACTIVE_TRIAL_PROTECTION");
            var candidates=jdbc.queryForList("select id,legal_name,created_at from organization where legal_name like 'SYNTHETIC-CONSTRAINT-ONLY-%' order by created_at,id");
            if(!cleanup){System.out.println(ManualL01HumanTrial.JSON.writeValueAsString(Map.of("mode","READ_ONLY","technicalFixtures",candidates)));connection.rollback();return;}
            UUID org=UUID.fromString(args[1]);
            if(candidates.stream().filter(r->org.equals(r.get("id"))).count()!=1)throw new IllegalStateException("EXACT_TECHNICAL_ORGANIZATION_REQUIRED");
            int drivers=jdbc.queryForObject("select count(*) from driver where organization_id=? and user_id is null and not active and full_name='SYNTHETIC CONSTRAINT ONLY'",Integer.class,org);
            int vehicles=jdbc.queryForObject("select count(*) from vehicle where organization_id=? and not active and status='INACTIVO' and plate like 'S-%'",Integer.class,org);
            if(drivers!=2||vehicles!=2||jdbc.queryForObject("select count(*) from app_user where organization_id=?",Integer.class,org)!=0||jdbc.queryForObject("select count(*) from assignment where organization_id=?",Integer.class,org)!=0)throw new IllegalStateException("TECHNICAL_FIXTURE_SHAPE_MISMATCH");
            Map<String,Object> evidence=new LinkedHashMap<>();evidence.put("organizationId",org);evidence.put("observedAt",Instant.now().toString());
            evidence.put("periods",jdbc.queryForList("select id,driver_id,vehicle_id,status,reason from driver_unit_period where organization_id=?",org));
            evidence.put("accountCreations",0);evidence.put("globalCleanup",false);
            String ledger=jdbc.queryForObject("select string_agg(user_id::text||':'||role,',' order by user_id) from pilot_account_creation",String.class);
            jdbc.update("delete from driver_unit_period where organization_id=? and reason='SYNTHETIC CONSTRAINT ONLY'",org);
            jdbc.update("delete from driver where organization_id=? and user_id is null and not active and full_name='SYNTHETIC CONSTRAINT ONLY'",org);
            jdbc.update("delete from vehicle where organization_id=? and not active and status='INACTIVO' and plate like 'S-%'",org);
            jdbc.update("delete from transport_group where organization_id=? and name='SYNTHETIC CONSTRAINT ONLY'",org);
            jdbc.update("delete from organization where id=? and legal_name like 'SYNTHETIC-CONSTRAINT-ONLY-%'",org);
            if(!Objects.equals(ledger,jdbc.queryForObject("select string_agg(user_id::text||':'||role,',' order by user_id) from pilot_account_creation",String.class)))throw new IllegalStateException("LEDGER_MISMATCH");
            connection.commit();evidence.put("result","PASS_EXACT_TECHNICAL_RESOURCES_REMOVED_LEDGER_PRESERVED");
            Path output=ManualL01HumanTrial.ROOT.resolve("docs/evidencia/L-02/recuperacion-fixture-tecnico-"+org+".json");Files.createDirectories(output.getParent());Files.write(output,ManualL01HumanTrial.JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence));
            System.out.println(ManualL01HumanTrial.JSON.writeValueAsString(evidence));
        }
    }
}
