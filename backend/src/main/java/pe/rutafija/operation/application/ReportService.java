package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.AssignmentReportResponse;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.AvailabilityReportResponse;
import pe.rutafija.operation.api.dto.IncidentReportResponse;
import pe.rutafija.operation.api.dto.IncidentResponse;
import pe.rutafija.operation.api.dto.StatusCountResponse;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.Incident;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.IncidentRepository;
import pe.rutafija.operation.infrastructure.OperationReportRepository;
import pe.rutafija.operation.infrastructure.StatusTotal;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Reportes obtenidos exclusivamente de asignaciones e incidencias persistidas. */
@Service
public class ReportService {

    private static final int MAX_RANGE_DAYS = 90;
    private static final int MAX_ITEMS = 10_000;

    private final CurrentUserService currentUserService;
    private final OperationReportRepository reportRepository;
    private final AssignmentRepository assignmentRepository;
    private final IncidentRepository incidentRepository;
    private final Clock clock;

    public ReportService(
            CurrentUserService currentUserService,
            OperationReportRepository reportRepository,
            AssignmentRepository assignmentRepository,
            IncidentRepository incidentRepository,
            Clock clock
    ) {
        this.currentUserService = currentUserService;
        this.reportRepository = reportRepository;
        this.assignmentRepository = assignmentRepository;
        this.incidentRepository = incidentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AvailabilityReportResponse availability() {
        AppUser actor = requireAdmin();
        return new AvailabilityReportResponse(
                Instant.now(clock),
                toResponse(reportRepository.driverAvailability(actor.getOrganizationId())),
                toResponse(reportRepository.vehicleAvailability(actor.getOrganizationId())),
                reportRepository.futureScheduledAssignments(actor.getOrganizationId(), Instant.now(clock)),
                reportRepository.assignmentsInService(actor.getOrganizationId()),
                reportRepository.openIncidents(actor.getOrganizationId())
        );
    }

    @Transactional(readOnly = true)
    public AssignmentReportResponse assignments(LocalDate from, LocalDate to) {
        AppUser actor = requireAdmin();
        ReportRange range = validateRange(actor, from, to);
        Page<Assignment> page = assignmentRepository.search(
                actor.getOrganizationId(),
                null,
                null,
                null,
                range.fromInclusive(),
                range.toExclusive(),
                PageRequest.of(0, MAX_ITEMS, Sort.by(Sort.Direction.ASC, "scheduledAt"))
        );
        assertResultSize(page.getTotalElements());
        return new AssignmentReportResponse(
                from,
                to,
                toResponse(reportRepository.assignmentStatusTotals(
                        actor.getOrganizationId(), range.fromInclusive(), range.toExclusive()
                )),
                page.getContent().stream().map(AssignmentResponse::from).toList()
        );
    }

    @Transactional(readOnly = true)
    public IncidentReportResponse incidents(LocalDate from, LocalDate to) {
        AppUser actor = requireAdmin();
        ReportRange range = validateRange(actor, from, to);
        Page<Incident> page = incidentRepository.search(
                actor.getOrganizationId(),
                null,
                null,
                null,
                range.fromInclusive(),
                range.toExclusive(),
                PageRequest.of(0, MAX_ITEMS, Sort.by(Sort.Direction.ASC, "reportedAt"))
        );
        assertResultSize(page.getTotalElements());
        return new IncidentReportResponse(
                from,
                to,
                toResponse(reportRepository.incidentStatusTotals(
                        actor.getOrganizationId(), range.fromInclusive(), range.toExclusive()
                )),
                toResponse(reportRepository.incidentCategoryTotals(
                        actor.getOrganizationId(), range.fromInclusive(), range.toExclusive()
                )),
                page.getContent().stream().map(IncidentResponse::from).toList()
        );
    }

    private AppUser requireAdmin() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMIN) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "Los reportes operativos requieren el rol ADMIN"
            );
        }
        return actor;
    }

    private ReportRange validateRange(AppUser actor, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.REPORT_RANGE_REQUIRED,
                    "Debe indicar un rango de fechas válido"
            );
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1L;
        if (days > MAX_RANGE_DAYS) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.REPORT_RANGE_REQUIRED,
                    "El rango máximo para un reporte es de 90 días"
            );
        }
        ZoneId zone = ZoneId.of(actor.getOrganization().getTimezone());
        return new ReportRange(
                from.atStartOfDay(zone).toInstant(),
                to.plusDays(1).atStartOfDay(zone).toInstant()
        );
    }

    private void assertResultSize(long totalElements) {
        if (totalElements > MAX_ITEMS) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.REPORT_RESULT_LIMIT_EXCEEDED,
                    "El reporte supera 10 000 registros; reduzca el rango de fechas"
            );
        }
    }

    private List<StatusCountResponse> toResponse(List<StatusTotal> totals) {
        return totals.stream().map(total -> new StatusCountResponse(total.status(), total.total())).toList();
    }

    private record ReportRange(Instant fromInclusive, Instant toExclusive) {
    }
}
