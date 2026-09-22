package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.AssignmentReportResponse;
import pe.rutafija.operation.api.dto.AvailabilityReportResponse;
import pe.rutafija.operation.api.dto.IncidentReportResponse;
import pe.rutafija.operation.application.ReportExport;
import pe.rutafija.operation.application.ReportExportFormat;
import pe.rutafija.operation.application.ReportExportService;
import pe.rutafija.operation.application.ReportService;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;
    private final ReportExportService reportExportService;

    public ReportController(ReportService reportService, ReportExportService reportExportService) {
        this.reportService = reportService;
        this.reportExportService = reportExportService;
    }

    @GetMapping("/availability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Resumen de disponibilidad actual basado en datos persistidos")
    public ResponseEntity<AvailabilityReportResponse> availability() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reportService.availability());
    }

    @GetMapping("/assignments")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reporte real de asignaciones por rango de fechas")
    public ResponseEntity<AssignmentReportResponse> assignments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reportService.assignments(from, to));
    }

    @GetMapping("/availability/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar el reporte real de disponibilidad en PDF o XLSX")
    public ResponseEntity<byte[]> exportAvailability(@RequestParam(defaultValue = "pdf") String format) {
        return file(reportExportService.availability(ReportExportFormat.from(format)));
    }

    @GetMapping("/assignments/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar el reporte real de asignaciones en PDF o XLSX")
    public ResponseEntity<byte[]> exportAssignments(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "xlsx") String format
    ) {
        return file(reportExportService.assignments(ReportExportFormat.from(format), from, to));
    }

    @GetMapping("/incidents")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reporte real de incidencias por rango de fechas")
    public ResponseEntity<IncidentReportResponse> incidents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reportService.incidents(from, to));
    }

    @GetMapping("/incidents/export")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Exportar el reporte real de incidencias en PDF o XLSX")
    public ResponseEntity<byte[]> exportIncidents(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "pdf") String format
    ) {
        return file(reportExportService.incidents(ReportExportFormat.from(format), from, to));
    }

    private ResponseEntity<byte[]> file(ReportExport export) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(export.contentType())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename(), StandardCharsets.UTF_8).build().toString()
                )
                .body(export.content());
    }
}
