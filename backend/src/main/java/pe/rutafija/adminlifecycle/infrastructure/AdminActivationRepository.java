package pe.rutafija.adminlifecycle.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.rutafija.adminlifecycle.api.AdminLifecycleContract.Summary;
import java.sql.*;
import java.time.Instant;
import java.util.*;

@Repository
public class AdminActivationRepository {
    private final JdbcTemplate jdbc;
    public AdminActivationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
    public List<Summary> list(){return jdbc.query("""
            select account.id,account.organization_id,account.active,account.created_at,
                   activation.id as activation_id,activation.status,activation.replaced_admin_user_id,activation.credential_expires_at,
                   exists(select 1 from person_identity person where person.user_id=account.id) as identity_verified
              from app_user account left join admin_activation activation on activation.admin_user_id=account.id
             where account.role='ADMIN' order by account.created_at,account.id
            """,(rs,row)->new Summary(rs.getObject("id",UUID.class),rs.getObject("organization_id",UUID.class),rs.getBoolean("active"),
            rs.getObject("activation_id",UUID.class),Objects.requireNonNullElse(rs.getString("status"),"NO_ACTIVATION"),
            rs.getObject("replaced_admin_user_id",UUID.class),time(rs,"credential_expires_at"),time(rs,"created_at"),rs.getBoolean("identity_verified")));}
    public boolean lockOrganization(UUID organizationId){return !jdbc.query("select id from organization where id=? and status='ACTIVE' for no key update",
            (rs,row)->rs.getObject(1,UUID.class),organizationId).isEmpty();}
    public Optional<Row> byId(UUID id){return find("id",id,false);}
    public Optional<Row> byUser(UUID id){return find("admin_user_id",id,false);}
    public Optional<Row> byCredential(String hash){return find("credential_hash",hash,false);}
    public Optional<Row> byToken(String hash){return find("activation_token_hash",hash,false);}
    public Optional<Row> lock(UUID id){return find("id",id,true);}
    private Optional<Row> find(String column,Object value,boolean lock){return jdbc.query("select * from admin_activation where "+column+"=?"+(lock?" for update":""),AdminActivationRepository::row,value).stream().findFirst();}
    public boolean pendingReplacement(UUID outgoingId){Integer count=jdbc.queryForObject("select count(*) from admin_activation where replaced_admin_user_id=? and status in ('PENDING_CLAIM','IN_PROGRESS')",Integer.class,outgoingId);return count!=null&&count>0;}
    public boolean completedReplacement(UUID outgoingId){Integer count=jdbc.queryForObject("select count(*) from admin_activation where replaced_admin_user_id=? and status='COMPLETED'",Integer.class,outgoingId);return count!=null&&count>0;}
    public boolean authorizedRevocation(UUID userId){Integer count=jdbc.queryForObject("select count(*) from admin_security_revocation where admin_user_id=?",Integer.class,userId);return count!=null&&count>0;}
    public void securityRevocation(UUID userId,UUID organizationId,UUID actorId,String reason,Instant now){jdbc.update("insert into admin_security_revocation(id,admin_user_id,organization_id,authorized_by_super_admin_id,reason,revoked_at) values(?,?,?,?,?,?)",UUID.randomUUID(),userId,organizationId,actorId,reason,ts(now));}
    public void insert(UUID id,UUID userId,UUID organizationId,UUID superAdminId,UUID outgoingId,String method,String reference,
                       String reason,String hash,Instant now,Instant expiresAt){jdbc.update("""
            insert into admin_activation(id,admin_user_id,organization_id,created_by_super_admin_id,replaced_admin_user_id,
              verification_method,verification_reference,creation_reason,credential_hash,credential_issued_at,credential_expires_at,created_at,updated_at)
            values(?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,id,userId,organizationId,superAdminId,outgoingId,method,reference,reason,hash,ts(now),ts(expiresAt),ts(now),ts(now));}
    public void claim(UUID id,String tokenHash,Instant now,Instant expiresAt){jdbc.update("""
            update admin_activation set credential_consumed_at=?,activation_token_hash=?,activation_expires_at=?,
              activation_token_revoked_at=null,password_set_at=?,email_verified_at=null,status='IN_PROGRESS',updated_at=? where id=?
            """,ts(now),tokenHash,ts(expiresAt),ts(now),ts(now),id);}
    public void setPassword(UUID userId,String hash,Instant now){jdbc.update("update app_user set password_hash=?,updated_at=? where id=? and role='ADMIN' and active=false",hash,ts(now),userId);}
    public void rotate(UUID id,String hash,Instant now,Instant expiresAt){jdbc.update("""
            update admin_activation set credential_hash=?,credential_issued_at=?,credential_expires_at=?,credential_consumed_at=null,
              activation_token_hash=null,activation_expires_at=null,activation_token_revoked_at=null,password_set_at=null,
              email_verified_at=null,status='PENDING_CLAIM',updated_at=? where id=?
            """,hash,ts(now),ts(expiresAt),ts(now),id);}
    public void markEmailVerified(UUID id,Instant now){jdbc.update("update admin_activation set email_verified_at=?,updated_at=? where id=?",ts(now),ts(now),id);}
    public void complete(UUID id,Instant now){jdbc.update("update admin_activation set status='COMPLETED',completed_at=?,activation_token_revoked_at=?,updated_at=? where id=?",ts(now),ts(now),ts(now),id);}
    public void revokeSessionsVersion(UUID id){jdbc.update("update app_user set session_version=session_version+1 where id=?",id);}
    public void event(UUID activationId,String action,UUID actorId,Instant now,String reason){jdbc.update("insert into admin_activation_event(id,activation_id,action,actor_id,occurred_at,reason) values(?,?,?,?,?,?)",UUID.randomUUID(),activationId,action,actorId,ts(now),reason);}
    private static Row row(ResultSet rs,int index)throws SQLException{return new Row(rs.getObject("id",UUID.class),rs.getObject("admin_user_id",UUID.class),rs.getObject("organization_id",UUID.class),
            rs.getObject("replaced_admin_user_id",UUID.class),rs.getString("credential_hash").trim(),time(rs,"credential_expires_at"),time(rs,"credential_consumed_at"),
            rs.getString("activation_token_hash"),time(rs,"activation_expires_at"),time(rs,"activation_token_revoked_at"),time(rs,"password_set_at"),time(rs,"email_verified_at"),rs.getString("status"));}
    private static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}
    private static Instant time(ResultSet rs,String column)throws SQLException{Timestamp value=rs.getTimestamp(column);return value==null?null:value.toInstant();}
    public record Row(UUID id,UUID userId,UUID organizationId,UUID outgoingId,String credentialHash,Instant credentialExpiresAt,
                      Instant credentialConsumedAt,String tokenHash,Instant expiresAt,Instant revokedAt,Instant passwordSetAt,
                      Instant emailVerifiedAt,String status) { }
}
