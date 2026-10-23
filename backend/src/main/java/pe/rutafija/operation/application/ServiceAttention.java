package pe.rutafija.operation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.*;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.operation.api.ServiceAttentionContract.*;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.domain.*;
import pe.rutafija.operation.infrastructure.*;
import pe.rutafija.qualification.application.OperationalQualificationPolicy;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Approved interruption/attention, with actual authorship and no automatic resource release or completion. */
@Service
public class ServiceAttention {
    private final JdbcTemplate jdbc;private final CurrentUserService users;private final MobileDriverContextService mobile;
    private final OrganizationSchedulingLock scheduling;private final AssignmentRepository assignments;private final IncidentRepository incidents;
    private final ServiceExecution execution;private final ScheduledServiceStore services;private final DriverCurrentLocationStore current;
    private final MobileCommandProcessor commands;private final EntityManager entities;private final DriverRepository drivers;private final VehicleRepository vehicles;
    private final OperationalQualificationPolicy qualification;private final OfferSchedulingPolicy margins;
    private final AuditService audit;private final OperationEventPublisher events;private final Clock clock;private final ObjectMapper json;
    public ServiceAttention(JdbcTemplate jdbc,CurrentUserService users,MobileDriverContextService mobile,OrganizationSchedulingLock scheduling,
        AssignmentRepository assignments,IncidentRepository incidents,ServiceExecution execution,ScheduledServiceStore services,
        DriverCurrentLocationStore current,MobileCommandProcessor commands,EntityManager entities,DriverRepository drivers,VehicleRepository vehicles,
        OperationalQualificationPolicy qualification,OfferSchedulingPolicy margins,AuditService audit,OperationEventPublisher events,Clock clock,ObjectMapper json) {
        this.jdbc=jdbc;this.users=users;this.mobile=mobile;this.scheduling=scheduling;this.assignments=assignments;this.incidents=incidents;
        this.execution=execution;this.services=services;this.current=current;this.commands=commands;this.entities=entities;this.drivers=drivers;this.vehicles=vehicles;
        this.qualification=qualification;this.margins=margins;this.audit=audit;this.events=events;this.clock=clock;this.json=json;
    }
    private AppUser admin(){var a=users.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN de esta organización.");return a;}
    private Assignment own(UUID id,MobileDriverActor actor) {return assignments.findMobileAssignment(id,actor.user().getOrganizationId(),actor.driver().getId()).orElseThrow(ServiceAttention::notFound);}
    private Assignment tenant(UUID id,UUID org) {return assignments.findByIdAndOrganization_Id(id,org).orElseThrow(ServiceAttention::notFound);}
    private void fresh(Assignment a){entities.refresh(a,LockModeType.PESSIMISTIC_WRITE);entities.refresh(a.getDriver(),LockModeType.PESSIMISTIC_WRITE);entities.refresh(a.getVehicle(),LockModeType.PESSIMISTIC_WRITE);}
    @Transactional(noRollbackFor=ApplicationException.class)
    public MobileCommandExecution<AssignmentResponse> interruptOwn(UUID id,OwnInterrupt c) {
        var actor=mobile.requireMobileConsultation();scheduling.acquire(actor.user().getOrganizationId());var a=own(id,actor);fresh(a);
        return commands.execute(actor,c.clientEventId(),c.occurredAt(),MobileCommandType.ASSIGNMENT_INTERRUPT,a,hash("OWN_INTERRUPT",id,c),
            receipt->AssignmentResponse.from(own(id,actor)),()->a.getStatus().name(),()->{
                validate(a,c.version());text(c.detail());
                register(a,actor.user(),c.clientEventId(),hash("OWN_INTERRUPT",id,c),c.reason(),c.detail(),c.safeStopConfirmed(),"CONDUCTOR","OWN_DRIVER_REPORT");
                return AssignmentResponse.from(a);
            },response->response.status().name());
    }
    @Transactional public Attention interruptAdmin(UUID id,AdminInterrupt c) {
        var actor=admin();UUID org=actor.getOrganizationId();scheduling.acquire(org);String hash=hash("ADMIN_INTERRUPT",id,c);
        var prior=jdbc.queryForList("select id,assignment_id,payload_sha256 from service_attention where recorded_by=? and operation_key=?",actor.getId(),c.operationKey());
        if(!prior.isEmpty()) {var p=prior.getFirst();if(!id.equals(p.get("assignment_id"))||!hash.equals(p.get("payload_sha256")))throw conflict("La clave ya corresponde a otra interrupción.");return read((UUID)p.get("id"),org);}
        var a=tenant(id,org);fresh(a);validate(a,c.expectedVersion());text(c.detail());text(c.verificationReference());
        if(!Boolean.TRUE.equals(c.safeStopConfirmed()))throw conflict("ADMIN debe comprobar la detención segura comunicada.");
        UUID attention=register(a,actor,c.operationKey(),hash,c.reason(),c.detail(),true,"ADMIN_VERIFIED_REPORT",c.verificationReference());return read(attention,org);
    }
    private void validate(Assignment a,long version) {
        if(a.getVersion()!=version)throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_VERSION_CONFLICT,"La ejecución cambió; consulta su estado.");
        if(a.getServiceId()==null||a.getStatus()!=AssignmentStatus.EN_SERVICIO||a.getStartedAt()==null||execution.active(a)==null)
            throw conflict("La interrupción requiere el tramo original abierto de un servicio iniciado; no se reconstruyen legados.");
        var s=services.read(a.getServiceId(),a.getOrganizationId());
        if(!"IN_SERVICE".equals(s.status())||!a.getId().equals(s.currentOfferId())||clock.instant().isBefore(a.getStartedAt()))throw conflict("La oferta ya no es responsable del tramo vigente.");
    }
    private UUID register(Assignment a,AppUser actor,UUID key,String hash,Reason reason,String detail,boolean stopped,String source,String reference) {
        Instant now=clock.instant();var segment=execution.active(a);if(segment==null)throw new IllegalStateException("INTERRUPTION_SEGMENT_MISSING_AFTER_VALIDATION");
        IncidentCategory category=switch(reason){case ACCIDENT_RISK->IncidentCategory.ACCIDENTE;case UNSAFE_BREAKDOWN->IncidentCategory.AVERIA;case IMPOSSIBLE_SERVICE->IncidentCategory.IMPOSIBILIDAD_SERVICIO;};
        Incident incident="CONDUCTOR".equals(source)?Incident.reportFromMobile(a.getOrganization(),a.getDriver(),a,actor,category,detail.strip(),now):Incident.report(a.getOrganization(),a.getDriver(),a,actor,category,detail.strip(),now);
        incident.linkExecutionSegment(segment.id());incident=incidents.saveAndFlush(incident);UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into service_attention(id,service_id,organization_id,assignment_id,segment_id,driver_id,vehicle_id,incident_id,
             recorded_by,source,operation_key,payload_sha256,reason,detail,safe_stop_confirmed,verification_reference,interrupted_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,a.getServiceId(),a.getOrganizationId(),a.getId(),segment.id(),a.getDriver().getId(),a.getVehicle().getId(),incident.getId(),actor.getId(),source,key,hash,reason.name(),detail.strip(),stopped,reference,ts(now));
        a.interrupt(now,reason.name());a.getDriver().enterSafetyAttention();a.getVehicle().enterSafetyAttention();assignments.saveAndFlush(a);
        execution.recordInterruption(a,actor,key,now);current.deleteByDriverId(a.getDriver().getId());
        audit.record(actor,"SERVICE_INTERRUPTED","SERVICE",a.getServiceId(),Map.of("attentionId",id.toString(),"incidentId",incident.getId().toString(),"reason",reason.name(),"source",source,"safeStopConfirmed",stopped));
        events.publish(a.getOrganizationId(),a.getDriver().getGroup().getId(),"service.interrupted",Map.of("serviceId",a.getServiceId().toString(),"assignmentId",a.getId().toString(),"attentionId",id.toString(),"reason",reason.name()));
        return id;
    }
    @Transactional(readOnly=true) public List<Attention> adminList(UUID service) {var a=admin();services.read(service,a.getOrganizationId());return list(service,a.getOrganizationId(),null);}
    @Transactional(readOnly=true) public List<Attention> ownList(UUID assignment) {var a=mobile.requireMobileConsultation();var offer=own(assignment,a);return offer.getServiceId()==null?List.of():list(offer.getServiceId(),a.user().getOrganizationId(),a.driver().getId());}
    private List<Attention> list(UUID service,UUID org,UUID driver) {
        return jdbc.query("select id from service_attention where service_id=? and organization_id=?"+(driver==null?"":" and driver_id=?")+" order by interrupted_at,id",(r,n)->read(r.getObject(1,UUID.class),org),driver==null?new Object[]{service,org}:new Object[]{service,org,driver});
    }
    private Attention read(UUID id,UUID org) {
        var records=jdbc.query("select * from service_attention where id=? and organization_id=?",(r,n)->{
            var decisions=jdbc.query("select * from service_attention_release where attention_id=? order by released_at,id",(v,i)->new Resolution(v.getObject("id",UUID.class),v.getObject("operation_key",UUID.class),v.getObject("actor_id",UUID.class),v.getString("resource_kind"),v.getString("disposition"),v.getString("reason"),v.getString("verification_reference"),v.getTimestamp("released_at").toInstant()),id);
            return new Attention(id,r.getObject("operation_key",UUID.class),r.getObject("service_id",UUID.class),r.getObject("assignment_id",UUID.class),r.getObject("segment_id",UUID.class),r.getObject("driver_id",UUID.class),r.getObject("vehicle_id",UUID.class),r.getObject("incident_id",UUID.class),r.getObject("recorded_by",UUID.class),r.getString("source"),Reason.valueOf(r.getString("reason")),r.getString("detail"),r.getBoolean("safe_stop_confirmed"),r.getString("verification_reference"),r.getTimestamp("interrupted_at").toInstant(),decisions.size(),decisions.stream().noneMatch(d->"DRIVER".equals(d.resourceKind())),decisions.stream().noneMatch(d->"VEHICLE".equals(d.resourceKind())&&"READY".equals(d.disposition())),decisions);
        },id,org);if(records.size()!=1)throw notFound();return records.getFirst();
    }
    @Transactional public Attention resolve(UUID id,Resolve c) {
        var actor=admin();UUID org=actor.getOrganizationId();scheduling.acquire(org);var r=read(id,org);String hash=hash("RESOLVE_ATTENTION",id,c);
        var prior=jdbc.queryForList("select attention_id,payload_sha256 from service_attention_release where actor_id=? and operation_key=?",actor.getId(),c.operationKey());
        if(!prior.isEmpty()){if(prior.stream().anyMatch(p->!id.equals(p.get("attention_id"))||!hash.equals(p.get("payload_sha256"))))throw conflict("La clave ya corresponde a otra revisión de atención.");return r;}
        if(r.version()!=c.expectedVersion())throw conflict("La atención cambió; consulta sus decisiones antes de continuar.");
        if(!Boolean.TRUE.equals(c.safetyAndTransferConfirmed()))throw conflict("Debe comprobarse la seguridad y transferencia cuando corresponda.");
        boolean releaseDriver=Boolean.TRUE.equals(c.releaseDriver());VehicleDecision unit=c.vehicleDecision();
        if(!releaseDriver&&unit==VehicleDecision.NO_CHANGE)throw conflict("Selecciona expresamente qué recurso se revisó.");
        if(releaseDriver&&!r.driverPending()||unit!=VehicleDecision.NO_CHANGE&&!r.vehiclePending())throw conflict("El recurso ya tiene una liberación final registrada.");
        if(unit==VehicleDecision.KEEP_WITHDRAWN&&r.resolutions().stream().anyMatch(d->"WITHDRAWN".equals(d.disposition())))throw conflict("La unidad ya permanece retirada; no se repite esa decisión.");
        text(c.reason());text(c.verificationReference());
        var driver=drivers.findById(r.driverId()).orElseThrow(ServiceAttention::notFound);var vehicle=vehicles.findById(r.vehicleId()).orElseThrow(ServiceAttention::notFound);
        entities.refresh(driver,LockModeType.PESSIMISTIC_WRITE);entities.refresh(vehicle,LockModeType.PESSIMISTIC_WRITE);
        Instant now=clock.instant();
        if(releaseDriver&&driver.getAvailabilityStatus()!=DriverAvailabilityStatus.ATENCION&&driver.getAvailabilityStatus()!=DriverAvailabilityStatus.NO_DISPONIBLE)throw conflict("El conductor tiene otra ocupación que debe revisarse.");
        if(unit==VehicleDecision.READY) {
            if(vehicle.getStatus()!=VehicleStatus.ATENCION&&vehicle.getStatus()!=VehicleStatus.INACTIVO)throw conflict("La unidad tiene otro estado que debe revisarse.");
            if(qualification.maintenanceBlocks(vehicle.getId(),org,now,now.plusNanos(1000))||!qualification.reviewBlockers(driver.getId(),org,vehicle.getId(),now.plusNanos(1000)).isEmpty())throw conflict("La unidad conserva bloqueos de mantenimiento o revisión documental; esta atención no los sustituye.");
        }
        // All expected failures have been checked. Any later invariant failure rolls back this entire ordinary transaction.
        if(unit!=VehicleDecision.NO_CHANGE) {
            decision(actor,r,c,hash,"VEHICLE",unit==VehicleDecision.READY?"READY":"WITHDRAWN",now);
            vehicle.finishSafetyAttention(unit==VehicleDecision.READY);vehicles.saveAndFlush(vehicle);
        }
        if(releaseDriver) {
            decision(actor,r,c,hash,"DRIVER","ELIGIBILITY_RECHECKED",now);
            driver.finishSafetyAttention(qualification.eligible(driver.getId(),org,true),margins.hasOtherConfirmed(org,driver.getId(),r.assignmentId()));drivers.saveAndFlush(driver);
        }
        audit.record(actor,"SERVICE_SAFETY_ATTENTION_REVIEWED","SERVICE_ATTENTION",id,Map.of("operationKey",c.operationKey().toString(),"driverReviewed",releaseDriver,"vehicleDecision",unit.name()));
        events.publish(org,driver.getGroup().getId(),"service.attention.updated",Map.of("serviceId",r.serviceId().toString(),"attentionId",id.toString()));return read(id,org);
    }
    private void decision(AppUser actor,Attention r,Resolve c,String hash,String resource,String disposition,Instant now) {
        jdbc.update("insert into service_attention_release(id,attention_id,organization_id,actor_id,operation_key,payload_sha256,resource_kind,disposition,reason,verification_reference,safe_stop_and_transfer_confirmed,released_at) values(?,?,?,?,?,?,?,?,?,?,true,?)",UUID.randomUUID(),r.id(),actor.getOrganizationId(),actor.getId(),c.operationKey(),hash,resource,disposition,c.reason().strip(),c.verificationReference(),ts(now));
    }
    private String hash(String action,UUID id,Object body){try{return commands.hash(action+'|'+id+'|'+json.writeValueAsString(body));}catch(Exception failure){throw new IllegalStateException("SAFETY_ACTION_HASH_UNAVAILABLE");}}
    private static void text(String value){if(value.contains("@")||value.matches("(?s).*[0-9]{8,}.*")||value.matches("(?s).*[-+]?\\d{1,3}\\.\\d+\\s*[,; ]\\s*[-+]?\\d{1,3}\\.\\d+.*"))throw conflict("Utiliza una referencia o motivo sin datos personales ni coordenadas.");}
    private static Timestamp ts(Instant instant){return Timestamp.from(instant);}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Ejecución o atención no encontrada.");}
}
