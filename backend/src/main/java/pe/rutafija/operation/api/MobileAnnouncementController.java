package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.mobile.MobileAnnouncementReadResponse;
import pe.rutafija.operation.api.dto.mobile.MobileAnnouncementResponse;
import pe.rutafija.operation.application.MobileAnnouncementService;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;
import pe.rutafija.shared.config.OpenApiConfig;

import java.util.Set;
import java.util.UUID;

@RestController
@Validated
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile/announcements")
@Tag(name = "Móvil conductor: comunicados", description = "Comunicados visibles para la organización o grupo propio del conductor.")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class MobileAnnouncementController {

    private static final Set<String> SORT_PROPERTIES = Set.of("createdAt", "title");

    private final MobileAnnouncementService mobileAnnouncementService;

    public MobileAnnouncementController(MobileAnnouncementService mobileAnnouncementService) {
        this.mobileAnnouncementService = mobileAnnouncementService;
    }

    @GetMapping
    @Operation(summary = "Listar comunicados visibles para el conductor")
    public ResponseEntity<PageResponse<MobileAnnouncementResponse>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileAnnouncementService.list(
                PageableFactory.create(page, size, sort, SORT_PROPERTIES, "createdAt")
        ));
    }

    @GetMapping("/{announcementId}")
    @Operation(summary = "Consultar un comunicado visible")
    public ResponseEntity<MobileAnnouncementResponse> get(@PathVariable UUID announcementId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(mobileAnnouncementService.get(announcementId));
    }

    @PostMapping("/{announcementId}/read")
    @Operation(summary = "Registrar una lectura idempotente de comunicado")
    public ResponseEntity<MobileAnnouncementReadResponse> markRead(@PathVariable UUID announcementId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(mobileAnnouncementService.markRead(announcementId));
    }
}
