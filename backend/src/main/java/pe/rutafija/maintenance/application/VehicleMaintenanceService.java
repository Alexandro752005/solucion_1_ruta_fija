package pe.rutafija.maintenance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.maintenance.api.MaintenanceContract.*;
import pe.rutafija.operation.application.OrganizationSchedulingLock;
import pe.rutafija.qualification.application.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;

@Service
public class VehicleMaintenanceService {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final CurrentUserService current;private final Clock clock;
    private final OrganizationSchedulingLock scheduling;private final VehicleRepository vehicles;private final DriverRepository drivers;
    private final OperationalQualificationPolicy policy;private final DriverQualificationService qualification;private final AuditService audit;
    public VehicleMaintenanceService(JdbcTemplate jdbc,ObjectMapper json,CurrentUserService current,Clock clock,OrganizationSchedulingLock scheduling,VehicleRepository vehicles,DriverRepository drivers,OperationalQualificationPolicy policy,DriverQualificationService qualification,AuditService audit){this.jdbc=jdbc;this.json=json;this.current=current;this.clock=clock;this.scheduling=scheduling;this.vehicles=vehicles;this.drivers=drivers;this.policy=policy;this.qualification=qualification;this.audit=audit;}
    @Transactional(readOnly=true) public View read(UUID vehicle){var a=current.requireTenantActor();own(vehicle,a);return view(vehicle,a.getOrganizationId());}
    @Transactional(readOnly=true) public Preview preview(UUID vehicle,LocalDate firstDay){var a=current.requireTenantActor();own(vehicle,a);Instant start=day(firstDay);var list=commitments(vehicle,a.getOrganizationId(),start);return new Preview(vehicle,firstDay,start,list,list.isEmpty(),now());}
    @Transactional public View create(UUID vehicle,Create c){
        var a=begin(vehicle);String action="CREATE_"+c.kind();if(prior(vehicle,a,c.operationKey(),action,c))return view(vehicle,a.getOrganizationId());checkVersion(vehicle,c.expectedVersion());safe(c.reason());
        LocalDate today=today(),first="URGENT".equals(c.kind())?today:c.startDate();
        if(first==null||first.isBefore(today)||("URGENT".equals(c.kind())&&c.startDate()!=null&&!today.equals(c.startDate())))throw conflict("El inicio debe corresponder a un día vigente de Lima; la avería usa el día real del servidor.");
        if(("PREVENTIVE".equals(c.kind())&&c.endDate()==null)||(c.endDate()!=null&&c.endDate().isBefore(first)))throw conflict("Define un intervalo previsto válido; el día final está incluido.");
        Instant start=day(first);var affected=commitments(vehicle,a.getOrganizationId(),start);
        if("PREVENTIVE".equals(c.kind())&&!affected.isEmpty())throw conflict("Resuelve todos los compromisos desde el inicio antes de confirmar el preventivo; el fin previsto no promete retorno.");
        UUID record=UUID.randomUUID();jdbc.update("insert into vehicle_maintenance(id,vehicle_id,organization_id,kind,starts_at,planned_ends_at,reason,created_by,created_at) values(?,?,?,?,?,?,?,?,?)",record,vehicle,a.getOrganizationId(),c.kind(),ts(start),ts(c.endDate()==null?null:day(c.endDate().plusDays(1))),c.reason().strip(),a.getId(),ts(now()));
        if("URGENT".equals(c.kind())){
            for(UUID driver:affected.stream().map(Commitment::driverId).distinct().sorted().toList()){
                var d=drivers.lockByIdAndOrganizationId(driver,a.getOrganizationId()).orElseThrow(VehicleMaintenanceService::notFound);
                qualification.effects(d,"URGENT_VEHICLE_MAINTENANCE");
            }
        }
        event(vehicle,a,c.operationKey(),action,c,record);return view(vehicle,a.getOrganizationId());
    }
    @Transactional public View completeWork(UUID vehicle,UUID record,Work c){
        var a=begin(vehicle);String action="WORK_COMPLETED";if(prior(vehicle,a,c.operationKey(),action+":"+record,c))return view(vehicle,a.getOrganizationId());checkVersion(vehicle,c.expectedVersion());safe(c.reason());reference(c.responsibleReference());reference(c.evidenceReference());
        var r=record(vehicle,record,a.getOrganizationId());if(!"OPEN".equals(r.get("status"))||((Timestamp)r.get("starts_at")).toInstant().isAfter(now())||!Boolean.TRUE.equals(c.workCompleted()))throw conflict("Solo puede acreditar trabajo realmente terminado de un mantenimiento iniciado.");
        jdbc.update("update vehicle_maintenance set status='WORK_COMPLETED',work_completed_by=?,work_completed_at=?,responsible_reference=?,evidence_reference=? where id=?",a.getId(),ts(now()),c.responsibleReference(),c.evidenceReference(),record);
        event(vehicle,a,c.operationKey(),action+":"+record,c,record);return view(vehicle,a.getOrganizationId());
    }
    @Transactional public View authorizeReturn(UUID vehicle,Basic c){
        var a=begin(vehicle);String action="RETURN_AUTHORIZED";if(prior(vehicle,a,c.operationKey(),action,c))return view(vehicle,a.getOrganizationId());checkVersion(vehicle,c.expectedVersion());safe(c.reason());Instant from=day(today().plusDays(1));
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from vehicle_maintenance where vehicle_id=? and organization_id=? and status='WORK_COMPLETED' and starts_at<=?)",Boolean.class,vehicle,a.getOrganizationId(),ts(now()))))throw conflict("Registra el fin del trabajo antes de autorizar el retorno.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from vehicle_maintenance where vehicle_id=? and organization_id=? and status='OPEN' and starts_at<=?)",Boolean.class,vehicle,a.getOrganizationId(),ts(from))))throw conflict("Queda otro mantenimiento sin finalizar que bloquea el retorno.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from assignment where vehicle_id=? and organization_id=? and status='EN_SERVICIO')",Boolean.class,vehicle,a.getOrganizationId())))throw conflict("Resuelve el servicio en curso antes de confirmar el retorno de la unidad.");
        var unit=vehicles.findByIdAndOrganization_Id(vehicle,a.getOrganizationId()).orElseThrow(VehicleMaintenanceService::notFound);
        boolean safetyPending=policy.safetyVehicleBlocks(vehicle,a.getOrganizationId());
        if(!unit.isActive()&&!safetyPending)throw conflict("La unidad permanece inactiva; el retorno no la activa por inferencia.");
        requireDocumentaryReturn(vehicle,a.getOrganizationId(),from);
        jdbc.update("update vehicle_maintenance set status='RETURNED',returned_by=?,returned_at=?,blocking_ends_at=? where vehicle_id=? and organization_id=? and status='WORK_COMPLETED' and starts_at<=?",a.getId(),ts(now()),ts(from),vehicle,a.getOrganizationId(),ts(now()));
        // An interrupted/withdrawn unit may record the explicit maintenance return
        // without becoming active or bypassing its independent safety attention.
        if(!safetyPending&&unit.getStatus()==pe.rutafija.fleet.domain.VehicleStatus.MANTENIMIENTO){unit.changeAdministrativeStatus(pe.rutafija.fleet.domain.VehicleStatus.DISPONIBLE);vehicles.flush();}
        event(vehicle,a,c.operationKey(),action,c,null);return view(vehicle,a.getOrganizationId());
    }
    @Transactional public View cancel(UUID vehicle,UUID record,Basic c){
        var a=begin(vehicle);String action="CANCELLED:"+record;if(prior(vehicle,a,c.operationKey(),action,c))return view(vehicle,a.getOrganizationId());checkVersion(vehicle,c.expectedVersion());safe(c.reason());var r=record(vehicle,record,a.getOrganizationId());
        if(!"OPEN".equals(r.get("status"))||!((Timestamp)r.get("starts_at")).toInstant().isAfter(now()))throw conflict("Solo puede cancelar un mantenimiento que todavía no empezó; el resto exige fin de trabajo y retorno real.");
        jdbc.update("update vehicle_maintenance set status='CANCELLED',cancelled_by=?,cancelled_at=? where id=?",a.getId(),ts(now()),record);event(vehicle,a,c.operationKey(),action,c,record);return view(vehicle,a.getOrganizationId());
    }
    private void requireDocumentaryReturn(UUID vehicle,UUID org,Instant from){
        var candidates=jdbc.query("select distinct driver_id from driver_unit_period where vehicle_id=? and organization_id=? and status='AUTHORIZED' and starts_at<=? and(ends_at is null or ends_at>?)",(r,n)->r.getObject(1,UUID.class),vehicle,org,ts(from),ts(from));
        if(candidates.isEmpty())candidates=jdbc.query("select distinct driver_id from qualification_review where vehicle_id=? and organization_id=? and scope='VEHICLE'",(r,n)->r.getObject(1,UUID.class),vehicle,org);
        if(candidates.size()!=1)throw conflict("No hay una revisión documental inequívoca de la unidad; completa su expediente antes del retorno.");
        UUID driver=candidates.getFirst();
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from qualification_review q where q.vehicle_id=? and q.organization_id=? and q.driver_id<>? and q.recorded_at>=(select max(recorded_at) from qualification_review where vehicle_id=? and driver_id=?))",Boolean.class,vehicle,org,driver,vehicle,driver)))throw conflict("Las revisiones de la unidad requieren reconciliación antes del retorno.");
        if(!policy.reviewBlockers(driver,org,vehicle,from.plusNanos(1000)).isEmpty())throw conflict("Documentación o título de la unidad/servicio sin vigencia suficiente para el retorno.");
    }
    private AppUser begin(UUID vehicle){var a=current.requireTenantActor();scheduling.acquire(a.getOrganizationId());vehicles.lockByIdAndOrganizationId(vehicle,a.getOrganizationId()).orElseThrow(VehicleMaintenanceService::notFound);jdbc.update("insert into vehicle_maintenance_control(vehicle_id,organization_id) values(?,?) on conflict do nothing",vehicle,a.getOrganizationId());jdbc.queryForObject("select version from vehicle_maintenance_control where vehicle_id=? for update",Long.class,vehicle);return a;}
    private void own(UUID vehicle,AppUser a){if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from vehicle where id=? and organization_id=?)",Boolean.class,vehicle,a.getOrganizationId())))throw notFound();}
    private long version(UUID vehicle){var v=jdbc.query("select version from vehicle_maintenance_control where vehicle_id=?",(r,n)->r.getLong(1),vehicle);return v.isEmpty()?0:v.getFirst();}
    private void checkVersion(UUID vehicle,long expected){if(version(vehicle)!=expected)throw conflict("El mantenimiento cambió; consulta antes de continuar.");}
    private boolean prior(UUID vehicle,AppUser a,String key,String action,Object c){var values=jdbc.query("select payload_sha256 from vehicle_maintenance_operation where vehicle_id=? and actor_id=? and operation_key=?",(r,n)->r.getString(1).strip(),vehicle,a.getId(),key);if(values.isEmpty())return false;if(!values.getFirst().equals(hash(action,c)))throw conflict("La clave de operación ya tiene otro contenido.");return true;}
    private void event(UUID vehicle,AppUser a,String key,String action,Object c,UUID record){jdbc.update("update vehicle_maintenance_control set version=version+1 where vehicle_id=?",vehicle);String kind=action.contains(":")?action.substring(0,action.indexOf(':')):action;String reason=c instanceof Create x?x.reason():c instanceof Work x?x.reason():((Basic)c).reason();jdbc.update("insert into vehicle_maintenance_operation(id,vehicle_id,organization_id,actor_id,operation_key,payload_sha256,action,record_id,reason,resulting_version,occurred_at) values(?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),vehicle,a.getOrganizationId(),a.getId(),key,hash(action,c),kind,record,reason.strip(),version(vehicle),ts(now()));audit.record(a,"VEHICLE_MAINTENANCE_"+kind,"VEHICLE",vehicle,Map.of("version",version(vehicle)));}
    private List<Commitment> commitments(UUID vehicle,UUID org,Instant start){return jdbc.query("select id,driver_id,status,scheduled_at,scheduled_end_at from assignment where vehicle_id=? and organization_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO') and(reservation_ends_at>? or(status='EN_SERVICIO' and ?<=?)) order by scheduled_at,id",(r,n)->new Commitment(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant()),vehicle,org,ts(start),ts(start),ts(now()));}
    private Map<String,Object> record(UUID vehicle,UUID id,UUID org){var rows=jdbc.queryForList("select status,starts_at from vehicle_maintenance where id=? and vehicle_id=? and organization_id=?",id,vehicle,org);if(rows.size()!=1)throw notFound();return rows.getFirst();}
    private View view(UUID vehicle,UUID org){var records=jdbc.query("select * from vehicle_maintenance where vehicle_id=? and organization_id=? order by created_at,id",(r,n)->new pe.rutafija.maintenance.api.MaintenanceContract.Record(r.getObject("id",UUID.class),r.getString("kind"),r.getString("status"),instant(r,"starts_at"),instant(r,"planned_ends_at"),instant(r,"blocking_ends_at"),r.getObject("created_by",UUID.class),instant(r,"created_at"),r.getObject("work_completed_by",UUID.class),instant(r,"work_completed_at"),r.getObject("returned_by",UUID.class),instant(r,"returned_at")),vehicle,org);
        var history=jdbc.query("select id,action,record_id,actor_id,resulting_version,occurred_at from vehicle_maintenance_operation where vehicle_id=? and organization_id=? order by resulting_version,id",(r,n)->new Change(r.getObject(1,UUID.class),r.getString(2),r.getObject(3,UUID.class),r.getObject(4,UUID.class),r.getLong(5),r.getTimestamp(6).toInstant()),vehicle,org);
        boolean blocked=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from vehicle_maintenance where vehicle_id=? and organization_id=? and status<>'CANCELLED' and effective_window @> ?::timestamptz)",Boolean.class,vehicle,org,ts(now())));return new View(vehicle,version(vehicle),records,blocked,history,now());}
    private String hash(String action,Object c){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((action+":"+json.writeValueAsString(c)).getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("MAINTENANCE_HASH_FAILED");}}
    private void safe(String value){if(value==null||value.isBlank()||value.contains("@")||value.matches(".*[0-9]{8,}.*"))throw conflict("Usa motivo y referencias sin datos personales ni secretos.");}
    private void reference(String value){safe(value);if(!value.matches("[A-Z][A-Z0-9_.:-]{2,159}"))throw conflict("Usa un código de comprobación en mayúsculas, sin nombre, correo, documentos ni archivos.");}
    private Instant now(){return Instant.now(clock);}private LocalDate today(){return now().atZone(LIMA).toLocalDate();}private Instant day(LocalDate date){if(date==null)throw conflict("Indica el primer día de mantenimiento.");return date.atStartOfDay(LIMA).toInstant();}
    private static Timestamp ts(Instant i){return i==null?null:Timestamp.from(i);}private static Instant instant(ResultSet r,String n)throws SQLException{var t=r.getTimestamp(n);return t==null?null:t.toInstant();}
    private static ApplicationException conflict(String s){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,s);}private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El recurso solicitado no existe.");}
}
