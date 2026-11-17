package pe.rutafija.operation.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.operation.api.dto.StatusCountResponse;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.IncidentRepository;
import pe.rutafija.operation.infrastructure.OperationReportRepository;
import pe.rutafija.operation.infrastructure.StatusTotal;
import pe.rutafija.shared.security.CurrentUserService;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real report/export projection; persistence aggregates and authenticated actor are isolated doubles. */
class ReportStatusTotalsTest {
    private final CurrentUserService actor=mock(CurrentUserService.class);
    private final OperationReportRepository aggregates=mock(OperationReportRepository.class);
    private final AssignmentRepository assignments=mock(AssignmentRepository.class);
    private final ReportService reports=new ReportService(actor,aggregates,assignments,mock(IncidentRepository.class),Clock.systemUTC());
    private final LocalDate from=LocalDate.of(2026,11,1),to=from.plusDays(1);
    private final Organization organization=Organization.active("Synthetic totals","Synthetic","America/Lima");

    @BeforeEach void isolatedActorAndQuery() {
        when(actor.requireTenantActor()).thenReturn(AppUser.organizationUser(organization,"report@rutafija.test","SYNTHETIC-HASH","Synthetic",UserRole.ADMIN));
        when(assignments.search(eq(organization.getId()),isNull(),isNull(),isNull(),any(),any(),any())).thenReturn(Page.empty());
    }
    @Test void interruptionAndTransferCountsAreNotDroppedFromTheReport() {
        when(aggregates.assignmentStatusTotals(eq(organization.getId()),any(),any())).thenReturn(List.of(
                new StatusTotal("COMPLETED",1),new StatusTotal("INTERRUPTED",2),new StatusTotal("TRANSFERRED",3)));
        var result=reports.assignments(from,to);
        assertThat(result.totalsByStatus()).contains(new StatusCountResponse("INTERRUPTED",2),new StatusCountResponse("TRANSFERRED",3));
        assertThat(result.totalsByStatus().stream().mapToLong(StatusCountResponse::total).sum()).isEqualTo(6);
        verify(aggregates).assignmentStatusTotals(organization.getId(),from.atStartOfDay(ZoneId.of("America/Lima")).toInstant(),
                to.plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).toInstant());
    }
    @Test void zeroTotalsCoverAllPersistedStatesAndKeepExistingDisplayOrder() {
        when(aggregates.assignmentStatusTotals(eq(organization.getId()),any(),any())).thenReturn(List.of());
        var totals=reports.assignments(from,to).totalsByStatus();
        assertThat(totals).extracting(StatusCountResponse::status).containsExactly(
                "PENDING_RESPONSE","SCHEDULED","EN_SERVICIO","COMPLETED","REJECTED","CANCELLED","EXPIRED","INTERRUPTED","TRANSFERRED");
        assertThat(totals).extracting(StatusCountResponse::status).containsExactlyInAnyOrder(
                java.util.Arrays.stream(AssignmentStatus.values()).map(Enum::name).toArray(String[]::new));
        assertThat(totals).allMatch(count->count.total()==0);
    }
    @Test void exportedCsvRetainsTheSameInterruptionAndTransferTotals() {
        when(aggregates.assignmentStatusTotals(eq(organization.getId()),any(),any())).thenReturn(List.of(
                new StatusTotal("INTERRUPTED",2),new StatusTotal("TRANSFERRED",3)));
        var exported=new ReportExportService(reports).assignments(ReportExportFormat.CSV,from,to);
        assertThat(new String(exported.content(),StandardCharsets.UTF_8)).contains("INTERRUPTED","TRANSFERRED");
        assertThat(exported.filename()).isEqualTo("asignaciones-2026-11-01-2026-11-02.csv");
    }
}
