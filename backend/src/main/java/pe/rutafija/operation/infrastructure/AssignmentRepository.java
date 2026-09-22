package pe.rutafija.operation.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.domain.Specification;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.AssignmentStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID>, JpaSpecificationExecutor<Assignment> {

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "vehicle", "createdBy"})
    @Override
    Page<Assignment> findAll(Specification<Assignment> specification, Pageable pageable);

    default Page<Assignment> search(
            UUID organizationId,
            UUID driverId,
            UUID vehicleId,
            AssignmentStatus status,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        return findAll(AssignmentSpecifications.filter(organizationId, driverId, vehicleId, status, from, to), pageable);
    }

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "vehicle", "createdBy"})
    Optional<Assignment> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "driver", "driver.group", "vehicle", "createdBy"})
    Optional<Assignment> findByOrganization_IdAndIdempotencyKey(UUID organizationId, String idempotencyKey);

    @Query("""
            select (count(assignment) > 0) from Assignment assignment
             where assignment.organization.id = :organizationId
               and assignment.status in :statuses
               and (assignment.driver.id = :driverId or assignment.vehicle.id = :vehicleId)
               and assignment.scheduledAt < :scheduledEndAt
               and assignment.scheduledEndAt > :scheduledAt
            """)
    boolean existsSchedulingConflict(
            @Param("organizationId") UUID organizationId,
            @Param("driverId") UUID driverId,
            @Param("vehicleId") UUID vehicleId,
            @Param("statuses") Collection<AssignmentStatus> statuses,
            @Param("scheduledAt") Instant scheduledAt,
            @Param("scheduledEndAt") Instant scheduledEndAt
    );

    @Query("""
            select (count(assignment) > 0) from Assignment assignment
             where assignment.organization.id = :organizationId
               and assignment.id <> :assignmentId
               and assignment.status in :statuses
               and (assignment.driver.id = :driverId or assignment.vehicle.id = :vehicleId)
               and assignment.scheduledAt < :scheduledEndAt
               and assignment.scheduledEndAt > :scheduledAt
            """)
    boolean existsSchedulingConflictExcluding(
            @Param("organizationId") UUID organizationId,
            @Param("assignmentId") UUID assignmentId,
            @Param("driverId") UUID driverId,
            @Param("vehicleId") UUID vehicleId,
            @Param("statuses") Collection<AssignmentStatus> statuses,
            @Param("scheduledAt") Instant scheduledAt,
            @Param("scheduledEndAt") Instant scheduledEndAt
    );

    boolean existsByDriver_IdAndStatusIn(UUID driverId, Collection<AssignmentStatus> statuses);

    boolean existsByVehicle_IdAndStatusIn(UUID vehicleId, Collection<AssignmentStatus> statuses);
}
