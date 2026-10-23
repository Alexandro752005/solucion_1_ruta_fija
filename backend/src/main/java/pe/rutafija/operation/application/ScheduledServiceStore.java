package pe.rutafija.operation.application;

import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.operation.api.ScheduledServiceContract.Summary;
import pe.rutafija.operation.api.dto.AssignmentCreateRequest;
import pe.rutafija.shared.exception.*;
import java.sql.*;
import java.time.*;
import java.util.*;

/** JDBC service header; offer lifecycle transitions are synchronized transactionally by PostgreSQL. */
@Service
public class ScheduledServiceStore {
    private final JdbcTemplate jdbc;private final Clock clock;
    public ScheduledServiceStore(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    public UUID create(AppUser actor,AssignmentCreateRequest offer,Instant now){
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into scheduled_service(id,organization_id,created_by,created_at,updated_at,
                scheduled_at,scheduled_end_at,route_version_id,service_type_version_id)
            values(?,?,?,?,?,?,?,?,?)
            """,id,actor.getOrganizationId(),actor.getId(),Timestamp.from(now),Timestamp.from(now),
            Timestamp.from(offer.scheduledAt()),Timestamp.from(offer.scheduledEndAt()),offer.routeVersionId(),offer.serviceTypeVersionId());
        return id;
    }
    public UUID requirePending(UUID id,UUID org){
        if(!"PENDING_REASSIGNMENT".equals(read(id,org).status()))throw conflict("El servicio no está pendiente de una nueva oferta.");
        return id;
    }
    private static final String SELECT="""
        select s.*,a.response_deadline_at,(select min(e.started_at) from service_execution_segment e where e.service_id=s.id) original_started_at from scheduled_service s
        left join assignment a on a.id=s.current_offer_id where s.organization_id=?
        """;
    public Summary read(UUID id,UUID org){
        var rows=jdbc.query(SELECT+" and s.id=?",this::map,org,id);
        if(rows.size()!=1)throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Servicio no encontrado.");
        return rows.getFirst();
    }
    public List<Summary> list(UUID org,int limit,int offset){
        return jdbc.query(SELECT+" order by s.created_at desc,s.id limit ? offset ?",this::map,org,limit,offset);
    }
    private Summary map(ResultSet r,int n)throws SQLException{
        String status=r.getString("status");Timestamp deadline=r.getTimestamp("response_deadline_at");
        String effective="PENDING_RESPONSE".equals(status)&&deadline!=null&&!deadline.toInstant().isAfter(clock.instant())?"PENDING_REASSIGNMENT":status;
        return new Summary(r.getObject("id",UUID.class),status,effective,r.getLong("version"),
            r.getTimestamp("scheduled_at").toInstant(),r.getTimestamp("scheduled_end_at").toInstant(),
            r.getObject("route_version_id",UUID.class),r.getObject("service_type_version_id",UUID.class),
            r.getObject("current_offer_id",UUID.class),r.getTimestamp("cancelled_at")==null?null:r.getTimestamp("cancelled_at").toInstant(),r.getString("cancellation_reason"),
            r.getTimestamp("original_started_at")==null?null:r.getTimestamp("original_started_at").toInstant(),
            "CONFIRMED".equals(status)&&r.getTimestamp("original_started_at")==null&&r.getTimestamp("scheduled_at").toInstant().isBefore(clock.instant()));
    }
    static ApplicationException conflict(String reason){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,reason);}
}
