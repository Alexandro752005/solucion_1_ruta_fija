package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityRequest;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityResponse;
import pe.rutafija.operation.api.dto.mobile.MobileDriverProfileResponse;
import pe.rutafija.operation.api.dto.mobile.MobileLocationConsentRequest;
import pe.rutafija.operation.api.dto.mobile.MobileLocationUpdateRequest;
import pe.rutafija.operation.application.MobileDriverService;

@RestController
@Validated
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile")
public class MobileDriverController {

    private final MobileDriverService mobileDriverService;

    public MobileDriverController(MobileDriverService mobileDriverService) {
        this.mobileDriverService = mobileDriverService;
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar el perfil propio del conductor movil")
    public ResponseEntity<MobileDriverProfileResponse> me() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileDriverService.profile());
    }

    @GetMapping("/availability")
    @Operation(summary = "Consultar la disponibilidad propia")
    public ResponseEntity<MobileAvailabilityResponse> availability() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileDriverService.availability());
    }

    @PutMapping("/availability")
    @Operation(summary = "Cambiar una disponibilidad administrativa permitida del conductor")
    public ResponseEntity<MobileAvailabilityResponse> changeAvailability(
            @Valid @RequestBody MobileAvailabilityRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileDriverService.changeAvailability(request));
    }

    @PutMapping("/location/consent")
    @Operation(summary = "Otorgar o revocar consentimiento funcional de ubicacion")
    public ResponseEntity<MobileDriverProfileResponse> changeLocationConsent(
            @Valid @RequestBody MobileLocationConsentRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mobileDriverService.changeLocationConsent(request));
    }

    @PutMapping("/location/current")
    @Operation(summary = "Crear o reemplazar exclusivamente la ubicacion vigente propia")
    public ResponseEntity<Void> upsertCurrentLocation(@Valid @RequestBody MobileLocationUpdateRequest request) {
        mobileDriverService.upsertCurrentLocation(request);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @DeleteMapping("/location/current")
    @Operation(summary = "Eliminar la ubicacion vigente propia")
    public ResponseEntity<Void> deleteCurrentLocation() {
        mobileDriverService.deleteCurrentLocation();
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
