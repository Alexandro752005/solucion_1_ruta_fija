package pe.rutafija.reporting.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.application.ReportExportFormat;
import pe.rutafija.reporting.application.*;
import pe.rutafija.reporting.api.ServiceReportContract.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;

@RestController
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.reporting.enabled",havingValue="true",matchIfMissing=true)
@RequestMapping({"/api/v1/reports/services","/api/v1/mobile/reports/services"})
@PreAuthorize("hasAnyRole('ADMIN','CONDUCTOR')")
public class ServiceReportController {
    private final ServiceReportService reports;
    public ServiceReportController(ServiceReportService reports){this.reports=reports;}
    private boolean own(HttpServletRequest r){return r.getRequestURI().startsWith("/api/v1/mobile/");}
    @GetMapping public ResponseEntity<View> current(HttpServletRequest r,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to){return ok(reports.current(own(r),from,to));}
    @PostMapping("/emissions") public ResponseEntity<Emission> issue(HttpServletRequest r,@Valid @RequestBody Issue c){return ok(reports.issue(own(r),c));}
    @GetMapping("/emissions") public ResponseEntity<List<Summary>> list(HttpServletRequest r,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to){return ok(reports.list(own(r),from,to));}
    @GetMapping("/emissions/{id}") public ResponseEntity<Emission> read(HttpServletRequest r,@PathVariable UUID id){return ok(reports.read(own(r),id));}
    @GetMapping("/emissions/{id}/export") public ResponseEntity<byte[]> export(HttpServletRequest r,@PathVariable UUID id,@RequestParam(defaultValue="csv")String format){
        var download=reports.export(own(r),id,ReportExportFormat.from(format));var emission=download.emission();var f=download.file();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(f.contentType())
            .header("X-Report-Content-SHA256",emission.contentSha256()).header("X-Report-Emission-Id",emission.id().toString())
            .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(f.filename(),StandardCharsets.UTF_8).build().toString()).body(f.content());
    }
    private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
