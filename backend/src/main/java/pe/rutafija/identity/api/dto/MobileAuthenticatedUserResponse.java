package pe.rutafija.identity.api.dto;

import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.identity.domain.AppUser;

import java.util.UUID;

/** Minimal identity returned to the native driver application. */
public record MobileAuthenticatedUserResponse(
        UUID id,
        UUID driverId,
        UUID organizationId,
        String fullName,
        String role
) {

    public static MobileAuthenticatedUserResponse from(AppUser user, Driver driver) {
        return new MobileAuthenticatedUserResponse(
                user.getId(),
                driver.getId(),
                user.getOrganizationId(),
                user.getFullName(),
                user.getRole().name()
        );
    }
}
