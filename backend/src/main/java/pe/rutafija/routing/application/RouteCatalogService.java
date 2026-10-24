package pe.rutafija.routing.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.routing.api.RouteContract.*;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.shared.security.MobileDriverContextService;
import pe.rutafija.shared.exception.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class RouteCatalogService {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final CurrentUserService current;
    private final MobileDriverContextService mobile;private final AuditService audit;private final Clock clock;
    public RouteCatalogService(JdbcTemplate jdbc,ObjectMapper json,CurrentUserService current,MobileDriverContextService mobile,AuditService audit,Clock clock){this.jdbc=jdbc;this.json=json;this.current=current;this.mobile=mobile;this.audit=audit;this.clock=clock;}
    @Transactional(readOnly=true) public List<Summary> list(int limit,int offset){var a=current.requireTenantActor();return jdbc.query("select id,name,active,version,current_version_id from route_definition where organization_id=? order by lower(name),id limit ? offset ?",(r,n)->new Summary(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getLong(4),r.getObject(5,UUID.class)),a.getOrganizationId(),limit,offset);}
    @Transactional(readOnly=true) public View read(UUID id){return view(id,current.requireTenantActor().getOrganizationId());}
    @Transactional(readOnly=true) public View byOperation(String key){var a=current.requireTenantActor();var ids=jdbc.query("select route_id from route_operation where organization_id=? and actor_id=? and operation_key=?",(r,n)->r.getObject(1,UUID.class),a.getOrganizationId(),a.getId(),key);if(ids.size()!=1)throw notFound();return view(ids.getFirst(),a.getOrganizationId());}
    @Transactional public View create(Create c){
        var a=current.requireTenantActor();lockOrganization(a);validate(c.name(),c.reason(),c.stops());String hash=hash("CREATE",c);
        UUID prior=prior(a,c.operationKey(),hash);if(prior!=null)return view(prior,a.getOrganizationId());
        UUID id=UUID.randomUUID(),version=UUID.randomUUID();Instant now=Instant.now(clock);
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from route_definition where organization_id=? and lower(name)=lower(?))",Boolean.class,a.getOrganizationId(),c.name().strip())))throw conflict("Ya existe una ruta con ese nombre.");
        jdbc.update("insert into route_definition(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,?,?,?,?)",id,a.getOrganizationId(),c.name().strip(),version,Timestamp.from(now),Timestamp.from(now));
        insertVersion(id,version,1,c.name(),c.stops(),a,now);event(a,c.operationKey(),hash,id,1,"CREATE",c.reason(),now);return view(id,a.getOrganizationId());
    }
    @Transactional public View revise(UUID id,Revise c){
        var a=current.requireTenantActor();lockOrganization(a);validate(c.name(),c.reason(),c.stops());String hash=hash("REVISE:"+id,c);
        UUID prior=prior(a,c.operationKey(),hash);if(prior!=null)return view(prior,a.getOrganizationId());
        var old=lock(id,a.getOrganizationId());checkVersion(old,c.expectedVersion());if(!Boolean.TRUE.equals(old.get("active")))throw conflict("La ruta está retirada; reactívala expresamente antes de editar.");
        UUID version=UUID.randomUUID();Instant now=Instant.now(clock);int revision=jdbc.queryForObject("select max(revision)+1 from route_version where route_id=?",Integer.class,id);
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from route_definition where organization_id=? and id<>? and lower(name)=lower(?))",Boolean.class,a.getOrganizationId(),id,c.name().strip())))throw conflict("Ya existe una ruta con ese nombre.");
        insertVersion(id,version,revision,c.name(),c.stops(),a,now);
        jdbc.update("update route_definition set name=?,version=version+1,current_version_id=?,updated_at=? where id=?",c.name().strip(),version,Timestamp.from(now),id);
        event(a,c.operationKey(),hash,id,c.expectedVersion()+1,"REVISE",c.reason(),now);return view(id,a.getOrganizationId());
    }
    @Transactional public View status(UUID id,Status c){
        var a=current.requireTenantActor();lockOrganization(a);safe(c.reason());String hash=hash("STATUS:"+id,c);UUID prior=prior(a,c.operationKey(),hash);if(prior!=null)return view(prior,a.getOrganizationId());
        var old=lock(id,a.getOrganizationId());checkVersion(old,c.expectedVersion());Instant now=Instant.now(clock);
        jdbc.update("update route_definition set active=?,version=version+1,updated_at=? where id=?",c.active(),Timestamp.from(now),id);
        event(a,c.operationKey(),hash,id,c.expectedVersion()+1,"STATUS",c.reason(),now);return view(id,a.getOrganizationId());
    }
    public Version usable(UUID id,UUID org){
        if(id==null)throw conflict("Selecciona una versión de ruta utilizable antes de programar.");
        var ids=jdbc.query("select v.id from route_version v join route_definition d on d.id=v.route_id and d.organization_id=v.organization_id where v.id=? and v.organization_id=? and d.active and d.current_version_id=v.id for share of d",(r,n)->r.getObject(1,UUID.class),id,org);
        if(ids.size()!=1)throw notFound();return version(id,org);
    }
    public void recordCapture(UUID assignment,UUID version,AppUser actor,Instant now){
        jdbc.update("insert into assignment_route_history(id,assignment_id,organization_id,previous_version_id,route_version_id,assignment_version,actor_id,reason,occurred_at) values(?,?,?,null,?,0,?,'CREATION_CAPTURE',?)",
            UUID.randomUUID(),assignment,actor.getOrganizationId(),version,actor.getId(),Timestamp.from(now));
    }
    @Transactional(readOnly=true) public Assigned assigned(UUID assignment){var a=current.requireTenantActor();return assignedFor(assignment,a.getOrganizationId(),null);}
    @Transactional(readOnly=true) public Assigned mobileAssigned(UUID assignment){var a=mobile.requireMobileConsultation();return assignedFor(assignment,a.user().getOrganizationId(),a.driver().getId());}
    private Assigned assignedFor(UUID id,UUID org,UUID driver){
        var rows=jdbc.queryForList("select route_version_id from assignment where id=? and organization_id=? and (?::uuid is null or driver_id=?)",id,org,driver,driver);
        if(rows.size()!=1)throw notFound();UUID version=(UUID)rows.getFirst().get("route_version_id");
        var declarations=jdbc.query("select declared_at,terminal_result,actor_id from assignment_route_declaration where assignment_id=? and organization_id=?",(r,n)->new Declaration(r.getTimestamp(1).toInstant(),r.getString(2),r.getObject(3,UUID.class)),id,org);
        var replacements=jdbc.query("select replacement_assignment_id from assignment_route_replacement where previous_assignment_id=? and organization_id=?",(r,n)->r.getObject(1,UUID.class),id,org);
        return new Assigned(id,version,version==null?"LEGACY_UNRECORDED":"CAPTURED",version==null?null:version(version,org),declarations.isEmpty()?null:declarations.getFirst(),replacements.isEmpty()?null:replacements.getFirst());
    }
    private View view(UUID id,UUID org){var rows=jdbc.queryForList("select name,active,version,current_version_id from route_definition where id=? and organization_id=?",id,org);if(rows.size()!=1)throw notFound();var r=rows.getFirst();
        var versions=jdbc.query("select id from route_version where route_id=? and organization_id=? order by revision",(v,n)->version(v.getObject(1,UUID.class),org),id,org);
        return new View(id,(String)r.get("name"),(Boolean)r.get("active"),((Number)r.get("version")).longValue(),(UUID)r.get("current_version_id"),versions,Instant.now(clock));}
    public Version version(UUID id,UUID org){var rows=jdbc.query("select id,revision,name,stops,content_sha256,created_at from route_version where id=? and organization_id=?",(r,n)->new Version(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),stops(r.getString(4)),r.getString(5).strip(),r.getTimestamp(6).toInstant()),id,org);if(rows.size()!=1)throw notFound();return rows.getFirst();}
    private List<Stop> stops(String text){try{return json.readValue(text,new TypeReference<>(){});}catch(Exception ex){throw new IllegalStateException("ROUTE_CONTENT_INVALID");}}
    private void validate(String name,String reason,List<Stop> stops){safe(reason);safe(name);
        if(stops==null||stops.size()<3||stops.size()>100)throw conflict("La ruta requiere inicio, intermedia y fin ordenados.");
        Set<String> keys=new HashSet<>();
        for(int i=0;i<stops.size();i++){Stop stop=stops.get(i);String expected=i==0?"START":i==stops.size()-1?"END":"INTERMEDIATE";
            if(stop==null||!expected.equals(stop.kind())||!keys.add(stop.key()))throw conflict("Paradas duplicadas o sin orden inicio/intermedias/fin.");safe(stop.name());
            if((stop.latitude()==null)!=(stop.longitude()==null))throw conflict("Indica ambas coordenadas o conserva la ubicación sin configurar.");
        }
    }
    private void insertVersion(UUID id,UUID v,int revision,String name,List<Stop> stops,AppUser a,Instant now){Map<String,Object> content=new LinkedHashMap<>();content.put("name",name.strip());content.put("stops",stops);jdbc.update("insert into route_version(id,route_id,organization_id,revision,name,stops,content_sha256,created_by,created_at) values(?,?,?,?,?,?::jsonb,?,?,?)",v,id,a.getOrganizationId(),revision,name.strip(),write(stops),digest(write(content)),a.getId(),Timestamp.from(now));}
    private UUID prior(AppUser a,String key,String hash){var rows=jdbc.queryForList("select route_id,payload_sha256 from route_operation where organization_id=? and actor_id=? and operation_key=?",a.getOrganizationId(),a.getId(),key);if(rows.isEmpty())return null;if(!hash.equals(((String)rows.getFirst().get("payload_sha256")).strip()))throw conflict("La clave de operación ya identifica otro cambio.");return (UUID)rows.getFirst().get("route_id");}
    private void event(AppUser a,String key,String hash,UUID id,long version,String action,String reason,Instant now){jdbc.update("insert into route_operation(id,organization_id,actor_id,operation_key,action,payload_sha256,route_id,resulting_version,reason,occurred_at) values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),a.getOrganizationId(),a.getId(),key,action,hash,id,version,reason.strip(),Timestamp.from(now));audit.record(a,"ROUTE_"+action,"ROUTE",id,Map.of("version",version));}
    private void lockOrganization(AppUser a){jdbc.queryForObject("select id from organization where id=? for no key update",UUID.class,a.getOrganizationId());}
    private Map<String,Object> lock(UUID id,UUID org){var rows=jdbc.queryForList("select active,version from route_definition where id=? and organization_id=? for update",id,org);if(rows.size()!=1)throw notFound();return rows.getFirst();}
    private void checkVersion(Map<String,Object> row,long expected){if(((Number)row.get("version")).longValue()!=expected)throw conflict("La ruta cambió; consulta su versión vigente.");}
    private void safe(String s){if(s==null||s.isBlank()||s.contains("@")||s.matches(".*[0-9]{8,}.*"))throw conflict("Usa referencias y nombres de lugares sin datos personales ni secretos.");}
    private String hash(String action,Object value){return digest(action+"|"+write(value));}
    private String write(Object o){try{return json.writeValueAsString(o);}catch(Exception ex){throw new IllegalArgumentException("ROUTE_JSON_INVALID");}}
    private static String digest(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception ex){throw new IllegalStateException("ROUTE_HASH_INVALID");}}
    private static ApplicationException conflict(String text){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,text);}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El recurso solicitado no existe.");}
}
