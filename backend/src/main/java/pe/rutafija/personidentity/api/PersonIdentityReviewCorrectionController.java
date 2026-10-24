package pe.rutafija.personidentity.api;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.personidentity.api.PersonIdentityContract.*;
import pe.rutafija.personidentity.application.PersonIdentityReviewCorrectionService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/drivers")
public class PersonIdentityReviewCorrectionController {
    private final PersonIdentityReviewCorrectionService service;
    public PersonIdentityReviewCorrectionController(PersonIdentityReviewCorrectionService service){this.service=service;}
    @PostMapping("/{driverId}/identity-review/corrections") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Status> correct(@PathVariable UUID driverId,@Valid @RequestBody ConductorReviewCorrection command){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.correct(driverId,command));
    }
}
