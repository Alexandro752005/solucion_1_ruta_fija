package pe.rutafija.operation.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.*;

/** Expiry never extends for requests or incidents. All readers also filter expiry independently of this sweep. */
@Service
public class ServiceGpsRetention {
    private final JdbcTemplate jdbc;private final Clock clock;
    public ServiceGpsRetention(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    @Transactional public int purgeExpired() {
        int total=0;
        var now=Timestamp.from(clock.instant());
        var organizations=jdbc.query("""
            select organization_id from service_gps_point where expires_at<=?
            union select organization_id from driver_current_location where expires_at<=?
               or(gps_point_id is not null and captured_at<=?)
            order by organization_id
            """,(r,n)->r.getObject(1,java.util.UUID.class),now,now,Timestamp.from(clock.instant().minus(Duration.ofDays(7))));
        for(var org:organizations)total+=purgeExpired(org);
        return total;
    }
    /** Same production algorithm, bounded to one tenant for independent rollback fixtures. */
    @Transactional public int purgeExpired(java.util.UUID organizationId) {
        var now=Timestamp.from(clock.instant());
        jdbc.update("delete from driver_current_location where organization_id=? and(expires_at<=? or (gps_point_id is not null and captured_at<=?))",organizationId,now,Timestamp.from(clock.instant().minus(Duration.ofDays(7))));
        // ON DELETE SET NULL removes incident/declaration references while retaining non-coordinate results.
        return jdbc.update("delete from service_gps_point where organization_id=? and expires_at<=?",organizationId,now);
    }
}
