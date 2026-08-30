package pe.rutafija.organization.api;

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
import pe.rutafija.organization.api.dto.OrganizationCreateRequest;
import pe.rutafija.organization.api.dto.OrganizationResponse;
import pe.rutafija.organization.api.dto.OrganizationUpdateRequest;
import pe.rutafija.organization.application.OrganizationManagementService;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "legalName",
            "createdAt",
            "updatedAt",
            "status"
    );

    private final OrganizationManagementService organizationManagementService;

    public OrganizationController(OrganizationManagementService organizationManagementService) {
        this.organizationManagementService = organizationManagementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Listar organizaciones")
    public ResponseEntity<PageResponse<OrganizationResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "legalName,asc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(organizationManagementService.list(
                        search,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "legalName")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Crear organización")
    public ResponseEntity<OrganizationResponse> create(@Valid @RequestBody OrganizationCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(organizationManagementService.create(request));
    }

    @PatchMapping("/{organizationId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Actualizar datos básicos o estado de una organización")
    public ResponseEntity<OrganizationResponse> update(
            @PathVariable UUID organizationId,
            @Valid @RequestBody OrganizationUpdateRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(organizationManagementService.update(organizationId, request));
    }
}
