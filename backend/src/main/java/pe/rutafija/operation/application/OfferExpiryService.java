package pe.rutafija.operation.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Expiration is checked under the same tenant lock as every new reservation. */
@Service
public class OfferExpiryService {
    private final AssignmentRepository assignments;
    private final OrganizationSchedulingLock scheduling;
    private final AuditService audit;
    private final OperationEventPublisher events;
    private final Clock clock;
    private final jakarta.persistence.EntityManager entities;

    public OfferExpiryService(AssignmentRepository assignments,OrganizationSchedulingLock scheduling,
            AuditService audit,OperationEventPublisher events,Clock clock,jakarta.persistence.EntityManager entities) {
        this.assignments=assignments;this.scheduling=scheduling;this.audit=audit;
        this.events=events;this.clock=clock;this.entities=entities;
    }

    @Transactional
    public int expireForOrganization(UUID organizationId) {
        scheduling.acquire(organizationId);
        Instant now=Instant.now(clock);int count=0;
        for (var assignment:assignments.lockDueOffers(organizationId,now)) {
            entities.refresh(assignment,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (!assignment.expireIfDue(now)) continue;
            assignments.saveAndFlush(assignment);
            audit.recordSystem(assignment.getOrganization(),"ASSIGNMENT_RESPONSE_EXPIRED","ASSIGNMENT",assignment.getId(),
                Map.of("mode",assignment.getResponseMode().name(),"changes",pe.rutafija.audit.application.AuditChanges.fields("status",pe.rutafija.operation.domain.AssignmentStatus.PENDING_RESPONSE,assignment.getStatus())));
            events.publish(organizationId,assignment.getDriver().getGroup().getId(),"assignment.expired",
                Map.of("assignmentId",assignment.getId().toString(),"status",assignment.getStatus().name(),
                    "driverId",assignment.getDriver().getId().toString(),"vehicleId",assignment.getVehicle().getId().toString(),"version",assignment.getVersion()));
            count++;
        }
        return count;
    }
}
