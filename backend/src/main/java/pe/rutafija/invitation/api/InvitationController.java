package pe.rutafija.invitation.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.invitation.api.dto.CreateInvitationRequest;
import pe.rutafija.invitation.api.dto.CreatedInvitationResponse;
import pe.rutafija.invitation.api.dto.InvitationSummaryResponse;
import pe.rutafija.invitation.api.dto.InvitationValidationRequest;
import pe.rutafija.invitation.api.dto.InvitationValidationResponse;
import pe.rutafija.invitation.application.InvitationService;
import pe.rutafija.invitation.application.InvitationValidationResult;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApiErrorResponse;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.observability.CorrelationIdFilter;

import java.time.Duration;

@RestController
@Validated
@RequestMapping("/api/v1/invitations")
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Emitir una invitación de incorporación para la organización autenticada")
    public ResponseEntity<CreatedInvitationResponse> create(
            @Valid @RequestBody CreateInvitationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(invitationService.create(request.recipientEmail()));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar invitaciones de la organización autenticada")
    public ResponseEntity<PageResponse<InvitationSummaryResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(invitationService.list(page, size));
    }

    @PostMapping("/{invitationId}/revoke")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Revocar una invitación de la organización autenticada")
    public ResponseEntity<InvitationSummaryResponse> revoke(@PathVariable java.util.UUID invitationId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(invitationService.revoke(invitationId));
    }

    @PostMapping("/validate")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Validar una invitación para la cuenta autenticada")
    public ResponseEntity<?> validate(
            @Valid @RequestBody InvitationValidationRequest request,
            HttpServletRequest servletRequest
    ) {
        InvitationValidationResult result = invitationService.validate(
                request.code(),
                servletRequest.getRemoteAddr()
        );
        if (result.isLimited()) {
            long retryAfter = Math.max(1, (Duration.between(
                    java.time.Instant.now(),
                    result.retryAt()
            ).toMillis() + 999) / 1000);
            ApiErrorResponse body = ApiErrorResponse.of(
                    HttpStatus.TOO_MANY_REQUESTS.value(),
                    ErrorCode.INVITATION_VALIDATION_LIMITED,
                    "La validación de invitaciones está pausada temporalmente",
                    servletRequest.getRequestURI(),
                    CorrelationIdFilter.from(servletRequest)
            );
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter))
                    .body(body);
        }
        InvitationValidationResponse body = result.valid()
                ? new InvitationValidationResponse(true, result.invitationId())
                : InvitationValidationResponse.invalid();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(body);
    }
}
