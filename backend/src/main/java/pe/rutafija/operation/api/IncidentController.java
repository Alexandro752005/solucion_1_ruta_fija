package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.IncidentCreateRequest;
import pe.rutafija.operation.api.dto.IncidentFollowUpRequest;
import pe.rutafija.operation.api.dto.IncidentResponse;
import pe.rutafija.operation.application.IncidentService;
import pe.rutafija.operation.domain.IncidentCategory;
import pe.rutafija.operation.domain.IncidentStatus;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/incidents")
public class IncidentController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "reportedAt",
            "status",
            "category",
            "createdAt",
            "updatedAt"
    );

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar incidencias registradas en el CRM")
    public ResponseEntity<PageResponse<IncidentResponse>> list(
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) IncidentStatus status,
            @RequestParam(required = false) IncidentCategory category,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "reportedAt,desc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(incidentService.listIncidents(
                        driverId,
                        status,
                        category,
                        from,
                        to,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "reportedAt")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar una incidencia manualmente desde el CRM web")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody IncidentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(incidentService.createIncident(request));
    }

    @GetMapping("/{incidentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar una incidencia")
    public ResponseEntity<IncidentResponse> get(@PathVariable UUID incidentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(incidentService.getIncident(incidentId));
    }

    @PatchMapping("/{incidentId}/follow-up")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar seguimiento o resolver una incidencia")
    public ResponseEntity<IncidentResponse> followUp(
            @PathVariable UUID incidentId,
            @Valid @RequestBody IncidentFollowUpRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(incidentService.followUpIncident(incidentId, request));
    }
}
