package pe.rutafija.onboarding.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/** Explicit legacy associations use V21 bindings; request links remain empty until approval. */
@Repository
public class LegacyConductorRegularizationRepository {
    public static final String OPENED = "LEGACY_REGULARIZATION_OPENED";
    private final JdbcTemplate jdbc;
    public LegacyConductorRegularizationRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Optional<UUID> driverUserId(UUID driverId,UUID organizationId){
        return jdbc.query("select user_id from driver where id=? and organization_id=?",
                (rs,row)->rs.getObject(1,UUID.class),driverId,organizationId).stream().filter(Objects::nonNull).findFirst();
    }
    public Optional<Opening> openingOperation(UUID actorId,String operationKey,UUID organizationId){
        return jdbc.query("""
                select request.id,request.status,event.payload_hash,binding.person_hash,binding.user_id,binding.driver_id,
                       binding.identity_id,binding.organization_id
                  from conductor_onboarding_review_event event
                  join conductor_onboarding_request request on request.id=event.request_id
                  join conductor_person_binding binding on binding.identity_id=request.identity_id
                    and binding.organization_id=request.organization_id
                 where event.action=? and event.actor_kind='ADMIN' and event.actor_id=?
                   and event.operation_key=? and request.organization_id=?
                """,(rs,row)->opening(rs),OPENED,actorId,operationKey,organizationId).stream().findFirst();
    }
    public Optional<Opening> opening(UUID requestId,UUID organizationId){
        return jdbc.query("""
                select request.id,request.status,event.payload_hash,binding.person_hash,binding.user_id,binding.driver_id,
                       binding.identity_id,binding.organization_id
                  from conductor_onboarding_request request
                  join conductor_onboarding_review_event event on event.request_id=request.id
                    and event.resulting_request_id=request.id and event.action=? and event.actor_kind='ADMIN'
                  join conductor_person_binding binding on binding.identity_id=request.identity_id
                    and binding.organization_id=request.organization_id
                 where request.id=? and request.organization_id=?
                """,(rs,row)->opening(rs),OPENED,requestId,organizationId).stream().findFirst();
    }
    public boolean hasBinding(String personHash,UUID userId,UUID driverId){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from conductor_person_binding where person_hash=? or user_id=? or driver_id=?)
                """,Boolean.class,personHash,userId,driverId));
    }
    public boolean hasOnboardingLink(UUID userId,UUID driverId){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from conductor_onboarding_request where linked_user_id=? or linked_driver_id=?)
                """,Boolean.class,userId,driverId));
    }
    public boolean hasOpenWork(UUID driverId){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from assignment where driver_id=? and status in ('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO'))
                    or exists(select 1 from service_execution_segment where driver_id=? and ended_at is null)
                """,Boolean.class,driverId,driverId));
    }
    public boolean createIdentity(UUID id,String email,String inertHash,String fullName,String phone,Instant now){
        return jdbc.update("""
                insert into conductor_onboarding_identity(id,email,password_hash,full_name,phone,created_at,updated_at)
                values(?,?,?,?,?,?,?) on conflict(email) do nothing
                """,id,email,inertHash,fullName,phone,ts(now),ts(now))==1;
    }
    /** This internal administrative reference is consumed immediately; no invitation or OTP was delivered. */
    public UUID createInternalInvitation(UUID organizationId,UUID actorId,String email,String nonceHash,Instant now){
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,
                    issued_at,expires_at,used_at) values(?,?,?,?,?,?,?,?)
                """,id,organizationId,actorId,email,nonceHash,ts(now),ts(now.plusSeconds(3600)),ts(now));
        return id;
    }
    public UUID createRequest(UUID identityId,UUID organizationId,UUID invitationId,String operationKey,Instant now){
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,
                    status,submitted_at,catalog_id,catalog_version,catalog_hash)
                values(?,?,?,?,?,'PENDING_REVIEW',?,?,?,?)
                """,id,identityId,organizationId,invitationId,operationKey,ts(now),ConductorDocumentaryCatalog.ID,
                ConductorDocumentaryCatalog.VERSION,ConductorDocumentaryCatalog.HASH);
        return id;
    }
    public boolean bind(String personHash,UUID identityId,UUID organizationId,UUID userId,UUID driverId,Instant now){
        return jdbc.update("""
                insert into conductor_person_binding(person_hash,identity_id,organization_id,user_id,driver_id,bound_at)
                values(?,?,?,?,?,?) on conflict do nothing
                """,personHash,identityId,organizationId,userId,driverId,ts(now))==1;
    }
    public boolean lockBinding(Opening opening){
        return !jdbc.query("""
                select person_hash from conductor_person_binding where person_hash=? and identity_id=?
                  and organization_id=? and user_id=? and driver_id=? for update
                """,(rs,row)->rs.getString(1),opening.personHash(),opening.identityId(),opening.organizationId(),
                opening.userId(),opening.driverId()).isEmpty();
    }
    public void invalidateSessions(UUID userId){jdbc.update("update app_user set session_version=session_version+1 where id=?",userId);}
    private static Opening opening(java.sql.ResultSet rs)throws java.sql.SQLException{
        return new Opening(rs.getObject("id",UUID.class),rs.getString("status"),rs.getString("payload_hash").trim(),
                rs.getString("person_hash").trim(),rs.getObject("user_id",UUID.class),rs.getObject("driver_id",UUID.class),
                rs.getObject("identity_id",UUID.class),rs.getObject("organization_id",UUID.class));
    }
    private static Timestamp ts(Instant now){return Timestamp.from(now);}
    public record Opening(UUID requestId,String status,String payloadHash,String personHash,UUID userId,UUID driverId,
                          UUID identityId,UUID organizationId){}
}
