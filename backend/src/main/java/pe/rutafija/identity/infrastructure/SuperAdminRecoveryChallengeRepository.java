package pe.rutafija.identity.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.identity.domain.SuperAdminRecoveryChallenge;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SuperAdminRecoveryChallengeRepository
        extends JpaRepository<SuperAdminRecoveryChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from SuperAdminRecoveryChallenge challenge where challenge.id = :id")
    Optional<SuperAdminRecoveryChallenge> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByUser_IdAndProofVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfter(
            UUID userId,
            Instant now
    );
}
