package pe.rutafija.identity.infrastructure;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.identity.domain.SuperAdminRecoveryCredential;

import java.util.Optional;

public interface SuperAdminRecoveryCredentialRepository
        extends JpaRepository<SuperAdminRecoveryCredential, Short> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select credential from SuperAdminRecoveryCredential credential where credential.id = 1")
    Optional<SuperAdminRecoveryCredential> findSingletonForUpdate();

    @Query("select credential from SuperAdminRecoveryCredential credential where credential.id = 1")
    Optional<SuperAdminRecoveryCredential> findSingleton();
}
