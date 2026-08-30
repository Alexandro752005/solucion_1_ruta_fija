package pe.rutafija.operation.infrastructure;

import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Consultas agregadas contra las tablas transaccionales de operación. */
@Repository
public class OperationReportRepository {

    private final EntityManager entityManager;

    public OperationReportRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public List<StatusTotal> driverAvailability(UUID organizationId) {
        return statusTotals(
                "select availability_status, count(*) from driver where organization_id = :organizationId group by availability_status order by availability_status",
                organizationId,
                null,
                null
        );
    }

    public List<StatusTotal> vehicleAvailability(UUID organizationId) {
        return statusTotals(
                "select status, count(*) from vehicle where organization_id = :organizationId group by status order by status",
                organizationId,
                null,
                null
        );
    }

    public long futureScheduledAssignments(UUID organizationId, Instant now) {
        return count(
                "select count(*) from assignment where organization_id = :organizationId and status = 'SCHEDULED' and scheduled_at > :now",
                organizationId,
                now
        );
    }

    public long assignmentsInService(UUID organizationId) {
        return count(
                "select count(*) from assignment where organization_id = :organizationId and status = 'EN_SERVICIO'",
                organizationId,
                null
        );
    }

    public long openIncidents(UUID organizationId) {
        return count(
                "select count(*) from incident where organization_id = :organizationId and status <> 'RESOLVED'",
                organizationId,
                null
        );
    }

    public List<StatusTotal> assignmentStatusTotals(UUID organizationId, Instant from, Instant to) {
        return statusTotals(
                "select status, count(*) from assignment where organization_id = :organizationId and scheduled_at >= :from and scheduled_at < :to group by status order by status",
                organizationId,
                from,
                to
        );
    }

    public List<StatusTotal> incidentStatusTotals(UUID organizationId, Instant from, Instant to) {
        return statusTotals(
                "select status, count(*) from incident where organization_id = :organizationId and reported_at >= :from and reported_at < :to group by status order by status",
                organizationId,
                from,
                to
        );
    }

    public List<StatusTotal> incidentCategoryTotals(UUID organizationId, Instant from, Instant to) {
        return statusTotals(
                "select category, count(*) from incident where organization_id = :organizationId and reported_at >= :from and reported_at < :to group by category order by category",
                organizationId,
                from,
                to
        );
    }

    @SuppressWarnings("unchecked")
    private List<StatusTotal> statusTotals(String sql, UUID organizationId, Instant from, Instant to) {
        var query = entityManager.createNativeQuery(sql).setParameter("organizationId", organizationId);
        if (from != null) {
            query.setParameter("from", from);
        }
        if (to != null) {
            query.setParameter("to", to);
        }
        return ((List<Object[]>) query.getResultList()).stream()
                .map(row -> new StatusTotal(String.valueOf(row[0]), ((Number) row[1]).longValue()))
                .toList();
    }

    private long count(String sql, UUID organizationId, Instant now) {
        var query = entityManager.createNativeQuery(sql).setParameter("organizationId", organizationId);
        if (now != null) {
            query.setParameter("now", now);
        }
        return ((Number) query.getSingleResult()).longValue();
    }
}
