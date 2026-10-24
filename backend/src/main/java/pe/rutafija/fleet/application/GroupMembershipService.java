package pe.rutafija.fleet.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.api.dto.GroupResourceContract.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class GroupMembershipService {
    private final JdbcTemplate jdbc; private final CurrentUserService current; private final AuditService audit; private final Clock clock;
    public GroupMembershipService(JdbcTemplate jdbc,CurrentUserService current,AuditService audit,Clock clock){this.jdbc=jdbc;this.current=current;this.audit=audit;this.clock=clock;}
    @Transactional(readOnly=true)
    public Resources read(UUID group){var actor=current.requireTenantActor();return view(group,actor.getOrganizationId());}
    @Transactional
    public Resources change(UUID group,MembershipCommand c){
        var actor=current.requireTenantActor();UUID org=actor.getOrganizationId();
        var state=lock(group,org);
        String payload=hash(c.expectedVersion()+"|"+c.resourceKind()+"|"+c.resourceId()+"|"+c.active()+"|"+c.reason());
        var prior=jdbc.query("select payload_sha256 from group_membership_operation where group_id=? and actor_id=? and operation_key=?",(r,n)->r.getString(1).strip(),group,actor.getId(),c.operationKey());
        if(!prior.isEmpty()){if(!prior.getFirst().equals(payload))throw conflict("La clave de operación corresponde a otro cambio.");return view(group,org);}
        if(!Boolean.TRUE.equals(state.get("active")))throw conflict("El grupo está retirado; no admite cambios de recursos.");
        if(((Number)state.get("version")).longValue()!=c.expectedVersion())throw conflict("El grupo cambió; consulta el estado antes de continuar.");
        // A reference rather than free-form personal evidence is enough for group organization.
        if(c.reason().contains("@")||c.reason().matches(".*[0-9]{8,}.*"))throw conflict("Usa un motivo sin correo, documento, teléfono ni secretos.");
        boolean driver="DRIVER".equals(c.resourceKind());String resource=driver?"driver":"vehicle";
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from "+resource+" where id=? and organization_id=?)",Boolean.class,c.resourceId(),org)))throw notFound();
        if(!c.active()&&!commitments(group,org).isEmpty())throw conflict("Resuelve los compromisos del grupo antes de retirar recursos.");
        String table=driver?"group_driver_membership":"group_vehicle_membership",column=driver?"driver_id":"vehicle_id";
        String source=driver?",source":"",sourceValue=driver?",'ADMIN'":"";
        jdbc.update("insert into "+table+"(group_id,"+column+",organization_id,active"+source+") values(?,?,?,?"+sourceValue+") on conflict(group_id,"+column+") do update set active=excluded.active,updated_at=now()",group,c.resourceId(),org,c.active());
        Instant now=Instant.now(clock);jdbc.update("update transport_group set version=version+1,updated_at=? where id=?",Timestamp.from(now),group);
        long version=((Number)state.get("version")).longValue()+1;
        jdbc.update("insert into group_membership_operation(id,group_id,organization_id,actor_id,operation_key,payload_sha256,resource_kind,resource_id,active,reason,resulting_version,occurred_at) values(?,?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),group,org,actor.getId(),c.operationKey(),payload,c.resourceKind(),c.resourceId(),c.active(),c.reason().strip(),version,Timestamp.from(now));
        audit.record(actor,"GROUP_MEMBERSHIP_CHANGED","TRANSPORT_GROUP",group,Map.of("resourceKind",c.resourceKind(),"resourceId",c.resourceId().toString(),"active",c.active(),"version",version));
        return view(group,org);
    }
    /** Lock shared with assignment group capture. Must remain held until the enclosing transaction ends. */
    public void guardRetirement(UUID group,UUID org){lock(group,org);if(!commitments(group,org).isEmpty())throw conflict("El grupo tiene compromisos pendientes, en curso o pendientes de reasignación. Consulta sus referencias.");}
    private Map<String,Object> lock(UUID group,UUID org){var rows=jdbc.queryForList("select active,version from transport_group where id=? and organization_id=? for update",group,org);if(rows.size()!=1)throw notFound();return rows.getFirst();}
    private Resources view(UUID group,UUID org){
        var rows=jdbc.queryForList("select active,version from transport_group where id=? and organization_id=?",group,org);if(rows.size()!=1)throw notFound();
        var members=new ArrayList<Member>();
        members.addAll(jdbc.query("select driver_id,active,source,recorded_at from group_driver_membership where group_id=? and organization_id=? order by driver_id",(r,n)->new Member(r.getObject(1,UUID.class),"DRIVER",r.getBoolean(2),r.getString(3),r.getTimestamp(4).toInstant()),group,org));
        members.addAll(jdbc.query("select vehicle_id,active,recorded_at from group_vehicle_membership where group_id=? and organization_id=? order by vehicle_id",(r,n)->new Member(r.getObject(1,UUID.class),"VEHICLE",r.getBoolean(2),"ADMIN",r.getTimestamp(3).toInstant()),group,org));
        var history=jdbc.query("select id,actor_id,resource_kind,resource_id,active,resulting_version,occurred_at from group_membership_operation where group_id=? and organization_id=? order by occurred_at,id",(r,n)->new Change(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getString(3),r.getObject(4,UUID.class),r.getBoolean(5),r.getLong(6),r.getTimestamp(7).toInstant()),group,org);
        return new Resources(group,((Number)rows.getFirst().get("version")).longValue(),Boolean.TRUE.equals(rows.getFirst().get("active")),List.copyOf(members),commitments(group,org),history,Instant.now(clock));
    }
    private List<Commitment> commitments(UUID group,UUID org){return jdbc.query("""
        select distinct a.id,a.status,coalesce(q.kind,case when safety.id is not null then 'SAFETY_ATTENTION' when execution.status='INTERRUPTED' and a.status='INTERRUPTED' then 'INTERRUPTED_CONTINUITY' end) kind,a.scheduled_at,a.scheduled_end_at,coalesce(s.source,'CURRENT_MEMBERSHIP') source
          from assignment a left join assignment_group_snapshot s on s.assignment_id=a.id and s.organization_id=a.organization_id and s.group_id=?
          left join qualification_service_attention q on q.assignment_id=a.id and q.organization_id=a.organization_id and q.status='OPEN'
          left join scheduled_service execution on execution.id=a.service_id and execution.organization_id=a.organization_id
          left join service_attention safety on safety.assignment_id=a.id and safety.organization_id=a.organization_id
            and(not exists(select 1 from service_attention_release r where r.attention_id=safety.id and r.resource_kind='DRIVER')
                or not exists(select 1 from service_attention_release r where r.attention_id=safety.id and r.resource_kind='VEHICLE' and r.disposition='READY'))
         where a.organization_id=? and (s.group_id is not null
            or exists(select 1 from group_driver_membership m where m.group_id=? and m.driver_id=a.driver_id and m.active)
            or exists(select 1 from group_vehicle_membership m where m.group_id=? and m.vehicle_id=a.vehicle_id and m.active))
           and (a.status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO') or q.id is not null or safety.id is not null
                or(a.status='INTERRUPTED' and execution.status='INTERRUPTED'))
         order by a.scheduled_at,a.id
        """,(r,n)->new Commitment(r.getObject(1,UUID.class),r.getString(2),r.getString(3),r.getTimestamp(4).toInstant(),r.getTimestamp(5).toInstant(),r.getString(6)),group,org,group,group);}
    private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("GROUP_OPERATION_HASH_FAILED");}}
    private static ApplicationException conflict(String text){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,text);}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El recurso solicitado no existe.");}
}
