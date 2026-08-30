package pe.rutafija.fleet.api;

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
import pe.rutafija.fleet.api.dto.VehicleCreateRequest;
import pe.rutafija.fleet.api.dto.VehicleResponse;
import pe.rutafija.fleet.api.dto.VehicleStatusRequest;
import pe.rutafija.fleet.api.dto.VehicleUpdateRequest;
import pe.rutafija.fleet.application.FleetManagementService;
import pe.rutafija.fleet.domain.VehicleStatus;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/vehicles")
public class VehicleController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "plate",
            "status",
            "createdAt",
            "updatedAt"
    );

    private final FleetManagementService fleetManagementService;

    public VehicleController(FleetManagementService fleetManagementService) {
        this.fleetManagementService = fleetManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDINADOR')")
    @Operation(summary = "Listar vehículos con filtros")
    public ResponseEntity<PageResponse<VehicleResponse>> list(
            @RequestParam(required = false) VehicleStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "plate,asc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.listVehicles(
                        status,
                        search,
                        active,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "plate")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Registrar vehículo")
    public ResponseEntity<VehicleResponse> create(@Valid @RequestBody VehicleCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.createVehicle(request));
    }

    @GetMapping("/{vehicleId}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDINADOR')")
    @Operation(summary = "Consultar vehículo")
    public ResponseEntity<VehicleResponse> get(@PathVariable UUID vehicleId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.getVehicle(vehicleId));
    }

    @PatchMapping("/{vehicleId}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Editar vehículo")
    public ResponseEntity<VehicleResponse> update(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleUpdateRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.updateVehicle(vehicleId, request));
    }

    @PostMapping("/{vehicleId}/status")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Cambiar estado administrativo válido del vehículo")
    public ResponseEntity<VehicleResponse> changeStatus(
            @PathVariable UUID vehicleId,
            @Valid @RequestBody VehicleStatusRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.changeVehicleStatus(vehicleId, request));
    }
}
