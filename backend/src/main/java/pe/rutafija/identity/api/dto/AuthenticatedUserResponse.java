package pe.rutafija.identity.api.dto;

import pe.rutafija.identity.domain.AppUser;

import java.util.UUID;

public record AuthenticatedUserResponse(
        UUID id,
        UUID organizationId,
        String organizationName,
        String email,
        String fullName,
        String role
) {

    public static AuthenticatedUserResponse from(AppUser user) {
        return new AuthenticatedUserResponse(
                user.getId(),
                user.getOrganizationId(),
                user.getOrganization() == null ? null : user.getOrganization().getTradeName(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
