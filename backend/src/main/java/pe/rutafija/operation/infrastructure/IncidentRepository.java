package pe.rutafija.operation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "assignment", "reportedBy", "followedUpBy"})
    Optional<Incident> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "assignment", "reportedBy", "followedUpBy"})
    @Query("""
            select incident from Incident incident
             where incident.organization.id = :organizationId
               and incident.driver.id = :driverId
            """)
    Page<Incident> findMobileIncidents(
            @Param("organizationId") UUID organizationId,
            @Param("driverId") UUID driverId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "assignment", "reportedBy", "followedUpBy"})
    Optional<Incident> findByIdAndOrganization_IdAndDriver_Id(UUID id, UUID organizationId, UUID driverId);
}
