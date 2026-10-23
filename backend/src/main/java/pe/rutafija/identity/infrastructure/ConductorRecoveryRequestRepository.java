package pe.rutafija.identity.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.identity.domain.ConductorRecoveryRequest;

import java.util.Optional;
import java.util.UUID;

public interface ConductorRecoveryRequestRepository extends JpaRepository<ConductorRecoveryRequest, UUID> {

    Optional<ConductorRecoveryRequest> findByIdAndOrganizationIdAndConductorUserId(
            UUID id,
            UUID organizationId,
            UUID conductorUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recovery from ConductorRecoveryRequest recovery where recovery.id = :id "
            + "and recovery.organizationId = :organizationId and recovery.conductorUserId = :conductorUserId")
    Optional<ConductorRecoveryRequest> findForUpdate(
            @Param("id") UUID id,
            @Param("organizationId") UUID organizationId,
            @Param("conductorUserId") UUID conductorUserId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select recovery from ConductorRecoveryRequest recovery where recovery.id = :id")
    Optional<ConductorRecoveryRequest> findByIdForUpdate(@Param("id") UUID id);

    Optional<ConductorRecoveryRequest> findByPasswordResetTokenHash(String tokenHash);

    boolean existsByConductorUserIdAndStatusIn(
            UUID conductorUserId,
            java.util.Collection<pe.rutafija.identity.domain.ConductorRecoveryStatus> statuses
    );
}
