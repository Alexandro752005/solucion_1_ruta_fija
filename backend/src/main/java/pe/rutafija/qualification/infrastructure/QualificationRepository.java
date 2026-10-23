package pe.rutafija.qualification.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.rutafija.qualification.api.QualificationContract.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class QualificationRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper json;
    public QualificationRepository(JdbcTemplate jdbc,ObjectMapper json){this.jdbc=jdbc;this.json=json;}
    public String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalArgumentException("Contenido estructurado inválido");}}
    private Map<String,String> fields(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception ex){throw new IllegalStateException("Revisión estructurada inválida");}}
    public static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}
    public static Instant instant(ResultSet rs,String name)throws SQLException{var value=rs.getTimestamp(name);return value==null?null:value.toInstant();}
    public long version(UUID driver){var values=jdbc.query("select version from driver_qualification_control where driver_id=?",(r,n)->r.getLong(1),driver);return values.isEmpty()?0:values.getFirst();}
    public boolean suspended(UUID driver){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from driver_qualification_control where driver_id=? and suspended)",Boolean.class,driver));}
    public void lock(UUID driver,UUID org,Instant now){
        jdbc.update("insert into driver_qualification_control(driver_id,organization_id,updated_at) values(?,?,?) on conflict do nothing",driver,org,ts(now));
        jdbc.queryForObject("select version from driver_qualification_control where driver_id=? and organization_id=? for update",Long.class,driver,org);
    }
    public void increment(UUID driver,Instant now){jdbc.update("update driver_qualification_control set version=version+1,updated_at=? where driver_id=?",ts(now),driver);}
    public void suspend(UUID driver,boolean value,String ref){jdbc.update("update driver_qualification_control set suspended=?,suspension_reference=? where driver_id=?",value,ref,driver);}
    public Optional<String> prior(UUID driver,UUID actor,String key){return jdbc.query("select payload_hash from qualification_operation where driver_id=? and actor_id=? and operation_key=?",(r,n)->r.getString(1).strip(),driver,actor,key).stream().findFirst();}
    public void event(UUID driver,UUID org,UUID actor,String key,String hash,String action,String reason,Instant now){jdbc.update("insert into qualification_operation(id,driver_id,organization_id,actor_id,operation_key,payload_hash,action,reason,resulting_version,occurred_at) values(?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),driver,org,actor,key,hash,action,reason,version(driver),ts(now));}
    public List<Review> reviews(UUID driver){return jdbc.query("""
        select distinct on(scope,requirement_id,vehicle_id) * from qualification_review where driver_id=?
        order by scope,requirement_id,vehicle_id,version desc
        """,(r,n)->new Review(r.getString("scope"),r.getString("requirement_id"),r.getObject("vehicle_id",UUID.class),fields(r.getString("fields")),r.getString("expiry_mode"),instant(r,"valid_until"),r.getString("applicability"),r.getString("applicability_condition"),r.getString("source_reference"),r.getString("result"),r.getString("review_reference"),r.getLong("version")),driver);}
    public void review(UUID driver,UUID org,UUID vehicle,String scope,ReviewInput d,UUID actor,Instant now){jdbc.update("""
        insert into qualification_review(id,organization_id,driver_id,vehicle_id,scope,requirement_id,fields,expiry_mode,valid_until,applicability,applicability_condition,source_reference,result,review_reference,version,reviewer_id,recorded_at)
        values(?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?)
        """,UUID.randomUUID(),org,driver,vehicle,scope,d.requirementId(),write(d.fields()),d.expiryMode(),ts(d.validUntil()),d.applicability(),d.condition(),d.sourceReference(),d.result(),d.reviewReference(),version(driver)+1,actor,ts(now));}
    public List<Period> periods(UUID driver){return jdbc.query("select * from driver_unit_period where driver_id=? order by starts_at,id",(r,n)->new Period(r.getObject("id",UUID.class),r.getObject("vehicle_id",UUID.class),r.getString("kind"),instant(r,"starts_at"),instant(r,"ends_at"),r.getString("status"),r.getString("reason"),r.getLong("version")),driver);}
    public void period(UUID driver,UUID org,PeriodCommand c,Instant start,Instant end,UUID actor,Instant now){jdbc.update("insert into driver_unit_period(id,organization_id,driver_id,vehicle_id,kind,starts_at,ends_at,status,reason,version,created_by,created_at) values(?,?,?,?,?,?,?,'AUTHORIZED',?,?,?,?)",UUID.randomUUID(),org,driver,c.vehicleId(),c.kind(),ts(start),ts(end),c.reason(),version(driver)+1,actor,ts(now));}
    public void revoke(UUID driver,UUID period,UUID actor,Instant now){jdbc.update("update driver_unit_period set status='REVOKED',revoked_at=?,revoked_by=?,version=? where id=? and driver_id=? and status='AUTHORIZED'",ts(now),actor,version(driver)+1,period,driver);}
    public List<UnitRequestView> requests(UUID driver){return jdbc.query("select * from driver_unit_request where driver_id=? order by created_at,id",(r,n)->new UnitRequestView(r.getObject("id",UUID.class),r.getObject("vehicle_id",UUID.class),r.getString("kind"),r.getString("status"),r.getString("reference"),instant(r,"created_at")),driver);}
    public void request(UUID driver,UUID org,UnitCommand c,Instant now){jdbc.update("insert into driver_unit_request(id,driver_id,organization_id,vehicle_id,kind,status,reference,created_at) values(?,?,?,?,?,'PENDING_REVIEW',?,?)",UUID.randomUUID(),driver,org,c.vehicleId(),c.kind(),c.reference(),ts(now));}
    public void decision(UUID driver,UUID id,UnitDecision c,UUID actor,Instant now){jdbc.update("update driver_unit_request set vehicle_id=?,status=?,decided_at=?,decided_by=? where id=? and driver_id=? and status='PENDING_REVIEW'",c.vehicleId(),c.approved()?"APPROVED":"REJECTED",ts(now),actor,id,driver);}
    public List<ServiceAttention> attention(UUID driver){return jdbc.query("""
        select a.id,a.assignment_id,a.kind,a.status,s.scheduled_at,s.scheduled_end_at
        from qualification_service_attention a join assignment s on s.id=a.assignment_id where a.driver_id=? order by a.created_at
        """,(r,n)->new ServiceAttention(r.getObject("id",UUID.class),r.getObject("assignment_id",UUID.class),r.getString("kind"),r.getString("status"),instant(r,"scheduled_at"),instant(r,"scheduled_end_at")),driver);}
}
