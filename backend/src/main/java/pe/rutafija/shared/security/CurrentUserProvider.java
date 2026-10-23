package pe.rutafija.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.util.UUID;
import java.util.List;
import java.util.Objects;

@Component
public class CurrentUserProvider {

    private final AppUserRepository userRepository;
    private final JdbcTemplate jdbc;

    public CurrentUserProvider(AppUserRepository userRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.jdbc = jdbc;
    }

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
            AuthenticatedUser authenticated = new AuthenticatedUser(
                    UUID.fromString(jwtAuthentication.getToken().getSubject()),
                    organizationClaim == null ? null : UUID.fromString(organizationClaim),
                    jwtAuthentication.getToken().getClaimAsString("email"),
                    UserRole.valueOf(jwtAuthentication.getToken().getClaimAsString("role"))
            );
            Object sessionVersionClaim = jwtAuthentication.getToken().getClaims().get("sessionVersion");
            if (TransactionSynchronizationManager.isActualTransactionActive()
                    && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                return requireWriteActor(authenticated, sessionVersionClaim);
            }
            AppUser user = userRepository.findOneById(authenticated.userId()).orElse(null);
            boolean sessionVersionMatches = user != null
                    && sessionVersionMatches(sessionVersionClaim, user.getSessionVersion());
            if (user == null
                    || !user.isActive()
                    || !user.isEmailVerified()
                    || !sessionVersionMatches
                    || (authenticated.email() != null && !user.getEmail().equals(authenticated.email()))
                    || user.getRole() != authenticated.role()
                    || !java.util.Objects.equals(user.getOrganizationId(), authenticated.organizationId())
                    || (user.getOrganization() != null && !user.getOrganization().isActive())) {
                throw new ApplicationException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AUTH_TOKEN_INVALID,
                        "La sesión no es válida"
                );
            }
            return new AuthenticatedUser(user.getId(), user.getOrganizationId(), user.getEmail(), user.getRole());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesión no es válida"
            );
        }
    }

    /**
     * Authorization for a write must use rows read after acquiring their locks,
     * rather than an entity cached earlier in this request. Keep the organization
     * NO KEY UPDATE lock and actor UPDATE lock until the transaction commits so revocation cannot
     * commit between authorization and the protected write. Tenant operations
     * always lock organization before actor, matching lifecycle and scheduling operations.
     * Taking SHARE first and upgrading later deadlocks when another authorized writer
     * holds SHARE while waiting for this actor. Acquire the final scheduling mode up front.
     */
    private AuthenticatedUser requireWriteActor(AuthenticatedUser tokenActor, Object sessionVersionClaim) {
        if (tokenActor.organizationId() != null) {
            List<Boolean> organizations = jdbc.query(
                    "select status = 'ACTIVE' from organization where id = ? for no key update",
                    (row, ignored) -> row.getBoolean(1), tokenActor.organizationId());
            if (organizations.size() != 1 || !organizations.getFirst()) {
                throw invalidSession();
            }
        }
        List<WriteActor> actors = jdbc.query("""
                select id, organization_id, email, role, active,
                       email_verified_at is not null as email_verified, session_version
                from app_user where id = ? for update
                """, (row, ignored) -> new WriteActor(
                row.getObject("id", UUID.class),
                row.getObject("organization_id", UUID.class),
                row.getString("email"),
                UserRole.valueOf(row.getString("role")),
                row.getBoolean("active"),
                row.getBoolean("email_verified"),
                row.getLong("session_version")), tokenActor.userId());
        if (actors.size() != 1) {
            throw invalidSession();
        }
        WriteActor actor = actors.getFirst();
        if (!actor.active() || !actor.emailVerified()
                || !sessionVersionMatches(sessionVersionClaim, actor.sessionVersion())
                || (tokenActor.email() != null && !actor.email().equals(tokenActor.email()))
                || actor.role() != tokenActor.role()
                || !Objects.equals(actor.organizationId(), tokenActor.organizationId())) {
            throw invalidSession();
        }
        return new AuthenticatedUser(actor.id(), actor.organizationId(), actor.email(), actor.role());
    }

    private boolean sessionVersionMatches(Object claim, long storedVersion) {
        // Existing legacy tokens without this claim are valid only at version 0.
        // A present but malformed claim must never be treated as an absent one.
        return claim == null ? storedVersion == 0
                : claim instanceof Number version && storedVersion == version.longValue();
    }

    private ApplicationException invalidSession() {
        return new ApplicationException(HttpStatus.UNAUTHORIZED, ErrorCode.AUTH_TOKEN_INVALID,
                "La sesión no es válida");
    }

    private record WriteActor(UUID id, UUID organizationId, String email, UserRole role,
                              boolean active, boolean emailVerified, long sessionVersion) { }

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
