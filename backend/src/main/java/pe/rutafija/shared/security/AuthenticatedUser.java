package pe.rutafija.shared.security;

import pe.rutafija.identity.domain.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID userId,
        UUID organizationId,
        String email,
        UserRole role
) {

    public boolean isSuperAdmin() {
        return role == UserRole.SUPER_ADMIN;
    }
}
