package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.operation.api.dto.mobile.MobileAnnouncementReadResponse;
import pe.rutafija.operation.api.dto.mobile.MobileAnnouncementResponse;
import pe.rutafija.operation.domain.Announcement;
import pe.rutafija.operation.infrastructure.AnnouncementReceiptStore;
import pe.rutafija.operation.infrastructure.AnnouncementRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.MobileDriverActor;
import pe.rutafija.shared.security.MobileDriverContextService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Announcement visibility follows organization/group audience before a receipt is created. */
@Service
public class MobileAnnouncementService {

    private final MobileDriverContextService mobileDriverContextService;
    private final AnnouncementRepository announcementRepository;
    private final AnnouncementReceiptStore receiptStore;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public MobileAnnouncementService(
            MobileDriverContextService mobileDriverContextService,
            AnnouncementRepository announcementRepository,
            AnnouncementReceiptStore receiptStore,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.announcementRepository = announcementRepository;
        this.receiptStore = receiptStore;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public PageResponse<MobileAnnouncementResponse> list(Pageable pageable) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Page<Announcement> announcements = announcementRepository.findVisibleToDriver(
                actor.user().getOrganizationId(), actor.driver().getGroup().getId(), pageable
        );
        Instant now = Instant.now(clock);
        return PageResponse.from(announcements, announcement -> deliveredResponse(announcement, actor, now));
    }

    @Transactional
    public MobileAnnouncementResponse get(UUID announcementId) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Announcement announcement = findVisible(actor, announcementId);
        return deliveredResponse(announcement, actor, Instant.now(clock));
    }

    @Transactional
    public MobileAnnouncementReadResponse markRead(UUID announcementId) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Announcement announcement = findVisible(actor, announcementId);
        Instant now = Instant.now(clock);
        AnnouncementReceiptStore.Receipt before = receiptStore.find(announcement.getId(), actor.user().getId())
                .orElse(null);
        AnnouncementReceiptStore.Receipt receipt = receiptStore.recordRead(
                announcement.getId(), actor.user().getId(), now
        );
        if (before == null || before.readAt() == null) {
            auditService.record(actor.user(), "MOBILE_ANNOUNCEMENT_READ", "ANNOUNCEMENT", announcement.getId(), Map.of());
            eventPublisher.publish(
                    actor.user().getOrganizationId(),
                    actor.driver().getGroup().getId(),
                    "announcement.read",
                    Map.of(
                            "announcementId", announcement.getId().toString(),
                            "driverId", actor.driver().getId().toString()
                    )
            );
        }
        return new MobileAnnouncementReadResponse(announcement.getId(), receipt.deliveredAt(), receipt.readAt());
    }

    private MobileAnnouncementResponse deliveredResponse(
            Announcement announcement,
            MobileDriverActor actor,
            Instant now
    ) {
        receiptStore.recordDelivery(announcement.getId(), actor.user().getId(), now);
        AnnouncementReceiptStore.Receipt receipt = receiptStore.find(announcement.getId(), actor.user().getId())
                .orElseThrow();
        return MobileAnnouncementResponse.from(announcement, receipt.deliveredAt(), receipt.readAt());
    }

    private Announcement findVisible(MobileDriverActor actor, UUID announcementId) {
        return announcementRepository.findVisibleById(
                        announcementId,
                        actor.user().getOrganizationId(),
                        actor.driver().getGroup().getId()
                )
                .orElseThrow(this::notFound);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe"
        );
    }
}
