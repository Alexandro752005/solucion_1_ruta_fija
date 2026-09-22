package pe.rutafija.identity.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.identity.api.dto.UserCreateRequest;
import pe.rutafija.identity.api.dto.UserResponse;
import pe.rutafija.identity.api.dto.UserUpdateRequest;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class UserManagementService {

    private final AppUserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DriverRepository driverRepository;
    private final AssignmentRepository assignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;

    public UserManagementService(
            AppUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            DriverRepository driverRepository,
            AssignmentRepository assignmentRepository,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.driverRepository = driverRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(
            String search,
            Boolean active,
            UserRole role,
            Pageable pageable
    ) {
        AppUser actor = currentUserService.requireTenantActor();
        Page<AppUser> users = userRepository.searchTenantUsers(
                actor.getOrganizationId(),
                normalizeSearch(search),
                active,
                role,
                pageable
        );
        return PageResponse.from(users, UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        AppUser actor = currentUserService.requireTenantActor();
        return UserResponse.from(findTenantUser(userId, actor.getOrganizationId()));
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        assertManageableRole(request.role());
        String email = request.email().strip();
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw conflict("Ya existe un usuario con ese correo");
        }

        AppUser created = userRepository.save(AppUser.organizationUser(
                actor.getOrganization(),
                email,
                passwordEncoder.encode(request.password()),
                request.fullName(),
                request.role()
        ));
        auditService.record(
                actor,
                "USER_CREATED",
                "APP_USER",
                created.getId(),
                Map.of("role", created.getRole().name())
        );
        return UserResponse.from(created);
    }

    @Transactional
    public UserResponse update(UUID userId, UserUpdateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        AppUser target = findTenantUser(userId, actor.getOrganizationId());
        assertManageableRole(request.role());
        if (target.getId().equals(actor.getId()) && target.getRole() != request.role()) {
            throw conflict("No puede cambiar su propio rol");
        }
        if (!target.getEmail().equalsIgnoreCase(request.email().strip())
                && userRepository.existsByEmailIgnoreCase(request.email().strip())) {
            throw conflict("Ya existe un usuario con ese correo");
        }

        if (target.getRole() != request.role()) {
            assertRoleChangeIsSafe(target, request.role());
        }
        UserRole previousRole = target.getRole();
        target.updateDetails(request.email(), request.fullName(), request.phone(), request.role());

        if (previousRole != target.getRole()) {
            // Un token emitido con el rol anterior no debe poder renovarse después del cambio.
            refreshTokenRepository.revokeActiveByUserId(target.getId(), Instant.now(clock));
        }

        auditService.record(
                actor,
                previousRole != target.getRole() ? "ROLE_CHANGED" : "USER_UPDATED",
                "APP_USER",
                target.getId(),
                Map.of("role", target.getRole().name())
        );
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse activate(UUID userId) {
        AppUser actor = currentUserService.requireTenantActor();
        AppUser target = findTenantUser(userId, actor.getOrganizationId());
        target.activate();
        auditService.record(actor, "USER_ACTIVATED", "APP_USER", target.getId(), Map.of());
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse deactivate(UUID userId) {
        AppUser actor = currentUserService.requireTenantActor();
        AppUser target = findTenantUser(userId, actor.getOrganizationId());
        if (target.getId().equals(actor.getId())) {
            throw conflict("No puede desactivar su propia cuenta");
        }

        Driver driver = driverRepository.findByUser_Id(target.getId())
                .filter(Driver::isActive)
                .orElse(null);
        if (driver != null && assignmentRepository.existsByDriver_IdAndStatusIn(
                driver.getId(),
                List.of(AssignmentStatus.SCHEDULED, AssignmentStatus.EN_SERVICIO)
        )) {
            throw conflict("No puede desactivar al conductor mientras tenga asignaciones programadas o en servicio");
        }

        target.deactivate();
        refreshTokenRepository.revokeActiveByUserId(target.getId(), Instant.now(clock));
        if (driver != null) {
            driver.deactivate();
            auditService.record(
                    actor,
                    "DRIVER_DISABLED",
                    "DRIVER",
                    driver.getId(),
                    Map.of("reason", "USER_DISABLED")
            );
        }
        auditService.record(actor, "USER_DISABLED", "APP_USER", target.getId(), Map.of());
        return UserResponse.from(target);
    }

    private void assertRoleChangeIsSafe(AppUser target, UserRole requestedRole) {
        if (target.getRole() == UserRole.CONDUCTOR
                && requestedRole != UserRole.CONDUCTOR
                && driverRepository.existsByUser_Id(target.getId())) {
            throw conflict("No puede cambiar el rol de un usuario vinculado a un conductor");
        }
    }

    private void assertManageableRole(UserRole role) {
        if (role == UserRole.SUPER_ADMIN) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "Un administrador de organización no puede asignar el rol SUPER_ADMIN"
            );
        }
    }

    private AppUser findTenantUser(UUID userId, UUID organizationId) {
        return userRepository.findByIdAndOrganization_Id(userId, organizationId)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "El usuario solicitado no existe"
                ));
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
