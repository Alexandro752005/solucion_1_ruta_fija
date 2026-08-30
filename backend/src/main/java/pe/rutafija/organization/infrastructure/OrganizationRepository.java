package pe.rutafija.organization.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.rutafija.organization.domain.Organization;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByLegalNameIgnoreCase(String legalName);

    Page<Organization> findByLegalNameContainingIgnoreCase(String legalName, Pageable pageable);

    boolean existsByLegalNameIgnoreCase(String legalName);
}
