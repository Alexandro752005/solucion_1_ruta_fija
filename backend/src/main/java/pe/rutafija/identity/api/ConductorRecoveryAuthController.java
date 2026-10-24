package pe.rutafija.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.identity.api.dto.CompleteConductorPasswordResetRequest;
import pe.rutafija.identity.api.dto.ConductorPasswordResetCompletedResponse;
import pe.rutafija.identity.api.dto.ConductorRecoveryOtpResponse;
import pe.rutafija.identity.api.dto.VerifyConductorRecoveryOtpRequest;
import pe.rutafija.identity.application.ConductorRecoveryService;
import pe.rutafija.shared.security.TrustedOriginValidator;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/conductor-recovery")
public class ConductorRecoveryAuthController {

    private final ConductorRecoveryService recoveryService;
    private final TrustedOriginValidator trustedOriginValidator;

    public ConductorRecoveryAuthController(
            ConductorRecoveryService recoveryService,
            TrustedOriginValidator trustedOriginValidator
    ) {
        this.recoveryService = recoveryService;
        this.trustedOriginValidator = trustedOriginValidator;
    }

    @PostMapping("/otp/verify")
    @SecurityRequirements
    @Operation(summary = "Verificar el correo solicitado para la recuperación del CONDUCTOR")
    public ResponseEntity<ConductorRecoveryOtpResponse> verifyOtp(
            @Valid @RequestBody VerifyConductorRecoveryOtpRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.verifyOtp(
                        request.recoveryId(),
                        request.verificationToken(),
                        request.code()
                ));
    }

    @PostMapping("/{recoveryId}/password-reset")
    @SecurityRequirements
    @Operation(summary = "Restablecer la contraseña con el token de un solo uso posterior al OTP")
    public ResponseEntity<ConductorPasswordResetCompletedResponse> completePasswordReset(
            @PathVariable UUID recoveryId,
            @Valid @RequestBody CompleteConductorPasswordResetRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        java.time.Instant completedAt = recoveryService.completePasswordReset(
                recoveryId,
                request.passwordResetToken(),
                request.newPassword()
        );
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new ConductorPasswordResetCompletedResponse(
                        recoveryId,
                        "COMPLETED",
                        completedAt
                ));
    }
}
