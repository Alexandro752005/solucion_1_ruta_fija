package pe.rutafija.onboarding.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.identity.api.dto.LoginRequest;
import pe.rutafija.onboarding.application.ConductorOnboardingAccessService;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingAccessRepository;
import pe.rutafija.shared.security.TrustedOriginValidator;

import java.util.List;

@RestController
@RequestMapping("/api/v1/onboarding")
public class ConductorOnboardingAccessController {

    private final ConductorOnboardingAccessService service;
    private final TrustedOriginValidator trustedOriginValidator;

    public ConductorOnboardingAccessController(ConductorOnboardingAccessService service,
                                               TrustedOriginValidator trustedOriginValidator) {
        this.service = service;
        this.trustedOriginValidator = trustedOriginValidator;
    }

    @PostMapping("/access/login")
    @Operation(summary = "Acceder exclusivamente al estado de las solicitudes propias de incorporacion")
    public ResponseEntity<ConductorOnboardingAccessService.ApplicantAccess> login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        trustedOriginValidator.validate(servletRequest);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.login(request.email(), request.password()));
    }

    @GetMapping("/applicant/requests")
    @Operation(summary = "Consultar solicitudes propias sin datos personales ni acceso operativo")
    public ResponseEntity<List<ConductorOnboardingAccessRepository.ApplicantRequest>> requests(
            @RequestHeader(value = "X-Onboarding-Token", required = false) String token) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.requests(token));
    }

    @PostMapping("/access/logout")
    @Operation(summary = "Revocar el acceso de postulante")
    public ResponseEntity<Void> logout(
            @RequestHeader(value = "X-Onboarding-Token", required = false) String token,
            HttpServletRequest servletRequest) {
        trustedOriginValidator.validate(servletRequest);
        service.logout(token);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
