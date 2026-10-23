package pe.rutafija.operation.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.operation.api.ServiceTypeContract.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.identity.domain.*;
import pe.rutafija.audit.application.AuditService;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class ServiceTypeService {
    private final JdbcTemplate jdbc; private final CurrentUserService current; private final OrganizationSchedulingLock lock;
    private final AuditService audit; private final ObjectMapper json; private final Clock clock;
    public ServiceTypeService(JdbcTemplate jdbc,CurrentUserService current,OrganizationSchedulingLock lock,AuditService audit,ObjectMapper json,Clock clock) {
        this.jdbc=jdbc;this.current=current;this.lock=lock;this.audit=audit;this.json=json;this.clock=clock;
    }
    private AppUser actor() {
        var a=current.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La configuración requiere ADMIN.");return a;
    }
    @Transactional(readOnly=true) public List<Summary> list(int limit,int offset) {
        var a=actor();return jdbc.query("select id,name,active,version,current_version_id from service_type where organization_id=? order by lower(name),id limit ? offset ?",
            (r,n)->new Summary(r.getObject(1,UUID.class),r.getString(2),r.getBoolean(3),r.getLong(4),r.getObject(5,UUID.class)),a.getOrganizationId(),limit,offset);
    }
    @Transactional(readOnly=true) public View read(UUID id) {return view(id,actor().getOrganizationId());}
    @Transactional(readOnly=true) public View operation(String key) {
        var a=actor();var ids=jdbc.query("select type_id from service_type_operation where organization_id=? and actor_id=? and operation_key=?",(r,n)->r.getObject(1,UUID.class),a.getOrganizationId(),a.getId(),key);
        if(ids.size()!=1)throw notFound();return view(ids.getFirst(),a.getOrganizationId());
    }
    @Transactional public View create(Create c) {
        var a=actor();lock.acquire(a.getOrganizationId());safe(c.name());safe(c.reason());String hash=hash("CREATE",c);
        UUID prior=prior(a,c.operationKey(),hash);if(prior!=null)return view(prior,a.getOrganizationId());
        unique(a.getOrganizationId(),null,c.name());UUID id=UUID.randomUUID(),v=UUID.randomUUID();Instant now=Instant.now(clock);
        jdbc.update("insert into service_type(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,?,?,?,?)",id,a.getOrganizationId(),c.name().strip(),v,Timestamp.from(now),Timestamp.from(now));
        revision(id,v,1,c.name(),c.beforeMinutes(),c.afterMinutes(),a,now);event(a,id,c.operationKey(),hash,"CREATE",1,c.reason(),now);return view(id,a.getOrganizationId());
    }
    @Transactional public View revise(UUID id,Revise c) {
        var a=actor();lock.acquire(a.getOrganizationId());safe(c.name());safe(c.reason());String hash=hash("REVISE:"+id,c);
        UUID prior=prior(a,c.operationKey(),hash);if(prior!=null)return view(prior,a.getOrganizationId());var old=header(id,a.getOrganizationId());version(old,c.expectedVersion());
        if(!Boolean.TRUE.equals(old.get("active")))throw conflict("Reactiva expresamente el tipo antes de editarlo.");unique(a.getOrganizationId(),id,c.name());
        UUID v=UUID.randomUUID();Instant now=Instant.now(clock);int revision=jdbc.queryForObject("select max(revision)+1 from service_type_version where type_id=?",Integer.class,id);
        revision(id,v,revision,c.name(),c.beforeMinutes(),c.afterMinutes(),a,now);
        jdbc.update("update service_type set name=?,version=version+1,current_version_id=?,updated_at=? where id=?",c.name().strip(),v,Timestamp.from(now),id);
        event(a,id,c.operationKey(),hash,"REVISE",c.expectedVersion()+1,c.reason(),now);return view(id,a.getOrganizationId());
    }
    @Transactional public View status(UUID id,Status c) {
        var a=actor();lock.acquire(a.getOrganizationId());safe(c.reason());String hash=hash("STATUS:"+id,c);
        UUID prior=prior(a,c.operationKey(),hash);if(prior!=null)return view(prior,a.getOrganizationId());version(header(id,a.getOrganizationId()),c.expectedVersion());Instant now=Instant.now(clock);
        jdbc.update("update service_type set active=?,version=version+1,updated_at=? where id=?",c.active(),Timestamp.from(now),id);
        event(a,id,c.operationKey(),hash,"STATUS",c.expectedVersion()+1,c.reason(),now);return view(id,a.getOrganizationId());
    }
    public Version usable(UUID id,UUID org) {
        if(id==null)throw conflict("Configura y selecciona el tipo de servicio y sus márgenes antes de ofertar.");
        var ids=jdbc.query("select v.id from service_type_version v join service_type t on t.id=v.type_id and t.organization_id=v.organization_id where v.id=? and v.organization_id=? and t.active and t.current_version_id=v.id",(r,n)->r.getObject(1,UUID.class),id,org);
        if(ids.size()!=1)throw notFound();return revision(id,org);
    }
    public Version revision(UUID id,UUID org) {
        var rows=jdbc.query("select id,revision,name,before_minutes,after_minutes,content_sha256,created_at from service_type_version where id=? and organization_id=?",
            (r,n)->new Version(r.getObject(1,UUID.class),r.getInt(2),r.getString(3),r.getInt(4),r.getInt(5),r.getString(6).strip(),r.getTimestamp(7).toInstant()),id,org);
        if(rows.size()!=1)throw notFound();return rows.getFirst();
    }
    private View view(UUID id,UUID org) {
        var h=header(id,org);var versions=jdbc.query("select id from service_type_version where type_id=? and organization_id=? order by revision",(r,n)->revision(r.getObject(1,UUID.class),org),id,org);
        return new View(id,(String)h.get("name"),(Boolean)h.get("active"),((Number)h.get("version")).longValue(),(UUID)h.get("current_version_id"),versions,Instant.now(clock));
    }
    private Map<String,Object> header(UUID id,UUID org) {
        var rows=jdbc.queryForList("select name,active,version,current_version_id from service_type where id=? and organization_id=?",id,org);if(rows.size()!=1)throw notFound();return rows.getFirst();
    }
    private void version(Map<String,Object> old,long expected) {if(((Number)old.get("version")).longValue()!=expected)throw conflict("La versión cambió; consulta antes de confirmar.");}
    private void unique(UUID org,UUID id,String name) {if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_type where organization_id=? and lower(name)=lower(?) and (?::uuid is null or id<>?))",Boolean.class,org,name.strip(),id,id)))throw conflict("Ya existe un tipo con ese nombre.");}
    private void revision(UUID id,UUID v,int revision,String name,int before,int after,AppUser a,Instant now) {
        if(before<0||after<0)throw conflict("Indica ambos márgenes no negativos expresamente.");
        jdbc.update("insert into service_type_version(id,type_id,organization_id,revision,name,before_minutes,after_minutes,content_sha256,created_by,created_at) values(?,?,?,?,?,?,?,?,?,?)",v,id,a.getOrganizationId(),revision,name.strip(),before,after,hash("CONTENT",List.of(name.strip(),before,after)),a.getId(),Timestamp.from(now));
    }
    private UUID prior(AppUser a,String key,String hash) {
        var rows=jdbc.queryForList("select type_id,payload_sha256 from service_type_operation where organization_id=? and actor_id=? and operation_key=?",a.getOrganizationId(),a.getId(),key);
        if(rows.isEmpty())return null;if(!hash.equals(((String)rows.getFirst().get("payload_sha256")).strip()))throw conflict("La clave ya identifica otro cambio.");return (UUID)rows.getFirst().get("type_id");
    }
    private void event(AppUser a,UUID id,String key,String hash,String action,long version,String reason,Instant now) {
        jdbc.update("insert into service_type_operation(id,organization_id,actor_id,operation_key,action,payload_sha256,type_id,resulting_version,reason,occurred_at) values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),a.getOrganizationId(),a.getId(),key,action,hash,id,version,reason.strip(),Timestamp.from(now));
        audit.record(a,"SERVICE_TYPE_"+action,"SERVICE_TYPE",id,Map.of("version",version));
    }
    private String hash(String action,Object value) {try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((action+":"+json.writeValueAsString(value)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("SERVICE_TYPE_HASH_FAILED");}}
    private void safe(String text) {if(text==null||text.isBlank()||text.contains("@")||text.matches(".*[0-9]{8,}.*"))throw conflict("Utiliza referencias sin datos personales ni secretos.");}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Tipo de servicio no disponible.");}
}
