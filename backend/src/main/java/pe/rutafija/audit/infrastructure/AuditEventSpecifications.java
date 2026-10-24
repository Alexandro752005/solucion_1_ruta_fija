package pe.rutafija.audit.infrastructure;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;
import pe.rutafija.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class AuditEventSpecifications {

    private AuditEventSpecifications() {
    }

    public static Specification<AuditEvent> filter(
            UUID organizationId,
            String action,
            String entityType,
            Instant from,
            Instant to
    ) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("organization").get("id"), organizationId));
            if (action != null && !action.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("action"), action.strip()));
            }
            if (entityType != null && !entityType.isBlank()) {
                predicates.add(criteriaBuilder.equal(root.get("entityType"), entityType.strip()));
            }
            if (from != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("occurredAt"), from));
            }
            if (to != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("occurredAt"), to));
            }
            return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
        };
    }

    public static Specification<AuditEvent> byIdAndOrganization(UUID id, UUID organizationId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.and(
                criteriaBuilder.equal(root.get("id"), id),
                criteriaBuilder.equal(root.get("organization").get("id"), organizationId)
        );
    }

    /** Expiry is enforced on reads even when the restricted purge has not run. */
    public static Specification<AuditEvent> visibleAt(Instant now){
        return (root,q,cb)->cb.isTrue(cb.function("retention_audit_visible",Boolean.class,
            root.get("id"),root.get("occurredAt"),cb.literal(now)));
    }

    /** Platform scope never includes an organization-owned event. */
    public static Specification<AuditEvent> platform(String action,String entityType,Instant from,Instant to){
        return (root,query,cb)->{
            var p=new ArrayList<Predicate>();p.add(cb.isNull(root.get("organization")));
            if(action!=null&&!action.isBlank())p.add(cb.equal(root.get("action"),action.strip()));
            if(entityType!=null&&!entityType.isBlank())p.add(cb.equal(root.get("entityType"),entityType.strip()));
            if(from!=null)p.add(cb.greaterThanOrEqualTo(root.get("occurredAt"),from));
            if(to!=null)p.add(cb.lessThanOrEqualTo(root.get("occurredAt"),to));
            return cb.and(p.toArray(Predicate[]::new));
        };
    }
}
