package pe.rutafija.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.identity.api.dto.LoginRequest;
import pe.rutafija.identity.api.dto.MobileAuthTokenResponse;
import pe.rutafija.identity.api.dto.MobileRefreshTokenRequest;
import pe.rutafija.identity.application.AuthService;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.application.MobileIssuedSession;

/**
 * Session endpoints for the native driver application. They use JSON transport
 * and never create, read or clear the browser refresh cookie.
 */
@RestController
@RequestMapping("/api/v1/mobile/auth")
public class MobileAuthController {

    private final AuthService authService;
    private final LoginRateLimiter loginRateLimiter;

    public MobileAuthController(AuthService authService, LoginRateLimiter loginRateLimiter) {
        this.authService = authService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Iniciar sesión móvil como conductor vinculado")
    public ResponseEntity<MobileAuthTokenResponse> login(@Valid @RequestBody LoginRequest request) {
        loginRateLimiter.checkAndRecordAttempt(request.email());
        MobileIssuedSession session = authService.mobileLogin(request);
        loginRateLimiter.recordSuccess(request.email());
        return noStore(session.response());
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    @Operation(summary = "Rotar un refresh token móvil enviado en el cuerpo")
    public ResponseEntity<MobileAuthTokenResponse> refresh(
            @Valid @RequestBody MobileRefreshTokenRequest request
    ) {
        return noStore(authService.refreshMobile(request.refreshToken()).response());
    }

    @PostMapping("/logout")
    @SecurityRequirements
    @Operation(summary = "Cerrar la sesión móvil y revocar su familia de refresh")
    public ResponseEntity<Void> logout(@Valid @RequestBody MobileRefreshTokenRequest request) {
        authService.logoutMobile(request.refreshToken());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<MobileAuthTokenResponse> noStore(MobileAuthTokenResponse response) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(response);
    }
}
