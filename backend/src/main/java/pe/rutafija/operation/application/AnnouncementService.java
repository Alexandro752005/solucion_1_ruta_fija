package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.AnnouncementCreateRequest;
import pe.rutafija.operation.api.dto.AnnouncementResponse;
import pe.rutafija.operation.domain.Announcement;
import pe.rutafija.operation.domain.AnnouncementAudienceType;
import pe.rutafija.operation.infrastructure.AnnouncementRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.util.Map;
import java.util.UUID;

/** Comunicados web, sin acuse de lectura porque no existe portal móvil. */
@Service
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;
    private final TransportGroupRepository groupRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;

    public AnnouncementService(
            AnnouncementRepository announcementRepository,
            TransportGroupRepository groupRepository,
            CurrentUserService currentUserService,
            AuditService auditService,
            OperationEventPublisher eventPublisher
    ) {
        this.announcementRepository = announcementRepository;
        this.groupRepository = groupRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PageResponse<AnnouncementResponse> listAnnouncements(
            AnnouncementAudienceType audienceType,
            Pageable pageable
    ) {
        AppUser actor = requireOperationalActor();
        Page<Announcement> announcements = announcementRepository.search(
                actor.getOrganizationId(), audienceType, pageable
        );
        return PageResponse.from(announcements, AnnouncementResponse::from);
    }

    @Transactional
    public AnnouncementResponse createAnnouncement(AnnouncementCreateRequest request) {
        AppUser actor = requireOperationalActor();
        if (Boolean.TRUE.equals(request.requireReadAck())) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "El acuse de lectura requiere una aplicación móvil y no está disponible en el CRM web"
            );
        }

        UUID audienceId = resolveAudience(actor, request.audienceType(), request.audienceId());
        Announcement announcement = announcementRepository.saveAndFlush(Announcement.publish(
                actor.getOrganization(),
                actor,
                request.title(),
                request.body(),
                request.audienceType(),
                audienceId
        ));
        auditService.record(actor, "ANNOUNCEMENT_PUBLISHED", "ANNOUNCEMENT", announcement.getId(), Map.of(
                "audienceType", announcement.getAudienceType().name()
        ));
        eventPublisher.publish(actor.getOrganizationId(), announcement.getAudienceId(), "announcement.published", Map.of(
                "announcementId", announcement.getId().toString(),
                "audienceType", announcement.getAudienceType().name()
        ));
        return AnnouncementResponse.from(announcement);
    }

    private AppUser requireOperationalActor() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La operación web requiere el rol ADMIN"
            );
        }
        return actor;
    }

    private UUID resolveAudience(AppUser actor, AnnouncementAudienceType audienceType, UUID requestedAudienceId) {
        if (audienceType == AnnouncementAudienceType.ORGANIZATION) {
            if (requestedAudienceId != null) {
                throw validation("Un comunicado para la organización no debe incluir audienceId");
            }
            return null;
        }
        if (audienceType != AnnouncementAudienceType.GROUP || requestedAudienceId == null) {
            throw validation("Un comunicado para un grupo requiere audienceId");
        }
        TransportGroup group = groupRepository.findByIdAndOrganization_Id(requestedAudienceId, actor.getOrganizationId())
                .orElseThrow(this::notFound);
        if (!group.isActive()) {
            throw validation("No puede publicar un comunicado para un grupo inactivo");
        }
        return group.getId();
    }

    private ApplicationException validation(String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe"
        );
    }
}
