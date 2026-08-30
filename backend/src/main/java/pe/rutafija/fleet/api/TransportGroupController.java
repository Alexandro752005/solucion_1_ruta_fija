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
import pe.rutafija.fleet.api.dto.DriverResponse;
import pe.rutafija.fleet.api.dto.GroupCoordinatorRequest;
import pe.rutafija.fleet.api.dto.TransportGroupCreateRequest;
import pe.rutafija.fleet.api.dto.TransportGroupResponse;
import pe.rutafija.fleet.api.dto.TransportGroupUpdateRequest;
import pe.rutafija.fleet.application.FleetManagementService;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/groups")
public class TransportGroupController {

    private static final Set<String> SORT_PROPERTIES = Set.of("name", "createdAt", "updatedAt");

    private final FleetManagementService fleetManagementService;

    public TransportGroupController(FleetManagementService fleetManagementService) {
        this.fleetManagementService = fleetManagementService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDINADOR')")
    @Operation(summary = "Listar grupos visibles para la organización y el rol")
    public ResponseEntity<PageResponse<TransportGroupResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "name,asc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.listGroups(
                        search,
                        active,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "name")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Crear grupo operativo")
    public ResponseEntity<TransportGroupResponse> create(
            @Valid @RequestBody TransportGroupCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.createGroup(request));
    }

    @PatchMapping("/{groupId}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Editar o desactivar grupo operativo")
    public ResponseEntity<TransportGroupResponse> update(
            @PathVariable UUID groupId,
            @Valid @RequestBody TransportGroupUpdateRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.updateGroup(groupId, request));
    }

    @PostMapping("/{groupId}/coordinators")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Asignar un coordinador a un grupo")
    public ResponseEntity<TransportGroupResponse> assignCoordinator(
            @PathVariable UUID groupId,
            @Valid @RequestBody GroupCoordinatorRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.assignCoordinator(groupId, request));
    }

    @DeleteMapping("/{groupId}/coordinators/{userId}")
    @PreAuthorize("hasRole('ADMINISTRADOR')")
    @Operation(summary = "Retirar un coordinador de un grupo")
    public ResponseEntity<Void> removeCoordinator(
            @PathVariable UUID groupId,
            @PathVariable UUID userId
    ) {
        fleetManagementService.removeCoordinator(groupId, userId);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/{groupId}/drivers")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR', 'COORDINADOR')")
    @Operation(summary = "Listar conductores de un grupo visible")
    public ResponseEntity<PageResponse<DriverResponse>> listDrivers(
            @PathVariable UUID groupId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "fullName,asc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(fleetManagementService.listGroupDrivers(
                        groupId,
                        PageableFactory.create(
                                page,
                                size,
                                sort,
                                Set.of("fullName", "documentNumber", "createdAt", "updatedAt"),
                                "fullName"
                        )
                ));
    }
}
