package pe.rutafija.identity.api;

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
import pe.rutafija.identity.api.dto.UserCreateRequest;
import pe.rutafija.identity.api.dto.UserResponse;
import pe.rutafija.identity.api.dto.UserUpdateRequest;
import pe.rutafija.identity.application.UserManagementService;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<String> SORT_PROPERTIES = Set.of(
            "fullName",
            "email",
            "role",
            "createdAt",
            "updatedAt"
    );

    private final UserManagementService userManagementService;

    public UserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar usuarios de la organización autenticada")
    public ResponseEntity<PageResponse<UserResponse>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) UserRole role,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "fullName,asc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userManagementService.list(
                        search,
                        active,
                        role,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "fullName")
                ));
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Consultar usuario de la organización autenticada")
    public ResponseEntity<UserResponse> get(@PathVariable UUID userId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userManagementService.get(userId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Crear usuario de la organización autenticada")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(userManagementService.create(request));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Editar usuario de la organización autenticada")
    public ResponseEntity<UserResponse> update(
            @PathVariable UUID userId,
            @Valid @RequestBody UserUpdateRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userManagementService.update(userId, request));
    }

    @PostMapping("/{userId}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Activar usuario")
    public ResponseEntity<UserResponse> activate(@PathVariable UUID userId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userManagementService.activate(userId));
    }

    @PostMapping("/{userId}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Desactivar usuario y revocar sus sesiones")
    public ResponseEntity<UserResponse> deactivate(@PathVariable UUID userId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(userManagementService.deactivate(userId));
    }
}
