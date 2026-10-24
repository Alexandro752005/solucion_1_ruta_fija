package pe.rutafija.routing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.routing.application.RouteCatalogService;
import pe.rutafija.routing.api.RouteContract.*;
import java.util.*;

@RestController @Validated @RequestMapping("/api/v1/routes") @PreAuthorize("hasRole('ADMIN')")
public class RouteController {
    private final RouteCatalogService service;
    public RouteController(RouteCatalogService service){this.service=service;}
    @GetMapping public ResponseEntity<List<Summary>> list(@RequestParam(defaultValue="100") @Min(1) @Max(100) int limit,@RequestParam(defaultValue="0") @Min(0) int offset){return ok(service.list(limit,offset));}
    @GetMapping("/{id}") public ResponseEntity<View> get(@PathVariable UUID id){return ok(service.read(id));}
    @GetMapping("/operations/{key}") public ResponseEntity<View> operation(@PathVariable @Size(min=8,max=100) String key){return ok(service.byOperation(key));}
    @PostMapping public ResponseEntity<View> create(@Valid @RequestBody Create c){return ok(service.create(c));}
    @PostMapping("/{id}/versions") public ResponseEntity<View> revise(@PathVariable UUID id,@Valid @RequestBody Revise c){return ok(service.revise(id,c));}
    @PostMapping("/{id}/status") public ResponseEntity<View> status(@PathVariable UUID id,@Valid @RequestBody Status c){return ok(service.status(id,c));}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
