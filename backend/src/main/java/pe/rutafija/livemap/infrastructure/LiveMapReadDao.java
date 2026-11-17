package pe.rutafija.livemap.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.rutafija.livemap.api.LiveMapContract;
import java.time.Instant;
import java.time.Duration;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
public class LiveMapReadDao {
    private final JdbcTemplate jdbc;
    public LiveMapReadDao(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    /** Read only the latest authorized snapshot, never the historical series or a legacy driver/unit link. */
    public List<LiveMapContract.Vehicle> vehicles(UUID organizationId,int limit,UUID groupId,String driverName,Instant now) {
        String sql="""
            select d.id driver_id,v.id vehicle_id,d.full_name driver_name,v.plate,
                   p.latitude,p.longitude,p.captured_at,p.received_at,p.expires_at
            from driver_current_location p
            join driver d on d.id=p.driver_id and d.organization_id=p.organization_id
            join app_user u on u.id=d.user_id and u.organization_id=d.organization_id and u.role='CONDUCTOR'
            join organization o on o.id=p.organization_id
            join service_execution_segment e on e.id=p.execution_segment_id
                 and e.organization_id=p.organization_id and e.driver_id=p.driver_id
            join assignment a on a.id=e.assignment_id and a.organization_id=e.organization_id
                 and a.driver_id=e.driver_id and a.vehicle_id=e.vehicle_id and a.service_id=e.service_id
            join scheduled_service s on s.id=e.service_id and s.organization_id=e.organization_id and s.current_offer_id=a.id
            join vehicle v on v.id=e.vehicle_id and v.organization_id=e.organization_id
            join service_gps_point g on g.id=p.gps_point_id and g.segment_id=e.id
                 and g.organization_id=p.organization_id and g.driver_id=p.driver_id
            where p.organization_id=? and o.status='ACTIVE'
              and d.active and d.location_consent and d.availability_status='EN_SERVICIO'
              and u.active and u.email_verified_at is not null
              and e.ended_at is null and a.status='EN_SERVICIO' and s.status='IN_SERVICE'
              and v.active and v.status='EN_SERVICIO'
              and p.expires_at>? and g.expires_at>?
              and p.captured_at<=? and p.received_at<=? and p.captured_at>=e.started_at
              and p.captured_at=g.captured_at and p.latitude=g.latitude and p.longitude=g.longitude
              and (?='' or strpos(lower(d.full_name),lower(?))>0)
            """;
        var args=new ArrayList<Object>(List.of(organizationId,Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),Timestamp.from(now),driverName,driverName));
        if(groupId!=null) {
            sql+="""
                 and exists(select 1 from group_driver_membership m
                    join transport_group t on t.id=m.group_id and t.organization_id=m.organization_id
                    where m.driver_id=d.id and m.organization_id=p.organization_id and m.group_id=? and m.active and t.active)
                """;
            args.add(groupId);
        }
        sql+=" order by p.captured_at desc,d.id limit ?";args.add(limit);
        return jdbc.query(sql,(r,index)->{
            Instant captured=r.getTimestamp("captured_at").toInstant();
            return new LiveMapContract.Vehicle(r.getObject("driver_id",UUID.class),r.getObject("vehicle_id",UUID.class),
                    r.getString("driver_name"),r.getString("plate"),r.getBigDecimal("latitude"),r.getBigDecimal("longitude"),
                    captured,r.getTimestamp("received_at").toInstant(),r.getTimestamp("expires_at").toInstant(),
                    Duration.between(captured,now).compareTo(Duration.ofSeconds(120))>=0?"STALE":"CURRENT");
        },args.toArray());
    }
    public List<LiveMapContract.Group> groups(UUID organizationId) {
        return jdbc.query("select id,name from transport_group where organization_id=? and active order by lower(name),id",
                (r,index)->new LiveMapContract.Group(r.getObject("id",UUID.class),r.getString("name")),organizationId);
    }
}
