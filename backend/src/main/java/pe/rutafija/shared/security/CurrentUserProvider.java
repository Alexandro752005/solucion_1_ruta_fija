package pe.rutafija.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    public AuthenticatedUser requireCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication) || !authentication.isAuthenticated()) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesión no es válida"
            );
        }

        try {
            String organizationClaim = jwtAuthentication.getToken().getClaimAsString("organizationId");
            return new AuthenticatedUser(
                    UUID.fromString(jwtAuthentication.getToken().getSubject()),
                    organizationClaim == null ? null : UUID.fromString(organizationClaim),
                    jwtAuthentication.getToken().getClaimAsString("email"),
                    UserRole.valueOf(jwtAuthentication.getToken().getClaimAsString("role"))
            );
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesión no es válida"
            );
        }
    }

    public UUID requireTenantId() {
        AuthenticatedUser user = requireCurrentUser();
        if (user.organizationId() == null) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.TENANT_ACCESS_DENIED,
                    "La operación requiere una organización explícitamente autorizada"
            );
        }
        return user.organizationId();
    }

    public void assertTenant(UUID resourceOrganizationId) {
        UUID authenticatedOrganizationId = requireTenantId();
        if (!authenticatedOrganizationId.equals(resourceOrganizationId)) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.TENANT_ACCESS_DENIED,
                    "No puede acceder a recursos de otra organización"
            );
        }
    }
}
