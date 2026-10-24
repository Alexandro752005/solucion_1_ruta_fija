package pe.rutafija.routing.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.operation.api.dto.*;
import pe.rutafija.operation.application.*;
import pe.rutafija.operation.domain.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.routing.api.RouteContract.Replace;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** An accepted route is never overwritten. Cancel + fresh mobile confirmation are atomic.
 * Existing response modes and acceptance timestamps remain authentic on the former service. */
@Service
public class AssignmentRouteReplacementService {
    private final JdbcTemplate jdbc;private final CurrentUserService current;private final OrganizationSchedulingLock lock;
    private final OperationService operations;private final RouteCatalogService routes;private final ObjectMapper json;private final Clock clock;
    private final ScheduledServiceDispatch dispatch;private final ScheduledServiceStore services;
    public AssignmentRouteReplacementService(JdbcTemplate jdbc,CurrentUserService current,OrganizationSchedulingLock lock,OperationService operations,RouteCatalogService routes,ObjectMapper json,Clock clock,ScheduledServiceDispatch dispatch,ScheduledServiceStore services){this.jdbc=jdbc;this.current=current;this.lock=lock;this.operations=operations;this.routes=routes;this.json=json;this.clock=clock;this.dispatch=dispatch;this.services=services;}
    @Transactional public AssignmentResponse replace(UUID id,Replace c){
        var actor=current.requireTenantActor();lock.acquire(actor.getOrganizationId());String hash=hash(id,c);
        var prior=jdbc.queryForList("select replacement_assignment_id,payload_sha256 from assignment_route_replacement where organization_id=? and actor_id=? and operation_key=?",actor.getOrganizationId(),actor.getId(),c.operationKey());
        if(!prior.isEmpty()){
            if(!hash.equals(((String)prior.getFirst().get("payload_sha256")).strip()))throw conflict("La clave ya corresponde a otro cambio de ruta.");
            return operations.getAssignment((UUID)prior.getFirst().get("replacement_assignment_id"));
        }
        if(c.reason().contains("@")||c.reason().matches(".*[0-9]{8,}.*"))throw conflict("Indica un motivo sin datos personales.");
        var old=operations.getAssignment(id);Instant now=Instant.now(clock);
        if(old.version()!=c.expectedVersion()||old.startedAt()!=null||!Set.of(AssignmentStatus.SCHEDULED,AssignmentStatus.PENDING_RESPONSE).contains(old.status())||!old.scheduledAt().isAfter(now))throw conflict("Solo puede cambiar una ruta vigente antes de iniciar y con versión actual.");
        if(Objects.equals(old.routeVersionId(),c.routeVersionId()))throw conflict("Selecciona una versión distinta.");
        var route=routes.usable(c.routeVersionId(),actor.getOrganizationId());
        if(old.serviceId()==null)throw conflict("El registro histórico no acredita un servicio separado; no se migra por inferencia.");
        Instant deadline=pe.rutafija.operation.domain.OfferResponseWindow.deadline(now,old.scheduledAt());
        if(!deadline.isAfter(now))throw conflict("No queda tiempo válido para una nueva aceptación; reprograma expresamente.");
        var command=new AssignmentCreateRequest(old.driverId(),old.vehicleId(),route.stops().getFirst().name(),route.stops().getLast().name(),old.scheduledAt(),old.scheduledEndAt(),old.notes(),AssignmentResponseMode.MOBILE_CONFIRMATION,deadline,route.id(),old.serviceTypeVersionId());
        var service=services.read(old.serviceId(),actor.getOrganizationId());
        var result=dispatch.reoffer(old.serviceId(),new pe.rutafija.operation.api.ScheduledServiceContract.Reoffer(
            service.version(),c.operationKey(),c.reason(),command));
        var next=operations.getAssignment(result.service().currentOfferId());
        jdbc.update("insert into assignment_route_replacement(previous_assignment_id,replacement_assignment_id,organization_id,actor_id,operation_key,payload_sha256,reason,occurred_at) values(?,?,?,?,?,?,?,?)",id,next.id(),actor.getOrganizationId(),actor.getId(),c.operationKey(),hash,c.reason(),Timestamp.from(now));
        return next;
    }
    @Transactional(readOnly=true) public AssignmentResponse operation(String key){var a=current.requireTenantActor();var ids=jdbc.query("select replacement_assignment_id from assignment_route_replacement where organization_id=? and actor_id=? and operation_key=?",(r,n)->r.getObject(1,UUID.class),a.getOrganizationId(),a.getId(),key);if(ids.size()!=1)throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Operación no registrada.");return operations.getAssignment(ids.getFirst());}
    private String hash(UUID id,Replace c){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((id+":"+json.writeValueAsString(c)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("ROUTE_REPLACEMENT_HASH_FAILED");}}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
}
