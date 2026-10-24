package pe.rutafija.maintenance.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.maintenance.api.MaintenanceContract.*;
import pe.rutafija.maintenance.application.VehicleMaintenanceService;
import java.time.LocalDate;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/vehicles/{vehicleId}/maintenance") @PreAuthorize("hasRole('ADMIN')")
public class VehicleMaintenanceController {
    private final VehicleMaintenanceService service;
    public VehicleMaintenanceController(VehicleMaintenanceService service){this.service=service;}
    @GetMapping public ResponseEntity<View> read(@PathVariable UUID vehicleId){return ok(service.read(vehicleId));}
    @GetMapping("/preview") public ResponseEntity<Preview> preview(@PathVariable UUID vehicleId,@RequestParam LocalDate startDate){return ok(service.preview(vehicleId,startDate));}
    @PostMapping public ResponseEntity<View> create(@PathVariable UUID vehicleId,@Valid @RequestBody Create c){return ok(service.create(vehicleId,c));}
    @PostMapping("/{id}/work-completed") public ResponseEntity<View> work(@PathVariable UUID vehicleId,@PathVariable UUID id,@Valid @RequestBody Work c){return ok(service.completeWork(vehicleId,id,c));}
    @PostMapping("/{id}/cancel") public ResponseEntity<View> cancel(@PathVariable UUID vehicleId,@PathVariable UUID id,@Valid @RequestBody Basic c){return ok(service.cancel(vehicleId,id,c));}
    @PostMapping("/return") public ResponseEntity<View> authorizeReturn(@PathVariable UUID vehicleId,@Valid @RequestBody Basic c){return ok(service.authorizeReturn(vehicleId,c));}
    private static <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
