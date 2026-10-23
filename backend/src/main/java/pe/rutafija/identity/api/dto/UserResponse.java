package pe.rutafija.identity.api.dto;

import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID organizationId,
        String email,
        String fullName,
        String phone,
        UserRole role,
        boolean active,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getOrganizationId(),
                "",
                pe.rutafija.shared.security.PersonalDataView.account(user.getId()),
                null,
                user.getRole(),
                user.isActive(),
                user.getLastLoginAt(),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
