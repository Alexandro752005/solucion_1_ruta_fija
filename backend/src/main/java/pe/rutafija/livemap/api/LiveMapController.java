package pe.rutafija.livemap.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.livemap.application.LiveMapService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/live-map")
@PreAuthorize("hasRole('ADMIN')")
public class LiveMapController {
    private final LiveMapService service;
    public LiveMapController(LiveMapService service){this.service=service;}
    @GetMapping("/vehicles")
    public ResponseEntity<LiveMapContract.Vehicles> vehicles(@RequestParam(defaultValue="5") int limit,
            @RequestParam(required=false) UUID groupId,@RequestParam(required=false) String driverName) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.vehicles(limit,groupId,driverName));
    }
    @GetMapping("/groups")
    public ResponseEntity<LiveMapContract.Groups> groups(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.groups());}
}
