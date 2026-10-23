package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.api.ServiceTypeContract.*;
import pe.rutafija.operation.application.*;
import java.time.Instant;
import java.util.*;

@RestController @Validated @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/v1")
public class ServiceTypeController {
    private final ServiceTypeService types; private final OfferSchedulingPolicy policy;
    public ServiceTypeController(ServiceTypeService types,OfferSchedulingPolicy policy){this.types=types;this.policy=policy;}
    @GetMapping("/service-types") public ResponseEntity<List<Summary>> list(@RequestParam(defaultValue="100") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) @Max(2147483547) int offset){return ok(types.list(limit,offset));}
    @GetMapping("/service-types/{id}") public ResponseEntity<View> read(@PathVariable UUID id){return ok(types.read(id));}
    @GetMapping("/service-types/operations/{key}") public ResponseEntity<View> operation(@PathVariable @Size(min=8,max=100) String key){return ok(types.operation(key));}
    @PostMapping("/service-types") public ResponseEntity<View> create(@Valid @RequestBody Create c){return ok(types.create(c));}
    @PostMapping("/service-types/{id}/versions") public ResponseEntity<View> revise(@PathVariable UUID id,@Valid @RequestBody Revise c){return ok(types.revise(id,c));}
    @PostMapping("/service-types/{id}/status") public ResponseEntity<View> status(@PathVariable UUID id,@Valid @RequestBody Status c){return ok(types.status(id,c));}
    @GetMapping("/assignment-candidates") public ResponseEntity<Candidates> candidates(@RequestParam UUID serviceTypeVersionId,@RequestParam UUID routeVersionId,@RequestParam Instant scheduledAt,@RequestParam Instant scheduledEndAt,
        @RequestParam(defaultValue="100") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) @Max(2147483547) int offset,
        @RequestParam(required=false) UUID serviceId){return ok(policy.candidates(serviceTypeVersionId,routeVersionId,scheduledAt,scheduledEndAt,limit,offset,serviceId));}
    private static <T> ResponseEntity<T> ok(T result){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);}
}
