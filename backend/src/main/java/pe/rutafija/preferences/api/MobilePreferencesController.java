package pe.rutafija.preferences.api;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.preferences.application.MobilePreferencesService;
import pe.rutafija.preferences.api.MobilePreferencesContract.*;

@RestController
@PreAuthorize("hasRole('CONDUCTOR')")
@RequestMapping("/api/v1/mobile/preferences")
public class MobilePreferencesController {
    private final MobilePreferencesService service;
    public MobilePreferencesController(MobilePreferencesService service) {this.service=service;}
    @GetMapping public ResponseEntity<View> read() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read());
    }
    @PutMapping public ResponseEntity<View> update(@Valid @RequestBody Update request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.update(request));
    }
}
