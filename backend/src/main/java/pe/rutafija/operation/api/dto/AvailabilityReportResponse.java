package pe.rutafija.operation.api.dto;

import java.time.Instant;
import java.util.List;

public record AvailabilityReportResponse(
        Instant generatedAt,
        List<StatusCountResponse> drivers,
        List<StatusCountResponse> vehicles,
        long futureScheduledAssignments,
        long assignmentsInService,
        long openIncidents
) {
}
