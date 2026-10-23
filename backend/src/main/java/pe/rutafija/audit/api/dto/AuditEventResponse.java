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
        Instant occurredAt,
        UUID organizationId,
        String scope,
        String outcome
) {
    public static AuditEventResponse from(AuditEvent event, Map<String, Object> safeMetadata) {
        return new AuditEventResponse(
                event.getId(),
                event.getUserId(),
                event.getUserId() == null ? null : pe.rutafija.shared.security.PersonalDataView.account(event.getUserId()),
                event.getAction(),
                event.getEntityType(),
                event.getEntityId(),
                pe.rutafija.audit.application.AuditCorrelationId.safe(event.getCorrelationId()),
                safeMetadata,
                event.getOccurredAt(),
                event.getOrganizationId(),
                event.getOrganizationId()==null?"PLATFORM":"ORGANIZATION",
                safeMetadata.get("auditOutcome") instanceof String value&&java.util.Set.of("SUCCEEDED","DENIED","FAILED").contains(value)?value:"LEGACY_RECORDED"
        );
    }
}
