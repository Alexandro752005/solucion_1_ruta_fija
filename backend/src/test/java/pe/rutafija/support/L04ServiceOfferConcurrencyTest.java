package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** PG aggregate race only, inactive accountless resources; not an HTTP provisioning/acceptance trial. */
class L04ServiceOfferConcurrencyTest {
    @Test @Timeout(45) void oneServiceCannotHaveTwoLiveOffersEvenForDifferentResources()throws Exception {
        var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();UUID run=UUID.randomUUID(),service=UUID.randomUUID();
        UUID org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a"),actor=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
        UUID group=UUID.randomUUID(),d1=UUID.randomUUID(),d2=UUID.randomUUID(),v1=UUID.randomUUID(),v2=UUID.randomUUID(),a1=UUID.randomUUID(),a2=UUID.randomUUID();
        Instant start=Instant.now().plusSeconds(172800),end=start.plusSeconds(3600);boolean prepared=false,passed=false;String losingState="NOT_RUN";
        try(Connection guard=connection(db)){
            assertThat(scalar(guard,"select current_database()||'/'||current_user")).isEqualTo("ruta_fija_test/rf_test");
            assertThat(scalar(guard,"select version from flyway_schema_history where success order by installed_rank desc limit 1")).isEqualTo("35");
            for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active"))assertThat(Files.exists(Path.of(".local",marker))).isFalse();
            assertThat(scalar(guard,"select pg_try_advisory_lock(7518440514)")).isEqualTo("true");
            String ledger=scalar(guard,"select string_agg(user_id::text||role,',' order by user_id) from pilot_account_creation");
            UUID type=UUID.fromString(scalar(guard,"select id from service_type_version where organization_id='"+org+"' order by id limit 1"));
            UUID route=UUID.fromString(scalar(guard,"select id from route_version where organization_id='"+org+"' order by id limit 1"));
            try{
                guard.setAutoCommit(false);
                update(guard,"insert into transport_group(id,organization_id,name,active,created_at,updated_at) values(?,?,'SYNTHETIC SERVICE RACE',true,now(),now())",group,org);
                for(UUID d:List.of(d1,d2))update(guard,"insert into driver(id,organization_id,group_id,full_name,document_type,document_number,availability_status,location_consent,active,created_at,updated_at) values(?,?,?,'SYNTHETIC SERVICE RACE','SYNTHETIC',?,'NO_DISPONIBLE',false,false,now(),now())",d,org,group,"R-"+d.toString().substring(0,14));
                for(UUID v:List.of(v1,v2))update(guard,"insert into vehicle(id,organization_id,plate,status,active,created_at,updated_at) values(?,?,?,'INACTIVO',false,now(),now())",v,org,"L4S-"+v.toString().substring(0,8));
                update(guard,"insert into scheduled_service(id,organization_id,created_by,created_at,updated_at,scheduled_at,scheduled_end_at,route_version_id,service_type_version_id) values(?,?,?,now(),now(),?,?,?,?)",service,org,actor,Timestamp.from(start),Timestamp.from(end),route,type);
                guard.commit();guard.setAutoCommit(true);prepared=true;
                try(Connection first=connection(db);Connection second=connection(db);ExecutorService worker=Executors.newSingleThreadExecutor()){
                    first.setAutoCommit(false);second.setAutoCommit(false);update(first,"set local statement_timeout='15000ms'");update(second,"set local statement_timeout='15000ms'");
                    insert(first,a1,service,org,actor,d1,v1,route,type,start,end);
                    int pid=Integer.parseInt(scalar(second,"select pg_backend_pid()"));
                    var loser=worker.submit(()->{try{insert(second,a2,service,org,actor,d2,v2,route,type,start,end);return "UNEXPECTED_INSERT";}catch(SQLException ex){return Objects.toString(ex.getSQLState(),"NONE");}});
                    long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);boolean waiting=false;
                    while(System.nanoTime()<until){if("Lock".equals(scalar(guard,"select coalesce(wait_event_type,'') from pg_stat_activity where pid="+pid))){waiting=true;break;}Thread.sleep(10);}
                    assertThat(waiting).isTrue();assertThat(loser.isDone()).isFalse();first.commit();
                    losingState=loser.get(8,TimeUnit.SECONDS);assertThat(losingState).isEqualTo("23514");second.rollback();
                    assertThat(scalar(guard,"select count(*) from assignment where service_id='"+service+"'")).isEqualTo("1");passed=true;
                }
            } finally {
                if(!guard.getAutoCommit())guard.rollback();
                if(prepared){
                    // Exact technical retirement. Keep service, offer, receipt and inactive resources as history.
                    guard.setAutoCommit(false);
                    update(guard,"update assignment set status='CANCELLED',cancelled_at=now(),cancellation_reason='SYNTHETIC TECHNICAL RETIRE',updated_at=now() where id=? and service_id=? and status='PENDING_RESPONSE'",a1,service);
                    update(guard,"update scheduled_service set status='CANCELLED',cancelled_at=now(),cancellation_reason='SYNTHETIC TECHNICAL RETIRE',updated_at=now(),version=version+1 where id=? and organization_id=?",service,org);
                    update(guard,"update transport_group set active=false,updated_at=now() where id=? and organization_id=?",group,org);
                    update(guard,"insert into scheduled_service_operation(id,organization_id,actor_id,service_id,operation_key,action,payload_sha256,previous_offer_id,reason,occurred_at,resulting_version) select ?,?,?,?,?,'CANCEL_SERVICE',?,?, 'SYNTHETIC TECHNICAL RETIRE',now(),version from scheduled_service where id=?",UUID.randomUUID(),org,actor,service,"technical-service-race-"+run,"0".repeat(64),a1,service);
                    guard.commit();guard.setAutoCommit(true);
                    assertThat(scalar(guard,"select string_agg(user_id::text||role,',' order by user_id) from pilot_account_creation")).isEqualTo(ledger);
                    assertThat(scalar(guard,"select count(*) from assignment where service_id='"+service+"' and status in ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')")).isEqualTo("0");
                    var evidence=new LinkedHashMap<String,Object>();evidence.put("runId",run);evidence.put("database","ruta_fija_test");evidence.put("role","rf_test");
                    evidence.put("serviceId",service);evidence.put("offerId",a1);evidence.put("groupId",group);evidence.put("driverIds",List.of(d1,d2));evidence.put("vehicleIds",List.of(v1,v2));
                    evidence.put("result",passed?"PASS_PG_SINGLE_LIVE_OFFER":"FAIL_PRESERVE_CASE");evidence.put("losingSqlState",losingState);
                    evidence.put("cleanup","EXACT_SERVICE_CANCELLED_HISTORY_AND_INACTIVE_ACCOUNTLESS_RESOURCES_RETAINED");
                    evidence.put("ledgerPreserved",true);evidence.put("accountsCreated",0);evidence.put("normalHttpOrApprovalEvidence",false);
                    Path folder=Path.of("../docs/evidencia/L-04/incremento-3-servicios/concurrencia").normalize();Files.createDirectories(folder);
                    Files.writeString(folder.resolve(run+".json"),new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
                }
                scalar(guard,"select pg_advisory_unlock(7518440514)");
            }
        }
    }
    private void insert(Connection c,UUID id,UUID service,UUID org,UUID actor,UUID driver,UUID vehicle,UUID route,UUID type,Instant start,Instant end)throws SQLException{
        update(c,"insert into assignment(id,service_id,organization_id,created_by,driver_id,vehicle_id,status,response_mode,response_deadline_at,origin_text,destination_text,scheduled_at,scheduled_end_at,route_version_id,service_type_version_id,margin_before_minutes,margin_after_minutes,created_at,updated_at) select ?,?,?,?,?,?,'PENDING_RESPONSE','MOBILE_CONFIRMATION',now()+interval '10 minutes','SYNTHETIC','SYNTHETIC',?,?,?,?,before_minutes,after_minutes,now(),now() from service_type_version where id=?",id,service,org,actor,driver,vehicle,Timestamp.from(start),Timestamp.from(end),route,type,type);
    }
}
