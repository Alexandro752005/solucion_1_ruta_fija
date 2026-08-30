package pe.rutafija.audit.api.dto;

import pe.rutafija.audit.domain.AuditEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Vista de solo lectura de auditoría. Los valores sensibles nunca se exponen aquí. */
public record AuditEventResponse(
        UUID id,
        UUID userId,
        String userFullName,
        String action,
        String entityType,
        UUID entityId,
        String correlationId,
        Map<String, Object> metadata,
        Instant occurredAt
) {
    public static AuditEventResponse from(AuditEvent event, Map<String, Object> safeMetadata) {
        return new AuditEventResponse(
                event.getId(),
                event.getUserId(),
                event.getUserFullName(),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                event.getCorrelationId(),
                safeMetadata,
                event.getOccurredAt()
        );
    }
}
