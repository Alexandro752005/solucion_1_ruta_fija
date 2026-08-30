package pe.rutafija.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

@Service
public class CurrentUserService {

    private final CurrentUserProvider currentUserProvider;
    private final AppUserRepository userRepository;

    public CurrentUserService(CurrentUserProvider currentUserProvider, AppUserRepository userRepository) {
        this.currentUserProvider = currentUserProvider;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AppUser requireTenantActor() {
        AuthenticatedUser authenticated = currentUserProvider.requireCurrentUser();
        if (authenticated.organizationId() == null) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.TENANT_ACCESS_DENIED,
                    "La operación requiere una organización autorizada"
            );
        }

        AppUser user = userRepository.findByIdAndOrganization_Id(
                        authenticated.userId(),
                        authenticated.organizationId()
                )
                .orElseThrow(this::invalidSession);
        if (!user.isActive()
                || !user.getOrganization().isActive()
                || user.getRole() != authenticated.role()) {
            throw invalidSession();
        }
        return user;
    }

    @Transactional(readOnly = true)
    public AppUser requireSuperAdminActor() {
        AuthenticatedUser authenticated = currentUserProvider.requireCurrentUser();
        if (!authenticated.isSuperAdmin()) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La operación requiere el rol SUPER_ADMIN"
            );
        }

        AppUser user = userRepository.findOneById(authenticated.userId())
                .orElseThrow(this::invalidSession);
        if (!user.isActive()
                || user.getRole() != UserRole.SUPER_ADMIN
                || user.getRole() != authenticated.role()
                || user.getOrganization() != null) {
            throw invalidSession();
        }
        return user;
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_TOKEN_INVALID,
                "La sesión no es válida"
        );
    }
}
