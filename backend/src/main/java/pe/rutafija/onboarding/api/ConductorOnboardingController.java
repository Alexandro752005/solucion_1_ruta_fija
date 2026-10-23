package pe.rutafija.onboarding.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingAcceptedResponse;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingExpedienteResponse;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingOtpResponse;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingRequestSummaryResponse;
import pe.rutafija.onboarding.api.dto.ResendConductorOnboardingOtpRequest;
import pe.rutafija.onboarding.api.dto.StartConductorOnboardingRequest;
import pe.rutafija.onboarding.api.dto.VerifyConductorOnboardingOtpRequest;
import pe.rutafija.onboarding.application.ConductorOnboardingService;
import pe.rutafija.onboarding.application.ConductorOnboardingReviewService;
import pe.rutafija.shared.security.TrustedOriginValidator;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/onboarding")
public class ConductorOnboardingController {

    private final ConductorOnboardingService service;
    private final ConductorOnboardingReviewService reviewService;
    private final TrustedOriginValidator trustedOriginValidator;

    public ConductorOnboardingController(
            ConductorOnboardingService service,
            ConductorOnboardingReviewService reviewService,
            TrustedOriginValidator trustedOriginValidator
    ) {
        this.service = service;
        this.reviewService = reviewService;
        this.trustedOriginValidator = trustedOriginValidator;
    }

    @GetMapping("/requests")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar solicitudes de incorporación sin datos personales")
    public ResponseEntity<List<ConductorOnboardingRequestSummaryResponse>> list() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reviewService.list());
    }

    @GetMapping("/requests/{requestId}/expediente")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Abrir los datos personales en la vista restringida de expediente")
    public ResponseEntity<ConductorOnboardingExpedienteResponse> expediente(@PathVariable UUID requestId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(reviewService.expediente(requestId));
    }

    @PostMapping("/requests")
    @Operation(summary = "Iniciar una solicitud de incorporación CONDUCTOR asociada a una invitación")
    public ResponseEntity<ConductorOnboardingOtpResponse> start(
            @Valid @RequestBody StartConductorOnboardingRequest request,
            @RequestHeader("Idempotency-Key") @Size(min = 8, max = 100) String idempotencyKey,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(service.start(request, idempotencyKey, servletRequest.getRemoteAddr()));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verificar correo, consumir la invitación y crear la solicitud pendiente")
    public ResponseEntity<ConductorOnboardingAcceptedResponse> verify(
            @Valid @RequestBody VerifyConductorOnboardingOtpRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(service.verify(request.verificationToken(), request.code()));
    }

    @PostMapping("/otp/resend")
    @Operation(summary = "Reenviar el OTP de incorporación y rotar el token de verificación")
    public ResponseEntity<ConductorOnboardingOtpResponse> resend(
            @Valid @RequestBody ResendConductorOnboardingOtpRequest request,
            HttpServletRequest servletRequest
    ) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.resend(request.verificationToken()));
    }
}
