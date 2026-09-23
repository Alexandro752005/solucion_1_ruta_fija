package pe.rutafija.operation.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.DriverCurrentLocationStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Server-driven expiry and privacy cleanup; no client can mark itself expired. */
@Service
public class MobileOperationMaintenanceService {

    private final AssignmentRepository assignmentRepository;
    private final DriverCurrentLocationStore currentLocationStore;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public MobileOperationMaintenanceService(
            AssignmentRepository assignmentRepository,
            DriverCurrentLocationStore currentLocationStore,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.assignmentRepository = assignmentRepository;
        this.currentLocationStore = currentLocationStore;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.mobile.pending-expiry-sweep:PT1M}")
    @Transactional
    public void expireDueMobileResponsesAndLocations() {
        Instant now = Instant.now(clock);
        List<Assignment> dueAssignments = assignmentRepository.findByStatusAndResponseDeadlineAtLessThanEqual(
                AssignmentStatus.PENDING_RESPONSE, now
        );
        for (Assignment assignment : dueAssignments) {
            if (assignment.expireIfDue(now)) {
                assignmentRepository.save(assignment);
                auditService.recordSystem(
                        assignment.getOrganization(),
                        "ASSIGNMENT_RESPONSE_EXPIRED",
                        "ASSIGNMENT",
                        assignment.getId(),
                        Map.of("responseMode", assignment.getResponseMode().name())
                );
                eventPublisher.publish(
                        assignment.getOrganizationId(),
                        assignment.getDriver().getGroup().getId(),
                        "assignment.expired",
                        Map.of(
                                "assignmentId", assignment.getId().toString(),
                                "status", assignment.getStatus().name(),
                                "driverId", assignment.getDriver().getId().toString(),
                                "vehicleId", assignment.getVehicle().getId().toString(),
                                "version", assignment.getVersion()
                        )
                );
            }
        }
        currentLocationStore.deleteExpiredBefore(now);
    }
}
