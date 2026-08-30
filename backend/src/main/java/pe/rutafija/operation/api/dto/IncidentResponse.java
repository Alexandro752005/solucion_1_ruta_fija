package pe.rutafija.operation.api.dto;

import pe.rutafija.operation.domain.Incident;
import pe.rutafija.operation.domain.IncidentCategory;
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
        Instant createdAt,
        Instant updatedAt
) {
    public static IncidentResponse from(Incident incident) {
        return new IncidentResponse(
                incident.getId(),
                incident.getVersion(),
                incident.getDriver().getId(),
                incident.getDriver().getFullName(),
                incident.getAssignment() == null ? null : incident.getAssignment().getId(),
                incident.getCategory(),
                incident.getStatus(),
                incident.getDescription(),
                incident.getReportedBy().getId(),
                incident.getReportedBy().getFullName(),
                incident.getReportedAt(),
                incident.getFollowUpNote(),
                incident.getFollowedUpBy() == null ? null : incident.getFollowedUpBy().getId(),
                incident.getFollowedUpBy() == null ? null : incident.getFollowedUpBy().getFullName(),
                incident.getFollowedUpAt(),
                incident.getResolvedAt(),
                incident.getCreatedAt(),
                incident.getUpdatedAt()
        );
    }
}
