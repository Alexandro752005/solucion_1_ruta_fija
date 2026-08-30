package pe.rutafija.audit.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.rutafija.audit.domain.AuditEvent;

import java.util.Optional;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {

    @EntityGraph(attributePaths = {"organization", "user"})
    @Override
    Page<AuditEvent> findAll(Specification<AuditEvent> specification, Pageable pageable);

    @EntityGraph(attributePaths = {"organization", "user"})
    @Override
    Optional<AuditEvent> findOne(Specification<AuditEvent> specification);
}
