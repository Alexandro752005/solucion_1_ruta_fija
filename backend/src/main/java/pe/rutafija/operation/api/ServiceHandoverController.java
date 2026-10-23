package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.api.ServiceHandoverContract.*;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.application.ServiceHandover;
import java.util.UUID;

@RestController
public class ServiceHandoverController {
    private final ServiceHandover handovers;
    private final pe.rutafija.operation.application.OfferSchedulingPolicy scheduling;
    public ServiceHandoverController(ServiceHandover handovers,pe.rutafija.operation.application.OfferSchedulingPolicy scheduling){this.handovers=handovers;this.scheduling=scheduling;}
    @GetMapping("/api/v1/scheduled-services/{id}/handover-candidates") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServiceTypeContract.Candidates> candidates(@PathVariable UUID id,@RequestParam java.time.Instant from,@RequestParam java.time.Instant to,@RequestParam(defaultValue="0") int offset){
        if(offset<0)throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);return ok(scheduling.candidatesForHandover(id,from,to,100,offset));
    }
    @GetMapping("/api/v1/scheduled-services/{id}/handovers") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<View> read(@PathVariable UUID id){return ok(handovers.adminView(id));}
    @PostMapping("/api/v1/scheduled-services/{id}/handovers") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<View> prepare(@PathVariable UUID id,@Valid @RequestBody Prepare c){return ok(handovers.prepare(id,c));}
    @PostMapping("/api/v1/service-handovers/{id}/withdraw") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<View> withdraw(@PathVariable UUID id,@Valid @RequestBody Withdraw c){return ok(handovers.withdraw(id,c));}
    @GetMapping("/api/v1/mobile/assignments/{id}/handovers") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<View> own(@PathVariable UUID id){return ok(handovers.ownView(id));}
    @PostMapping("/api/v1/mobile/assignments/{id}/assume-handover") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<AssignmentResponse> assume(@PathVariable UUID id,@Valid @RequestBody Assume c){var r=handovers.assume(id,c);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Idempotent-Replay",Boolean.toString(r.replayed())).body(r.value());}
    private static <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
