package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static pe.rutafija.support.L03GroupSnapshotConcurrencyTest.*;

/** Two real PG connections test the shared scheduling-lock order, not two HTTP clients.
 * Accountless technical resources, exact cleanup only. No ledger deletion or reset. */
class L03MaintenanceSchedulingConcurrencyTest {
    @Test @Timeout(35) void maintenanceCommittedFirstIsVisibleBeforeReservationCanContinue()throws Exception{race(true);}
    @Test @Timeout(35) void reservationCommittedFirstIsVisibleBeforePreventiveCanContinue()throws Exception{race(false);}
    private void race(boolean maintenanceFirst)throws Exception {
        var db=NativePostgresTestDatabase.load();db.assertTestRolePermissions();
        UUID org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a"),actor=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
        UUID group=UUID.randomUUID(),driver=UUID.randomUUID(),vehicle=UUID.randomUUID(),assignment=UUID.randomUUID(),record=UUID.randomUUID();String tag=UUID.randomUUID().toString();boolean prepared=false;
        try(Connection guard=connection(db)) {
            assertThat(scalar(guard,"select current_database()||'/'||current_user")).isEqualTo("ruta_fija_test/rf_test");
            assertThat(scalar(guard,"select version from flyway_schema_history where success order by installed_rank desc limit 1")).isEqualTo("32");
            for(String marker:List.of(".local/l01-human-trial.active",".local/l02-connected.active",".local/l03-connected.active"))assertThat(Files.exists(Path.of(marker))).isFalse();
            assertThat(scalar(guard,"select pg_try_advisory_lock(7518440514)")).isEqualTo("true");
            String ledger=scalar(guard,"select string_agg(user_id::text,',' order by user_id) from pilot_account_creation");
            try {
                guard.setAutoCommit(false);
                update(guard,"insert into transport_group(id,organization_id,name,active,created_at,updated_at) values(?,?,'SYNTHETIC MAINTENANCE RACE',true,now(),now())",group,org);
                update(guard,"insert into driver(id,organization_id,group_id,full_name,document_type,document_number,availability_status,location_consent,active,created_at,updated_at) values(?,?,?,'SYNTHETIC MAINTENANCE RACE','SYNTHETIC',?,'NO_DISPONIBLE',false,false,now(),now())",driver,org,group,"S-"+tag.substring(0,15));
                update(guard,"insert into vehicle(id,organization_id,plate,status,active,created_at,updated_at) values(?,?,?,'INACTIVO',false,now(),now())",vehicle,org,"M-"+tag.substring(0,10));
                guard.commit();prepared=true;guard.setAutoCommit(true);
                try(Connection first=connection(db);Connection second=connection(db);ExecutorService executor=Executors.newSingleThreadExecutor()) {
                    first.setAutoCommit(false);second.setAutoCommit(false);update(first,"set local statement_timeout='12000ms'");update(second,"set local statement_timeout='12000ms'");
                    scalar(first,"select id from organization where id='"+org+"' for no key update");int pid=Integer.parseInt(scalar(second,"select pg_backend_pid()"));
                    if(maintenanceFirst)update(first,"insert into vehicle_maintenance(id,vehicle_id,organization_id,kind,starts_at,planned_ends_at,reason,created_by,created_at) values(?,?,?,'PREVENTIVE',now()+interval '1 day',now()+interval '2 days','SYNTHETIC LOCK FIXTURE',?,now())",record,vehicle,org,actor);
                    else insertAssignment(first,assignment,org,driver,vehicle,actor);
                    Future<String> seen=executor.submit(()->{
                        scalar(second,"select id from organization where id='"+org+"' for no key update");
                        return maintenanceFirst?scalar(second,"select count(*) from vehicle_maintenance where vehicle_id='"+vehicle+"' and effective_window && tstzrange(now()+interval '2 days',now()+interval '3 days','[)')")
                            :scalar(second,"select count(*) from assignment where vehicle_id='"+vehicle+"' and status='SCHEDULED' and scheduled_end_at>now()+interval '1 day'");
                    });
                    long limit=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                    while(System.nanoTime()<limit){if("Lock".equals(scalar(guard,"select coalesce(wait_event_type,'') from pg_stat_activity where pid="+pid))){waiting=true;break;}Thread.sleep(10);}
                    assertThat(waiting).isTrue();assertThat(seen.isDone()).isFalse();first.commit();assertThat(seen.get(8,TimeUnit.SECONDS)).isEqualTo("1");second.rollback();
                }
            } finally {
                if(!guard.getAutoCommit())guard.rollback();
                if(prepared){guard.setAutoCommit(false);
                    assertThat(scalar(guard,"select count(*) from driver where id='"+driver+"' and organization_id='"+org+"' and user_id is null and document_number='S-"+tag.substring(0,15)+"'")).isEqualTo("1");
                    update(guard,"delete from assignment_group_snapshot where assignment_id=? and organization_id=?",assignment,org);update(guard,"delete from assignment where id=? and organization_id=?",assignment,org);
                    update(guard,"delete from vehicle_maintenance where id=? and vehicle_id=? and reason='SYNTHETIC LOCK FIXTURE'",record,vehicle);
                    update(guard,"delete from group_driver_membership where driver_id=? and organization_id=?",driver,org);update(guard,"delete from driver where id=? and organization_id=? and user_id is null",driver,org);
                    update(guard,"delete from vehicle where id=? and organization_id=?",vehicle,org);update(guard,"delete from transport_group where id=? and organization_id=?",group,org);guard.commit();guard.setAutoCommit(true);
                    assertThat(scalar(guard,"select string_agg(user_id::text,',' order by user_id) from pilot_account_creation")).isEqualTo(ledger);
                }
                scalar(guard,"select pg_advisory_unlock(7518440514)");
            }
        }
    }
}
