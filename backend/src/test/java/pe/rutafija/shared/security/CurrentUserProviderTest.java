package pe.rutafija.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class CurrentUserProviderTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserProvider provider = new CurrentUserProvider(userRepository, jdbc);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void derivesTenantOnlyFromAuthenticatedJwt() {
        Organization organization = Organization.active("Org Test", "Org Test", "America/Lima");
        AppUser storedUser = AppUser.organizationUser(
                organization,
                "user@example.test",
                "hash",
                "Test Admin",
                UserRole.ADMIN
        );
        storedUser.verifyEmail(Instant.now());
        UUID userId = storedUser.getId();
        UUID organizationId = storedUser.getOrganizationId();
        when(userRepository.findOneById(userId)).thenReturn(Optional.of(storedUser));
        Jwt jwt = jwt(userId, organizationId, "ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        AuthenticatedUser user = provider.requireCurrentUser();

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(provider.requireTenantId()).isEqualTo(organizationId);
    }

    @Test
    void globalUserCannotImplicitlyActAsATenant() {
        AppUser storedUser = AppUser.superAdmin("user@example.test", "hash", "Test Owner");
        storedUser.verifyEmail(Instant.now());
        UUID userId = storedUser.getId();
        when(userRepository.findOneById(userId)).thenReturn(Optional.of(storedUser));
        Jwt jwt = jwt(userId, null, "SUPER_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        ));

        assertThatThrownBy(provider::requireTenantId)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.TENANT_ACCESS_DENIED));
    }

    @Test
    void rejectsAnAccessTokenWithARetiredRole() {
        Jwt jwt = jwt(UUID.randomUUID(), UUID.randomUUID(), "COORDINADOR");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
        ));

        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    @Test
    void readOnlyTransactionDoesNotAcquireWriteAuthorizationLocks() {
        AppUser storedUser = AppUser.superAdmin("user@example.test", "hash", "Test Owner");
        storedUser.verifyEmail(Instant.now());
        when(userRepository.findOneById(storedUser.getId())).thenReturn(Optional.of(storedUser));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt(storedUser.getId(), null, "SUPER_ADMIN"),
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        assertThat(provider.requireCurrentUser().userId()).isEqualTo(storedUser.getId());

        verifyNoInteractions(jdbc);
    }

    @Test
    void malformedSessionVersionIsNotAcceptedAsLegacyVersionZero() {
        AppUser storedUser = AppUser.superAdmin("user@example.test", "hash", "Test Owner");
        storedUser.verifyEmail(Instant.now());
        when(userRepository.findOneById(storedUser.getId())).thenReturn(Optional.of(storedUser));
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(storedUser.getId().toString()).claim("role", "SUPER_ADMIN")
                .claim("sessionVersion", "0").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))));

        assertThatThrownBy(provider::requireCurrentUser)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.AUTH_TOKEN_INVALID));
    }

    private Jwt jwt(UUID userId, UUID organizationId, String role) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("test-token")
                .headers(headers -> headers.put("alg", "none"))
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
                .claim("email", "user@example.test")
                .claim("role", role);
        if (organizationId != null) {
            builder.claim("organizationId", organizationId.toString());
        }
        return builder.build();
    }
}
