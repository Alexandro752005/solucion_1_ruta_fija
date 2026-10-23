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
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.mobile.maintenance-enabled",havingValue="true",matchIfMissing=true)
public class MobileOperationMaintenanceService {

    private final AssignmentRepository assignmentRepository;
    private final DriverCurrentLocationStore currentLocationStore;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;
    private final OfferExpiryService expiry;
    private final ServiceGpsRetention gpsRetention;

    public MobileOperationMaintenanceService(
            AssignmentRepository assignmentRepository,
            DriverCurrentLocationStore currentLocationStore,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock,
            OfferExpiryService expiry,
            ServiceGpsRetention gpsRetention
    ) {
        this.assignmentRepository = assignmentRepository;
        this.currentLocationStore = currentLocationStore;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.expiry = expiry;
        this.gpsRetention = gpsRetention;
    }

    @Scheduled(fixedDelayString = "${app.mobile.pending-expiry-sweep:PT1M}")
    public void expireDueMobileResponsesAndLocations() {
        Instant now = Instant.now(clock);
        // One transaction per organization, always organization lock before rows.
        for (var organizationId : assignmentRepository.findOrganizationsWithDueOffers(now)) {
            expiry.expireForOrganization(organizationId);
        }
        currentLocationStore.deleteExpiredBefore(now);
        gpsRetention.purgeExpired();
    }
}
