package pe.rutafija.audit.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.audit.api.dto.AuditEventResponse;
import pe.rutafija.audit.application.AuditQueryService;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** No contiene endpoints mutables: el CRM solo puede consultar la auditoría. */
@RestController
@Validated
@RequestMapping("/api/v1/audit-events")
public class AuditEventController {

    private static final Set<String> SORT_PROPERTIES = Set.of("occurredAt", "action", "entityType");

    private final AuditQueryService auditQueryService;

    public AuditEventController(AuditQueryService auditQueryService) {
        this.auditQueryService = auditQueryService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar eventos críticos de auditoría de la organización activa")
    public ResponseEntity<PageResponse<AuditEventResponse>> list(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(auditQueryService.list(
                        action,
                        entityType,
                        from,
                        to,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "occurredAt")
                ));
    }

    @GetMapping("/{auditEventId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar el detalle inmutable de un evento de auditoría")
    public ResponseEntity<AuditEventResponse> get(@PathVariable UUID auditEventId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(auditQueryService.get(auditEventId));
    }
}
