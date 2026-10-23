package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** Real PG exclusion races using accountless resources. Not two HTTP requests or an approval trial. */
class L04BufferedReservationConcurrencyTest {
    @Test @Timeout(45) void driverMarginExcludesAnotherUnitUntilTheExactBoundary()throws Exception {race(true);}
    @Test @Timeout(45) void unitMarginExcludesAnotherDriverUntilTheExactBoundary()throws Exception {race(false);}
    private void race(boolean sameDriver)throws Exception {
        var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();UUID run=UUID.randomUUID();String tag=run.toString();
        UUID org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a"),actor=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
        UUID type=UUID.randomUUID(),typeVersion=UUID.randomUUID(),group=UUID.randomUUID();
        UUID d1=UUID.randomUUID(),d2=UUID.randomUUID(),v1=UUID.randomUUID(),v2=UUID.randomUUID();
        UUID a1=UUID.randomUUID(),a2=UUID.randomUUID(),boundary=UUID.randomUUID();
        Instant start=Instant.now().plusSeconds(172800),end=start.plusSeconds(3600);boolean committed=false,passed=false;
        try(Connection guard=connection(db)) {
            assertThat(scalar(guard,"select current_database()||'/'||current_user")).isEqualTo("ruta_fija_test/rf_test");
            assertThat(scalar(guard,"select version from flyway_schema_history where success order by installed_rank desc limit 1")).isEqualTo("34");
            for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active"))assertThat(Files.exists(Path.of(".local",marker))).isFalse();
            assertThat(scalar(guard,"select pg_try_advisory_lock(7518440514)")).isEqualTo("true");
            String ledger=scalar(guard,"select string_agg(user_id::text||role,',' order by user_id) from pilot_account_creation");
            try {
                guard.setAutoCommit(false);
                update(guard,"insert into service_type(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,?, ?,now(),now())",type,org,"SYNTHETIC MARGIN RACE "+tag,typeVersion);
                update(guard,"insert into service_type_version(id,type_id,organization_id,revision,name,before_minutes,after_minutes,content_sha256,created_by,created_at) values(?,?,?,1,'SYNTHETIC LOCK FIXTURE',5,10,?,?,now())",typeVersion,type,org,"0".repeat(64),actor);
                update(guard,"insert into transport_group(id,organization_id,name,active,created_at,updated_at) values(?,?,'SYNTHETIC MARGIN RACE',true,now(),now())",group,org);
                for(var d:List.of(d1,d2))update(guard,"insert into driver(id,organization_id,group_id,full_name,document_type,document_number,availability_status,location_consent,active,created_at,updated_at) values(?,?,?,'SYNTHETIC MARGIN RACE','SYNTHETIC',?,'NO_DISPONIBLE',false,false,now(),now())",d,org,group,"R-"+d.toString().substring(0,14));
                for(var v:List.of(v1,v2))update(guard,"insert into vehicle(id,organization_id,plate,status,active,created_at,updated_at) values(?,?,?,'INACTIVO',false,now(),now())",v,org,"L4-"+v.toString().substring(0,8));
                guard.commit();committed=true;guard.setAutoCommit(true);
                try(Connection first=connection(db);Connection second=connection(db);ExecutorService executor=Executors.newSingleThreadExecutor()) {
                    first.setAutoCommit(false);second.setAutoCommit(false);update(first,"set local statement_timeout='15000ms'");update(second,"set local statement_timeout='15000ms'");
                    insert(first,a1,org,d1,v1,actor,typeVersion,start,end);
                    int pid=Integer.parseInt(scalar(second,"select pg_backend_pid()"));
                    Future<String> rejected=executor.submit(()->{
                        try{insert(second,a2,org,sameDriver?d1:d2,sameDriver?v2:v1,actor,typeVersion,end.plusSeconds(840),end.plusSeconds(4440));return "UNEXPECTED_INSERT";}
                        catch(SQLException ex){return Objects.toString(ex.getSQLState(),"NONE");}
                    });
                    long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);boolean waiting=false;
                    while(System.nanoTime()<limit){if("Lock".equals(scalar(guard,"select coalesce(wait_event_type,'') from pg_stat_activity where pid="+pid))){waiting=true;break;}Thread.sleep(10);}
                    assertThat(waiting).isTrue();assertThat(rejected.isDone()).isFalse();first.commit();
                    assertThat(rejected.get(8,TimeUnit.SECONDS)).isEqualTo("23P01");second.rollback();
                    insert(second,boundary,org,sameDriver?d1:d2,sameDriver?v2:v1,actor,typeVersion,end.plusSeconds(900),end.plusSeconds(4500));second.rollback();passed=true;
                }
            } finally {
                if(!guard.getAutoCommit())guard.rollback();
                if(committed) {
                    guard.setAutoCommit(false);
                    for(var d:List.of(d1,d2))assertThat(scalar(guard,"select count(*) from driver where id='"+d+"' and organization_id='"+org+"' and group_id='"+group+"' and user_id is null and full_name='SYNTHETIC MARGIN RACE'")).isEqualTo("1");
                    for(var a:List.of(a1,a2,boundary)) {
                        update(guard,"delete from assignment_group_snapshot where assignment_id=? and organization_id=?",a,org);
                        update(guard,"delete from assignment where id=? and organization_id=? and service_type_version_id=?",a,org,typeVersion);
                    }
                    for(var d:List.of(d1,d2)){update(guard,"delete from group_driver_membership where driver_id=? and organization_id=?",d,org);update(guard,"delete from driver where id=? and organization_id=? and user_id is null",d,org);}
                    for(var v:List.of(v1,v2))update(guard,"delete from vehicle where id=? and organization_id=?",v,org);
                    update(guard,"delete from transport_group where id=? and organization_id=?",group,org);
                    update(guard,"update service_type set active=false,version=version+1,updated_at=now() where id=? and organization_id=?",type,org);
                    update(guard,"insert into service_type_operation(id,organization_id,actor_id,operation_key,action,payload_sha256,type_id,resulting_version,reason,occurred_at) values(?,?,?,?,'TECHNICAL_RETIRE',?,?,2,'SYNTHETIC CONCURRENCY FIXTURE CLEANUP',now())",UUID.randomUUID(),org,actor,"technical-race-"+run,"0".repeat(64),type);
                    guard.commit();guard.setAutoCommit(true);
                    assertThat(scalar(guard,"select string_agg(user_id::text||role,',' order by user_id) from pilot_account_creation")).isEqualTo(ledger);
                    assertThat(scalar(guard,"select count(*) from service_type_version where id='"+typeVersion+"'")).isEqualTo("1");
                    var result=new LinkedHashMap<String,Object>();result.put("runId",run.toString());result.put("database","ruta_fija_test");result.put("role","rf_test");result.put("technicalAccountlessFixture",true);result.put("technicalLegacyRowMode",true);
                    result.put("typeId",type.toString());result.put("typeVersionId",typeVersion.toString());result.put("resource",sameDriver?"DRIVER":"VEHICLE");result.put("result",passed?"PASS_PG_BUFFER_EXCLUSION_AND_BOUNDARY":"FAIL_NEEDS_REVIEW");
                    result.put("cleanup","EXACT_RESOURCES_REMOVED_TYPE_RETIRED_VERSION_AND_RECEIPT_RETAINED");result.put("accountCreations",0);result.put("ledgerPreserved",true);
                    Path evidence=Path.of("../docs/evidencia/L-04/incremento-2-margenes/concurrencia").normalize();Files.createDirectories(evidence);Files.writeString(evidence.resolve(run+".json"),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result));
                }
                scalar(guard,"select pg_advisory_unlock(7518440514)");
            }
        }
    }
    private void insert(Connection c,UUID id,UUID org,UUID driver,UUID unit,UUID actor,UUID type,Instant start,Instant end)throws SQLException {
        update(c,"insert into assignment(id,organization_id,driver_id,vehicle_id,created_by,status,origin_text,destination_text,scheduled_at,scheduled_end_at,service_type_version_id,margin_before_minutes,margin_after_minutes,created_at,updated_at) values(?,?,?,?,?,'SCHEDULED','SYNTHETIC','SYNTHETIC',?,?,?,5,10,now(),now())",id,org,driver,unit,actor,Timestamp.from(start),Timestamp.from(end),type);
    }
}
