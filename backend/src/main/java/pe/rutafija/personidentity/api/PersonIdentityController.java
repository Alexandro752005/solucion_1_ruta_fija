package pe.rutafija.personidentity.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.personidentity.application.PersonIdentityService;
import pe.rutafija.personidentity.api.PersonIdentityContract.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PersonIdentityController {
    private final PersonIdentityService service;
    public PersonIdentityController(PersonIdentityService service){this.service=service;}
    private ResponseEntity<Status> response(Status status){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(status);}
    @GetMapping("/identity/status")
    public ResponseEntity<Status> status(){return response(service.status());}
    @PostMapping("/platform/identity/self-enrollment") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Status> enroll(@Valid @RequestBody SelfEnrollment command){return response(service.enrollOwner(command));}
    @PostMapping("/platform/admins/{userId}/identity-review") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Status> review(@PathVariable UUID userId,@Valid @RequestBody AdminReview command){return response(service.reviewAdmin(userId,command));}
    @PostMapping("/drivers/{driverId}/identity-review") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Status> reviewConductor(@PathVariable UUID driverId,@Valid @RequestBody AdminReview command){return response(service.reviewExistingConductor(driverId,command));}
}
