package pe.rutafija.operation.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.rutafija.operation.domain.MobileCommandReceipt;

import java.util.Optional;
import java.util.UUID;

public interface MobileCommandReceiptRepository extends JpaRepository<MobileCommandReceipt, UUID> {

    @EntityGraph(attributePaths = {"organization", "driver", "assignment"})
    Optional<MobileCommandReceipt> findByEventId(UUID eventId);
}
