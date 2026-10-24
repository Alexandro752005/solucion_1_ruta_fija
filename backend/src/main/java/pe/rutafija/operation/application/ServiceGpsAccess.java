package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.domain.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import pe.rutafija.operation.api.ServiceGpsContract.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;

/** Verified own-data access, never a global GPS or ordinary history-download endpoint. */
@Service
public class ServiceGpsAccess {
    private final JdbcTemplate jdbc; private final CurrentUserService users; private final MobileDriverContextService mobile;
    private final PasswordEncoder passwords; private final LoginRateLimiter limiter; private final AuditService audit;
    private final OrganizationSchedulingLock scheduling; private final Clock clock;
    public ServiceGpsAccess(JdbcTemplate jdbc,CurrentUserService users,MobileDriverContextService mobile,
        PasswordEncoder passwords,LoginRateLimiter limiter,AuditService audit,OrganizationSchedulingLock scheduling,Clock clock) {
        this.jdbc=jdbc;this.users=users;this.mobile=mobile;this.passwords=passwords;this.limiter=limiter;
        this.audit=audit;this.scheduling=scheduling;this.clock=clock;
    }
    private AppUser admin() {
        var a=users.requireTenantActor();
        if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN de la misma organización.");
        return a;
    }
    private void reauthenticate(AppUser actor,String password) {
        String key="gps-restricted:"+actor.getId();limiter.checkAndRecordAttempt(key);
        if(password==null || !passwords.matches(password,actor.getPasswordHash()))throw new InvalidCredentialsException();
        limiter.recordSuccess(key);
    }
    private RequestView read(UUID id,UUID org,UUID driver) {
        var rows=jdbc.query("select * from gps_access_request where id=? and organization_id=?"+(driver==null?"":" and driver_id=?"),
            this::map,driver==null?new Object[]{id,org}:new Object[]{id,org,driver});
        if(rows.size()!=1)throw notFound();return rows.getFirst();
    }
    @Transactional public RequestView request(Request c) {
        var actor=mobile.requireMobileConsultation();UUID org=actor.user().getOrganizationId();UUID driver=actor.driver().getId();scheduling.acquire(org);
        Instant now=clock.instant();
        Timestamp firstStart=jdbc.queryForObject("select min(started_at) from service_execution_segment where service_id=? and organization_id=? and driver_id=?",Timestamp.class,c.serviceId(),org,driver);
        if(firstStart==null)throw notFound();
        Instant from=c.fromAt()==null?firstStart.toInstant():c.fromAt(),to=c.toAt()==null?now:c.toAt();
        if(to.isBefore(from)||to.isAfter(now))throw conflict("Selecciona un intervalo propio pasado o actual.");
        // Server defaults are captured once. Replaying the same key never moves the original interval forward.
        String hash=hash(c.serviceId()+"|"+Objects.toString(c.fromAt(),"SERVICE_START")+"|"+Objects.toString(c.toAt(),"SERVER_NOW")+"|OWN_LOCATION_ACCESS");
        var prior=jdbc.queryForList("select id,payload_sha256 from gps_access_request where requester_id=? and request_key=?",actor.user().getId(),c.requestKey());
        if(!prior.isEmpty()) {
            if(!hash.equals(prior.getFirst().get("payload_sha256")))throw conflict("La clave ya corresponde a otro alcance solicitado.");
            return read((UUID)prior.getFirst().get("id"),org,driver);
        }
        UUID id=UUID.randomUUID();
        jdbc.update("""
            insert into gps_access_request(id,organization_id,driver_id,requester_id,service_id,request_key,payload_sha256,
                from_at,to_at,purpose,requested_at,review_due_at,status)
            values(?,?,?,?,?,?,?,?,?,'OWN_LOCATION_ACCESS',?,?,'PENDING')
            """,id,org,driver,actor.user().getId(),c.serviceId(),c.requestKey(),hash,ts(from),ts(to),ts(now),ts(now.plus(Duration.ofHours(48))));
        audit.record(actor.user(),"GPS_OWN_ACCESS_REQUESTED","GPS_ACCESS_REQUEST",id,Map.of("serviceId",c.serviceId().toString()));
        return read(id,org,driver);
    }
    @Transactional(readOnly=true) public RequestView own(UUID id) {
        var a=mobile.requireMobileConsultation();return read(id,a.user().getOrganizationId(),a.driver().getId());
    }
    @Transactional(readOnly=true) public List<RequestView> pending(int limit,int offset) {
        return pending(limit,offset,null);
    }
    @Transactional(readOnly=true) public List<RequestView> pending(int limit,int offset,UUID serviceId) {
        var a=admin();
        if(serviceId!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from scheduled_service where id=? and organization_id=?)",Boolean.class,serviceId,a.getOrganizationId())))throw notFound();
        return jdbc.query("select * from gps_access_request where organization_id=?"+(serviceId==null?"":" and service_id=?")+" order by requested_at desc,id limit ? offset ?",this::map,
            serviceId==null?new Object[]{a.getOrganizationId(),limit,offset}:new Object[]{a.getOrganizationId(),serviceId,limit,offset});
    }
    @Transactional(readOnly=true) public List<RequestView> ownList(UUID serviceId,int limit,int offset) {
        var a=mobile.requireMobileConsultation();
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_execution_segment where service_id=? and organization_id=? and driver_id=?)",Boolean.class,serviceId,a.user().getOrganizationId(),a.driver().getId())))throw notFound();
        return jdbc.query("select * from gps_access_request where organization_id=? and driver_id=? and service_id=? order by requested_at desc,id limit ? offset ?",this::map,a.user().getOrganizationId(),a.driver().getId(),serviceId,limit,offset);
    }
    @Transactional public RequestView review(UUID id,Review c) {
        var a=admin();reauthenticate(a,c.currentPassword());scheduling.acquire(a.getOrganizationId());
        var r=read(id,a.getOrganizationId(),null);if(r.version()!=c.expectedVersion())throw conflict("La solicitud cambió; consulta su estado.");
        if(!"PENDING".equals(r.status()))throw conflict("La solicitud ya fue resuelta; no se amplía su autorización.");
        if(!Boolean.TRUE.equals(c.identityConfirmed()))throw conflict("Debe comprobarse la correspondencia del solicitante en la revisión restringida.");
        Instant now=clock.instant();Timestamp expiryValue=jdbc.queryForObject("""
            select min(p.expires_at) from service_gps_point p join service_execution_segment e on e.id=p.segment_id
             where e.service_id=? and p.organization_id=? and p.driver_id=? and p.captured_at>=? and p.captured_at<=?
              and p.received_at<=? and p.expires_at>?
            """,Timestamp.class,r.serviceId(),a.getOrganizationId(),r.driverId(),ts(r.fromAt()),ts(r.toAt()),ts(now),ts(now));
        Instant firstExpiry=expiryValue==null?null:expiryValue.toInstant();
        String state=!c.approved()?"DENIED":firstExpiry==null?"NO_DATA":"APPROVED";
        Instant expiry="APPROVED".equals(state)?earlier(now.plus(Duration.ofMinutes(15)),firstExpiry):null;
        jdbc.update("update gps_access_request set status=?,version=version+1,reviewed_by=?,reviewed_at=?,verification_reference=?,grant_expires_at=? where id=?",
            state,a.getId(),ts(now),c.verificationReference(),expiry==null?null:ts(expiry),id);
        audit.record(a,"GPS_OWN_ACCESS_REVIEWED","GPS_ACCESS_REQUEST",id,Map.of("status",state,"identityConfirmed",true));
        return read(id,a.getOrganizationId(),null);
    }
    @Transactional public Points deliver(UUID id,Delivery c) {
        var a=mobile.requireMobileConsultation();reauthenticate(a.user(),c.currentPassword());
        var r=read(id,a.user().getOrganizationId(),a.driver().getId());Instant now=clock.instant();
        if(!"APPROVED".equals(r.status()) || r.grantExpiresAt()==null || !now.isBefore(r.grantExpiresAt()))
            throw conflict("No existe una autorización vigente; la solicitud nunca prorroga la retención de puntos.");
        var points=jdbc.query("""
            select p.* from service_gps_point p join service_execution_segment e on e.id=p.segment_id
             where e.service_id=? and p.organization_id=? and p.driver_id=? and p.captured_at>=? and p.captured_at<=?
              and p.received_at<=? and p.expires_at>? order by p.captured_at,p.id limit ? offset ?
            """,(row,n)->new Point(row.getObject("id",UUID.class),row.getObject("segment_id",UUID.class),row.getBigDecimal("latitude"),
                row.getBigDecimal("longitude"),row.getBigDecimal("accuracy_m"),row.getTimestamp("captured_at").toInstant(),row.getTimestamp("expires_at").toInstant()),
            r.serviceId(),a.user().getOrganizationId(),a.driver().getId(),ts(r.fromAt()),ts(r.toAt()),ts(r.reviewedAt()),ts(now),c.limit(),c.offset());
        audit.record(a.user(),"GPS_OWN_ACCESS_DELIVERED","GPS_ACCESS_REQUEST",id,Map.of("pointCount",points.size()));
        return new Points(id,r.grantExpiresAt(),points,points.size()==c.limit()?Math.addExact(c.offset(),c.limit()):null);
    }
    @Transactional public IncidentEvidence attach(UUID incidentId,Attach c) {
        var a=admin();reauthenticate(a,c.currentPassword());scheduling.acquire(a.getOrganizationId());Instant now=clock.instant();
        var existing=jdbc.queryForList("select id from gps_incident_evidence where incident_id=? and point_reference=? and organization_id=?",incidentId,c.pointId(),a.getOrganizationId());
        if(!existing.isEmpty())return evidence((UUID)existing.getFirst().get("id"),a.getOrganizationId());
        var points=jdbc.queryForList("""
            select p.* from service_gps_point p join service_execution_segment e on e.id=p.segment_id
             join incident i on i.id=? and i.organization_id=p.organization_id and i.driver_id=p.driver_id
             join assignment ia on ia.id=i.assignment_id and ia.service_id=e.service_id
             where p.id=? and p.organization_id=? and p.expires_at>?
            """,incidentId,c.pointId(),a.getOrganizationId(),ts(now));
        if(points.size()!=1)throw notFound();var p=points.getFirst();UUID id=UUID.randomUUID();
        jdbc.update("insert into gps_incident_evidence(id,organization_id,incident_id,point_id,point_reference,created_by,created_at,captured_at,expires_at,justification) values(?,?,?,?,?,?,?,?,?,?)",
            id,a.getOrganizationId(),incidentId,c.pointId(),c.pointId(),a.getId(),ts(now),p.get("captured_at"),p.get("expires_at"),c.justification().name());
        audit.record(a,"GPS_INCIDENT_POINT_JUSTIFIED","GPS_INCIDENT_EVIDENCE",id,Map.of("incidentId",incidentId.toString(),"justification",c.justification().name()));
        return evidence(id,a.getOrganizationId());
    }
    @Transactional(readOnly=true) public IncidentEvidence evidence(UUID id) {return evidence(id,admin().getOrganizationId());}
    private IncidentEvidence evidence(UUID id,UUID org) {
        var rows=jdbc.query("select * from gps_incident_evidence where id=? and organization_id=?",(r,n)->new IncidentEvidence(
            r.getObject("id",UUID.class),r.getObject("incident_id",UUID.class),r.getObject("point_reference",UUID.class),r.getString("justification"),
            r.getTimestamp("expires_at").toInstant(),r.getObject("point_id")!=null&&clock.instant().isBefore(r.getTimestamp("expires_at").toInstant())),id,org);
        if(rows.size()!=1)throw notFound();return rows.getFirst();
    }
    private RequestView map(ResultSet r,int n)throws SQLException {
        Instant now=clock.instant();Timestamp expiry=r.getTimestamp("grant_expires_at");String state=r.getString("status");
        String effective="APPROVED".equals(state)&&expiry!=null&&!now.isBefore(expiry.toInstant())?"EXPIRED":
            "PENDING".equals(state)&&!now.isBefore(r.getTimestamp("review_due_at").toInstant())?"OVERDUE":state;
        return new RequestView(r.getObject("id",UUID.class),r.getObject("driver_id",UUID.class),r.getObject("service_id",UUID.class),r.getLong("version"),state,
            effective,
            r.getTimestamp("from_at").toInstant(),r.getTimestamp("to_at").toInstant(),r.getTimestamp("requested_at").toInstant(),r.getTimestamp("review_due_at").toInstant(),
            r.getTimestamp("reviewed_at")==null?null:r.getTimestamp("reviewed_at").toInstant(),expiry==null?null:expiry.toInstant(),r.getObject("request_key",UUID.class));
    }
    private static Instant earlier(Instant a,Instant b){return a.isBefore(b)?a:b;}
    private static Timestamp ts(Instant i){return Timestamp.from(i);}
    private static String hash(String text) {try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException("GPS_HASH_UNAVAILABLE");}}
    private static ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Recurso GPS no encontrado en su alcance autorizado.");}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
}
