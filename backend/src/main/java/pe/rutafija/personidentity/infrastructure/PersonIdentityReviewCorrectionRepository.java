package pe.rutafija.personidentity.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** The application has no INSERT/UPDATE/DELETE capability on correction evidence. */
@Repository
public class PersonIdentityReviewCorrectionRepository {
    private final JdbcTemplate jdbc;
    public PersonIdentityReviewCorrectionRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}

    public Optional<Correction> prior(UUID actorId,String operationKey){
        return jdbc.query("""
                select id,user_id,driver_id,organization_id,payload_hash,corrected_person_hash
                  from person_identity_review_correction where reviewer_user_id=? and operation_key=?
                """,(rs,row)->new Correction(rs.getObject("id",UUID.class),rs.getObject("user_id",UUID.class),
                rs.getObject("driver_id",UUID.class),rs.getObject("organization_id",UUID.class),
                rs.getString("payload_hash").trim(),rs.getString("corrected_person_hash").trim()),actorId,operationKey)
                .stream().findFirst();
    }
    public boolean hasBindingOrProvision(UUID userId,UUID driverId,String previousHash,String correctedHash){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from conductor_person_binding where user_id=? or driver_id=?
                         or person_hash in(?,?))
                    or exists(select 1 from conductor_common_person_bridge
                         where person_hash in(?,?) or common_person_hash in(?,?))
                    or exists(select 1 from conductor_onboarding_request where linked_user_id=? or linked_driver_id=?)
                """,Boolean.class,userId,driverId,previousHash,correctedHash,previousHash,correctedHash,
                previousHash,correctedHash,userId,driverId));
    }
    public boolean hasOpenWork(UUID driverId){
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from assignment where driver_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO'))
                    or exists(select 1 from service_execution_segment where driver_id=? and ended_at is null)
                """,Boolean.class,driverId,driverId));
    }
    public UUID correct(UUID id,UUID actorId,UUID userId,UUID driverId,UUID organizationId,
            String expectedHash,String correctedHash,String method,String reference,String reason,
            String operationKey,String payloadHash,Instant now){
        return jdbc.queryForObject("""
                select public.correct_unprovisioned_conductor_identity(?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,UUID.class,id,actorId,userId,driverId,organizationId,expectedHash,correctedHash,method,
                reference,reason,operationKey,payloadHash,Timestamp.from(now));
    }
    public record Correction(UUID id,UUID userId,UUID driverId,UUID organizationId,String payloadHash,String correctedHash){}
}
