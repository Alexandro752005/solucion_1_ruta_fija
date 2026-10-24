package pe.rutafija.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.identity.api.dto.EmailOtpChallengeResponse;
import pe.rutafija.identity.api.dto.EmailOtpResendRequest;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryChallengeResponse;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryCompletionRequest;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryCompletionResponse;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryEnrollmentRequest;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryEnrollmentResponse;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryProofRequest;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryProofResponse;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryStartRequest;
import pe.rutafija.identity.application.EmailOtpTicket;
import pe.rutafija.identity.application.SuperAdminRecoveryService;
import pe.rutafija.shared.security.TrustedOriginValidator;

import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/auth/super-admin-recovery")
public class SuperAdminRecoveryController {

    private final SuperAdminRecoveryService recoveryService;
    private final TrustedOriginValidator trustedOriginValidator;

    public SuperAdminRecoveryController(
            SuperAdminRecoveryService recoveryService,
            TrustedOriginValidator trustedOriginValidator
    ) {
        this.recoveryService = recoveryService;
        this.trustedOriginValidator = trustedOriginValidator;
    }

    @PostMapping("/enrollment")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Inscribir una vez la clave pública de recuperación SUPER_ADMIN")
    public ResponseEntity<SuperAdminRecoveryEnrollmentResponse> enroll(
            @Valid @RequestBody SuperAdminRecoveryEnrollmentRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.enroll(request.publicKeyX509Base64(), request.currentPassword()));
    }

    @PostMapping("/challenges")
    @SecurityRequirements
    @Operation(summary = "Solicitar un reto temporal de recuperación SUPER_ADMIN")
    public ResponseEntity<SuperAdminRecoveryChallengeResponse> start(
            @Valid @RequestBody SuperAdminRecoveryStartRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.start(request.accountEmail(), request.newEmail()));
    }

    @PostMapping("/challenges/{challengeId}/proof")
    @SecurityRequirements
    @Operation(summary = "Probar el código de emergencia y la firma del reto")
    public ResponseEntity<SuperAdminRecoveryProofResponse> prove(
            @PathVariable UUID challengeId,
            @Valid @RequestBody SuperAdminRecoveryProofRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        EmailOtpTicket ticket = recoveryService.prove(
                challengeId,
                request.emergencyCode(),
                request.signatureBase64Url()
        );
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(new SuperAdminRecoveryProofResponse(
                        ticket.verificationToken(),
                        ticket.otpExpiresAt(),
                        ticket.challengeExpiresAt(),
                        ticket.emailSent()
                ));
    }

    @PostMapping("/challenges/{challengeId}/email/resend")
    @SecurityRequirements
    @Operation(summary = "Reenviar el OTP de la recuperación al nuevo correo solicitado")
    public ResponseEntity<EmailOtpChallengeResponse> resendEmail(
            @PathVariable UUID challengeId,
            @Valid @RequestBody EmailOtpResendRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        EmailOtpTicket ticket = recoveryService.resendEmail(challengeId, request.verificationToken());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(EmailOtpChallengeResponse.from(ticket));
    }

    @PostMapping("/challenges/{challengeId}/complete")
    @SecurityRequirements
    @Operation(summary = "Verificar el nuevo correo y completar la recuperación")
    public ResponseEntity<SuperAdminRecoveryCompletionResponse> complete(
            @PathVariable UUID challengeId,
            @Valid @RequestBody SuperAdminRecoveryCompletionRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.complete(
                        challengeId,
                        request.verificationToken(),
                        request.code(),
                        request.newPassword()
                ));
    }
}
