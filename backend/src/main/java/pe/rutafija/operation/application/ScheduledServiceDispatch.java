package pe.rutafija.operation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import pe.rutafija.identity.domain.*;
import pe.rutafija.operation.api.ScheduledServiceContract.*;
import pe.rutafija.operation.api.dto.*;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.shared.exception.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static pe.rutafija.operation.application.ScheduledServiceStore.conflict;

/** Explicit reoffers and service cancellation, under the same tenant lock as reservations. */
@Service
public class ScheduledServiceDispatch {
    private final ScheduledServiceStore store;private final JdbcTemplate jdbc;
    private final CurrentUserService current;private final OrganizationSchedulingLock lock;
    private final OperationService operations;private final OfferExpiryService expiry;
    private final ObjectMapper json;private final Clock clock;private final AuditService audit;
    private final OperationEventPublisher events;
    private final ServiceHandover handovers;
    public ScheduledServiceDispatch(ScheduledServiceStore store,JdbcTemplate jdbc,CurrentUserService current,
            OrganizationSchedulingLock lock,OperationService operations,OfferExpiryService expiry,ObjectMapper json,
            Clock clock,AuditService audit,OperationEventPublisher events,ServiceHandover handovers){
        this.store=store;this.jdbc=jdbc;this.current=current;this.lock=lock;this.operations=operations;
        this.expiry=expiry;this.json=json;this.clock=clock;this.audit=audit;this.events=events;
        this.handovers=handovers;
    }
    private AppUser actor(){
        var a=current.requireTenantActor();
        if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN de la organización.");
        return a;
    }
    @Transactional(readOnly=true) public List<Summary> list(int limit,int offset){return store.list(actor().getOrganizationId(),limit,offset);}
    @Transactional(readOnly=true) public View read(UUID id){
        var a=actor();var header=store.read(id,a.getOrganizationId());
        var offers=jdbc.query("select id from assignment where service_id=? and organization_id=? order by created_at,id",
            (r,n)->r.getObject(1,UUID.class),id,a.getOrganizationId()).stream().map(operations::getAssignment).toList();
        return new View(header,offers);
    }
    @Transactional(readOnly=true) public Receipt operation(String key){return receipt(actor(),key).orElseThrow(()->
        new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Operación no registrada; conserva la solicitud sin repetir automáticamente."));}
    @Transactional public View reoffer(UUID id,Reoffer c){
        var a=actor();lock.acquire(a.getOrganizationId());String hash=hash("REOFFER",id,c);
        if(replay(a,c.operationKey(),hash,id))return read(id);
        reason(c.reason());var before=store.read(id,a.getOrganizationId());version(before,c.expectedVersion());
        if(Set.of("IN_SERVICE","COMPLETED","CANCELLED","INTERRUPTED").contains(before.status()))throw conflict("El servicio no admite otra oferta ordinaria en ese estado; una ejecución interrumpida requiere su relevo autorizado.");
        expiry.expireForOrganization(a.getOrganizationId());
        retireCurrent(a,id,c.reason());
        var next=operations.createOffer(c.offer(),"service-offer-"+UUID.randomUUID(),id).assignment();
        record(a,id,c.operationKey(),"REOFFER",hash,before.currentOfferId(),next.id(),c.reason());
        return read(id);
    }
    @Transactional public View cancel(UUID id,Cancel c){
        var a=actor();lock.acquire(a.getOrganizationId());String hash=hash("CANCEL_SERVICE",id,c);
        if(replay(a,c.operationKey(),hash,id))return read(id);
        reason(c.reason());var before=store.read(id,a.getOrganizationId());version(before,c.expectedVersion());
        if(Set.of("IN_SERVICE","COMPLETED","CANCELLED").contains(before.status()))throw conflict("Solo se cancela expresamente un servicio pendiente o confirmado antes de iniciar.");
        expiry.expireForOrganization(a.getOrganizationId());
        if(!"INTERRUPTED".equals(before.status()))retireCurrent(a,id,c.reason()); // Interrupted participation stays original; attention holds remain.
        handovers.withdrawPendingForCancellation(id,a);
        Instant now=clock.instant();
        jdbc.update("update scheduled_service set status='CANCELLED',cancelled_at=?,cancellation_reason=?,updated_at=?,version=version+1 where id=? and organization_id=?",
            Timestamp.from(now),c.reason().strip(),Timestamp.from(now),id,a.getOrganizationId());
        record(a,id,c.operationKey(),"CANCEL_SERVICE",hash,before.currentOfferId(),null,c.reason());
        return read(id);
    }
    private void retireCurrent(AppUser a,UUID id,String reason){
        var s=store.read(id,a.getOrganizationId());if(s.currentOfferId()==null)return;
        var old=operations.getAssignment(s.currentOfferId());
        if(old.startedAt()!=null||old.status()==AssignmentStatus.EN_SERVICIO)throw conflict("La prestación ya empezó; requiere atención segura.");
        if(Set.of(AssignmentStatus.SCHEDULED,AssignmentStatus.PENDING_RESPONSE).contains(old.status())){
            operations.cancelAssignment(old.id(),new AssignmentCancelRequest(old.version(),"SERVICE_COMMAND: "+reason));
        }
    }
    private void record(AppUser a,UUID id,String key,String action,String hash,UUID previous,UUID next,String reason){
        var s=store.read(id,a.getOrganizationId());Instant now=clock.instant();
        jdbc.update("""
            insert into scheduled_service_operation(id,organization_id,actor_id,service_id,operation_key,action,
                payload_sha256,previous_offer_id,resulting_offer_id,reason,occurred_at,resulting_version)
            values(?,?,?,?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),a.getOrganizationId(),a.getId(),id,key,action,hash,previous,next,reason.strip(),Timestamp.from(now),s.version());
        audit.record(a,"SERVICE_"+action,"SCHEDULED_SERVICE",id,Map.of("operationKey",key,"reason",reason.strip()));
        events.publish(a.getOrganizationId(),null,"service.updated",Map.of("serviceId",id.toString(),"status",s.status(),"version",s.version()));
    }
    private Optional<Receipt> receipt(AppUser a,String key){
        return jdbc.query("select * from scheduled_service_operation where organization_id=? and actor_id=? and operation_key=?",
            (r,n)->new Receipt(r.getObject("id",UUID.class),r.getObject("service_id",UUID.class),r.getString("operation_key"),r.getString("action"),
            r.getObject("previous_offer_id",UUID.class),r.getObject("resulting_offer_id",UUID.class),r.getLong("resulting_version"),r.getTimestamp("occurred_at").toInstant()),
            a.getOrganizationId(),a.getId(),key).stream().findFirst();
    }
    private boolean replay(AppUser a,String key,String hash,UUID id){
        var prior=receipt(a,key);if(prior.isEmpty())return false;
        var stored=jdbc.queryForObject("select payload_sha256 from scheduled_service_operation where id=?",String.class,prior.get().id());
        if(!id.equals(prior.get().serviceId())||!hash.equals(stored.strip()))throw conflict("La clave corresponde a otra solicitud de servicio.");
        return true;
    }
    private void version(Summary s,long expected){if(s.version()!=expected)throw conflict("El servicio cambió; consulta su estado y conserva el formulario.");}
    private void reason(String reason){if(reason.contains("@")||reason.matches(".*[0-9]{8,}.*"))throw conflict("Utiliza un motivo sin datos personales.");}
    private String hash(String action,UUID id,Object c){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
        .digest((action+":"+id+":"+json.writeValueAsString(c)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(Exception e){throw new IllegalStateException("SERVICE_HASH_FAILED");}}
}
