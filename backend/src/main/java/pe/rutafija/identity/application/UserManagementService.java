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
import pe.rutafija.pilotquota.application.ConductorProvisioningPolicy;
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
    private final EmailOtpService emailOtpService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;
    private final ConductorProvisioningPolicy provisioningPolicy;

    public UserManagementService(
            AppUserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            DriverRepository driverRepository,
            AssignmentRepository assignmentRepository,
            PasswordEncoder passwordEncoder,
            EmailOtpService emailOtpService,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock,
            ConductorProvisioningPolicy provisioningPolicy
    ) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.driverRepository = driverRepository;
        this.assignmentRepository = assignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailOtpService = emailOtpService;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
        this.provisioningPolicy = provisioningPolicy;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(
            String search,
            Boolean active,
            UserRole role,
            Pageable pageable
    ) {
        AppUser actor = currentUserService.requireTenantActor();
        if (role != null && role != UserRole.CONDUCTOR) {
            throw forbiddenAdminManagement();
        }
        Page<AppUser> users = userRepository.searchTenantUsers(
                actor.getOrganizationId(),
                normalizeSearch(search),
                active,
                UserRole.CONDUCTOR,
                pageable
        );
        return PageResponse.from(users, UserResponse::from);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        AppUser actor = currentUserService.requireTenantActor();
        AppUser target = findTenantUser(userId, actor.getOrganizationId());
        if (target.getRole() != UserRole.CONDUCTOR) {
            throw notFound();
        }
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        assertConductorRole(request.role());
        provisioningPolicy.rejectLegacyCreation();
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
        assertConductorRole(target.getRole());
        assertConductorRole(request.role());
        if (target.getId().equals(actor.getId()) && target.getRole() != request.role()) {
            throw conflict("No puede cambiar su propio rol");
        }
        String requestedEmail = request.email().strip();
        boolean emailChanged = !target.getEmail().equalsIgnoreCase(requestedEmail);
        if (emailChanged) {
            throw conflict("El correo del CONDUCTOR solo puede cambiarse mediante recuperación asistida");
        }
        if (!target.getFullName().equals(request.fullName().strip())
                || !java.util.Objects.equals(normalizeOptional(target.getPhone()), normalizeOptional(request.phone()))) {
            provisioningPolicy.rejectIdentityChange();
        }
        String previousEmail = target.getEmail();
        UserRole previousRole = target.getRole();
        target.updateDetails(requestedEmail, request.fullName(), request.phone(), request.role());

        if (!previousEmail.equals(target.getEmail())) {
            refreshTokenRepository.revokeActiveByUserId(target.getId(), Instant.now(clock));
            emailOtpService.invalidateForEmailChange(target.getId());
        }

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
        assertConductorRole(target.getRole());
        provisioningPolicy.requireApproved(target.getId(), actor.getOrganizationId(), null);
        target.activate();
        auditService.record(actor, "USER_ACTIVATED", "APP_USER", target.getId(), Map.of());
        return UserResponse.from(target);
    }

    @Transactional
    public UserResponse deactivate(UUID userId) {
        AppUser actor = currentUserService.requireTenantActor();
        AppUser target = findTenantUser(userId, actor.getOrganizationId());
        assertConductorRole(target.getRole());
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

    private void assertConductorRole(UserRole role) {
        if (role != UserRole.CONDUCTOR) {
            throw forbiddenAdminManagement();
        }
    }

    private ApplicationException forbiddenAdminManagement() {
        return new ApplicationException(
                HttpStatus.FORBIDDEN,
                ErrorCode.FORBIDDEN_ROLE,
                "Solo SUPER_ADMIN puede crear o administrar cuentas ADMIN"
        );
    }

    private AppUser findTenantUser(UUID userId, UUID organizationId) {
        return userRepository.findByIdAndOrganization_Id(userId, organizationId)
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.NOT_FOUND,
                        ErrorCode.RESOURCE_NOT_FOUND,
                        "El usuario solicitado no existe"
                ));
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El usuario solicitado no existe"
        );
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
