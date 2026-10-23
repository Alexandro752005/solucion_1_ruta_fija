package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PostgreSQL exclusion constraints, with two genuinely independent connections.
 * Technical drivers have no AppUser, approval or operational authorization.
 * No identity/quota is created. Only this class's exact FK fixture is committed
 * and then removed selectively. This is not an HTTP/onboarding lifecycle test.
 */
class L02TemporalConcurrencyTest {
    private static final long CLEANUP_LOCK = 7_518_440_514L;

    @Test @Timeout(40)
    void sameDriverCannotHoldOverlappingDifferentUnitsConcurrently()throws Exception {race(true);}

    @Test @Timeout(40)
    void sameUnitCannotBelongToDifferentDriversConcurrently()throws Exception {race(false);}

    private void race(boolean sameDriver)throws Exception {
        NativePostgresTestDatabase database=NativePostgresTestDatabase.load();
        String caseId=UUID.randomUUID().toString();
        UUID org=UUID.randomUUID(),group=UUID.randomUUID(),driverOne=UUID.randomUUID(),driverTwo=UUID.randomUUID();
        UUID vehicleOne=UUID.randomUUID(),vehicleTwo=UUID.randomUUID(),periodOne=UUID.randomUUID(),periodTwo=UUID.randomUUID();
        String ledgerBefore;
        boolean committedFixture=false,cleanupPassed=false;
        try(Connection guard=connection(database)) {
            assertTarget(guard);assertNoHumanMarker();
            assertThat(scalar(guard,"select pg_try_advisory_lock("+CLEANUP_LOCK+")")).isEqualTo("true");
            try {
                assertNoHumanMarker();
                ledgerBefore=ledger(guard);
                UUID creator=UUID.fromString(scalar(guard,"select id from app_user where role='SUPER_ADMIN' and lower(email) like '%@rutafija.test' order by id limit 1"));
                guard.setAutoCommit(false);
                update(guard,"insert into organization(id,legal_name,trade_name,status,timezone,created_at,updated_at) values(?,?,'SYNTHETIC CONSTRAINT ONLY','ACTIVE','America/Lima',now(),now())",org,"SYNTHETIC-CONSTRAINT-ONLY-"+caseId);
                update(guard,"insert into transport_group(id,organization_id,name,active,created_at,updated_at) values(?,?,'SYNTHETIC CONSTRAINT ONLY',true,now(),now())",group,org);
                for(UUID driver:List.of(driverOne,driverTwo))update(guard,"""
                        insert into driver(id,organization_id,user_id,group_id,full_name,document_type,document_number,
                          availability_status,location_consent,active,created_at,updated_at)
                        values(?,?,null,?,'SYNTHETIC CONSTRAINT ONLY','SYNTHETIC',?,'NO_DISPONIBLE',false,false,now(),now())
                        """,driver,org,group,"SYN-"+driver.toString().substring(0,16));
                for(UUID unit:List.of(vehicleOne,vehicleTwo))update(guard,"insert into vehicle(id,organization_id,plate,status,active,created_at,updated_at) values(?,?,?,'INACTIVO',false,now(),now())",unit,org,"S-"+unit.toString().substring(0,10));
                guard.commit();committedFixture=true;guard.setAutoCommit(true);

                try(Connection first=connection(database);Connection second=connection(database);ExecutorService executor=Executors.newSingleThreadExecutor()) {
                    first.setAutoCommit(false);second.setAutoCommit(false);
                    update(first,"set local statement_timeout='15000ms'");
                    update(second,"set local statement_timeout='15000ms'");
                    int secondPid=Integer.parseInt(scalar(second,"select pg_backend_pid()"));
                    Instant start=Instant.now().plusSeconds(86400);
                    period(first,periodOne,org,driverOne,vehicleOne,creator,start,start.plusSeconds(3600));
                    CountDownLatch entering=new CountDownLatch(1);
                    Future<String> loser=executor.submit(()->{
                        entering.countDown();
                        try {
                            period(second,periodTwo,org,sameDriver?driverOne:driverTwo,sameDriver?vehicleTwo:vehicleOne,creator,start.plusSeconds(60),start.plusSeconds(1800));
                            second.commit();return "UNEXPECTED_COMMIT";
                        } catch(SQLException conflict) {
                            second.rollback();return conflict.getSQLState();
                        }
                    });
                    assertThat(entering.await(3,TimeUnit.SECONDS)).isTrue();
                    boolean waiting=false;long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    while(System.nanoTime()<deadline&&!loser.isDone()) {
                        waiting="1".equals(scalar(guard,"select count(*) from pg_stat_activity where pid="+secondPid+" and wait_event_type='Lock'"));
                        if(waiting)break;
                        Thread.sleep(25);
                    }
                    assertThat(waiting).as("second real transaction waited on the first exclusion entry").isTrue();
                    first.commit();
                    assertThat(loser.get(15,TimeUnit.SECONDS)).isEqualTo("23P01");
                    assertThat(scalar(guard,"select count(*) from driver_unit_period where organization_id='"+org+"'")).isEqualTo("1");
                }
            } finally {
                if(committedFixture) {
                    if(!guard.getAutoCommit())guard.rollback();guard.setAutoCommit(false);
                    assertThat(scalar(guard,"select count(*) from organization where id='"+org+"' and legal_name='SYNTHETIC-CONSTRAINT-ONLY-"+caseId+"'")).isEqualTo("1");
                    assertThat(scalar(guard,"select count(*) from driver where id in ('"+driverOne+"','"+driverTwo+"') and organization_id='"+org+"' and user_id is null and not active")).isEqualTo("2");
                    update(guard,"delete from driver_unit_period where organization_id=? and driver_id in (?,?) and id in (?,?)",org,driverOne,driverTwo,periodOne,periodTwo);
                    if(!scalar(guard,"select coalesce(to_regclass('public.group_driver_membership')::text,'')").isEmpty())
                        update(guard,"delete from group_driver_membership where organization_id=? and driver_id in (?,?)",org,driverOne,driverTwo);
                    update(guard,"delete from driver where organization_id=? and id in (?,?) and user_id is null",org,driverOne,driverTwo);
                    update(guard,"delete from vehicle where organization_id=? and id in (?,?)",org,vehicleOne,vehicleTwo);
                    update(guard,"delete from transport_group where id=? and organization_id=?",group,org);
                    update(guard,"delete from organization where id=? and legal_name=?",org,"SYNTHETIC-CONSTRAINT-ONLY-"+caseId);
                    guard.commit();guard.setAutoCommit(true);
                    cleanupPassed="0".equals(scalar(guard,"select count(*) from organization where id='"+org+"'"));
                }
                scalar(guard,"select pg_advisory_unlock("+CLEANUP_LOCK+")");
            }
            assertThat(cleanupPassed).isTrue();
            assertThat(ledger(guard)).isEqualTo(ledgerBefore);
            System.out.println("L02_TEMPORAL_CONSTRAINT_CASE="+(sameDriver?"SAME_DRIVER":"SAME_VEHICLE")
                    +"; RESULT=PASS_REAL_POSTGRES_23P01; WAIT=LOCK; SELECTIVE_CLEANUP=PASS; LEDGER_PRESERVED=true");
        }
    }

