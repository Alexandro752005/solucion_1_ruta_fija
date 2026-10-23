package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.api.ServiceAttentionContract.*;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.application.ServiceAttention;
import java.util.*;

@RestController
public class ServiceAttentionController {
    private final ServiceAttention attention;
    public ServiceAttentionController(ServiceAttention attention){this.attention=attention;}
    @PostMapping("/api/v1/mobile/assignments/{id}/interrupt") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<AssignmentResponse> interruptOwn(@PathVariable UUID id,@Valid @RequestBody OwnInterrupt c) {
        var r=attention.interruptOwn(id,c);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Idempotent-Replay",Boolean.toString(r.replayed())).body(r.value());
    }
    @PostMapping("/api/v1/assignments/{id}/interrupt") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Attention> interruptAdmin(@PathVariable UUID id,@Valid @RequestBody AdminInterrupt c){return ok(attention.interruptAdmin(id,c));}
    @GetMapping("/api/v1/scheduled-services/{id}/attention") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Attention>> list(@PathVariable UUID id){return ok(attention.adminList(id));}
    @GetMapping("/api/v1/mobile/assignments/{id}/attention") @PreAuthorize("hasRole('CONDUCTOR')")
    public ResponseEntity<List<Attention>> own(@PathVariable UUID id){return ok(attention.ownList(id));}
    @PostMapping("/api/v1/service-attention/{id}/resolve") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Attention> resolve(@PathVariable UUID id,@Valid @RequestBody Resolve c){return ok(attention.resolve(id,c));}
    private static <T> ResponseEntity<T> ok(T body){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);}
}
