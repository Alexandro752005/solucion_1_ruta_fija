package pe.rutafija.shared.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void derivesTenantOnlyFromAuthenticatedJwt() {
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        Jwt jwt = jwt(userId, organizationId, "ADMINISTRADOR");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        ));

        AuthenticatedUser user = provider.requireCurrentUser();

        assertThat(user.userId()).isEqualTo(userId);
        assertThat(provider.requireTenantId()).isEqualTo(organizationId);
    }

    @Test
    void globalUserCannotImplicitlyActAsATenant() {
        Jwt jwt = jwt(UUID.randomUUID(), null, "SUPER_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        ));

        assertThatThrownBy(provider::requireTenantId)
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo(ErrorCode.TENANT_ACCESS_DENIED));
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
