package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.AssignmentCancelRequest;
import pe.rutafija.operation.api.dto.AssignmentCreateRequest;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.AssignmentUpdateRequest;
import pe.rutafija.operation.api.dto.AssignmentVersionRequest;
import pe.rutafija.operation.application.AssignmentCreationResult;
import pe.rutafija.operation.application.OperationService;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/assignments")
@Tag(
        name = "CRM: asignaciones",
        description = "ADMIN opera el tenant. Puede crear una solicitud MOBILE_CONFIRMATION, pero no aceptar o rechazar por el conductor."
)
public class AssignmentController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "scheduledAt",
            "scheduledEndAt",
            "status",
            "createdAt",
            "updatedAt"
    );

    private final OperationService operationService;

    public AssignmentController(OperationService operationService) {
        this.operationService = operationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar asignaciones operativas")
    public ResponseEntity<PageResponse<AssignmentResponse>> list(
            @RequestParam(required = false) UUID driverId,
            @RequestParam(required = false) UUID vehicleId,
            @RequestParam(required = false) AssignmentStatus status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "scheduledAt,desc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.listAssignments(
                        driverId,
                        vehicleId,
                        status,
                        from,
                        to,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "scheduledAt")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(
            summary = "Crear una asignación ADMIN_DIRECT o una solicitud MOBILE_CONFIRMATION",
            description = "Si responseMode se omite se crea ADMIN_DIRECT en SCHEDULED. MOBILE_CONFIRMATION nace PENDING_RESPONSE "
                    + "y solo puede recibir respuesta desde /api/v1/mobile/assignments del conductor vinculado."
    )
    public ResponseEntity<AssignmentResponse> create(
            @Valid @RequestBody AssignmentCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        AssignmentCreationResult result = operationService.createAssignment(request, idempotencyKey);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .cacheControl(CacheControl.noStore())
                .body(result.assignment());
    }

    @GetMapping("/{assignmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar una asignación")
    public ResponseEntity<AssignmentResponse> get(@PathVariable UUID assignmentId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.getAssignment(assignmentId));
    }

    @PatchMapping("/{assignmentId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar una asignación SCHEDULED no reservada")
    public ResponseEntity<AssignmentResponse> update(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentUpdateRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.updateAssignment(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/reserve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Reservar el conductor para una asignación programada")
    public ResponseEntity<AssignmentResponse> reserve(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentVersionRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.reserveAssignment(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/start")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Iniciar una asignación reservada")
    public ResponseEntity<AssignmentResponse> start(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentVersionRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.startAssignment(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Completar una asignación en servicio")
    public ResponseEntity<AssignmentResponse> complete(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentVersionRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.completeAssignment(assignmentId, request));
    }

    @PostMapping("/{assignmentId}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cancelar una asignación")
    public ResponseEntity<AssignmentResponse> cancel(
            @PathVariable UUID assignmentId,
            @Valid @RequestBody AssignmentCancelRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.cancelAssignment(assignmentId, request));
    }
}
