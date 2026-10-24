package pe.rutafija.personidentity.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class PersonIdentityRepository {
    private final JdbcTemplate jdbc;
    public PersonIdentityRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public boolean verified(UUID userId){return !jdbc.query("select user_id from person_identity where user_id=?",
            (rs,row)->rs.getObject(1,UUID.class),userId).isEmpty();}
    public Optional<Binding> byHash(String hash){return jdbc.query("select person_hash,user_id from person_identity where person_hash=?",
            (rs,row)->new Binding(rs.getString(1).trim(),rs.getObject(2,UUID.class)),hash).stream().findFirst();}
    public Optional<Binding> byUser(UUID userId){return jdbc.query("select person_hash,user_id from person_identity where user_id=?",
            (rs,row)->new Binding(rs.getString(1).trim(),rs.getObject(2,UUID.class)),userId).stream().findFirst();}
    public boolean lockPerson(String hash){return Boolean.TRUE.equals(jdbc.queryForObject(
            "select pg_try_advisory_xact_lock(hashtextextended(?,0))",Boolean.class,"person-identity:"+hash));}
    public void lockUser(UUID userId){jdbc.queryForObject("select id from app_user where id=? for update",UUID.class,userId);}
    public Optional<UUID> driverUserId(UUID driverId,UUID organizationId){return jdbc.query(
            "select user_id from driver where id=? and organization_id=?",
            (rs,row)->rs.getObject(1,UUID.class),driverId,organizationId).stream().filter(Objects::nonNull).findFirst();}
    public void bind(String hash,UUID userId,UUID reviewerId,String method,String reference,Instant now){jdbc.update("""
            insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at)
            values(?,?,'DNI',?,?,?,?)
            """,hash,userId,reviewerId,method,reference,Timestamp.from(now));}
    public void bridgeConductor(String hash){jdbc.update("insert into conductor_common_person_bridge(person_hash,common_person_hash) values(?,?)",hash,hash);}
    public Optional<String> keyFingerprint(){return jdbc.query("select key_fingerprint from person_identity_hash_key where id=1",
            (rs,row)->rs.getString(1).trim()).stream().findFirst();}
    public void initializeKey(String fingerprint,Instant now){jdbc.update("""
            insert into person_identity_hash_key(id,key_fingerprint,recorded_at) values(1,?,?) on conflict do nothing
            """,fingerprint,Timestamp.from(now));}
    public List<LegacyDocument> legacyDocuments(){return jdbc.query("""
            select binding.person_hash,driver.document_type,driver.document_number
              from conductor_person_binding binding left join driver on driver.id=binding.driver_id
            """,(rs,row)->new LegacyDocument(rs.getString(1).trim(),rs.getString(2),rs.getString(3)));}
    public List<DriverDocument> driverDocuments(){return jdbc.query("select user_id,document_type,document_number from driver",
            (rs,row)->new DriverDocument(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)));}
    public void revokeSessions(UUID userId){jdbc.update("update app_user set session_version=session_version+1 where id=?",userId);}
    public record Binding(String personHash,UUID userId) { }
    public record LegacyDocument(String personHash,String documentType,String documentNumber) { }
    public record DriverDocument(UUID userId,String documentType,String documentNumber) { }
}
