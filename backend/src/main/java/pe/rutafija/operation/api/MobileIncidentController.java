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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.IncidentResponse;
import pe.rutafija.operation.api.dto.mobile.MobileIncidentCreateRequest;
import pe.rutafija.operation.application.MobileIncidentService;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile/incidents")
public class MobileIncidentController {

    private static final Set<String> SORT_PROPERTIES = Set.of("reportedAt", "createdAt", "updatedAt", "status");

    private final MobileIncidentService mobileIncidentService;

    public MobileIncidentController(MobileIncidentService mobileIncidentService) {
        this.mobileIncidentService = mobileIncidentService;
    }

    @GetMapping
    @Operation(summary = "Listar incidencias propias")
    public ResponseEntity<PageResponse<IncidentResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "reportedAt,desc") String sort
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileIncidentService.list(
                PageableFactory.create(page, size, sort, SORT_PROPERTIES, "reportedAt")
        ));
    }

    @GetMapping("/{incidentId}")
    @Operation(summary = "Consultar una incidencia propia")
    public ResponseEntity<IncidentResponse> get(@PathVariable UUID incidentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileIncidentService.get(incidentId));
    }

    @PostMapping
    @Operation(summary = "Reportar una incidencia como conductor autenticado")
    public ResponseEntity<IncidentResponse> create(@Valid @RequestBody MobileIncidentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(mobileIncidentService.create(request));
    }
}
