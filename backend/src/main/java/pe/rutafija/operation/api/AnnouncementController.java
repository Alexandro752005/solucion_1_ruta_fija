package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.AnnouncementCreateRequest;
import pe.rutafija.operation.api.dto.AnnouncementResponse;
import pe.rutafija.operation.application.AnnouncementService;
import pe.rutafija.operation.domain.AnnouncementAudienceType;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.api.PageableFactory;

import java.util.Set;

@RestController
@Validated
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private static final Set<String> SORT_PROPERTIES = Set.of("title", "createdAt");

    private final AnnouncementService announcementService;

    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar comunicados visibles")
    public ResponseEntity<PageResponse<AnnouncementResponse>> list(
            @RequestParam(required = false) AnnouncementAudienceType audienceType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(announcementService.listAnnouncements(
                        audienceType,
                        PageableFactory.create(page, size, sort, SORT_PROPERTIES, "createdAt")
                ));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Publicar un comunicado desde el CRM web")
    public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(announcementService.createAnnouncement(request));
    }
}
