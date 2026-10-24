package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.application.ServiceGpsAccess;
import pe.rutafija.operation.api.ServiceGpsContract.*;
import java.util.*;

@RestController @Validated
public class ServiceGpsController {
    private final ServiceGpsAccess access;
    public ServiceGpsController(ServiceGpsAccess access){this.access=access;}
    @PostMapping("/api/v1/mobile/gps/access-requests") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<RequestView> request(@Valid @RequestBody Request c){return ok(access.request(c));}
    @GetMapping("/api/v1/mobile/gps/access-requests") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<List<RequestView>> ownList(@RequestParam UUID serviceId,@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int offset){return ok(access.ownList(serviceId,limit,offset));}
    @GetMapping("/api/v1/mobile/gps/access-requests/{id}") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<RequestView> own(@PathVariable UUID id){return ok(access.own(id));}
    @PostMapping("/api/v1/mobile/gps/access-requests/{id}/points") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<Points> deliver(@PathVariable UUID id,@Valid @RequestBody Delivery c){return ok(access.deliver(id,c));}
    @GetMapping("/api/v1/gps/access-requests") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<RequestView>> pending(@RequestParam(defaultValue="50") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue="0") @Min(0) @Max(100000) int offset,@RequestParam(required=false) UUID serviceId){return ok(access.pending(limit,offset,serviceId));}
    @PostMapping("/api/v1/gps/access-requests/{id}/review") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RequestView> review(@PathVariable UUID id,@Valid @RequestBody Review c){return ok(access.review(id,c));}
    @PostMapping("/api/v1/incidents/{id}/gps-evidence") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IncidentEvidence> attach(@PathVariable UUID id,@Valid @RequestBody Attach c){return ok(access.attach(id,c));}
    @GetMapping("/api/v1/gps/incident-evidence/{id}") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<IncidentEvidence> evidence(@PathVariable UUID id){return ok(access.evidence(id));}
    private static <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
