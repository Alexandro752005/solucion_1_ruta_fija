package pe.rutafija.operation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.operation.api.ServiceHandoverContract.*;
import pe.rutafija.operation.api.dto.*;
import pe.rutafija.operation.domain.*;
import pe.rutafija.operation.infrastructure.*;
import pe.rutafija.qualification.application.OperationalQualificationPolicy;
import pe.rutafija.routing.application.RouteCatalogService;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** Preparation is ADMIN's; the effective atomic transfer belongs only to the accepted incoming conductor. */
@Service
public class ServiceHandover {
    private final JdbcTemplate jdbc;private final CurrentUserService current;private final MobileDriverContextService mobile;
    private final OrganizationSchedulingLock lock;private final ScheduledServiceStore services;private final OperationService operations;
    private final AssignmentRepository assignments;private final EntityManager entities;private final MobileCommandProcessor commands;
    private final OperationalQualificationPolicy qualification;private final OfferSchedulingPolicy margins;private final ServiceExecution execution;
    private final DriverCurrentLocationStore locations;private final AuditService audit;private final OperationEventPublisher events;
    private final Clock clock;private final ObjectMapper json;private final RouteCatalogService routes;private final OfferExpiryService expiry;
    public ServiceHandover(JdbcTemplate jdbc,CurrentUserService current,MobileDriverContextService mobile,OrganizationSchedulingLock lock,
        ScheduledServiceStore services,OperationService operations,AssignmentRepository assignments,EntityManager entities,MobileCommandProcessor commands,
        OperationalQualificationPolicy qualification,OfferSchedulingPolicy margins,ServiceExecution execution,DriverCurrentLocationStore locations,
        AuditService audit,OperationEventPublisher events,Clock clock,ObjectMapper json,RouteCatalogService routes,OfferExpiryService expiry){
        this.jdbc=jdbc;this.current=current;this.mobile=mobile;this.lock=lock;this.services=services;this.operations=operations;this.assignments=assignments;
        this.entities=entities;this.commands=commands;this.qualification=qualification;this.margins=margins;this.execution=execution;this.locations=locations;
        this.audit=audit;this.events=events;this.clock=clock;this.json=json;this.routes=routes;this.expiry=expiry;
    }
    private AppUser admin(){var a=current.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN propio.");return a;}
    @Transactional(readOnly=true) public View adminView(UUID service){return view(service,admin().getOrganizationId(),null);}
    @Transactional(readOnly=true) public View ownView(UUID assignment){var a=mobile.requireMobileConsultation();var own=own(a,assignment);if(own.getServiceId()==null)throw notFound();return view(own.getServiceId(),a.user().getOrganizationId(),a.driver().getId());}
    private View view(UUID service,UUID org,UUID ownDriver){
        var header=services.read(service,org);
        var rows=jdbc.query("""
          select h.*,i.id incoming_offer_id,i.status incoming_status,r.kind,r.occurred_at resolved_at,r.actor_id resolved_by,r.operation_key resolution_key,
           e.ended_at outgoing_end from service_handover h join assignment o on o.id=h.outgoing_assignment_id
           left join assignment i on i.handover_id=h.id left join service_handover_resolution r on r.handover_id=h.id
           left join service_execution_segment e on e.assignment_id=o.id
          where h.service_id=? and h.organization_id=?
          """+(ownDriver==null?"":" and(h.incoming_driver_id=? or o.driver_id=?)")+" order by h.prepared_at,h.id",(r,n)->{
            Instant resolved=r.getTimestamp("resolved_at")==null?null:r.getTimestamp("resolved_at").toInstant();String kind=r.getString("kind"),state=r.getString("incoming_status");
            return new Handover(r.getObject("id",UUID.class),r.getObject("operation_key",UUID.class),r.getObject("outgoing_assignment_id",UUID.class),r.getObject("incoming_offer_id",UUID.class),r.getObject("incoming_driver_id",UUID.class),r.getObject("incoming_vehicle_id",UUID.class),r.getObject("prepared_by",UUID.class),r.getString("reason"),r.getString("point_reference"),r.getTimestamp("prepared_at").toInstant(),kind!=null?kind:"SCHEDULED".equals(state)?"ACCEPTED":state,resolved,r.getObject("resolved_by",UUID.class),r.getObject("resolution_key",UUID.class),"ASSUMED".equals(kind)&&r.getTimestamp("outgoing_end")!=null?Duration.between(r.getTimestamp("outgoing_end").toInstant(),resolved).getSeconds():null);
        },ownDriver==null?new Object[]{service,org}:new Object[]{service,org,ownDriver,ownDriver});
        return new View(header,rows);
    }
    @Transactional public View prepare(UUID service,Prepare c){
        var a=admin();UUID org=a.getOrganizationId();lock.acquire(org);String hash=hash("PREPARE",service,c);
        var prior=jdbc.queryForList("select service_id,payload_sha256 from service_handover where prepared_by=? and operation_key=?",a.getId(),c.operationKey());
        if(!prior.isEmpty()){if(!service.equals(prior.getFirst().get("service_id"))||!hash.equals(prior.getFirst().get("payload_sha256")))throw conflict("La clave ya corresponde a otra preparación.");return view(service,org,null);}
        expiry.expireForOrganization(org);var s=services.read(service,org);version(s.version(),c.expectedVersion());text(c.reason());
        if(!Boolean.TRUE.equals(c.safetyAndTransferConfirmed())||!Set.of("IN_SERVICE","INTERRUPTED").contains(s.status())||s.currentOfferId()==null)throw conflict("El relevo requiere ejecución previa y comprobación ADMIN de seguridad/transferencia.");
        var outgoing=tenant(s.currentOfferId(),org);fresh(outgoing);
        if(outgoing.getStartedAt()==null||!Set.of(AssignmentStatus.EN_SERVICIO,AssignmentStatus.INTERRUPTED).contains(outgoing.getStatus())||outgoing.getDriver().getId().equals(c.driverId())||outgoing.getVehicle().getId().equals(c.vehicleId()))throw conflict("El relevo requiere sustituto y unidad vinculada distintos del tramo saliente.");
        if(!c.scheduledAt().isAfter(clock.instant())||!c.scheduledEndAt().isAfter(c.scheduledAt()))throw conflict("Indica una ventana futura válida para la oferta del nuevo tramo.");
        if(!pending(service,org).isEmpty())throw conflict("El servicio ya tiene una oferta de relevo pendiente o aceptada.");
        UUID id=UUID.randomUUID();Instant now=clock.instant();
        jdbc.update("insert into service_handover(id,service_id,organization_id,outgoing_assignment_id,incoming_driver_id,incoming_vehicle_id,prepared_by,operation_key,payload_sha256,prepared_at,reason,point_reference,safety_and_transfer_confirmed,scheduled_at,scheduled_end_at) values(?,?,?,?,?,?,?,?,?,?,?,?,true,?,?)",
            id,service,org,outgoing.getId(),c.driverId(),c.vehicleId(),a.getId(),c.operationKey(),hash,ts(now),c.reason().strip(),c.pointReference(),ts(c.scheduledAt()),ts(c.scheduledEndAt()));
        var route=routes.version(s.routeVersionId(),org);
        operations.createReliefOffer(new AssignmentCreateRequest(c.driverId(),c.vehicleId(),route.stops().getFirst().name(),route.stops().getLast().name(),c.scheduledAt(),c.scheduledEndAt(),null,AssignmentResponseMode.MOBILE_CONFIRMATION,null,s.routeVersionId(),s.serviceTypeVersionId()),"handover-"+id,service,id);
        audit.record(a,"SERVICE_HANDOVER_PREPARED","SERVICE",service,Map.of("handoverId",id.toString(),"outgoingAssignmentId",outgoing.getId().toString(),"pointReference",c.pointReference()));publish(org,service,"service.handover.prepared");return view(service,org,null);
    }
    @Transactional public View withdraw(UUID id,Withdraw c){
        var a=admin();UUID org=a.getOrganizationId();lock.acquire(org);var h=handover(id,org);UUID service=(UUID)h.get("service_id");String hash=hash("WITHDRAW",id,c);
        var prior=jdbc.queryForList("select handover_id,payload_sha256 from service_handover_resolution where actor_id=? and operation_key=?",a.getId(),c.operationKey());
        if(!prior.isEmpty()){if(!id.equals(prior.getFirst().get("handover_id"))||!hash.equals(prior.getFirst().get("payload_sha256")))throw conflict("La clave ya corresponde a otro retiro.");return view(service,org,null);}
        version(services.read(service,org).version(),c.expectedVersion());text(c.reason());var incoming=incoming(id,org);fresh(incoming);
        if(!Set.of(AssignmentStatus.PENDING_RESPONSE,AssignmentStatus.SCHEDULED).contains(incoming.getStatus())||hasResolution(id))throw conflict("Solo se retira un relevo todavía no asumido.");
        resolution(a,id,org,c.operationKey(),hash,"WITHDRAWN",c.reason().strip(),clock.instant());operations.cancelAssignment(incoming.getId(),new AssignmentCancelRequest(incoming.getVersion(),"HANDOVER_WITHDRAWN"));publish(org,service,"service.handover.withdrawn");return view(service,org,null);
    }
    /** Invoked only by the already-authorized ADMIN service cancellation while holding the same tenant lock. */
    void withdrawPendingForCancellation(UUID service,AppUser a){
        for(UUID id:pending(service,a.getOrganizationId()))withdraw(id,new Withdraw(UUID.randomUUID(),services.read(service,a.getOrganizationId()).version(),"SERVICE_CANCELLED_BEFORE_HANDOVER"));
    }
    /** A valid own completion also ends its unstarted standby; preserves actual conductor/system causality, never ADMIN impersonation. */
    void closePendingOnCompletion(Assignment outgoing,MobileDriverActor a,Instant now){
        for(UUID id:pending(outgoing.getServiceId(),outgoing.getOrganizationId())){
            var h=handover(id,outgoing.getOrganizationId());if(!outgoing.getId().equals(h.get("outgoing_assignment_id")))throw new IllegalStateException("HANDOVER_PENDING_SCOPE_INVARIANT");
            var i=incoming(id,outgoing.getOrganizationId());fresh(i);resolution(a.user(),id,outgoing.getOrganizationId(),UUID.randomUUID(),commands.hash("SERVICE_ENDED|"+id),"SERVICE_ENDED","SERVICE_COMPLETED_BEFORE_HANDOVER",now);
            if(i.getStatus()==AssignmentStatus.SCHEDULED&&i.getDriver().getAvailabilityStatus()==DriverAvailabilityStatus.RESERVADO&&!margins.hasOtherConfirmed(i.getOrganizationId(),i.getDriver().getId(),i.getId()))i.getDriver().releaseReservation();
            i.cancel("SERVICE_COMPLETED_BEFORE_HANDOVER",now);assignments.saveAndFlush(i);
            audit.record(a.user(),"SERVICE_HANDOVER_SUPERSEDED_BY_OWN_COMPLETION","SERVICE_HANDOVER",id,Map.of("outgoingAssignmentId",outgoing.getId().toString()));
        }
    }
    @Transactional(noRollbackFor=ApplicationException.class) public MobileCommandExecution<AssignmentResponse> assume(UUID assignment,Assume c){
        var a=mobile.requireMobileDriver();UUID org=a.user().getOrganizationId();lock.acquire(org);entities.refresh(a.driver(),LockModeType.PESSIMISTIC_WRITE);var incoming=own(a,assignment);String hash=hash("ASSUME",assignment,c);
        return commands.execute(a,c.clientEventId(),c.occurredAt(),MobileCommandType.ASSIGNMENT_ASSUME_HANDOVER,incoming,hash,
            receipt->AssignmentResponse.from(own(a,receipt.getAssignment().getId())),()->incoming.getStatus().name(),()->assumeLocked(a,incoming,c,hash),r->r.status().name());
    }
    private AssignmentResponse assumeLocked(MobileDriverActor a,Assignment incoming,Assume c,String hash){
        fresh(incoming);version(incoming.getVersion(),c.version());UUID org=a.user().getOrganizationId();
        if(incoming.getHandoverId()==null||incoming.getStatus()!=AssignmentStatus.SCHEDULED||incoming.getAcceptedAt()==null||incoming.getReservedAt()==null||hasResolution(incoming.getHandoverId()))throw conflict("Asumir requiere una oferta propia de relevo aceptada y vigente.");
        var h=handover(incoming.getHandoverId(),org);var header=services.read(incoming.getServiceId(),org);var outgoing=tenant((UUID)h.get("outgoing_assignment_id"),org);fresh(outgoing);
        if(!outgoing.getId().equals(header.currentOfferId())||!Set.of("IN_SERVICE","INTERRUPTED").contains(header.status())||!Set.of(AssignmentStatus.EN_SERVICIO,AssignmentStatus.INTERRUPTED).contains(outgoing.getStatus()))throw conflict("La participación saliente o el servicio cambiaron; no puede asumirse este relevo.");
        if(!Boolean.TRUE.equals(c.readyConfirmed()))throw conflict("Confirma personalmente que puedes asumir el relevo.");
        if(!incoming.getDriver().isLocationConsent())throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.LOCATION_CONSENT_REQUIRED,"El relevo requiere consentimiento GPS propio.");
        if(c.gps()==null||!c.gps().permitsStart())throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.LOCATION_PERMISSION_NOT_REPORTED,"El relevo requiere permiso, ubicación habilitada y primer plano.");
        margins.requireCaptured(incoming);qualification.requireStart(incoming.getDriver().getId(),org,incoming.getVehicle().getId(),incoming.getScheduledAt(),incoming.getScheduledEndAt());
        if(incoming.getDriver().getAvailabilityStatus()!=DriverAvailabilityStatus.RESERVADO||!incoming.getVehicle().isActive()||incoming.getVehicle().getStatus()!=VehicleStatus.DISPONIBLE)throw conflict("El sustituto o su unidad ya no están disponibles para asumir.");
        boolean ongoing=outgoing.getStatus()==AssignmentStatus.EN_SERVICIO;
        if(ongoing&&execution.active(outgoing)==null)throw conflict("La participación saliente no conserva su tramo abierto.");
        if(jdbc.queryForObject("select count(*) from service_execution_segment where ended_at is null and(driver_id=? or vehicle_id=?)",Integer.class,incoming.getDriver().getId(),incoming.getVehicle().getId())!=0)throw conflict("El entrante ya tiene un tramo abierto incompatible.");
        Instant now=clock.instant();
        // All expected failures precede these writes. Unexpected invariants are runtime errors and roll back the physical transaction.
        resolution(a.user(),incoming.getHandoverId(),org,c.clientEventId(),hash,"ASSUMED","INCOMING_CONDUCTOR_CONFIRMED_HANDOVER",now);
        if(ongoing){outgoing.transfer(now);outgoing.getVehicle().finishTransferredService();outgoing.getDriver().finishTransferredService(qualification.eligible(outgoing.getDriver().getId(),org,true),margins.hasOtherConfirmed(org,outgoing.getDriver().getId(),outgoing.getId()));assignments.saveAndFlush(outgoing);execution.recordTransfer(outgoing,a,c.clientEventId(),now);}
        incoming.getDriver().beginService();incoming.getVehicle().beginService();incoming.start(now);assignments.saveAndFlush(incoming);execution.recordStart(incoming,a,c.clientEventId(),now);
        locations.deleteByDriverId(outgoing.getDriver().getId());locations.deleteByDriverId(incoming.getDriver().getId());
        audit.record(a.user(),"SERVICE_HANDOVER_ASSUMED","SERVICE",incoming.getServiceId(),Map.of("handoverId",incoming.getHandoverId().toString(),"outgoingAssignmentId",outgoing.getId().toString(),"incomingAssignmentId",incoming.getId().toString()));publish(org,incoming.getServiceId(),"service.handover.assumed");return AssignmentResponse.from(incoming);
    }
    private void resolution(AppUser a,UUID id,UUID org,UUID key,String hash,String kind,String reason,Instant now){jdbc.update("insert into service_handover_resolution(id,handover_id,organization_id,actor_id,operation_key,payload_sha256,kind,reason,occurred_at) values(?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,org,a.getId(),key,hash,kind,reason,ts(now));}
    private List<UUID> pending(UUID service,UUID org){return jdbc.query("select h.id from service_handover h join assignment i on i.handover_id=h.id where h.service_id=? and h.organization_id=? and i.status in('PENDING_RESPONSE','SCHEDULED') and not exists(select 1 from service_handover_resolution r where r.handover_id=h.id)",(r,n)->r.getObject(1,UUID.class),service,org);}
    private boolean hasResolution(UUID id){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_handover_resolution where handover_id=?)",Boolean.class,id));}
    private Map<String,Object> handover(UUID id,UUID org){var rows=jdbc.queryForList("select * from service_handover where id=? and organization_id=?",id,org);if(rows.size()!=1)throw notFound();return rows.getFirst();}
    private Assignment incoming(UUID id,UUID org){var rows=jdbc.query("select id from assignment where handover_id=? and organization_id=?",(r,n)->r.getObject(1,UUID.class),id,org);if(rows.size()!=1)throw notFound();return tenant(rows.getFirst(),org);}
    private Assignment tenant(UUID id,UUID org){return assignments.findByIdAndOrganization_Id(id,org).orElseThrow(ServiceHandover::notFound);}
    private Assignment own(MobileDriverActor a,UUID id){var r=tenant(id,a.user().getOrganizationId());if(!r.getDriver().getId().equals(a.driver().getId()))throw notFound();return r;}
    private void fresh(Assignment a){entities.refresh(a,LockModeType.PESSIMISTIC_WRITE);entities.refresh(a.getDriver(),LockModeType.PESSIMISTIC_WRITE);entities.refresh(a.getVehicle(),LockModeType.PESSIMISTIC_WRITE);}
    private void publish(UUID org,UUID service,String event){events.publish(org,null,event,Map.of("serviceId",service.toString()));}
    private static void version(long actual,long expected){if(actual!=expected)throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_VERSION_CONFLICT,"La versión cambió; consulta el mismo caso.");}
    private static void text(String value){if(value.contains("@")||value.matches("(?s).*[0-9]{8,}.*")||value.matches("(?s).*[-+]?\\d{1,3}\\.\\d+\\s*[,; ]\\s*[-+]?\\d{1,3}\\.\\d+.*"))throw conflict("Utiliza un motivo sin datos personales ni coordenadas.");}
    private String hash(String action,UUID id,Object c){try{return commands.hash(action+'|'+id+'|'+json.writeValueAsString(c));}catch(Exception e){throw new IllegalStateException("HANDOVER_HASH_UNAVAILABLE");}}
    private static Timestamp ts(Instant now){return Timestamp.from(now);}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Servicio, oferta o relevo no encontrado.");}
}
