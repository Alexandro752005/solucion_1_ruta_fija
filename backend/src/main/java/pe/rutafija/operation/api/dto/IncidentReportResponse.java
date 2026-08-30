package pe.rutafija.operation.api.dto;

import java.time.LocalDate;
import java.util.List;

public record IncidentReportResponse(
        LocalDate from,
        LocalDate to,
        List<StatusCountResponse> totalsByStatus,
        List<StatusCountResponse> totalsByCategory,
        List<IncidentResponse> items
) {
}
