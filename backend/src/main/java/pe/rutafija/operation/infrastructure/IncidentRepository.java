package pe.rutafija.operation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import pe.rutafija.operation.domain.Incident;
import pe.rutafija.operation.domain.IncidentCategory;
import pe.rutafija.operation.domain.IncidentStatus;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID>, JpaSpecificationExecutor<Incident> {

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "assignment", "reportedBy", "followedUpBy"})
    @Override
    Page<Incident> findAll(Specification<Incident> specification, Pageable pageable);

    default Page<Incident> search(
            UUID organizationId,
            UUID driverId,
            IncidentStatus status,
            IncidentCategory category,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(IncidentSpecifications.filter(organizationId, driverId, status, category, from, to), pageable);
    }

    default Page<Incident> searchVisibleToCoordinator(
            UUID organizationId,
            UUID coordinatorId,
            UUID driverId,
            IncidentStatus status,
            IncidentCategory category,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        Specification<Incident> filter = IncidentSpecifications.filter(
                organizationId, driverId, status, category, from, to
        ).and(IncidentSpecifications.visibleToCoordinator(coordinatorId));
        return findAll(filter, pageable);
    }

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "assignment", "reportedBy", "followedUpBy"})
    Optional<Incident> findByIdAndOrganization_Id(UUID id, UUID organizationId);
}
