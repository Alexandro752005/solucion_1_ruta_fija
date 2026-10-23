package pe.rutafija.shared.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.pilotquota.application.ConductorProvisioningPolicy;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.util.UUID;

/**
 * Resolves a conductor from a mobile JWT and verifies that the token claims
 * still agree with the database relation. This prevents web JWTs from being
 * reused as mobile-operation credentials and prevents client-supplied tenant
 * or driver identifiers from becoming authority.
 */
@Service
public class MobileDriverContextService {

    private final CurrentUserService currentUserService;
    private final DriverRepository driverRepository;
    private final ConductorProvisioningPolicy conductorProvisioningPolicy;

    public MobileDriverContextService(
            CurrentUserService currentUserService,
            DriverRepository driverRepository,
            ConductorProvisioningPolicy conductorProvisioningPolicy
    ) {
        this.currentUserService = currentUserService;
        this.driverRepository = driverRepository;
        this.conductorProvisioningPolicy = conductorProvisioningPolicy;
    }

    // Validation failures must preserve their HTTP status when the caller keeps
    // a rejected command receipt (noRollbackFor=ApplicationException).
    @Transactional(readOnly = true, noRollbackFor = ApplicationException.class)
    public MobileDriverActor requireMobileDriver() {
        var actor=requireMobileConsultation();
        var token=requireMobileToken();
        if (!"OPERATIONS".equals(token.getToken().getClaimAsString("accessScope")))
            throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.DRIVER_INACTIVE,"La sesión solo permite incorporación; renueva la sesión tras habilitarse.");
        conductorProvisioningPolicy.requireOperationalEligibility(actor.driver().getId(),actor.user().getOrganizationId());
        return actor;
    }

    @Transactional(readOnly = true, noRollbackFor = ApplicationException.class)
    public MobileDriverActor requireMobileConsultation() {
        JwtAuthenticationToken token = requireMobileToken();
        AppUser user = currentUserService.requireTenantActor();
        if (user.getRole() != UserRole.CONDUCTOR) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La operacion movil requiere el rol CONDUCTOR"
            );
        }

        UUID claimedDriverId = requiredUuidClaim(token, "driverId");
        Driver driver = driverRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ApplicationException(
                        HttpStatus.FORBIDDEN,
                        ErrorCode.MOBILE_USER_NOT_DRIVER,
                        "El usuario movil no tiene un conductor vinculado"
                ));
        if (!driver.getOrganizationId().equals(user.getOrganizationId())
                || !driver.getId().equals(claimedDriverId)) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesion movil ya no es valida"
            );
        }
        conductorProvisioningPolicy.requireApproved(user.getId(), user.getOrganizationId(), driver.getId());
        return new MobileDriverActor(user, driver);
    }

    private JwtAuthenticationToken requireMobileToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token) || !authentication.isAuthenticated()) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesion movil no es valida"
            );
        }
        if (!"MOBILE".equals(token.getToken().getClaimAsString("sessionChannel"))) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.MOBILE_SESSION_REQUIRED,
                    "Se requiere una sesion movil de conductor"
            );
        }
        return token;
    }

    private UUID requiredUuidClaim(JwtAuthenticationToken token, String claim) {
        try {
            return UUID.fromString(token.getToken().getClaimAsString(claim));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesion movil no es valida"
            );
        }
    }
}