    private static void period(Connection c,UUID id,UUID org,UUID driver,UUID unit,UUID creator,Instant start,Instant end)throws SQLException {
        update(c,"""
                insert into driver_unit_period(id,organization_id,driver_id,vehicle_id,kind,starts_at,ends_at,status,reason,version,created_by,created_at)
                values(?,?,?,?,'COMPANY',?,?,'AUTHORIZED','SYNTHETIC CONSTRAINT ONLY',1,?,now())
                """,id,org,driver,unit,Timestamp.from(start),Timestamp.from(end),creator);
    }
    private static void update(Connection connection,String sql,Object... args)throws SQLException {
        try(PreparedStatement statement=connection.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);statement.execute();
        }
    }
    private static String scalar(Connection connection,String sql)throws SQLException {
        try(Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery(sql)) {
            return rows.next()?Objects.toString(rows.getObject(1),"").strip():"";
        }
    }
    private static String ledger(Connection connection)throws SQLException {
        return scalar(connection,"select coalesce(string_agg(user_id||':'||role||':'||created_at, '|' order by user_id),'') from pilot_account_creation");
    }
    private static Connection connection(NativePostgresTestDatabase database)throws Exception {
        return DriverManager.getConnection(value(database,"testUrl"),value(database,"testUsername"),value(database,"testPassword"));
    }
    private static String value(NativePostgresTestDatabase database,String name)throws Exception {
        Field field=NativePostgresTestDatabase.class.getDeclaredField(name);field.setAccessible(true);return (String)field.get(database);
    }
    private static void assertTarget(Connection c)throws SQLException {
        assertThat(scalar(c,"select current_database()")).isEqualTo("ruta_fija_test");
        assertThat(scalar(c,"select current_user")).isEqualTo("rf_test");
        assertThat(scalar(c,"select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1")).isIn("28","29","30","31","32","32");
    }
    private static void assertNoHumanMarker() {
        Path cwd=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for(Path marker:List.of(cwd.resolve(".local/l01-human-trial.active"),cwd.resolve("backend/.local/l01-human-trial.active"),cwd.resolve("../backend/.local/l01-human-trial.active").normalize()))
            assertThat(Files.exists(marker)).as("no active human cleanup protection").isFalse();
    }
}
