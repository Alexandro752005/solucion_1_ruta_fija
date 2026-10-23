package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventsRequest;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventsResponse;
import pe.rutafija.operation.application.MobileOfflineSyncService;
import pe.rutafija.shared.config.OpenApiConfig;

@RestController
@Profile("test")
@ConditionalOnProperty(
        prefix = "app.mobile.offline-sync",
        name = "enabled",
        havingValue = "true"
)
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile/sync")
@Tag(
        name = "Móvil conductor: sincronización limitada",
        description = "Aplica en orden solo disponibilidad, inicio y cierre propios con recibos idempotentes."
)
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class MobileOfflineSyncController {

    private final MobileOfflineSyncService mobileOfflineSyncService;

    public MobileOfflineSyncController(MobileOfflineSyncService mobileOfflineSyncService) {
        this.mobileOfflineSyncService = mobileOfflineSyncService;
    }

    @PostMapping("/events")
    @Operation(summary = "Conciliar en orden una cola móvil limitada")
    public ResponseEntity<MobileSyncEventsResponse> synchronize(
            @Valid @RequestBody MobileSyncEventsRequest request
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mobileOfflineSyncService.synchronize(request));
    }
}
