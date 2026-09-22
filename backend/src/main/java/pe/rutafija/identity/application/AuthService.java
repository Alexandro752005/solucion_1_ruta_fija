package pe.rutafija.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.identity.api.dto.AuthTokenResponse;
import pe.rutafija.identity.api.dto.AuthenticatedUserResponse;
import pe.rutafija.identity.api.dto.LoginRequest;
import pe.rutafija.identity.api.dto.MobileAuthTokenResponse;
import pe.rutafija.identity.api.dto.MobileAuthenticatedUserResponse;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.RefreshToken;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.shared.config.SecurityProperties;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.exception.InvalidCredentialsException;
import pe.rutafija.shared.exception.InvalidRefreshTokenException;
import pe.rutafija.shared.exception.RefreshTokenReuseException;
import pe.rutafija.shared.security.AuthenticatedUser;
import pe.rutafija.shared.security.CurrentUserProvider;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
public class AuthService {

    private final AppUserRepository userRepository;
    private final DriverRepository driverRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OpaqueTokenService opaqueTokenService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final String dummyPasswordHash;

    public AuthService(
            AppUserRepository userRepository,
            DriverRepository driverRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            OpaqueTokenService opaqueTokenService,
            AuditService auditService,
            CurrentUserProvider currentUserProvider,
            SecurityProperties securityProperties,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.driverRepository = driverRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.opaqueTokenService = opaqueTokenService;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
        this.securityProperties = securityProperties;
        this.clock = clock;
        // Generated once at startup so an unknown user still incurs a real bcrypt check.
        this.dummyPasswordHash = passwordEncoder.encode("ruta-fija-dummy-credential");
    }

    @Transactional(noRollbackFor = InvalidCredentialsException.class)
    public IssuedSession login(LoginRequest request) {
        AppUser user = authenticate(request, "LOGIN_FAILED");
        Instant now = Instant.now(clock);
        user.recordSuccessfulLogin(now);
        String rawRefreshToken = opaqueTokenService.generate();
        refreshTokenRepository.save(RefreshToken.firstInFamily(
                user,
                opaqueTokenService.hash(rawRefreshToken),
                now.plus(securityProperties.jwt().refreshTtl())
        ));
        auditService.record(user, "LOGIN_SUCCESS", Map.of());
        return issuedSession(user, rawRefreshToken);
    }

