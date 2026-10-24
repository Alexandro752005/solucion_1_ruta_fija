package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

/** Lock protocol only: technical resources without accounts; exact committed cleanup and preserved ledger. */
class L03GroupSnapshotConcurrencyTest {
    @Test @Timeout(35) void committedAssignmentIsVisibleBeforeTheRetirementLockCanContinue()throws Exception{race(true);}
    @Test @Timeout(35) void retirementCommittedFirstPreventsANewAssignmentFromCapturingAnInactiveGroup()throws Exception{race(false);}
    private void race(boolean assignmentFirst)throws Exception{
        var db=NativePostgresTestDatabase.load();String caseId=UUID.randomUUID().toString();
        UUID org=UUID.randomUUID(),group=UUID.randomUUID(),driver=UUID.randomUUID(),vehicle=UUID.randomUUID(),assignment=UUID.randomUUID();
        boolean fixtureCommitted=false;String ledger;
        try(Connection guard=connection(db)){
            assertThat(scalar(guard,"select current_database()||'/'||current_user")).isEqualTo("ruta_fija_test/rf_test");
            assertThat(scalar(guard,"select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1")).isIn("29","30","31","32");
            for(String marker:List.of(".local/l01-human-trial.active",".local/l02-connected.active"))assertThat(Files.exists(Path.of(marker))).isFalse();
            assertThat(scalar(guard,"select pg_try_advisory_lock(7518440514)")).isEqualTo("true");
            ledger=scalar(guard,"select string_agg(role||':'||n,',' order by role) from(select role,count(*) n from pilot_account_creation group by role)s");
            try{
                UUID creator=UUID.fromString(scalar(guard,"select id from app_user where id='088518c1-821c-4316-9f87-090f51ac44e0' and role='SUPER_ADMIN'"));
                guard.setAutoCommit(false);
                update(guard,"insert into organization(id,legal_name,status,timezone,created_at,updated_at) values(?,?,'ACTIVE','America/Lima',now(),now())",org,"SYNTHETIC-GROUP-RACE-"+caseId);
                update(guard,"insert into transport_group(id,organization_id,name,active,created_at,updated_at) values(?,?,'SYNTHETIC GROUP RACE',true,now(),now())",group,org);
                update(guard,"insert into driver(id,organization_id,group_id,full_name,document_type,document_number,availability_status,location_consent,active,created_at,updated_at) values(?,?,?,'SYNTHETIC GROUP RACE','SYNTHETIC',?,'NO_DISPONIBLE',false,false,now(),now())",driver,org,group,"S-"+caseId.substring(0,15));
                update(guard,"insert into vehicle(id,organization_id,plate,status,active,created_at,updated_at) values(?,?,?,'INACTIVO',false,now(),now())",vehicle,org,"G-"+caseId.substring(0,10));
                guard.commit();fixtureCommitted=true;guard.setAutoCommit(true);
                try(Connection first=connection(db);Connection second=connection(db);ExecutorService executor=Executors.newSingleThreadExecutor()){
                    first.setAutoCommit(false);second.setAutoCommit(false);
                    update(first,"set local statement_timeout='12000ms'");update(second,"set local statement_timeout='12000ms'");
                    int pid=Integer.parseInt(scalar(second,"select pg_backend_pid()"));
                    if(assignmentFirst)insertAssignment(first,assignment,org,driver,vehicle,creator);
                    else update(first,"update transport_group set active=false,version=version+1 where id=?",group);
                    CountDownLatch entering=new CountDownLatch(1);
                    Future<String> result=executor.submit(()->{
                        entering.countDown();
                        try{
                            if(assignmentFirst){
                                scalar(second,"select version from transport_group where id='"+group+"' for update");
                                return scalar(second,"select count(*) from assignment_group_snapshot s join assignment a on a.id=s.assignment_id where s.group_id='"+group+"' and a.status='SCHEDULED'");
                            }
                            insertAssignment(second,assignment,org,driver,vehicle,creator);return "UNEXPECTED_INSERT";
                        }catch(SQLException ex){return ex.getSQLState();}
                    });
                    assertThat(entering.await(2,TimeUnit.SECONDS)).isTrue();
                    long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);boolean waiting=false;
                    while(System.nanoTime()<deadline){if("Lock".equals(scalar(guard,"select coalesce(wait_event_type,'') from pg_stat_activity where pid="+pid))){waiting=true;break;}Thread.sleep(10);}
                    assertThat(waiting).isTrue();assertThat(result.isDone()).isFalse();
                    first.commit();assertThat(result.get(8,TimeUnit.SECONDS)).isEqualTo(assignmentFirst?"1":"23514");second.rollback();
                }
            }finally{
                if(!guard.getAutoCommit())guard.rollback();
                if(fixtureCommitted){
                    guard.setAutoCommit(false);
                    assertThat(scalar(guard,"select count(*) from organization where id='"+org+"' and legal_name='SYNTHETIC-GROUP-RACE-"+caseId+"'")).isEqualTo("1");
                    assertThat(scalar(guard,"select count(*) from driver where id='"+driver+"' and organization_id='"+org+"' and user_id is null")).isEqualTo("1");
                    update(guard,"delete from assignment_group_snapshot where assignment_id=? and organization_id=?",assignment,org);
                    update(guard,"delete from assignment where id=? and organization_id=?",assignment,org);
                    update(guard,"delete from group_driver_membership where driver_id=? and organization_id=?",driver,org);
                    update(guard,"delete from driver where id=? and organization_id=? and user_id is null",driver,org);
                    update(guard,"delete from vehicle where id=? and organization_id=?",vehicle,org);
                    update(guard,"delete from transport_group where id=? and organization_id=?",group,org);
                    update(guard,"delete from organization where id=?",org);guard.commit();guard.setAutoCommit(true);
                    assertThat(scalar(guard,"select string_agg(role||':'||n,',' order by role) from(select role,count(*) n from pilot_account_creation group by role)s")).isEqualTo(ledger);
                }
                scalar(guard,"select pg_advisory_unlock(7518440514)");
            }
        }
    }
    static void insertAssignment(Connection c,UUID id,UUID org,UUID driver,UUID vehicle,UUID creator)throws SQLException{
        update(c,"insert into assignment(id,organization_id,driver_id,vehicle_id,created_by,status,origin_text,destination_text,scheduled_at,scheduled_end_at,created_at,updated_at) values(?,?,?,?,?,'SCHEDULED','SYNTHETIC','SYNTHETIC',now()+interval '2 days',now()+interval '2 days 1 hour',now(),now())",id,org,driver,vehicle,creator);
    }
    static Connection connection(NativePostgresTestDatabase db)throws Exception{return DriverManager.getConnection(value(db,"testUrl"),value(db,"testUsername"),value(db,"testPassword"));}
    private static String value(NativePostgresTestDatabase db,String key)throws Exception{Field f=NativePostgresTestDatabase.class.getDeclaredField(key);f.setAccessible(true);return (String)f.get(db);}
    static void update(Connection c,String sql,Object... args)throws SQLException{try(PreparedStatement q=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);q.executeUpdate();}}
    static String scalar(Connection c,String sql)throws SQLException{try(Statement q=c.createStatement();ResultSet r=q.executeQuery(sql)){return r.next()?Objects.toString(r.getObject(1),""):"";}}
}
