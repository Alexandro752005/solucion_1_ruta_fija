package pe.rutafija.operation.api.dto;

import java.time.LocalDate;
import java.util.List;

public record AssignmentReportResponse(
        LocalDate from,
        LocalDate to,
        List<StatusCountResponse> totalsByStatus,
        List<AssignmentResponse> items
) {
}
