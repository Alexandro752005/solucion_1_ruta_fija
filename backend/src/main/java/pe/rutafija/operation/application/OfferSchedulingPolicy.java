package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.operation.api.ServiceTypeContract.*;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.qualification.application.OperationalQualificationPolicy;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.identity.domain.UserRole;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class OfferSchedulingPolicy {
    public record Window(Instant startsAt,Instant endsAt) { }
    private final JdbcTemplate jdbc; private final ServiceTypeService types; private final OperationalQualificationPolicy qualification;
    private final CurrentUserService current; private final Clock clock;
    public OfferSchedulingPolicy(JdbcTemplate jdbc,ServiceTypeService types,OperationalQualificationPolicy qualification,CurrentUserService current,Clock clock) {
        this.jdbc=jdbc;this.types=types;this.qualification=qualification;this.current=current;this.clock=clock;
    }
    public Window window(Instant start,Instant end,int before,int after) {
        if(start==null||end==null||!end.isAfter(start)||before<0||after<0)throw conflict("Indica horario y ambos márgenes válidos.");
        try {return new Window(start.minusSeconds(Math.multiplyExact((long)before,60)),end.plusSeconds(Math.multiplyExact((long)after,60)));}
        catch(DateTimeException|ArithmeticException ex){throw conflict("La ventana con márgenes queda fuera del intervalo admitido.");}
    }
    public void requireCompatible(UUID org,UUID driver,UUID vehicle,Window window,UUID excluded) {
        if(conflicts(org,driver,vehicle,window,excluded))throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.ASSIGNMENT_SCHEDULE_CONFLICT,"El conductor o la unidad tienen una reserva u ocupación incompatible con estos márgenes.");
    }
    public boolean hasOtherConfirmed(UUID org,UUID driver,UUID excluded) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from assignment where organization_id=? and driver_id=? and status='SCHEDULED' and accepted_at is not null and reserved_at is not null and id<>?)",Boolean.class,org,driver,excluded));
    }
    private boolean conflicts(UUID org,UUID driver,UUID vehicle,Window w,UUID excluded) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from assignment a where a.organization_id=? and (a.driver_id=? or a.vehicle_id=?)
             and (?::uuid is null or a.id<>?)
             and (a.status in ('SCHEDULED','EN_SERVICIO') or(a.status='PENDING_RESPONSE' and a.response_deadline_at>?))
             and (a.status='EN_SERVICIO' or a.service_type_version_id is null or(a.reservation_starts_at<? and a.reservation_ends_at>?)))
            """,Boolean.class,org,driver,vehicle,excluded,excluded,Timestamp.from(Instant.now(clock)),Timestamp.from(w.endsAt()),Timestamp.from(w.startsAt())));
    }
    public Window requireCaptured(Assignment assignment) {
        if(assignment.getServiceTypeVersionId()==null||assignment.getMarginBeforeMinutes()==null||assignment.getMarginAfterMinutes()==null)
            throw conflict("La oferta histórica no tiene márgenes acreditados; prepara una sustitución expresa con configuración vigente.");
        var revision=types.revision(assignment.getServiceTypeVersionId(),assignment.getOrganizationId());
        if(revision.beforeMinutes()!=assignment.getMarginBeforeMinutes()||revision.afterMinutes()!=assignment.getMarginAfterMinutes())throw conflict("La captura de márgenes no corresponde a su versión.");
        Window w=window(assignment.getScheduledAt(),assignment.getScheduledEndAt(),revision.beforeMinutes(),revision.afterMinutes());
        qualification.require(assignment.getDriver().getId(),assignment.getOrganizationId(),assignment.getVehicle().getId(),w.startsAt(),w.endsAt());
        requireCompatible(assignment.getOrganizationId(),assignment.getDriver().getId(),assignment.getVehicle().getId(),w,assignment.getId());return w;
    }
    @Transactional(readOnly=true)
    public Candidates candidates(UUID typeVersion,UUID routeVersion,Instant start,Instant end,int limit,int offset) {
        return candidates(typeVersion,routeVersion,start,end,limit,offset,null);
    }
    @Transactional(readOnly=true)
    public Candidates candidates(UUID typeVersion,UUID routeVersion,Instant start,Instant end,int limit,int offset,UUID serviceId) {
        return candidates(typeVersion,routeVersion,start,end,limit,offset,serviceId,false);
    }
    @Transactional(readOnly=true)
    public Candidates candidatesForHandover(UUID service,Instant start,Instant end,int limit,int offset){
        var a=current.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN propio.");
        var rows=jdbc.queryForList("select status,route_version_id,service_type_version_id from scheduled_service where id=? and organization_id=?",service,a.getOrganizationId());
        if(rows.size()!=1)throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Servicio no encontrado.");var s=rows.getFirst();
        if(!Set.of("IN_SERVICE","INTERRUPTED").contains(s.get("status")))throw conflict("El servicio no admite relevo de ejecución.");
        return candidates((UUID)s.get("service_type_version_id"),(UUID)s.get("route_version_id"),start,end,limit,offset,service,true);
    }
    private Candidates candidates(UUID typeVersion,UUID routeVersion,Instant start,Instant end,int limit,int offset,UUID serviceId,boolean handover) {
        var a=current.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La consulta requiere ADMIN.");
        UUID excluded=null;
        if(serviceId!=null){
            var headers=jdbc.queryForList("select current_offer_id,status from scheduled_service where id=? and organization_id=?",serviceId,a.getOrganizationId());
            if(headers.size()!=1)throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Servicio no encontrado.");
            if(!handover&&Set.of("IN_SERVICE","COMPLETED","CANCELLED","INTERRUPTED").contains(headers.getFirst().get("status")))throw conflict("El servicio no admite sustitución ordinaria.");
            excluded=(UUID)headers.getFirst().get("current_offer_id");
        }
        var type=handover?types.revision(typeVersion,a.getOrganizationId()):types.usable(typeVersion,a.getOrganizationId());Window w=window(start,end,type.beforeMinutes(),type.afterMinutes());
        if(!start.isAfter(Instant.now(clock)))throw conflict("Selecciona un horario futuro.");
        String routeCondition=handover?"":" and r.active and r.current_version_id=v.id";
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from route_version v join route_definition r on r.id=v.route_id and r.organization_id=v.organization_id where v.id=? and v.organization_id=?"+routeCondition+")",Boolean.class,routeVersion,a.getOrganizationId())))
            throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Ruta no disponible.");
        var potential=jdbc.query("""
            select d.id,v.id,p.id from driver_unit_period p
             join driver d on d.id=p.driver_id and d.organization_id=p.organization_id
             join vehicle v on v.id=p.vehicle_id and v.organization_id=p.organization_id
             join app_user u on u.id=d.user_id and u.organization_id=d.organization_id
             where p.organization_id=? and p.status='AUTHORIZED' and p.revoked_at is null
              and p.starts_at<=? and (p.ends_at is null or p.ends_at>=?)
              and d.active and u.active and u.email_verified_at is not null and u.role='CONDUCTOR'
              and d.declared_availability_status='DISPONIBLE' and d.availability_status in ('DISPONIBLE','RESERVADO')
              and v.active and v.status='DISPONIBLE' order by d.id,v.id,p.id limit ? offset ?
            """,(r,n)->new Candidate(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class)),a.getOrganizationId(),Timestamp.from(w.startsAt()),Timestamp.from(w.endsAt()),limit,offset);
        var found=new ArrayList<Candidate>();
        for(var c:potential)if(qualification.blockers(c.driverId(),a.getOrganizationId(),c.vehicleId(),w.startsAt(),w.endsAt(),true).isEmpty()
            &&!conflicts(a.getOrganizationId(),c.driverId(),c.vehicleId(),w,excluded))found.add(c);
        return new Candidates(typeVersion,w.startsAt(),w.endsAt(),found,potential.size()==limit?Math.addExact(offset,limit):null,Instant.now(clock));
    }
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
}
