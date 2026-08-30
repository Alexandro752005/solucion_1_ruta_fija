package pe.rutafija.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.identity.api.dto.AuthTokenResponse;
import pe.rutafija.identity.api.dto.AuthenticatedUserResponse;
import pe.rutafija.identity.api.dto.LoginRequest;
import pe.rutafija.identity.application.AuthService;
import pe.rutafija.identity.application.IssuedSession;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.shared.config.OpenApiConfig;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.TrustedOriginValidator;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService cookieService;
    private final LoginRateLimiter loginRateLimiter;
    private final TrustedOriginValidator trustedOriginValidator;

    public AuthController(
            AuthService authService,
            RefreshCookieService cookieService,
            LoginRateLimiter loginRateLimiter,
            TrustedOriginValidator trustedOriginValidator
    ) {
        this.authService = authService;
        this.cookieService = cookieService;
        this.loginRateLimiter = loginRateLimiter;
        this.trustedOriginValidator = trustedOriginValidator;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión")
    public ResponseEntity<AuthTokenResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        loginRateLimiter.checkAndRecordAttempt(request.email());
        IssuedSession session = authService.login(request);
        loginRateLimiter.recordSuccess(request.email());
        return withRefreshCookie(session);
    }

    @PostMapping("/refresh")
    @SecurityRequirement(name = OpenApiConfig.REFRESH_COOKIE_SCHEME)
    @Operation(summary = "Rotar el refresh token y emitir un access token")
    public ResponseEntity<AuthTokenResponse> refresh(HttpServletRequest request) {
        trustedOriginValidator.validate(request);
        IssuedSession session = authService.refresh(readRefreshToken(request));
        return withRefreshCookie(session);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = OpenApiConfig.REFRESH_COOKIE_SCHEME)
    @Operation(summary = "Cerrar sesión y revocar la familia del refresh token")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        trustedOriginValidator.validate(request);
        authService.logout(readOptionalRefreshToken(request));
        return ResponseEntity.noContent()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookieService.clear())
                .build();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Obtener la identidad autenticada")
    public ResponseEntity<AuthenticatedUserResponse> me() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(authService.me());
    }

    private ResponseEntity<AuthTokenResponse> withRefreshCookie(IssuedSession session) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, cookieService.create(session.rawRefreshToken()))
                .body(session.response());
    }

    private String readRefreshToken(HttpServletRequest request) {
        String token = readOptionalRefreshToken(request);
        if (token == null) {
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_TOKEN_INVALID,
                    "La sesión no es válida o ha expirado"
            );
        }
        return token;
    }

    private String readOptionalRefreshToken(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(cookie -> cookieService.cookieName().equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
