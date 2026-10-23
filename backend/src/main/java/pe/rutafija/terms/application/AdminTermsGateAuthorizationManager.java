package pe.rutafija.terms.application;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import pe.rutafija.shared.exception.ApplicationException;

import java.util.function.Supplier;

/**
 * Prevents ADMIN accounts from using protected API routes until they have
 * expressly accepted the exact current terms document for their organization.
 */
@Component
public class AdminTermsGateAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private final AdminTermsService adminTermsService;
    private final boolean enabled;

    public AdminTermsGateAuthorizationManager(
            AdminTermsService adminTermsService,
            @Value("${app.security.admin-terms-gate.enabled:true}") boolean enabled
    ) {
        this.adminTermsService = adminTermsService;
        this.enabled = enabled;
    }

    @Override
    @Deprecated
    public AuthorizationDecision check(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        return authorize(authenticationSupplier, context);
    }

    @Override
    public AuthorizationDecision authorize(
            Supplier<Authentication> authenticationSupplier,
            RequestAuthorizationContext context
    ) {
        Authentication authentication = authenticationSupplier.get();
        if (authentication == null || !authentication.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        if (!enabled || !hasAdminRole(authentication) || isSessionBootstrap(context.getRequest())) {
            return new AuthorizationDecision(true);
        }
        try {
            return new AuthorizationDecision(adminTermsService.hasCurrentAcceptanceForCurrentActor());
        } catch (ApplicationException failure) {
            // This manager executes outside MVC exception advice. Let Spring's
            // ExceptionTranslationFilter render the normal sanitized 401 envelope.
            if (failure.getStatus() == HttpStatus.UNAUTHORIZED)
                throw new InsufficientAuthenticationException("La sesión ya no es válida.");
            if (failure.getStatus() == HttpStatus.FORBIDDEN) return new AuthorizationDecision(false);
            throw failure;
        }
    }

    private static boolean hasAdminRole(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static boolean isSessionBootstrap(HttpServletRequest request) {
        return "GET".equals(request.getMethod())
                && ("/api/v1/auth/me".equals(request.getRequestURI())
                    || "/api/v1/identity/status".equals(request.getRequestURI()));
    }
}
