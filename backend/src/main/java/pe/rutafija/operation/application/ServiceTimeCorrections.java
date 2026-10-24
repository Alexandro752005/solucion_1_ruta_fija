package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.*;
import pe.rutafija.operation.api.ServiceTimeContract.*;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.routing.application.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Q064: versioned effective times; original segments, declarations and points remain immutable. */
@Service
public class ServiceTimeCorrections {
    private final JdbcTemplate jdbc; private final CurrentUserService users; private final MobileDriverContextService mobile;
    private final OrganizationSchedulingLock scheduling; private final ScheduledServiceStore services;
    private final AssignmentRepository assignments; private final RouteCatalogService routes; private final AuditService audit; private final Clock clock;
    public ServiceTimeCorrections(JdbcTemplate jdbc,CurrentUserService users,MobileDriverContextService mobile,
        OrganizationSchedulingLock scheduling,ScheduledServiceStore services,AssignmentRepository assignments,
        RouteCatalogService routes,AuditService audit,Clock clock) {
        this.jdbc=jdbc;this.users=users;this.mobile=mobile;this.scheduling=scheduling;this.services=services;
        this.assignments=assignments;this.routes=routes;this.audit=audit;this.clock=clock;
    }
    private AppUser admin() {
        var a=users.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN de esta organización.");return a;
    }
    private UUID ownService(UUID assignment) {
        var a=mobile.requireMobileConsultation();return assignments.findMobileAssignment(assignment,a.user().getOrganizationId(),a.driver().getId())
            .map(x->x.getServiceId()).orElseThrow(ServiceTimeCorrections::notFound);
    }
    @Transactional(readOnly=true) public View read(UUID service) {var a=admin();return view(service,a.getOrganizationId(),null);}
    @Transactional(readOnly=true) public View own(UUID assignment) {var a=mobile.requireMobileConsultation();return view(ownService(assignment),a.user().getOrganizationId(),a.driver().getId());}
    private List<EffectiveSegment> effective(UUID service,UUID org,UUID own) {
        return jdbc.query("""
            select e.*,coalesce(c.corrected_started_at,e.started_at) effective_start,
             coalesce(c.corrected_ended_at,e.ended_at) effective_end
            from service_execution_segment e left join lateral(
             select d.corrected_started_at,d.corrected_ended_at from service_time_correction_segment d
              join service_time_correction p on p.id=d.correction_id
              where d.segment_id=e.id order by p.revision desc limit 1) c on true
            where e.service_id=? and e.organization_id=?
            """+(own==null?"":" and e.driver_id=?")+" order by e.ordinal",(r,n)->new EffectiveSegment(r.getObject("id",UUID.class),r.getObject("driver_id",UUID.class),r.getObject("vehicle_id",UUID.class),r.getInt("ordinal"),instant(r,"started_at"),instant(r,"ended_at"),instant(r,"effective_start"),instant(r,"effective_end")),
            own==null?new Object[]{service,org}:new Object[]{service,org,own});
    }
    private View view(UUID service,UUID org,UUID own) {
        if(service==null)throw notFound();var header=services.read(service,org);var segments=effective(service,org,own);
        var corrections=jdbc.query("select * from service_time_correction where service_id=? and organization_id=? order by revision",(r,n)->{
            UUID id=r.getObject("id",UUID.class);
            var changes=jdbc.query("""
                select c.* from service_time_correction_segment c join service_execution_segment e on e.id=c.segment_id
                 where c.correction_id=?
                """+(own==null?"":" and e.driver_id=?")+" order by c.ordinal",(v,i)->new Change(v.getObject("segment_id",UUID.class),v.getInt("ordinal"),instant(v,"before_started_at"),instant(v,"before_ended_at"),instant(v,"corrected_started_at"),instant(v,"corrected_ended_at")),own==null?new Object[]{id}:new Object[]{id,own});
            return new Revision(id,r.getObject("operation_key",UUID.class),r.getInt("revision"),r.getObject("actor_id",UUID.class),instant(r,"corrected_at"),r.getString("reason"),r.getString("original_terminal_result"),r.getString("effective_terminal_result"),r.getString("verification_effect"),r.getObject("request_id",UUID.class),changes);
        },service,org);
        var requests=jdbc.query("""
            select r.*,c.id correction_id from service_time_request r join service_execution_segment e on e.id=r.segment_id
            left join service_time_correction c on c.request_id=r.id where r.service_id=? and r.organization_id=?
            """+(own==null?"":" and e.driver_id=?")+" order by r.requested_at,r.id",(r,n)->new RequestView(r.getObject("id",UUID.class),r.getObject("request_key",UUID.class),r.getObject("segment_id",UUID.class),r.getObject("requested_by",UUID.class),r.getString("reason"),instant(r,"requested_at"),r.getObject("correction_id",UUID.class)),own==null?new Object[]{service,org}:new Object[]{service,org,own});
        return new View(service,header.status(),header.version(),segments,corrections,requests);
    }
    @Transactional public RequestView request(UUID assignment,Request c) {
        var a=mobile.requireMobileConsultation();UUID org=a.user().getOrganizationId();scheduling.acquire(org);
        reason(c.reason());
        UUID service=ownService(assignment);if(!Set.of("COMPLETED","CANCELLED").contains(services.read(service,org).status()))throw conflict("La solicitud de corrección requiere un servicio finalizado.");
        var own=effective(service,org,a.driver().getId()).stream().filter(e->jdbc.queryForObject("select assignment_id=? from service_execution_segment where id=?",Boolean.class,assignment,e.segmentId())).toList();
        if(own.size()!=1||own.getFirst().endedAt()==null)throw notFound();
        var prior=jdbc.queryForList("select id,segment_id,reason from service_time_request where requested_by=? and request_key=?",a.user().getId(),c.requestKey());
        if(!prior.isEmpty()) {
            var p=prior.getFirst();if(!own.getFirst().segmentId().equals(p.get("segment_id"))||!c.reason().strip().equals(p.get("reason")))throw conflict("La clave de solicitud ya corresponde a otro contenido.");
            return view(service,org,a.driver().getId()).requests().stream().filter(x->x.id().equals(p.get("id"))).findFirst().orElseThrow();
        }
        UUID id=UUID.randomUUID();jdbc.update("insert into service_time_request(id,service_id,organization_id,segment_id,requested_by,request_key,reason,requested_at) values(?,?,?,?,?,?,?,?)",id,service,org,own.getFirst().segmentId(),a.user().getId(),c.requestKey(),c.reason().strip(),ts(clock.instant()));
        audit.record(a.user(),"SERVICE_TIME_CORRECTION_REQUESTED","SERVICE",service,Map.of("requestId",id.toString()));
        return view(service,org,a.driver().getId()).requests().stream().filter(x->x.id().equals(id)).findFirst().orElseThrow();
    }
    @Transactional public View correct(UUID service,Correct c) {
        var a=admin();UUID org=a.getOrganizationId();scheduling.acquire(org);var header=services.read(service,org);
        reason(c.reason());
        var originals=effective(service,org,null);var submitted=new HashMap<UUID,Time>();
        for(var t:c.segments())if(submitted.put(t.segmentId(),t)!=null)throw conflict("Un tramo no puede repetirse.");
        if(originals.isEmpty()||originals.size()!=submitted.size()||originals.stream().anyMatch(s->!submitted.containsKey(s.segmentId())))throw conflict("La corrección debe conservar y describir todos los tramos originales del servicio.");
        String canonical=service+"|"+c.expectedVersion()+"|"+c.reason().strip()+"|"+c.requestId();
        for(var s:originals) {var t=submitted.get(s.segmentId());canonical+='|'+s.segmentId().toString()+'|'+t.startedAt()+'|'+t.endedAt();}
        String hash=hash(canonical);var prior=jdbc.queryForList("select service_id,payload_sha256 from service_time_correction where actor_id=? and operation_key=?",a.getId(),c.operationKey());
        if(!prior.isEmpty()) {
            if(!service.equals(prior.getFirst().get("service_id"))||!hash.equals(prior.getFirst().get("payload_sha256")))throw conflict("La clave de corrección ya corresponde a otro contenido.");
            return view(service,org,null);
        }
        if(header.version()!=c.expectedVersion())throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_VERSION_CONFLICT,"El servicio cambió; consulta su estado antes de corregir.");
        if(!Set.of("COMPLETED","CANCELLED").contains(header.status())||originals.stream().anyMatch(s->s.originalEndedAt()==null))throw conflict("Solo se corrigen tiempos de un servicio finalizado con todos sus tramos cerrados.");
        Instant now=clock.instant(),previousEnd=null;boolean changed=false;
        for(var s:originals) {
            var t=submitted.get(s.segmentId());
            if(t.endedAt().isBefore(t.startedAt())||t.endedAt().isAfter(now)||previousEnd!=null&&t.startedAt().isBefore(previousEnd))throw conflict("Los tiempos deben ser pasados, ordenados y no superponer tramos.");
            previousEnd=t.endedAt();changed|=!s.startedAt().equals(t.startedAt())||!s.endedAt().equals(t.endedAt());
            if(overlapsOther(service,org,s,t))throw conflict("La corrección superpone la participación del conductor o unidad con otro servicio.");
        }
        if(!changed)throw conflict("No hay una modificación temporal que registrar.");
        if(c.requestId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_time_request r where r.id=? and r.service_id=? and r.organization_id=? and not exists(select 1 from service_time_correction c where c.request_id=r.id))",Boolean.class,c.requestId(),service,org)))throw conflict("La solicitud no es propia de este servicio o ya fue atendida.");
        var last=originals.getLast();var lastTime=submitted.get(last.segmentId());String originalResult=jdbc.query("select d.terminal_result from assignment_route_declaration d join service_execution_segment e on e.assignment_id=d.assignment_id where e.id=?",(r,n)->r.getString(1),last.segmentId()).stream().findFirst().orElse(null);
        String result=effectiveResult(last,lastTime,header.routeVersionId(),org,now);
        int revision=jdbc.queryForObject("select coalesce(max(revision),0)+1 from service_time_correction where service_id=?",Integer.class,service);UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into service_time_correction(id,service_id,organization_id,actor_id,revision,operation_key,payload_sha256,
             previous_service_version,resulting_service_version,reason,corrected_at,request_id,original_terminal_result,effective_terminal_result,verification_effect)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,service,org,a.getId(),revision,c.operationKey(),hash,header.version(),header.version()+1,c.reason().strip(),ts(now),c.requestId(),originalResult,result,"ORIGINALS_RETAINED_EFFECTIVE_TIME_REVIEW");
        for(var s:originals) {
            var t=submitted.get(s.segmentId());jdbc.update("insert into service_time_correction_segment values(?,?,?,?,?,?,?,?,?)",id,s.segmentId(),service,org,s.ordinal(),ts(s.startedAt()),ts(s.endedAt()),ts(t.startedAt()),ts(t.endedAt()));
        }
        jdbc.update("update scheduled_service set version=version+1,updated_at=? where id=? and organization_id=?",ts(now),service,org);
        var differences=new ArrayList<Map<String,Object>>();
        for(var s:originals){var t=submitted.get(s.segmentId());for(var change:pe.rutafija.audit.application.AuditChanges.fields("startedAt",s.startedAt(),t.startedAt(),"endedAt",s.endedAt(),t.endedAt())){var entry=new LinkedHashMap<>(change);entry.put("segmentId",s.segmentId());differences.add(entry);}}
        audit.record(a,"SERVICE_TIMES_CORRECTED","SERVICE",service,Map.of("correctionId",id,"revision",revision,"result",result,"reasonReferenceId",id,"changes",differences));
        return view(service,org,null);
    }
    private boolean overlapsOther(UUID service,UUID org,EffectiveSegment s,Time t) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            select exists(select 1 from service_execution_segment e left join lateral(
             select d.corrected_started_at,d.corrected_ended_at from service_time_correction_segment d join service_time_correction p on p.id=d.correction_id
              where d.segment_id=e.id order by p.revision desc limit 1)c on true
             where e.organization_id=? and e.service_id<>? and(e.driver_id=? or e.vehicle_id=?)
              and tstzrange(coalesce(c.corrected_started_at,e.started_at),coalesce(c.corrected_ended_at,e.ended_at),'[)') && tstzrange(?,?,'[)'))
            """,Boolean.class,org,service,s.driverId(),s.vehicleId(),ts(t.startedAt()),ts(t.endedAt())));
    }
    private String effectiveResult(EffectiveSegment segment,Time t,UUID route,UUID org,Instant now) {
        var points=jdbc.query("""
            select p.latitude,p.longitude,p.accuracy_m,p.captured_at from assignment_route_declaration d
             join service_execution_segment e on e.assignment_id=d.assignment_id join service_gps_point p on p.id=d.evidence_point_id
            where e.id=? and p.expires_at>?
            """,(r,n)->new TerminalCorroborationPolicy.Point(r.getDouble(1),r.getDouble(2),r.getDouble(3),instant(r,"captured_at")),segment.segmentId(),ts(now));
        if(points.isEmpty())return "NOT_REASSESSABLE_ORIGINAL_POINT_EXPIRED_OR_ABSENT";
        var point=points.getFirst();if(point.capturedAt().isBefore(t.startedAt()))return "NOT_CORROBORATED_OUTSIDE_CORRECTED_SEGMENT";
        var terminal=routes.version(route,org).stops().getLast();return TerminalCorroborationPolicy.result(terminal.latitude(),terminal.longitude(),point,t.endedAt());
    }
    private static Instant instant(ResultSet r,String key)throws SQLException {Timestamp value=r.getTimestamp(key);return value==null?null:value.toInstant();}
    private static Timestamp ts(Instant value){return Timestamp.from(value);}
    private static void reason(String value){if(value.contains("@")||value.matches(".*[0-9]{8,}.*"))throw conflict("Utiliza un motivo sin datos personales.");}
    private static String hash(String s) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception failure){throw new IllegalStateException("TIME_CORRECTION_HASH_UNAVAILABLE");}}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Servicio o tramo propio no encontrado.");}
}
