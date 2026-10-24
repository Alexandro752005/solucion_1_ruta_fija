package pe.rutafija.operation.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.*;
import pe.rutafija.operation.api.AnnouncementAttentionContract.*;
import pe.rutafija.operation.domain.*;
import pe.rutafija.operation.infrastructure.AnnouncementRepository;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.shared.exception.*;
import java.security.MessageDigest;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

@Service
public class AnnouncementDelivery {
    private final JdbcTemplate jdbc;private final AnnouncementRepository announcements;
    private final CurrentUserService current;private final OrganizationSchedulingLock lock;
    private final AuditService audit;private final ObjectMapper json;private final Clock clock;
    public AnnouncementDelivery(JdbcTemplate jdbc,AnnouncementRepository announcements,CurrentUserService current,OrganizationSchedulingLock lock,AuditService audit,ObjectMapper json,Clock clock){this.jdbc=jdbc;this.announcements=announcements;this.current=current;this.lock=lock;this.audit=audit;this.json=json;this.clock=clock;}
    public String hash(Object payload){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(payload)));}catch(Exception e){throw new IllegalStateException("ANNOUNCEMENT_PAYLOAD_HASH_FAILED");}}
    public void validateText(String value){if(value==null||value.isBlank()||value.contains("@")||value.matches("(?s).*[0-9]{8,}.*")||value.matches("(?s).*[-+]?\\d{1,3}\\.\\d+\\s*[,; ]\\s*[-+]?\\d{1,3}\\.\\d+.*"))throw new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,"Usa hechos y referencias sin datos personales ni coordenadas");}
    public void capture(Announcement a){
        jdbc.update("""
          insert into announcement_recipient(announcement_id,organization_id,user_id,driver_id,group_at_publication,provenance,assigned_at)
          select ?,u.organization_id,u.id,d.id,?,'PUBLISH_SNAPSHOT',?
          from app_user u join driver d on d.user_id=u.id and d.organization_id=u.organization_id
          where u.organization_id=? and u.role='CONDUCTOR' and u.active
          and (?::uuid is null or exists(select 1 from group_driver_membership m join transport_group g on g.id=m.group_id
            where m.driver_id=d.id and m.organization_id=u.organization_id and m.group_id=? and m.active and g.active))
          """,a.getId(),a.getAudienceId(),Timestamp.from(clock.instant()),a.getOrganization().getId(),a.getAudienceId(),a.getAudienceId());
    }
    @Transactional(readOnly=true) public Attention attention(UUID id){var actor=admin();return read(require(id,actor));}
    @Transactional(readOnly=true) public Publication publication(UUID key){var actor=admin();var a=announcements.findByOrganization_IdAndOperationKey(actor.getOrganizationId(),key).filter(v->v.getCreatedBy().getId().equals(actor.getId())).orElseThrow(this::notFound);return new Publication(key,a.getId(),a.getCreatedAt());}
    @Transactional public Attention withdraw(UUID id,UUID user,Withdrawal command){
        var actor=admin();lock.acquire(actor.getOrganizationId());var a=require(id,actor);validateText(command.reason());
        var rows=jdbc.queryForList("select * from announcement_recipient where announcement_id=? and user_id=? and organization_id=?",id,user,actor.getOrganizationId());
        if(rows.isEmpty())throw notFound();var r=rows.getFirst();var fingerprint=hash(command);
        if(r.get("withdrawn_at")!=null){
            if(!actor.getId().equals(r.get("withdrawn_by"))||!command.operationKey().equals(r.get("withdrawal_key"))||!fingerprint.equals(r.get("withdrawal_sha256")))throw conflict("La retirada de seguridad ya está registrada");
            return read(a);
        }
        if(command.expectedVersion()==null||((Number)r.get("version")).longValue()!=command.expectedVersion())throw conflict("El destinatario cambió; consulta su estado");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from announcement_recipient where organization_id=? and withdrawal_key=?)",Boolean.class,actor.getOrganizationId(),command.operationKey())))throw conflict("La clave ya pertenece a otra retirada");
        jdbc.update("update announcement_recipient set withdrawn_at=?,withdrawn_by=?,withdrawal_reason=?,withdrawal_key=?,withdrawal_sha256=?,version=version+1 where announcement_id=? and user_id=?",Timestamp.from(clock.instant()),actor.getId(),command.reason().strip(),command.operationKey(),fingerprint,id,user);
        audit.record(actor,"ANNOUNCEMENT_ACCESS_WITHDRAWN","ANNOUNCEMENT",id,Map.of("userId",user,"operationKey",command.operationKey()));return read(a);
    }
    private Attention read(Announcement a){
        var recipients=jdbc.query("""
          select r.user_id,r.driver_id,r.version,r.assigned_at,r.withdrawn_at,r.withdrawal_key,r.withdrawn_by,c.delivered_at,c.read_at,
            case when r.withdrawn_at is not null then 'WITHDRAWN' when not u.active or u.organization_id is distinct from r.organization_id then 'MEMBERSHIP_ENDED' else 'AVAILABLE' end access_status
          from announcement_recipient r join app_user u on u.id=r.user_id
          left join announcement_receipt c on c.announcement_id=r.announcement_id and c.user_id=r.user_id
          where r.announcement_id=? and r.organization_id=? order by r.assigned_at,r.user_id
          """,(r,n)->new Recipient(r.getObject("user_id",UUID.class),r.getObject("driver_id",UUID.class),"CONDUCTOR-"+r.getObject("driver_id",UUID.class),r.getLong("version"),r.getString("access_status"),r.getTimestamp("assigned_at").toInstant(),time(r.getTimestamp("delivered_at")),time(r.getTimestamp("read_at")),time(r.getTimestamp("withdrawn_at")),r.getObject("withdrawal_key",UUID.class),r.getObject("withdrawn_by",UUID.class)),a.getId(),a.getOrganization().getId());
        long pending=a.isRequireReadAck()?recipients.stream().filter(r->r.accessStatus().equals("AVAILABLE")&&r.readAt()==null).count():0;
        return new Attention(a.getId(),a.isRequireReadAck(),recipients.size(),pending,recipients);
    }
    private Instant time(Timestamp t){return t==null?null:t.toInstant();}
    private AppUser admin(){var a=current.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN");return a;}
    private Announcement require(UUID id,AppUser a){return announcements.findByIdAndOrganization_Id(id,a.getOrganizationId()).orElseThrow(this::notFound);}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El recurso no existe");}
    private ApplicationException conflict(String m){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,m);}
}
