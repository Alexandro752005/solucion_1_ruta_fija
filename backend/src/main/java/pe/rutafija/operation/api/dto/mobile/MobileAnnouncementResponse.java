package pe.rutafija.operation.api.dto.mobile;

import pe.rutafija.operation.domain.Announcement;

import java.time.Instant;
import java.util.UUID;

/** Driver-safe announcement view, enriched with only this user's receipt timestamps. */
public record MobileAnnouncementResponse(
        UUID id,
        String title,
        String body,
        boolean requireReadAck,
        Instant createdAt,
        Instant deliveredAt,
        Instant readAt
) {
    public static MobileAnnouncementResponse from(
            Announcement announcement,
            Instant deliveredAt,
            Instant readAt
    ) {
        return new MobileAnnouncementResponse(
                announcement.getId(),
                announcement.getTitle(),
                announcement.getBody(),
                announcement.isRequireReadAck(),
                announcement.getCreatedAt(),
                deliveredAt,
                readAt
        );
    }
}
