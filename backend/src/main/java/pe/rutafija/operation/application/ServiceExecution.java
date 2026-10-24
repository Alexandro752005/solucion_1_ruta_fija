package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.rutafija.operation.api.dto.mobile.MobileGpsStartProof;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.MobileDriverActor;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Original server execution times. Corrections must never rewrite these rows. Caller holds the tenant scheduling lock. */
@Service
public class ServiceExecution {
    private final JdbcTemplate jdbc;
    public ServiceExecution(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public void requireStart(Assignment a, MobileGpsStartProof proof) {
        if(!a.getDriver().isLocationConsent())
            throw conflict(ErrorCode.LOCATION_CONSENT_REQUIRED,"Todos los servicios requieren consentimiento de ubicación antes del inicio.");
        if(proof==null || !proof.permitsStart())
            throw conflict(ErrorCode.LOCATION_PERMISSION_NOT_REPORTED,"Iniciar requiere permiso GPS, ubicación habilitada y aplicación en primer plano.");
        if(a.getServiceId()==null)
            throw conflict(ErrorCode.ASSIGNMENT_INVALID_TRANSITION,"La oferta histórica no tiene servicio trazable; requiere regularización expresa, no inicio nuevo.");
        Integer allowed=jdbc.queryForObject("""
            select count(*) from scheduled_service s where s.id=? and s.organization_id=?
              and s.status='CONFIRMED' and s.current_offer_id=?
              and not exists(select 1 from service_execution_segment e
                  where e.ended_at is null and (e.service_id=s.id or e.driver_id=? or e.vehicle_id=?))
            """,Integer.class,a.getServiceId(),a.getOrganizationId(),a.getId(),a.getDriver().getId(),a.getVehicle().getId());
        if(allowed==null || allowed!=1)
            throw conflict(ErrorCode.ASSIGNMENT_INVALID_TRANSITION,"El servicio o sus recursos ya no admiten un tramo nuevo.");
    }

    public void recordStart(Assignment a,MobileDriverActor actor,UUID eventId,Instant now) {
        // Validation preceded all domain mutations. An unexpected SQL failure rolls back the entire physical transaction.
        jdbc.update("""
            insert into service_execution_segment(id,service_id,organization_id,assignment_id,driver_id,vehicle_id,
                ordinal,started_at,start_recorded_at,started_by,start_event_id)
            select ?,?,?,?,?,?,coalesce(max(ordinal),0)+1,?,?,?,? from service_execution_segment where service_id=?
            """,UUID.randomUUID(),a.getServiceId(),a.getOrganizationId(),a.getId(),a.getDriver().getId(),a.getVehicle().getId(),
            Timestamp.from(now),Timestamp.from(now),actor.user().getId(),eventId,a.getServiceId());
    }

    public void requireCompletion(Assignment a) {
        if(a.getServiceId()!=null && active(a)==null)
            throw conflict(ErrorCode.ASSIGNMENT_INVALID_TRANSITION,"El servicio no tiene un tramo propio abierto; no se reconstruye ejecución histórica.");
    }

    public void recordCompletion(Assignment a,MobileDriverActor actor,UUID eventId,Instant now) {
        if(a.getServiceId()==null) return; // Existing legacy completion remains an explicit declaration without invented segments.
        int changed=jdbc.update("""
            update service_execution_segment set ended_at=?,end_recorded_at=?,ended_by=?,end_event_id=?,end_kind='COMPLETED'
             where assignment_id=? and organization_id=? and driver_id=? and ended_at is null
            """,Timestamp.from(now),Timestamp.from(now),actor.user().getId(),eventId,a.getId(),a.getOrganizationId(),a.getDriver().getId());
        if(changed!=1) throw new IllegalStateException("EXECUTION_SEGMENT_CLOSE_INVARIANT");
    }

    public void recordInterruption(Assignment a,pe.rutafija.identity.domain.AppUser actor,UUID eventId,Instant now) {
        int changed=jdbc.update("""
            update service_execution_segment set ended_at=?,end_recorded_at=?,ended_by=?,end_event_id=?,end_kind='INTERRUPTED'
             where assignment_id=? and organization_id=? and driver_id=? and ended_at is null
            """,Timestamp.from(now),Timestamp.from(now),actor.getId(),eventId,a.getId(),a.getOrganizationId(),a.getDriver().getId());
        if(changed!=1)throw new IllegalStateException("INTERRUPTION_SEGMENT_CLOSE_INVARIANT");
    }
    public void recordTransfer(Assignment outgoing,MobileDriverActor incoming,UUID event,Instant now){
        int changed=jdbc.update("update service_execution_segment set ended_at=?,end_recorded_at=?,ended_by=?,end_event_id=?,end_kind='TRANSFERRED' where assignment_id=? and organization_id=? and ended_at is null",
            Timestamp.from(now),Timestamp.from(now),incoming.user().getId(),event,outgoing.getId(),outgoing.getOrganizationId());
        if(changed!=1)throw new IllegalStateException("HANDOVER_SEGMENT_CLOSE_INVARIANT");
    }

    /** One offer describes one original segment, including a report created after that segment ended. */
    public void linkIncident(pe.rutafija.operation.domain.Incident incident,Assignment assignment) {
        if(assignment==null)return;
        var ids=jdbc.query("select id from service_execution_segment where assignment_id=? and organization_id=? and driver_id=?",
            (r,n)->r.getObject(1,UUID.class),assignment.getId(),assignment.getOrganizationId(),assignment.getDriver().getId());
        if(ids.size()>1)throw new IllegalStateException("INCIDENT_SEGMENT_AMBIGUOUS");
        if(ids.size()==1)incident.linkExecutionSegment(ids.getFirst());
    }

    public Active active(Assignment a) {
        var rows=jdbc.query("""
            select id,started_at from service_execution_segment where assignment_id=? and organization_id=? and driver_id=? and ended_at is null
            """,(r,n)->new Active(r.getObject(1,UUID.class),r.getTimestamp(2).toInstant()),a.getId(),a.getOrganizationId(),a.getDriver().getId());
        return rows.isEmpty()?null:rows.getFirst();
    }

    public List<Segment> segments(UUID serviceId,UUID org,UUID ownDriver) {
        return jdbc.query("""
            select * from service_execution_segment where service_id=? and organization_id=?
            """+(ownDriver==null?"":" and driver_id=?")+" order by ordinal",(r,n)->new Segment(
                r.getObject("id",UUID.class),r.getObject("assignment_id",UUID.class),r.getObject("driver_id",UUID.class),
                r.getObject("vehicle_id",UUID.class),r.getInt("ordinal"),r.getTimestamp("started_at").toInstant(),
                r.getTimestamp("ended_at")==null?null:r.getTimestamp("ended_at").toInstant(),r.getString("end_kind")),
            ownDriver==null?new Object[]{serviceId,org}:new Object[]{serviceId,org,ownDriver});
    }

    public record Active(UUID id,Instant startedAt) { }
    public record Segment(UUID id,UUID assignmentId,UUID driverId,UUID vehicleId,int ordinal,Instant startedAt,Instant endedAt,String endKind) { }
    private static ApplicationException conflict(ErrorCode code,String message) { return new ApplicationException(HttpStatus.CONFLICT,code,message); }
}