    /**
     * Native sessions are limited to active CONDUCTOR users with one active
     * driver relation in their own organization. Browser cookies are not part
     * of this flow.
     */
    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileIssuedSession mobileLogin(LoginRequest request) {
        AppUser user = authenticate(request, "MOBILE_LOGIN_FAILED");
        Driver driver;
        try {
            driver = requireMobileDriver(user);
        } catch (ApplicationException exception) {
            auditService.record(user, "MOBILE_LOGIN_DENIED", Map.of("code", exception.getCode().name()));
            throw exception;
        }

        Instant now = Instant.now(clock);
        user.recordSuccessfulLogin(now);
        String rawRefreshToken = opaqueTokenService.generateMobile();
        refreshTokenRepository.save(RefreshToken.firstInFamily(
                user,
                opaqueTokenService.hash(rawRefreshToken),
                now.plus(securityProperties.jwt().refreshTtl())
        ));
        auditService.record(user, "MOBILE_LOGIN_SUCCESS", Map.of());
        return mobileIssuedSession(user, driver, rawRefreshToken);
    }

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, RefreshTokenReuseException.class})
    public IssuedSession refresh(String rawRefreshToken) {
        RefreshRotation rotation = rotateRefresh(rawRefreshToken, RefreshChannel.WEB);
        return issuedSession(rotation.user(), rotation.rawRefreshToken());
    }

    @Transactional(noRollbackFor = ApplicationException.class)
    public MobileIssuedSession refreshMobile(String rawRefreshToken) {
        RefreshRotation rotation = rotateRefresh(rawRefreshToken, RefreshChannel.MOBILE);
        return mobileIssuedSession(rotation.user(), rotation.driver(), rotation.rawRefreshToken());
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        logout(rawRefreshToken, RefreshChannel.WEB);
    }

    @Transactional
    public void logoutMobile(String rawRefreshToken) {
        logout(rawRefreshToken, RefreshChannel.MOBILE);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse me() {
        AuthenticatedUser authenticated = currentUserProvider.requireCurrentUser();
        AppUser user = findActiveAuthenticatedUser(authenticated);
        return AuthenticatedUserResponse.from(user);
    }

    private AppUser authenticate(LoginRequest request, String failureAction) {
        String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            auditService.record(null, failureAction, Map.of("reason", "INVALID_CREDENTIALS"));
            throw invalidCredentials();
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        boolean organizationIsActive = user.getOrganization() == null || user.getOrganization().isActive();
        if (!passwordMatches || !user.isActive() || !organizationIsActive) {
            auditService.record(user, failureAction, Map.of("reason", "INVALID_CREDENTIALS"));
            throw invalidCredentials();
        }
        return user;
    }

    private RefreshRotation rotateRefresh(String rawRefreshToken, RefreshChannel channel) {
        if (!matchesRefreshChannel(rawRefreshToken, channel)) {
            throw invalidRefreshToken();
        }
        String tokenHash;
        try {
            tokenHash = opaqueTokenService.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            throw invalidRefreshToken();
        }

        RefreshToken current = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(this::invalidRefreshToken);
        Instant now = Instant.now(clock);

        if (current.hasBeenUsed()) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            auditService.record(current.getUser(), channel == RefreshChannel.WEB
                    ? "REFRESH_REUSE_DETECTED"
                    : "MOBILE_REFRESH_REUSE_DETECTED", Map.of());
            throw new RefreshTokenReuseException();
        }
        if (current.isRevoked() || current.isExpiredAt(now)) {
            current.revoke(now);
            throw invalidRefreshToken();
        }

        AppUser user = current.getUser();
        boolean organizationIsActive = user.getOrganization() == null || user.getOrganization().isActive();
        if (!user.isActive() || !organizationIsActive) {
            refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
            throw invalidRefreshToken();
        }

        Driver driver = null;
        if (channel == RefreshChannel.MOBILE) {
            try {
                driver = requireMobileDriver(user);
            } catch (ApplicationException exception) {
                refreshTokenRepository.revokeFamily(current.getFamilyId(), now);
                auditService.record(user, "MOBILE_SESSION_REVOKED", Map.of("code", exception.getCode().name()));
                throw exception;
            }
        }

        String successorRawToken = channel == RefreshChannel.WEB
                ? opaqueTokenService.generate()
                : opaqueTokenService.generateMobile();
        RefreshToken successor = current.successor(
                opaqueTokenService.hash(successorRawToken),
                now.plus(securityProperties.jwt().refreshTtl())
        );
        refreshTokenRepository.saveAndFlush(successor);
        current.markRotated(successor, now);
        auditService.record(user, channel == RefreshChannel.WEB
                ? "TOKEN_REFRESHED"
                : "MOBILE_TOKEN_REFRESHED", Map.of());
        return new RefreshRotation(user, driver, successorRawToken);
    }

    private void logout(String rawRefreshToken, RefreshChannel channel) {
        if (!matchesRefreshChannel(rawRefreshToken, channel)) {
            return;
        }
        String tokenHash;
        try {
            tokenHash = opaqueTokenService.hash(rawRefreshToken);
        } catch (IllegalArgumentException exception) {
            return;
        }
        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .ifPresent(token -> {
                    int revokedTokens = refreshTokenRepository.revokeFamily(
                            token.getFamilyId(),
                            Instant.now(clock)
                    );
                    if (revokedTokens > 0) {
                        auditService.record(token.getUser(), channel == RefreshChannel.WEB
                                ? "LOGOUT"
                                : "MOBILE_LOGOUT", Map.of());
                    }
                });
    }

    private AppUser findActiveAuthenticatedUser(AuthenticatedUser authenticated) {
        AppUser user = authenticated.isSuperAdmin()
                ? userRepository.findOneById(authenticated.userId()).orElseThrow(this::invalidAccessToken)
                : userRepository.findByIdAndOrganization_Id(
                        authenticated.userId(),
                        authenticated.organizationId()
                ).orElseThrow(this::invalidAccessToken);
        if (!user.isActive() || (user.getOrganization() != null && !user.getOrganization().isActive())) {
            throw invalidAccessToken();
        }
        return user;
    }

    private IssuedSession issuedSession(AppUser user, String rawRefreshToken) {
        return new IssuedSession(
                new AuthTokenResponse(
                        jwtService.issueAccessToken(user),
                        "Bearer",
                        jwtService.accessTokenExpiresInSeconds(),
                        AuthenticatedUserResponse.from(user)
                ),
                rawRefreshToken
        );
    }

    private MobileIssuedSession mobileIssuedSession(AppUser user, Driver driver, String rawRefreshToken) {
        return new MobileIssuedSession(
                new MobileAuthTokenResponse(
                        jwtService.issueMobileAccessToken(user, driver.getId()),
                        rawRefreshToken,
                        "Bearer",
                        jwtService.accessTokenExpiresInSeconds(),
                        jwtService.refreshTokenExpiresInSeconds(),
                        MobileAuthenticatedUserResponse.from(user, driver)
                )
        );
    }

    private Driver requireMobileDriver(AppUser user) {
        if (user.getRole() != UserRole.CONDUCTOR) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.MOBILE_USER_NOT_DRIVER,
                    "La cuenta no está habilitada para la aplicación móvil"
            );
        }
        Driver driver = driverRepository.findByUser_Id(user.getId()).orElseThrow(() -> new ApplicationException(
                HttpStatus.FORBIDDEN,
                ErrorCode.MOBILE_USER_NOT_DRIVER,
                "La cuenta no está habilitada para la aplicación móvil"
        ));
        if (!Objects.equals(driver.getOrganizationId(), user.getOrganizationId())) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.MOBILE_USER_NOT_DRIVER,
                    "La cuenta no está habilitada para la aplicación móvil"
            );
        }
        if (!driver.isActive()) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.DRIVER_INACTIVE,
                    "El conductor no está habilitado para operar desde la aplicación móvil"
            );
        }
        return driver;
    }

    private boolean matchesRefreshChannel(String rawRefreshToken, RefreshChannel channel) {
        return switch (channel) {
            case WEB -> opaqueTokenService.isWebRefreshToken(rawRefreshToken);
            case MOBILE -> opaqueTokenService.isMobileRefreshToken(rawRefreshToken);
        };
    }

    private InvalidCredentialsException invalidCredentials() {
        return new InvalidCredentialsException();
    }

    private InvalidRefreshTokenException invalidRefreshToken() {
        return new InvalidRefreshTokenException();
    }

    private ApplicationException invalidAccessToken() {
        return new ApplicationException(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_TOKEN_INVALID,
                "La sesión no es válida"
        );
    }

    private enum RefreshChannel {
        WEB,
        MOBILE
    }

    private record RefreshRotation(AppUser user, Driver driver, String rawRefreshToken) {
    }
}
