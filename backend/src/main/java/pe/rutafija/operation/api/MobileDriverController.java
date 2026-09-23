package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import pe.rutafija.shared.config.OpenApiConfig;

@RestController
@Validated
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile")
@Tag(
        name = "Móvil conductor: perfil y ubicación",
        description = "La ubicación es propia, vigente y efímera; requiere consentimiento funcional y atestación de permiso."
)
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
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
    @Operation(
            summary = "Crear o reemplazar exclusivamente la ubicación vigente propia",
            description = "No crea historial. El servidor calcula la retención y no emite coordenadas en auditoría ni WebSocket."
    )
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
