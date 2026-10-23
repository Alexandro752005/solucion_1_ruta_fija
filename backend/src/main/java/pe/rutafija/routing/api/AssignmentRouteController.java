package pe.rutafija.routing.api;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.routing.application.RouteCatalogService;
import pe.rutafija.routing.api.RouteContract.Assigned;
import java.util.UUID;

@RestController
public class AssignmentRouteController {
    private final RouteCatalogService service;
    private final pe.rutafija.routing.application.AssignmentRouteReplacementService replacement;
    public AssignmentRouteController(RouteCatalogService service,pe.rutafija.routing.application.AssignmentRouteReplacementService replacement){this.service=service;this.replacement=replacement;}
    @PostMapping("/api/v1/assignments/{id}/route-replacement") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<pe.rutafija.operation.api.dto.AssignmentResponse> replace(@PathVariable UUID id,@jakarta.validation.Valid @RequestBody RouteContract.Replace c){return ok(replacement.replace(id,c));}
    @GetMapping("/api/v1/assignment-route-operations/{key}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<pe.rutafija.operation.api.dto.AssignmentResponse> operation(@PathVariable String key){return ok(replacement.operation(key));}
    @GetMapping("/api/v1/assignments/{id}/route") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Assigned> read(@PathVariable UUID id){return ok(service.assigned(id));}
    @GetMapping("/api/v1/mobile/assignments/{id}/route") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<Assigned> own(@PathVariable UUID id){return ok(service.mobileAssigned(id));}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
