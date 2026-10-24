package pe.rutafija.operation.application;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import pe.rutafija.operation.api.OperationNoticeContract.Notice;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.security.*;
import pe.rutafija.shared.exception.*;
import pe.rutafija.identity.domain.*;
import java.sql.*;
import java.time.*;
import java.util.*;
@Service
public class OperationNoticeService {
    private final JdbcTemplate jdbc;private final ObjectMapper json;private final CurrentUserService current;
    private final MobileDriverContextService mobile;private final Clock clock;
    public OperationNoticeService(JdbcTemplate jdbc,ObjectMapper json,CurrentUserService current,MobileDriverContextService mobile,Clock clock){this.jdbc=jdbc;this.json=json;this.current=current;this.mobile=mobile;this.clock=clock;}
    @Transactional(readOnly=true) public PageResponse<Notice> inbox(boolean ownMobile,boolean failures,int page,int size){
        var actor=actor(ownMobile);if(failures&&actor.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La entrega requiere ADMIN");
        String predicate="n.organization_id=?"+(failures?" and n.external_status in('NOT_CONFIGURED','FAILED')":" and n.user_id=?");
        var args=new ArrayList<Object>();args.add(actor.getOrganizationId());if(!failures)args.add(actor.getId());
        if(ownMobile)predicate+=" and (not n.references_json ? 'announcementId' or exists(select 1 from announcement_recipient r where r.announcement_id=(n.references_json->>'announcementId')::uuid and r.user_id=n.user_id and r.organization_id=n.organization_id and r.withdrawn_at is null))";
        // JDBC interprets ? as a parameter; use jsonb_exists instead of PostgreSQL's ? operator.
        predicate=predicate.replace("n.references_json ? 'announcementId'","jsonb_exists(n.references_json,'announcementId')");
        long count=jdbc.queryForObject("select count(*) from operation_notice n where "+predicate,Long.class,args.toArray());
        args.add(size);args.add(page*size);
        var rows=jdbc.query("select n.* from operation_notice n where "+predicate+" order by n.created_at desc,n.id limit ? offset ?",(r,n)->read(r),args.toArray());
        return new PageResponse<>(rows,page,size,count,(int)((count+size-1)/size));
    }
    @Transactional public Notice markRead(boolean ownMobile,UUID id){
        var actor=actor(ownMobile);String authorization=ownMobile?" and (not jsonb_exists(n.references_json,'announcementId') or exists(select 1 from announcement_recipient r where r.announcement_id=(n.references_json->>'announcementId')::uuid and r.user_id=n.user_id and r.organization_id=n.organization_id and r.withdrawn_at is null))":"";
        int changed=jdbc.update("update operation_notice n set read_at=coalesce(read_at,greatest(created_at,?::timestamptz)) where n.id=? and n.organization_id=? and n.user_id=?"+authorization,Timestamp.from(clock.instant()),id,actor.getOrganizationId(),actor.getId());
        if(changed!=1)throw new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El aviso no existe");
        return jdbc.queryForObject("select * from operation_notice where id=?",(r,n)->read(r),id);
    }
    private AppUser actor(boolean ownMobile){if(ownMobile)return mobile.requireMobileConsultation().user();var a=current.requireTenantActor();if(a.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Requiere ADMIN");return a;}
    private Notice read(ResultSet r)throws SQLException{Map<String,String> refs;try{refs=json.readValue(r.getString("references_json"),new TypeReference<>(){});}catch(Exception failure){throw new IllegalStateException("NOTICE_REFERENCE_DECODING_FAILED");}return new Notice(r.getObject("id",UUID.class),r.getObject("user_id",UUID.class),r.getString("event_type"),refs,r.getTimestamp("created_at").toInstant(),r.getTimestamp("read_at")==null?null:r.getTimestamp("read_at").toInstant(),r.getString("external_status"),r.getString("external_reason"));}
}
