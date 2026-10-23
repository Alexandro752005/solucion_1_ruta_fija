package pe.rutafija.terms.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.rutafija.terms.domain.AdminTermsAcceptance;

import java.util.Optional;
import java.util.UUID;

public interface AdminTermsAcceptanceRepository extends JpaRepository<AdminTermsAcceptance, UUID> {

    Optional<AdminTermsAcceptance> findByAdminUser_IdAndDocument_Id(UUID adminUserId, UUID documentId);
}
