package pe.rutafija.operation.infrastructure;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import pe.rutafija.fleet.domain.GroupCoordinator;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.AssignmentStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class AssignmentSpecifications {

    private AssignmentSpecifications() {
    }

    static Specification<Assignment> filter(
            UUID organizationId,
            UUID driverId,
            UUID vehicleId,
            AssignmentStatus status,
            Instant from,
            Instant to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("organization").get("id"), organizationId));
            if (driverId != null) {
                predicates.add(criteriaBuilder.equal(root.get("driver").get("id"), driverId));
            }
            if (vehicleId != null) {
                predicates.add(criteriaBuilder.equal(root.get("vehicle").get("id"), vehicleId));
            }
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("scheduledAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThan(root.get("scheduledAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    static Specification<Assignment> visibleToCoordinator(UUID coordinatorId) {
        return (root, query, criteriaBuilder) -> {
            Subquery<Integer> membership = query.subquery(Integer.class);
            Root<GroupCoordinator> coordinator = membership.from(GroupCoordinator.class);
            membership.select(criteriaBuilder.literal(1));
            membership.where(
                    criteriaBuilder.equal(coordinator.get("group"), root.get("driver").get("group")),
                    criteriaBuilder.equal(coordinator.get("user").get("id"), coordinatorId)
            );
            return criteriaBuilder.exists(membership);
        };
    }
}
