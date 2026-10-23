package pe.rutafija.terms.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.shared.config.OpenApiConfig;
import pe.rutafija.terms.api.dto.AcceptAdminTermsRequest;
import pe.rutafija.terms.api.dto.AdminTermsAcceptanceResponse;
import pe.rutafija.terms.api.dto.AdminTermsCurrentResponse;
import pe.rutafija.terms.application.AdminTermsService;

@RestController
@RequestMapping("/api/v1/admin/terms")
@PreAuthorize("hasRole('ADMIN')")
public class AdminTermsController {

    private final AdminTermsService service;

    public AdminTermsController(AdminTermsService service) {
        this.service = service;
    }

    @GetMapping("/current")
    @Operation(summary = "Consultar la versión vigente de términos ADMIN")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<AdminTermsCurrentResponse> getCurrentTerms() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.getCurrentTerms());
    }

    @PostMapping("/acceptances")
    @Operation(summary = "Registrar la aceptación expresa de la versión vigente de términos ADMIN")
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<AdminTermsAcceptanceResponse> acceptCurrentTerms(
            @Valid @RequestBody AcceptAdminTermsRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(service.acceptCurrentTerms(request));
    }
}
