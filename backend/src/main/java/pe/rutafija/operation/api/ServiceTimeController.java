package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.api.ServiceTimeContract.*;
import pe.rutafija.operation.application.ServiceTimeCorrections;
import java.util.UUID;

@RestController
public class ServiceTimeController {
    private final ServiceTimeCorrections times;
    public ServiceTimeController(ServiceTimeCorrections times){this.times=times;}
    @GetMapping("/api/v1/scheduled-services/{id}/time-history") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<View> admin(@PathVariable UUID id){return ok(times.read(id));}
    @PostMapping("/api/v1/scheduled-services/{id}/time-corrections") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<View> correct(@PathVariable UUID id,@Valid @RequestBody Correct c){return ok(times.correct(id,c));}
    @GetMapping("/api/v1/mobile/assignments/{id}/time-history") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<View> own(@PathVariable UUID id){return ok(times.own(id));}
    @PostMapping("/api/v1/mobile/assignments/{id}/time-correction-requests") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<RequestView> request(@PathVariable UUID id,@Valid @RequestBody Request c){return ok(times.request(id,c));}
    private static <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
