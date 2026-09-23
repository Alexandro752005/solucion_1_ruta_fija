package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
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
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.mobile.MobileAssignmentCommandRequest;
import pe.rutafija.operation.api.dto.mobile.MobileAssignmentRejectRequest;
import pe.rutafija.operation.application.MobileAssignmentService;
import pe.rutafija.operation.application.MobileCommandExecution;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile/assignments")
public class MobileAssignmentController {

    private static final Set<String> SORT_PROPERTIES = Set.of("scheduledAt", "scheduledEndAt", "createdAt", "updatedAt", "status");

    private final MobileAssignmentService mobileAssignmentService;

    public MobileAssignmentController(MobileAssignmentService mobileAssignmentService) {
        this.mobileAssignmentService = mobileAssignmentService;
    }

    @GetMapping
    @Operation(summary = "Listar exclusivamente las asignaciones propias")
    public ResponseEntity<PageResponse<AssignmentResponse>> list(
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "scheduledAt,asc") String sort
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileAssignmentService.list(
                status,
                PageableFactory.create(page, size, sort, SORT_PROPERTIES, "scheduledAt")
        ));
    }

    @GetMapping("/{assignmentId}")
    @Operation(summary = "Consultar una asignacion propia")
    public ResponseEntity<AssignmentResponse> get(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileAssignmentService.get(assignmentId));
    }

    @PostMapping("/{assignmentId}/accept")
    @Operation(summary = "Aceptar una solicitud MOBILE_CONFIRMATION propia")
    public ResponseEntity<AssignmentResponse> accept(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody MobileAssignmentCommandRequest request
    ) {
        return commandResponse(mobileAssignmentService.accept(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/reject")
    @Operation(summary = "Rechazar una solicitud MOBILE_CONFIRMATION propia")
    public ResponseEntity<AssignmentResponse> reject(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody MobileAssignmentRejectRequest request
    ) {
        return commandResponse(mobileAssignmentService.reject(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/start")
    @Operation(summary = "Iniciar una asignacion propia previamente reservada")
    public ResponseEntity<AssignmentResponse> start(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody MobileAssignmentCommandRequest request
    ) {
        return commandResponse(mobileAssignmentService.start(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/complete")
    @Operation(summary = "Completar una asignacion propia en servicio")
    public ResponseEntity<AssignmentResponse> complete(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody MobileAssignmentCommandRequest request
    ) {
        return commandResponse(mobileAssignmentService.complete(assignmentId, request));
    }

    private ResponseEntity<AssignmentResponse> commandResponse(MobileCommandExecution<AssignmentResponse> result) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Idempotent-Replay", Boolean.toString(result.replayed()))
                .body(result.value());
    }
}
