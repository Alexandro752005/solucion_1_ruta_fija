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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.fleet.api.dto.DriverCreateRequest;
import pe.rutafija.fleet.api.dto.DriverDetailResponse;
import pe.rutafija.fleet.api.dto.DriverResponse;
import pe.rutafija.fleet.api.dto.DriverUpdateRequest;
import pe.rutafija.fleet.application.FleetManagementService;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.operation.api.dto.DriverAvailabilityRequest;
import pe.rutafija.operation.application.OperationService;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/drivers")
public class DriverController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "fullName",
            "documentNumber",
            "createdAt",
            "updatedAt"
    );

    private final FleetManagementService fleetManagementService;
    private final OperationService operationService;

    public DriverController(FleetManagementService fleetManagementService, OperationService operationService) {
        this.fleetManagementService = fleetManagementService;
        this.operationService = operationService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar conductores con filtros")
    public ResponseEntity<PageResponse<DriverResponse>> list(
            @RequestParam(required = false) UUID groupId,
            @RequestParam(required = false) DriverAvailabilityStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "fullName,asc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.listDrivers(
                        groupId,
                        status,
                        search,
                        active,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "fullName")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Registrar conductor")
    public ResponseEntity<DriverResponse> create(@Valid @RequestBody DriverCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.createDriver(request));
    }

    @GetMapping("/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar conductor y sus vehículos vinculados")
    public ResponseEntity<DriverDetailResponse> get(@PathVariable UUID driverId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.getDriver(driverId));
    }

    @PatchMapping("/{driverId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar conductor")
    public ResponseEntity<DriverResponse> update(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverUpdateRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.updateDriver(driverId, request));
    }

    @PostMapping("/{driverId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar conductor")
    public ResponseEntity<DriverResponse> activate(@PathVariable UUID driverId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.activateDriver(driverId));
    }

    @PostMapping("/{driverId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar conductor")
    public ResponseEntity<DriverResponse> deactivate(@PathVariable UUID driverId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.deactivateDriver(driverId));
    }

    @PostMapping("/{driverId}/availability")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Cambiar disponibilidad administrativa del conductor")
    public ResponseEntity<DriverResponse> changeAvailability(
            @PathVariable UUID driverId,
            @Valid @RequestBody DriverAvailabilityRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(operationService.changeDriverAvailability(driverId, request));
    }

    @PostMapping("/{driverId}/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Vincular vehículo a conductor")
    public ResponseEntity<DriverDetailResponse> linkVehicle(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.linkVehicle(driverId, vehicleId));
    }

    @DeleteMapping("/{driverId}/vehicles/{vehicleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desvincular vehículo de conductor")
    public ResponseEntity<Void> unlinkVehicle(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId
    ) {
        fleetManagementService.unlinkVehicle(driverId, vehicleId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @PostMapping("/{driverId}/vehicles/{vehicleId}/primary")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Marcar vehículo principal del conductor")
    public ResponseEntity<DriverDetailResponse> markPrimaryVehicle(
            @PathVariable UUID driverId,
            @PathVariable UUID vehicleId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.markPrimaryVehicle(driverId, vehicleId));
    }
}
