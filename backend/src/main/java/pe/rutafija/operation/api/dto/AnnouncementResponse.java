package pe.rutafija.operation.api.dto;

import pe.rutafija.operation.domain.Announcement;
import pe.rutafija.operation.domain.AnnouncementAudienceType;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String body,
        AnnouncementAudienceType audienceType,
        UUID audienceId,
        boolean requireReadAck,
        UUID createdById,
        String createdByName,
        Instant createdAt
) {
    public static AnnouncementResponse from(Announcement announcement) {
        return new AnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.getAudienceType(),
                announcement.getAudienceId(),
                announcement.isRequireReadAck(),
                announcement.getCreatedBy().getId(),
                announcement.getCreatedBy().getFullName(),
                announcement.getCreatedAt()
        );
    }
}
