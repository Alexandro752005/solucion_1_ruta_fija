package pe.rutafija.operation.api.dto;

import pe.rutafija.operation.domain.Incident;
import pe.rutafija.operation.domain.IncidentCategory;
import pe.rutafija.operation.domain.IncidentSource;
import pe.rutafija.operation.domain.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        long version,
        UUID driverId,
        String driverName,
        UUID assignmentId,
        IncidentCategory category,
        IncidentSource source,
        IncidentStatus status,
        String description,
        UUID reportedById,
        String reportedByName,
        Instant reportedAt,
        String followUpNote,
        UUID followedUpById,
        String followedUpByName,
        Instant followedUpAt,
        Instant resolvedAt,
        UUID driverClosedById,
        String driverClosedByName,
        Instant driverClosedAt,
        Instant createdAt,
        Instant updatedAt,
        UUID executionSegmentId,
        pe.rutafija.operation.domain.IncidentSeverity severity,
        UUID responsibleAdminId,
        UUID previousIncidentId,
        String relationReason,
        String operationalEffect
) {
    public static IncidentResponse from(Incident incident) {
        return from(incident,false);
    }
    public static IncidentResponse from(Incident incident,boolean interruptionRecorded) {
        return new IncidentResponse(
                incident.getId(),
                incident.getVersion(),
                incident.getDriver().getId(),
                pe.rutafija.shared.security.PersonalDataView.driver(incident.getDriver().getId()),
                incident.getAssignment() == null ? null : incident.getAssignment().getId(),
                incident.getCategory(),
                incident.getSource(),
                incident.getStatus(),
                incident.getDescription(),
                incident.getReportedBy() == null ? null : incident.getReportedBy().getId(),
                incident.getSource() == IncidentSource.SYSTEM ? "Sistema" : pe.rutafija.shared.security.PersonalDataView.account(incident.getReportedBy().getId()),
                incident.getReportedAt(),
                incident.getFollowUpNote(),
                incident.getFollowedUpBy() == null ? null : incident.getFollowedUpBy().getId(),
                incident.getFollowedUpBy() == null ? null : pe.rutafija.shared.security.PersonalDataView.account(incident.getFollowedUpBy().getId()),
                incident.getFollowedUpAt(),
                incident.getResolvedAt(),
                incident.getDriverClosedBy() == null ? null : incident.getDriverClosedBy().getId(),
                incident.getDriverClosedBy() == null ? null : pe.rutafija.shared.security.PersonalDataView.account(incident.getDriverClosedBy().getId()),
                incident.getDriverClosedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt(),incident.getExecutionSegmentId(),incident.getSeverity(),
                incident.getResponsibleAdmin()==null?null:incident.getResponsibleAdmin().getId(),
                incident.getPreviousIncidentId(),incident.getRelationReason(),
                interruptionRecorded?"INTERRUPTION_RECORDED":"NO_INTERRUPTION_RECORDED"
        );
    }
}
