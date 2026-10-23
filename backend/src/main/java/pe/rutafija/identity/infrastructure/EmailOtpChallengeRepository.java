package pe.rutafija.identity.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.identity.domain.EmailOtpChallenge;

import java.util.Optional;
import java.util.UUID;

public interface EmailOtpChallengeRepository extends JpaRepository<EmailOtpChallenge, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from EmailOtpChallenge challenge where challenge.user.id = :userId")
    Optional<EmailOtpChallenge> findByUserIdForUpdate(@Param("userId") UUID userId);

    @Query("select challenge.user.id from EmailOtpChallenge challenge "
            + "where challenge.verificationTokenHash = :tokenHash")
    Optional<UUID> findUserIdByVerificationTokenHash(@Param("tokenHash") String tokenHash);
}
