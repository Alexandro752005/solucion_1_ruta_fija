package pe.rutafija.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SecurityConfigTest {

    private final AppUserRepository userRepository = mock(AppUserRepository.class);
    private final Converter<Jwt, AbstractAuthenticationToken> converter =
            new SecurityConfig().jwtAuthenticationConverter(userRepository);

    @Test
    void mapsTheCurrentDatabaseRoleToSpringAuthorities() {
        Organization organization = Organization.active("Operación", "Operación", "America/Lima");
        AppUser user = AppUser.organizationUser(
                organization,
                "admin@rutafija.test",
                "hash",
                "Administrador actual",
                UserRole.ADMINISTRADOR
        );
        when(userRepository.findOneById(user.getId())).thenReturn(Optional.of(user));

        AbstractAuthenticationToken authentication = converter.convert(jwt(user, "ADMINISTRADOR"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMINISTRADOR");
    }

    @Test
    void doesNotGrantAnAuthorityWhenTheSubjectNoLongerExists() {
        Organization organization = Organization.active("Operación", "Operación", "America/Lima");
        AppUser missingUser = AppUser.organizationUser(
                organization,
                "missing@rutafija.test",
                "hash",
                "Usuario eliminado",
                UserRole.ADMINISTRADOR
        );
        when(userRepository.findOneById(missingUser.getId())).thenReturn(Optional.empty());

        AbstractAuthenticationToken authentication = converter.convert(jwt(missingUser, "ADMINISTRADOR"));

        assertThat(authentication).isNotNull();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    private Jwt jwt(AppUser user, String role) {
        Instant issuedAt = Instant.parse("2026-08-29T12:00:00Z");
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(900))
                .claim("organizationId", user.getOrganizationId().toString())
                .claim("email", user.getEmail())
                .claim("role", role)
                .build();
    }
}
