package pe.rutafija.operation.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.operation.api.IncidentWorkflowContract.*;
import pe.rutafija.operation.api.dto.*;
import pe.rutafija.operation.domain.*;
import pe.rutafija.operation.infrastructure.IncidentRepository;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;

/** Versioned incident attention. Projection fields keep compatibility; action history never overwrites. */
@Service
public class IncidentWorkflow {
    private final JdbcTemplate jdbc;
    private final IncidentRepository incidents;
    private final AppUserRepository users;
    private final CurrentUserService current;
    private final OrganizationSchedulingLock scheduling;
    private final ObjectMapper json;
    private final Clock clock;
    private final AuditService audit;
    private final OperationEventPublisher events;
    private final jakarta.persistence.EntityManager entities;
    public IncidentWorkflow(JdbcTemplate jdbc,IncidentRepository incidents,AppUserRepository users,CurrentUserService current,
        OrganizationSchedulingLock scheduling,ObjectMapper json,Clock clock,AuditService audit,OperationEventPublisher events,jakarta.persistence.EntityManager entities){
        this.jdbc=jdbc;this.incidents=incidents;this.users=users;this.current=current;this.scheduling=scheduling;
        this.json=json;this.clock=clock;this.audit=audit;this.events=events;
        this.entities=entities;
    }
    public void beforeWrite(AppUser actor){scheduling.acquire(actor.getOrganizationId());}
    public IncidentResponse view(Incident incident){
        boolean interrupted=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_attention where incident_id=?)",Boolean.class,incident.getId()));
        return IncidentResponse.from(incident,interrupted);
    }
    public Optional<IncidentResponse> replayReport(AppUser actor,UUID key,Object payload){
        return replay(actor,null,"REPORT",key,payload).map(this::view);
    }
    public void prepareReport(Incident report,AppUser actor,IncidentSeverity severity,UUID previous,String relation){
        text(report.getDescription());explicitSeverity(severity);report.assess(severity);
        if(previous==null){if(relation!=null&&!relation.isBlank())throw invalid("El motivo de vínculo requiere una incidencia previa");return;}
        if(!Set.of("RECURRENCE","CLOSURE_ERROR").contains(Objects.toString(relation,"")))throw invalid("Indica recurrencia o cierre erróneo");
        var prior=incidents.findByIdAndOrganization_Id(previous,actor.getOrganizationId()).orElseThrow(this::notFound);
        if(actor.getRole()==UserRole.CONDUCTOR&&(prior.getDriver().getUser()==null||!prior.getDriver().getUser().getId().equals(actor.getId())))throw notFound();
        if(!Set.of(IncidentStatus.RESOLVED,IncidentStatus.CLOSED_BY_DRIVER).contains(prior.getStatus()))throw conflict("El caso anterior debe estar cerrado");
        report.relatePrevious(previous,relation);
    }
    public void recordReport(Incident report,AppUser actor,UUID key,Object payload){
        var details=new LinkedHashMap<String,Object>();details.put("severity",report.getSeverity());
        if(report.getPreviousIncidentId()!=null){details.put("previousIncidentId",report.getPreviousIncidentId());details.put("relationReason",report.getRelationReason());}
        append(report,actor,"REPORT",key,payload,details);
    }
    public void recordDriverClosure(Incident report,AppUser actor){
        UUID key=UUID.randomUUID();
        append(report,actor,"DRIVER_CLOSE",key,Map.of("incidentId",report.getId()),Map.of("closedAt",report.getDriverClosedAt()));
    }
    @Transactional(readOnly=true)
    public List<Candidate> responsibleCandidates(){
        var actor=admin();return jdbc.query("select id from app_user where organization_id=? and role='ADMIN' and active order by id",
            (r,n)->new Candidate(r.getObject(1,UUID.class),"CUENTA-"+r.getObject(1,UUID.class)),actor.getOrganizationId());
    }
    @Transactional(readOnly=true)
    public List<Action> history(UUID id){
        var actor=admin();requireIncident(id,actor);
        return actions(id,actor.getOrganizationId());
    }
    public List<Action> ownHistory(Incident incident){return actions(incident.getId(),incident.getOrganizationId());}
    @Transactional(readOnly=true)
    public Operation operation(UUID key){return operation(current.requireTenantActor(),key);}
    public Operation operation(AppUser actor,UUID key){
        var rows=jdbc.query("select operation_key,incident_id,kind,recorded_at,resulting_version from incident_action where organization_id=? and actor_id=? and operation_key=?",
            (r,n)->new Operation(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getTimestamp(4).toInstant(),r.getLong(5)),actor.getOrganizationId(),actor.getId(),key);
        if(rows.isEmpty())throw notFound();return rows.getFirst();
    }
    private List<Action> actions(UUID id,UUID organization){
        return jdbc.query("select id,operation_key,actor_id,kind,detail::text,recorded_at,resulting_version from incident_action where incident_id=? and organization_id=? order by resulting_version,recorded_at,id",
            (r,n)->new Action(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class),r.getString(4),readDetail(r.getString(5)),r.getTimestamp(6).toInstant(),r.getLong(7)),id,organization);
    }
    public IncidentResponse information(Incident incident,AppUser actor,Information command){
        beforeWrite(actor);
        var prior=replay(actor,incident.getId(),"INFORMATION",command.operationKey(),command);if(prior.isPresent())return view(prior.get());
        incident=mutable(incident.getId(),actor,command.expectedVersion());text(command.note());
        if(!incident.getDriver().getUser().getId().equals(actor.getId()))throw notFound();
        incident.recordAdditionalInformation();incidents.saveAndFlush(incident);
        append(incident,actor,"INFORMATION",command.operationKey(),command,Map.of("note",command.note().strip()));return view(incident);
    }
    @Transactional
    public IncidentResponse responsibility(UUID id,Responsibility command){
        var actor=admin();beforeWrite(actor);var prior=replay(actor,id,"RESPONSIBILITY",command.operationKey(),command);if(prior.isPresent())return view(prior.get());
        var incident=mutable(id,actor,command.expectedVersion());text(command.reason());
        var target=users.findByIdAndOrganization_IdAndRole(command.adminId(),actor.getOrganizationId(),UserRole.ADMIN).filter(AppUser::isActive).orElseThrow(this::notFound);
        var detail=new LinkedHashMap<String,Object>();detail.put("reason",command.reason().strip());
        if(incident.getResponsibleAdmin()!=null)detail.put("previousAdminId",incident.getResponsibleAdmin().getId());
        detail.put("adminId",target.getId());incident.assignResponsible(target);incidents.saveAndFlush(incident);
        append(incident,actor,"RESPONSIBILITY",command.operationKey(),command,detail);return view(incident);
    }
    @Transactional
    public IncidentResponse assess(UUID id,Assessment command){
        var actor=admin();beforeWrite(actor);var prior=replay(actor,id,"ASSESSMENT",command.operationKey(),command);if(prior.isPresent())return view(prior.get());
        var incident=mutable(id,actor,command.expectedVersion());text(command.reason());explicitSeverity(command.severity());
        var before=incident.getSeverity();incident.assess(command.severity());incidents.saveAndFlush(incident);
        append(incident,actor,"ASSESSMENT",command.operationKey(),command,Map.of("before",before,"after",command.severity(),"reason",command.reason().strip()));return view(incident);
    }
    @Transactional
    public IncidentResponse followUp(UUID id,IncidentFollowUpRequest command){
        var actor=admin();beforeWrite(actor);var prior=replay(actor,id,"FOLLOW_UP",command.operationKey(),command);if(prior.isPresent())return view(prior.get());
        var incident=mutable(id,actor,command.version());requireResponsible(incident);text(command.note());
        explicitSeverity(incident.getSeverity());
        if(command.resolve())throw invalid("Para cerrar registra diagnóstico, acciones, resultado y evidencia en el cierre estructurado");
        incident.followUp(command.note(),false,actor,clock.instant());incidents.saveAndFlush(incident);
        append(incident,actor,"FOLLOW_UP",command.operationKey(),command,Map.of("note",command.note().strip()));return view(incident);
    }
    @Transactional
    public IncidentResponse close(UUID id,Closure command){
        var actor=admin();beforeWrite(actor);var prior=replay(actor,id,"RESOLUTION",command.operationKey(),command);if(prior.isPresent())return view(prior.get());
        var incident=mutable(id,actor,command.expectedVersion());requireResponsible(incident);
        explicitSeverity(incident.getSeverity());
        text(command.diagnosis());text(command.actions());text(command.result());
        if(command.evidenceMode()==null||command.evidenceRequired()==null)throw invalid("Selecciona el tratamiento de evidencia");
        // Validate optional fields too: a field hidden by the selected mode must
        // not become a way to retain PII or coordinates in the immutable history.
        if(command.evidenceExplanation()!=null)text(command.evidenceExplanation());
        if(command.evidenceReference()!=null){text(command.evidenceReference());if(!command.evidenceReference().matches("[A-Z][A-Z0-9_:-]{2,99}"))throw invalid("La referencia de evidencia debe ser codificada");}
        if(command.evidenceRequired()&&command.evidenceMode()==EvidenceMode.NOT_REQUIRED)throw invalid("La evidencia requerida necesita referencia o imposibilidad justificada");
        if(command.evidenceMode()==EvidenceMode.REFERENCE){
            if(command.evidenceReference()==null||!command.evidenceReference().matches("[A-Z][A-Z0-9_:-]{2,99}"))throw invalid("Usa una referencia codificada de evidencia, sin archivos ni datos personales");
        }else text(command.evidenceExplanation());
        var detail=new LinkedHashMap<String,Object>();detail.put("diagnosis",command.diagnosis().strip());detail.put("actions",command.actions().strip());detail.put("result",command.result().strip());detail.put("evidenceRequired",command.evidenceRequired());detail.put("evidenceMode",command.evidenceMode());
        if(command.evidenceReference()!=null)detail.put("evidenceReference",command.evidenceReference());if(command.evidenceExplanation()!=null)detail.put("evidenceExplanation",command.evidenceExplanation());
        detail.put("resourcesReleased",false);detail.put("responsibleAdminId",incident.getResponsibleAdmin().getId());
        incident.followUp(command.result(),true,actor,clock.instant());incidents.saveAndFlush(incident);
        append(incident,actor,"RESOLUTION",command.operationKey(),command,detail);return view(incident);
    }
    private AppUser admin(){var actor=current.requireTenantActor();if(actor.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La atención requiere ADMIN de su organización");return actor;}
    private Incident requireIncident(UUID id,AppUser actor){return incidents.findByIdAndOrganization_Id(id,actor.getOrganizationId()).orElseThrow(this::notFound);}
    private Incident mutable(UUID id,AppUser actor,Long version){
        var incident=requireIncident(id,actor);
        // Own consultation may have loaded this row before waiting on the
        // organization lock. Compare the command against committed state.
        entities.refresh(incident);
        if(Set.of(IncidentStatus.RESOLVED,IncidentStatus.CLOSED_BY_DRIVER).contains(incident.getStatus()))throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.INCIDENT_INVALID_STATE,"Una incidencia cerrada no se reabre ni modifica");
        if(version==null||incident.getVersion()!=version)throw new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_VERSION_CONFLICT,"La incidencia cambió; consulta su estado");return incident;
    }
    private void requireResponsible(Incident incident){if(incident.getResponsibleAdmin()==null||!incident.getResponsibleAdmin().isActive())throw conflict("Designa un ADMIN responsable activo antes de atender o cerrar");}
    private void explicitSeverity(IncidentSeverity severity){if(severity==null||severity==IncidentSeverity.UNASSESSED)throw invalid("Selecciona una gravedad explícita; los legados sin evaluación no se califican por inferencia");}
    private void text(String value){
        if(value==null||value.isBlank())throw invalid("Completa el motivo o resultado obligatorio");
        if(value.contains("@")||value.matches("(?s).*[0-9]{8,}.*")||value.matches("(?s).*[-+]?\\d{1,3}\\.\\d+\\s*[,; ]\\s*[-+]?\\d{1,3}\\.\\d+.*"))throw invalid("No incluyas datos personales ni coordenadas en las notas ordinarias");
    }
    private Optional<Incident> replay(AppUser actor,UUID id,String kind,UUID key,Object payload){
        if(key==null)throw invalid("La operación necesita una clave UUID");
        var rows=jdbc.queryForList("select incident_id,actor_id,kind,payload_sha256 from incident_action where organization_id=? and operation_key=?",actor.getOrganizationId(),key);
        if(rows.isEmpty())return Optional.empty();var r=rows.getFirst();
        if(!actor.getId().equals(r.get("actor_id"))||!kind.equals(r.get("kind"))||(id!=null&&!id.equals(r.get("incident_id")))||!hash(payload).equals(r.get("payload_sha256")))throw conflict("La clave ya corresponde a otra operación");
        return Optional.of(requireIncident((UUID)r.get("incident_id"),actor));
    }
    private void append(Incident incident,AppUser actor,String kind,UUID key,Object payload,Map<String,Object> detail){
        try{
            UUID actionId=UUID.randomUUID();
            jdbc.update("insert into incident_action(id,organization_id,incident_id,actor_id,operation_key,payload_sha256,kind,detail,recorded_at,resulting_version) values(?,?,?,?,?,?,?,?::jsonb,?,?)",
                actionId,actor.getOrganizationId(),incident.getId(),actor.getId(),key,hash(payload),kind,json.writeValueAsString(detail),Timestamp.from(clock.instant()),incident.getVersion());
            var differences=new ArrayList<Map<String,Object>>();
            if(incident.getVersion()>0)differences.addAll(pe.rutafija.audit.application.AuditChanges.fields("version",incident.getVersion()-1,incident.getVersion()));
            if("ASSESSMENT".equals(kind))differences.addAll(pe.rutafija.audit.application.AuditChanges.fields("severity",detail.get("before"),detail.get("after")));
            if("RESPONSIBILITY".equals(kind))differences.addAll(pe.rutafija.audit.application.AuditChanges.fields("responsibleAdminId",detail.get("previousAdminId"),detail.get("adminId")));
            audit.record(actor,"INCIDENT_"+kind,"INCIDENT",incident.getId(),Map.of("operationKey",key,"actionId",actionId,"resultingVersion",incident.getVersion(),"changes",differences,"reasonReferenceId",actionId,"evidenceReference",actionId));
            if(!Set.of("REPORT","DRIVER_CLOSE").contains(kind))events.publish(actor.getOrganizationId(),incident.getDriver().getGroup().getId(),"incident.attention.changed",Map.of("incidentId",incident.getId(),"driverId",incident.getDriver().getId(),"kind",kind));
        }catch(com.fasterxml.jackson.core.JsonProcessingException failure){throw new IllegalStateException("INCIDENT_ACTION_SERIALIZATION_FAILED");}
    }
    private String hash(Object payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(payload)));}catch(Exception e){throw new IllegalStateException("INCIDENT_PAYLOAD_HASH_FAILED");}}
    private Map<String,Object> readDetail(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("INCIDENT_HISTORY_PARSE_FAILED");}}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El recurso no existe");}
    private ApplicationException invalid(String value){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,value);}
    private ApplicationException conflict(String value){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,value);}
}
