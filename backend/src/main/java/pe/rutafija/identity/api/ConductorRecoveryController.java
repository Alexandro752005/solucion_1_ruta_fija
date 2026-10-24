package pe.rutafija.identity.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.identity.api.dto.ConductorRecoveryStartedResponse;
import pe.rutafija.identity.api.dto.ConductorRecoveryStatusResponse;
import pe.rutafija.identity.api.dto.StartConductorRecoveryRequest;
import pe.rutafija.identity.application.ConductorRecoveryService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/{userId}/conductor-recovery")
@PreAuthorize("hasRole('ADMIN')")
public class ConductorRecoveryController {

    private final ConductorRecoveryService recoveryService;

    public ConductorRecoveryController(ConductorRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @PostMapping
    @Operation(summary = "Autorizar recuperación asistida de un CONDUCTOR de la organización")
    public ResponseEntity<ConductorRecoveryStartedResponse> start(
            @PathVariable UUID userId,
            @Valid @RequestBody StartConductorRecoveryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.authorizeAndStart(userId, request));
    }

    @GetMapping("/{recoveryId}")
    @Operation(summary = "Consultar una recuperación asistida de la organización")
    public ResponseEntity<ConductorRecoveryStatusResponse> status(
            @PathVariable UUID userId,
            @PathVariable UUID recoveryId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.status(userId, recoveryId));
    }

    @PostMapping("/{recoveryId}/otp/resend")
    @Operation(summary = "Reenviar el OTP del correo nuevo bajo los límites vigentes")
    public ResponseEntity<ConductorRecoveryStartedResponse> resendOtp(
            @PathVariable UUID userId,
            @PathVariable UUID recoveryId,
            @Valid @RequestBody ConductorRecoveryOtpResendRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.resendOtp(userId, recoveryId, request.verificationToken()));
    }

    @PostMapping("/{recoveryId}/cancel")
    @Operation(summary = "Cancelar una recuperación asistida pendiente")
    public ResponseEntity<ConductorRecoveryStatusResponse> cancel(
            @PathVariable UUID userId,
            @PathVariable UUID recoveryId
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(recoveryService.cancel(userId, recoveryId));
    }
}
