package pe.rutafija.routing.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.shared.security.MobileDriverActor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class RouteDeclarationService {
    private final JdbcTemplate jdbc;private final RouteCatalogService routes;
    private final pe.rutafija.operation.application.OperationEventPublisher events;
    public RouteDeclarationService(JdbcTemplate jdbc,RouteCatalogService routes,pe.rutafija.operation.application.OperationEventPublisher events){this.jdbc=jdbc;this.routes=routes;this.events=events;}
    public void record(Assignment a,MobileDriverActor actor,Instant now){
        Double lat=null,lon=null;
        if(a.getRouteVersionId()!=null){var terminal=routes.version(a.getRouteVersionId(),a.getOrganizationId()).stops().getLast();lat=terminal.latitude();lon=terminal.longitude();}
        var points=jdbc.query("""
            select p.latitude,p.longitude,p.accuracy_m,p.captured_at,p.gps_point_id from driver_current_location p
            join service_execution_segment e on e.id=p.execution_segment_id and e.driver_id=p.driver_id and e.organization_id=p.organization_id
            join service_gps_point g on g.id=p.gps_point_id and g.segment_id=e.id and g.expires_at>?
            where p.driver_id=? and p.organization_id=? and p.expires_at>? and e.assignment_id=?
              and p.captured_at>=e.started_at and p.captured_at<=?
            """,(r,n)->new TerminalPoint(r.getObject(5,UUID.class),new TerminalCorroborationPolicy.Point(r.getDouble(1),r.getDouble(2),r.getDouble(3),r.getTimestamp(4).toInstant())),Timestamp.from(now),a.getDriver().getId(),a.getOrganizationId(),Timestamp.from(now),a.getId(),Timestamp.from(now));
        var point=points.isEmpty()?null:points.getFirst();
        var result=TerminalCorroborationPolicy.result(lat,lon,point==null?null:point.value(),now);
        jdbc.update("insert into assignment_route_declaration(assignment_id,organization_id,driver_id,actor_id,route_version_id,declared_at,terminal_result,radius_m,maximum_age_seconds,maximum_accuracy_m,evidence_point_id) values(?,?,?,?,?,?,?,200,120,100,?)",a.getId(),a.getOrganizationId(),a.getDriver().getId(),actor.user().getId(),a.getRouteVersionId(),Timestamp.from(now),result,point==null?null:point.id());
        if("DISCREPANCY".equals(result)) {
            jdbc.update("insert into service_closure_notice(assignment_id,organization_id,created_at,kind) values(?,?,?,'TERMINAL_DISCREPANCY')",a.getId(),a.getOrganizationId(),Timestamp.from(now));
            events.publish(a.getOrganizationId(),a.getDriver().getGroup().getId(),"service.closure.observed",java.util.Map.of("assignmentId",a.getId().toString(),"result",result));
        }
    }
    private record TerminalPoint(UUID id,TerminalCorroborationPolicy.Point value) { }
}
