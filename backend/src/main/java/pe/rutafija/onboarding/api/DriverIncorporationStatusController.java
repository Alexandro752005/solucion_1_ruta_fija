package pe.rutafija.onboarding.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.onboarding.api.dto.DriverIncorporationStatusResponse;
import pe.rutafija.onboarding.application.DriverIncorporationStatusService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverIncorporationStatusController {
    private final DriverIncorporationStatusService service;
    public DriverIncorporationStatusController(DriverIncorporationStatusService service) { this.service = service; }

    @GetMapping("/{driverId}/incorporation-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DriverIncorporationStatusResponse> status(@PathVariable UUID driverId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.status(driverId));
    }
}
