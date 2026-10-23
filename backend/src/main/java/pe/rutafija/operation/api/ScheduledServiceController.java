package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.api.ScheduledServiceContract.*;
import pe.rutafija.operation.application.ScheduledServiceDispatch;
import java.util.*;

@RestController @Validated @PreAuthorize("hasRole('ADMIN')") @RequestMapping("/api/v1/scheduled-services")
public class ScheduledServiceController {
    private final ScheduledServiceDispatch dispatch;
    public ScheduledServiceController(ScheduledServiceDispatch dispatch){this.dispatch=dispatch;}
    @GetMapping public ResponseEntity<List<Summary>> list(@RequestParam(defaultValue="100") @Min(1) @Max(100) int limit,
        @RequestParam(defaultValue="0") @Min(0) @Max(2147483547) int offset){return ok(dispatch.list(limit,offset));}
    @GetMapping("/{id}") public ResponseEntity<View> read(@PathVariable UUID id){return ok(dispatch.read(id));}
    @GetMapping("/operations/{key}") public ResponseEntity<Receipt> operation(@PathVariable @Size(min=8,max=100) String key){return ok(dispatch.operation(key));}
    @PostMapping("/{id}/offers") public ResponseEntity<View> reoffer(@PathVariable UUID id,@Valid @RequestBody Reoffer c){return ok(dispatch.reoffer(id,c));}
    @PostMapping("/{id}/cancel") public ResponseEntity<View> cancel(@PathVariable UUID id,@Valid @RequestBody Cancel c){return ok(dispatch.cancel(id,c));}
    private static <T> ResponseEntity<T> ok(T result){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);}
}
