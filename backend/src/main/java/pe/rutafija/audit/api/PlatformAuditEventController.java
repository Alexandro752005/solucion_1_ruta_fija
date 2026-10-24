package pe.rutafija.audit.api;

import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.audit.api.dto.AuditEventResponse;
import pe.rutafija.audit.application.AuditQueryService;
import pe.rutafija.shared.api.*;
import java.time.Instant;
import java.util.*;

/** Read-only platform administration, without any tenant-selection parameter. */
@RestController @Validated @RequestMapping("/api/v1/platform/audit-events")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class PlatformAuditEventController {
    private final AuditQueryService service;
    public PlatformAuditEventController(AuditQueryService service){this.service=service;}
    @GetMapping public ResponseEntity<PageResponse<AuditEventResponse>> list(
        @RequestParam(required=false) String action,@RequestParam(required=false) String entityType,
        @RequestParam(required=false) Instant from,@RequestParam(required=false) Instant to,
        @RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue="occurredAt,desc") String sort){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.platformList(action,entityType,from,to,PageableFactory.create(page,size,sort,Set.of("occurredAt","action","entityType"),"occurredAt")));
    }
    @GetMapping("/{id}") public ResponseEntity<AuditEventResponse> get(@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.platformGet(id));}
}
