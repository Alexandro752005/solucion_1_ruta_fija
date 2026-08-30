package pe.rutafija.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.api.dto.AuthTokenResponse;
import pe.rutafija.identity.api.dto.AuthenticatedUserResponse;
import pe.rutafija.identity.api.dto.LoginRequest;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.RefreshToken;
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

@Service
public class AuthService {

    private final AppUserRepository userRepository;
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
        String normalizedEmail = request.email().strip().toLowerCase(Locale.ROOT);
        AppUser user = userRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);

        if (user == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            auditService.record(null, "LOGIN_FAILED", Map.of("reason", "INVALID_CREDENTIALS"));
            throw invalidCredentials();
        }

        boolean passwordMatches = passwordEncoder.matches(request.password(), user.getPasswordHash());
        boolean organizationIsActive = user.getOrganization() == null || user.getOrganization().isActive();
        if (!passwordMatches || !user.isActive() || !organizationIsActive) {
            auditService.record(user, "LOGIN_FAILED", Map.of("reason", "INVALID_CREDENTIALS"));
            throw invalidCredentials();
        }

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

    @Transactional(noRollbackFor = {InvalidRefreshTokenException.class, RefreshTokenReuseException.class})
    public IssuedSession refresh(String rawRefreshToken) {
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
            auditService.record(current.getUser(), "REFRESH_REUSE_DETECTED", Map.of());
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

        String successorRawToken = opaqueTokenService.generate();
        RefreshToken successor = current.successor(
                opaqueTokenService.hash(successorRawToken),
                now.plus(securityProperties.jwt().refreshTtl())
        );
        refreshTokenRepository.saveAndFlush(successor);
        current.markRotated(successor, now);
        auditService.record(user, "TOKEN_REFRESHED", Map.of());
        return issuedSession(user, successorRawToken);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
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
                        auditService.record(token.getUser(), "LOGOUT", Map.of());
                    }
                });
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserResponse me() {
        AuthenticatedUser authenticated = currentUserProvider.requireCurrentUser();
        AppUser user = findActiveAuthenticatedUser(authenticated);
        return AuthenticatedUserResponse.from(user);
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
}
