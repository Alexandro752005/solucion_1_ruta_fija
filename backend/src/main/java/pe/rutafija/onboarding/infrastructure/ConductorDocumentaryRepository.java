package pe.rutafija.onboarding.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.*;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConductorDocumentaryRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private static final String COLUMNS = "id,identity_id,organization_id,status,version,review_rounds,modality," +
            "correction_deadline,personal_approved_at,reapply_after,linked_user_id,linked_driver_id," +
            "catalog_id,catalog_version,catalog_hash";

    public ConductorDocumentaryRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public Optional<State> find(UUID id, UUID organizationId, UUID identityId) {
        if (organizationId != null) {
            return jdbc.query("select " + COLUMNS + " from conductor_onboarding_request where id=? and organization_id=?",
                    ConductorDocumentaryRepository::state, id, organizationId).stream().findFirst();
        }
        return jdbc.query("select " + COLUMNS + " from conductor_onboarding_request where id=? and identity_id=?",
                ConductorDocumentaryRepository::state, id, identityId).stream().findFirst();
    }

    public Optional<State> lock(UUID id, UUID organizationId, UUID identityId) {
        var visible = find(id, organizationId, identityId);
        if (visible.isEmpty()) return Optional.empty();
        // All onboarding writers use identity -> request/challenge -> organization -> invitation.
        jdbc.queryForObject("select id from conductor_onboarding_identity where id=? for update", UUID.class,
                visible.get().identityId());
        return jdbc.query("select " + COLUMNS + " from conductor_onboarding_request where id=? for update",
                ConductorDocumentaryRepository::state, id).stream().findFirst();
    }

    public List<Document> documents(UUID requestId) {
        return jdbc.query("""
                select distinct on (requirement_id) requirement_id, entity, fields::text, expiry_mode,
                       valid_until, applicability, applicability_condition, source_reference, result,
                       review_reference, observation
                  from conductor_document_revision where request_id=?
                 order by requirement_id,revision_no desc
                """, (rs, row) -> new Document(rs.getString("requirement_id"), rs.getString("entity"),
                readFields(rs.getString("fields")), rs.getString("expiry_mode"), instant(rs,"valid_until"),
                rs.getString("applicability"), rs.getString("applicability_condition"),
                rs.getString("source_reference"), rs.getString("result"), rs.getString("review_reference"),
                rs.getString("observation")), requestId);
    }

    public void appendDocument(State state, Document document, UUID reviewerId, String actorKind, Instant now) {
        jdbc.update("""
                insert into conductor_document_revision
                    (id,request_id,requirement_id,revision_no,fields,expiry_mode,valid_until,applicability,
                     applicability_condition,source_reference,result,review_reference,observation,reviewer_id,
                     actor_kind,recorded_at,catalog_id,catalog_version,catalog_hash)
                values (?,?,?,?,?::jsonb,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, UUID.randomUUID(),state.id(),document.requirementId(),state.version()+1,
                write(document.fields()),document.expiryMode(),ts(document.validUntil()),document.applicability(),
                document.condition(),document.sourceReference(),document.result(),document.reviewReference(),
                document.observation(),reviewerId,actorKind,ts(now),state.catalogId(),state.catalogVersion(),state.catalogHash());
    }

    public Optional<Event> priorOperation(UUID requestId,String actorKind,UUID actorId,String operationKey) {
        return jdbc.query("""
                select payload_hash,resulting_request_id from conductor_onboarding_review_event
                 where request_id=? and actor_kind=? and actor_id=? and operation_key=?
                """,(rs,row)->new Event(rs.getString("payload_hash").trim(),rs.getObject("resulting_request_id",UUID.class)),
                requestId,actorKind,actorId,operationKey).stream().findFirst();
    }

    public void event(State state,String actorKind,UUID actorId,String operationKey,String hash,String action,
                      String reason,Instant now,UUID resultingRequestId) {
        jdbc.update("""
                insert into conductor_onboarding_review_event
                    (id,request_id,operation_key,payload_hash,action,actor_kind,actor_id,occurred_at,reason,
                     review_round,resulting_version,resulting_request_id,catalog_id,catalog_version,catalog_hash)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),state.id(),operationKey,hash,action,actorKind,actorId,ts(now),reason,
                state.reviewRounds(),state.version(),resultingRequestId,state.catalogId(),state.catalogVersion(),state.catalogHash());
    }

    public List<History> history(UUID requestId) {
        return jdbc.query("""
                select action,occurred_at,reason,review_round from conductor_onboarding_review_event
                 where request_id=? order by resulting_version,occurred_at,id
                """,(rs,row)->new History(rs.getString("action"),instant(rs,"occurred_at"),
                rs.getString("reason"),rs.getInt("review_round")),requestId);
    }

    public void touch(UUID id,String modality) {
        jdbc.update("update conductor_onboarding_request set version=version+1,modality=? where id=?",modality,id);
    }

    public void returnRequest(UUID id,UUID admin,Instant now,Instant deadline) {
        jdbc.update("""
                update conductor_onboarding_request set status='RETURNED',review_rounds=review_rounds+1,
                    version=version+1,returned_at=?,correction_deadline=?,reviewed_by_admin_id=?,reviewed_at=? where id=?
                """,ts(now),ts(deadline),admin,ts(now),id);
    }

    public void extend(UUID id,UUID admin,Instant now,Instant deadline) {
        jdbc.update("""
                update conductor_onboarding_request set version=version+1,correction_deadline=?,
                    reviewed_by_admin_id=?,reviewed_at=? where id=?
                """,ts(deadline),admin,ts(now),id);
    }

    public void resubmit(UUID id) {
        jdbc.update("update conductor_onboarding_request set status='PENDING_REVIEW',version=version+1 where id=?",id);
    }

    public void approve(UUID id,UUID admin,Instant now) {
        jdbc.update("""
                update conductor_onboarding_request set status='APPROVED',version=version+1,
                    personal_approved_at=?,reviewed_by_admin_id=?,reviewed_at=? where id=?
                """,ts(now),admin,ts(now),id);
    }

    public void reject(UUID id,UUID admin,Instant now,boolean reiteration,Instant reapplyAfter) {
        jdbc.update("""
                update conductor_onboarding_request set status='REJECTED',version=version+1,rejected_at=?,
                    rejection_kind=?,reapply_after=?,reviewed_by_admin_id=?,reviewed_at=? where id=?
                """,ts(now),reiteration?"REITERATION":"GENERAL",ts(reapplyAfter),admin,ts(now),id);
    }

    public void provision(UUID id,UUID userId,UUID driverId,Instant now) {
        jdbc.update("""
                update conductor_onboarding_request set linked_user_id=?,linked_driver_id=?,provisioned_at=?,
                    version=version+1 where id=?
                """,userId,driverId,ts(now),id);
    }

    /** Carries the person's rolling OTP budget across provisioning without any usable secret. */
    public void transferOtpHistory(UUID identityId, UUID userId, String currentEmail,
                                   String inertVerificationHash, Instant now) {
        jdbc.update("""
                insert into email_otp_challenge
                    (id,user_id,failed_attempts,blocked_until,send_at_1,send_at_2,send_at_3,
                     consumed_at,created_at,updated_at,purpose,recipient_email)
                select ?,?,failed_attempts,blocked_until,send_at_1,send_at_2,send_at_3,
                       coalesce(consumed_at,?),created_at,?,'LOGIN',?
                  from conductor_onboarding_email_challenge where identity_id=?
                """, UUID.randomUUID(), userId, ts(now), ts(now), currentEmail, identityId);
        // Keep send/error history intact while retiring the old preliminary
        // verification proof, including its former idempotent acceptance path.
        jdbc.update("""
                update conductor_onboarding_email_challenge
                   set verification_token_hash=?,otp_hash=null,otp_expires_at=null,
                       consumed_at=coalesce(consumed_at,?),updated_at=?
                 where identity_id=?
                """, inertVerificationHash,ts(now),ts(now),identityId);
        // The request link also invalidates these logically; revoke explicitly so
        // neither a pre-provisioning token nor challenge survives the handoff.
        jdbc.update("""
                update conductor_onboarding_access_token set revoked_at=greatest(?,created_at)
                 where identity_id=? and revoked_at is null
                """, ts(now), identityId);
    }

    public ApplicantIdentity identity(UUID id) {
        return jdbc.queryForObject("select email,password_hash,full_name,phone,email_verified_at from conductor_onboarding_identity where id=?",
                (rs,row)->new ApplicantIdentity(rs.getString("email"),rs.getString("password_hash"),
                        rs.getString("full_name"),rs.getString("phone"),instant(rs,"email_verified_at")),id);
    }

    public boolean reservePerson(String personHash,State state,Instant now) {
        return jdbc.update("""
                insert into conductor_person_binding(person_hash,identity_id,organization_id,bound_at)
                values(?,?,?,?) on conflict do nothing
                """,personHash,state.identityId(),state.organizationId(),ts(now))==1;
    }

    public boolean legacyPersonExists(String type,String number) {
        Long count=jdbc.queryForObject("select count(*) from driver where upper(document_type)=? and upper(document_number)=?",Long.class,type,number);
        return count!=null&&count>0;
    }

    public void completePerson(String personHash,UUID userId,UUID driverId) {
        jdbc.update("update conductor_person_binding set user_id=?,driver_id=? where person_hash=?",userId,driverId,personHash);
    }

    public boolean activeOrganization(UUID id) {
        return !jdbc.query("select id from organization where id=? and status='ACTIVE' for share",(rs,row)->rs.getObject(1,UUID.class),id).isEmpty();
    }

    public boolean activeGroup(UUID id,UUID organizationId) {
        return !jdbc.query("select id from transport_group where id=? and organization_id=? and active=true for share",
                (rs,row)->rs.getObject(1,UUID.class),id,organizationId).isEmpty();
    }

    public Optional<Invitation> lockInvitation(UUID organizationId,String email,String codeHash,Instant now) {
        return jdbc.query("""
                select id from organization_invitation where organization_id=? and recipient_email=? and code_hash=?
                  and revoked_at is null and used_at is null and expires_at>? for update
                """,(rs,row)->new Invitation(rs.getObject("id",UUID.class)),organizationId,email,codeHash,ts(now)).stream().findFirst();
    }

    public UUID reapply(State old,UUID invitationId,String operationKey,Instant now) {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,
                    status,submitted_at,modality,catalog_id,catalog_version,catalog_hash)
                values(?,?,?,?,?,'PENDING_REVIEW',?,?,?,?,?)
                """,id,old.identityId(),old.organizationId(),invitationId,operationKey,ts(now),old.modality(),
                ConductorDocumentaryCatalog.ID,ConductorDocumentaryCatalog.VERSION,ConductorDocumentaryCatalog.HASH);
        jdbc.update("update organization_invitation set used_at=? where id=? and used_at is null",ts(now),invitationId);
        jdbc.update("update conductor_onboarding_request set version=version+1 where id=?",old.id());
        return id;
    }

    public String write(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch(JsonProcessingException exception){throw new IllegalStateException("Documentary data serialization failed",exception);}
    }

    private Map<String,String> readFields(String value) {
        try{return mapper.readValue(value,new TypeReference<Map<String,String>>(){});}
        catch(JsonProcessingException exception){throw new IllegalStateException("Stored documentary data is invalid",exception);}
    }

    private static State state(ResultSet rs,int row)throws SQLException {
        return new State(rs.getObject("id",UUID.class),rs.getObject("identity_id",UUID.class),
                rs.getObject("organization_id",UUID.class),rs.getString("status"),rs.getLong("version"),
                rs.getInt("review_rounds"),rs.getString("modality"),instant(rs,"correction_deadline"),
                instant(rs,"personal_approved_at"),instant(rs,"reapply_after"),rs.getObject("linked_user_id",UUID.class),
                rs.getObject("linked_driver_id",UUID.class),rs.getString("catalog_id"),rs.getString("catalog_version"),
                rs.getString("catalog_hash").trim());
    }
    private static Timestamp ts(Instant value){return value==null?null:Timestamp.from(value);}
    private static Instant instant(ResultSet rs,String column)throws SQLException{Timestamp value=rs.getTimestamp(column);return value==null?null:value.toInstant();}

    public record State(UUID id,UUID identityId,UUID organizationId,String status,long version,int reviewRounds,
                        String modality,Instant correctionDeadline,Instant personalApprovedAt,Instant reapplyAfter,
                        UUID linkedUserId,UUID linkedDriverId,String catalogId,String catalogVersion,String catalogHash){}
    public record Event(String payloadHash,UUID resultingRequestId){}
    public record ApplicantIdentity(String email,String passwordHash,String fullName,String phone,Instant verifiedAt){}
    public record Invitation(UUID id){}
}
