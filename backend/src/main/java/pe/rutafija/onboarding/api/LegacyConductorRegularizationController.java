package pe.rutafija.onboarding.api;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.*;
import pe.rutafija.onboarding.application.LegacyConductorRegularizationService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class LegacyConductorRegularizationController {
    private final LegacyConductorRegularizationService service;
    public LegacyConductorRegularizationController(LegacyConductorRegularizationService service){this.service=service;}
    @PostMapping("/{driverId}/regularization") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<LegacyRegularizationOpened> open(@PathVariable UUID driverId,@Valid @RequestBody OpenLegacyRegularization command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.open(driverId,command));
    }
}
