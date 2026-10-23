package pe.rutafija.reporting.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.*;
import pe.rutafija.operation.application.OrganizationSchedulingLock;
import pe.rutafija.operation.application.ReportExport;
import pe.rutafija.operation.application.ReportExportFormat;
import pe.rutafija.reporting.api.ServiceReportContract.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

/** The source view, immutable issuance and authorized download are distinct operations. */
@Service
public class ServiceReportService {
    private final JdbcTemplate jdbc;private final CurrentUserService users;private final MobileDriverContextService mobile;
    private final ServiceReportQuery query;private final OrganizationSchedulingLock scheduling;private final AuditService audit;
    private final Clock clock;private final ObjectMapper json;private final ServiceReportExport exports;
    public ServiceReportService(JdbcTemplate jdbc,CurrentUserService users,MobileDriverContextService mobile,ServiceReportQuery query,
        OrganizationSchedulingLock scheduling,AuditService audit,Clock clock,ObjectMapper json,ServiceReportExport exports){
        this.jdbc=jdbc;this.users=users;this.mobile=mobile;this.query=query;this.scheduling=scheduling;this.audit=audit;this.clock=clock;this.json=json;this.exports=exports;
    }
    private Actor actor(boolean own){
        if(own){var a=mobile.requireMobileConsultation();return new Actor(a.user(),a.driver().getId());}
        var a=users.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN de la organización.");return new Actor(a,null);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public View current(boolean own,LocalDate from,LocalDate to){var a=actor(own);requireContentAvailable();return view(a,from,to);}
    private View view(Actor a,LocalDate from,LocalDate to){return query.read(a.user.getOrganizationId(),a.driver,from,to,a.user.getOrganization().getTimezone(),clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS));}
    @Transactional
    public Emission issue(boolean own,Issue c){
        var a=actor(own);ServiceReportQuery.range(c.from(),c.to());scheduling.acquire(a.user.getOrganizationId());requireContentAvailable();
        // Revalidate database state after waiting for the same tenant scheduling lock.
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from app_user u join organization o on o.id=u.organization_id where u.id=? and u.active and o.status='ACTIVE' and u.email_verified_at is not null and u.session_version=?)",Boolean.class,a.user.getId(),a.user.getSessionVersion())))
            throw new ApplicationException(HttpStatus.UNAUTHORIZED,ErrorCode.AUTH_TOKEN_INVALID,"La sesión ya no es válida.");
        String filterHash=hash(a.user.getOrganizationId()+"|"+a.driver+"|"+c.from()+"|"+c.to());
        var prior=jdbc.queryForList("select filters_sha256,emission_id from report_emission_request where issuer_id=? and operation_key=?",a.user.getId(),c.operationKey());
        if(!prior.isEmpty()){
            if(!filterHash.equals(prior.getFirst().get("filters_sha256")))throw conflict("La clave de emisión ya corresponde a otro periodo.");
            return load(a,(UUID)prior.getFirst().get("emission_id"));
        }
        String scope=a.driver==null?"ORGANIZATION":"OWN_PARTICIPATION";
        var existing=jdbc.query("select id from report_emission_series where organization_id=? and issuer_id=? and scope=? and from_day=? and to_day=?",(r,n)->r.getObject(1,UUID.class),a.user.getOrganizationId(),a.user.getId(),scope,c.from(),c.to());
        UUID series=existing.isEmpty()?UUID.randomUUID():existing.getFirst();
        if(existing.isEmpty())jdbc.update("insert into report_emission_series(id,organization_id,issuer_id,own_driver_id,scope,from_day,to_day,zone_id) values(?,?,?,?,?,?,?,?)",series,a.user.getOrganizationId(),a.user.getId(),a.driver,scope,c.from(),c.to(),a.user.getOrganization().getTimezone());
        var previous=jdbc.queryForList("select id,revision from report_emission where series_id=? order by revision desc limit 1",series);
        UUID priorId=previous.isEmpty()?null:(UUID)previous.getFirst().get("id");int revision=previous.isEmpty()?1:((Number)previous.getFirst().get("revision")).intValue()+1;
        View report=view(a,c.from(),c.to());String body=encode(report),sha=hash(body);UUID id=UUID.randomUUID();
        jdbc.update("insert into report_emission(id,series_id,revision,previous_emission_id,issued_at,content_sha256,rule_version) values(?,?,?,?,?,?,?)",id,series,revision,priorId,Timestamp.from(report.cutoffAt()),sha,ServiceReportQuery.RULE);
        jdbc.update("insert into report_emission_content(emission_id,series_id,body_json) values(?,?,?::jsonb)",id,series,body);
        jdbc.update("insert into report_emission_request(issuer_id,operation_key,filters_sha256,emission_id) values(?,?,?,?)",a.user.getId(),c.operationKey(),filterHash,id);
        audit.record(a.user,"REPORT_EMITTED","REPORT_EMISSION",id,Map.of("seriesId",series,"revision",revision,"scope",scope,"contentSha256",sha));
        return new Emission(id,series,revision,priorId,report.cutoffAt(),sha,report);
    }
    @Transactional(readOnly=true) public Emission read(boolean own,UUID id){return load(actor(own),id);}
    @Transactional public Download export(boolean own,UUID id,ReportExportFormat format){
        var a=actor(own);var emission=load(a,id);var file=exports.render(emission,format);
        // Server preparation is auditable; this does not claim a client saved
        // the response. Plain reads and failed authorization never reach here.
        audit.record(a.user,"REPORT_EXPORTED","REPORT_EMISSION",id,Map.of("seriesId",emission.seriesId(),"revision",emission.revision(),"scope",emission.report().scope(),"contentSha256",emission.contentSha256(),"kind",format));
        return new Download(emission,file);
    }
    public record Download(Emission emission,ReportExport file) { }
    @Transactional(readOnly=true) public List<Summary> list(boolean own,LocalDate from,LocalDate to){
        var a=actor(own);ServiceReportQuery.range(from,to);requireContentAvailable();
        return jdbc.query("""
            select e.id,e.revision,e.previous_emission_id,e.issued_at,e.content_sha256 from report_emission e
             join report_emission_content c on c.emission_id=e.id join report_emission_series s on s.id=c.series_id
            where s.organization_id=? and s.issuer_id=? and s.scope=? and s.from_day=? and s.to_day=? order by e.revision desc limit 100
            """,(r,n)->new Summary(r.getObject(1,UUID.class),r.getInt(2),r.getObject(3,UUID.class),r.getTimestamp(4).toInstant(),r.getString(5)),a.user.getOrganizationId(),a.user.getId(),a.driver==null?"ORGANIZATION":"OWN_PARTICIPATION",from,to);
    }
    private Emission load(Actor a,UUID id){
        requireContentAvailable();
        var values=jdbc.query("""
            select e.*,c.body_json::text from report_emission e join report_emission_content c on c.emission_id=e.id
             join report_emission_series s on s.id=c.series_id
            where e.id=? and s.organization_id=? and s.issuer_id=? and s.scope=?
            """,(r,n)->new Emission(r.getObject("id",UUID.class),r.getObject("series_id",UUID.class),r.getInt("revision"),r.getObject("previous_emission_id",UUID.class),r.getTimestamp("issued_at").toInstant(),r.getString("content_sha256"),decode(r.getString("body_json"))),id,a.user.getOrganizationId(),a.user.getId(),a.driver==null?"ORGANIZATION":"OWN_PARTICIPATION");
        if(values.isEmpty())throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Emisión no disponible.");return values.getFirst();
    }
    private void requireContentAvailable(){
        Boolean expired=jdbc.queryForObject("select exists(select 1 from pilot_lifecycle where id='PILOT' and ended_at+interval '90 days'<=?)",Boolean.class,Timestamp.from(clock.instant()));
        if(Boolean.TRUE.equals(expired))throw new ApplicationException(HttpStatus.GONE,ErrorCode.RESOURCE_NOT_FOUND,"El contenido de reportes alcanzó su plazo de retiro.");
    }
    private String encode(View value){try{return json.writeValueAsString(value);}catch(Exception failure){throw new IllegalStateException("REPORT_ENCODING_UNAVAILABLE");}}
    private View decode(String value){try{return json.readValue(value,View.class);}catch(Exception failure){throw new IllegalStateException("REPORT_DECODING_UNAVAILABLE");}}
    static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception failure){throw new IllegalStateException("REPORT_HASH_UNAVAILABLE");}}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private record Actor(AppUser user,UUID driver) { }
}
