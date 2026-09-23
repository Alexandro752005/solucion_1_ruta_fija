package pe.rutafija.operation.api.dto.mobile;

import java.time.Instant;
import java.util.UUID;

public record MobileAnnouncementReadResponse(UUID announcementId, Instant deliveredAt, Instant readAt) {
}
