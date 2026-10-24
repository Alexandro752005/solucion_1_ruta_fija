package pe.rutafija.terms.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.rutafija.terms.domain.AdminTermsDocument;

import java.util.Optional;
import java.util.UUID;

public interface AdminTermsDocumentRepository extends JpaRepository<AdminTermsDocument, UUID> {

    Optional<AdminTermsDocument> findFirstByDocumentKeyOrderByCreatedAtDescIdDesc(String documentKey);

    Optional<AdminTermsDocument> findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc(
            String documentKey
    );
}
