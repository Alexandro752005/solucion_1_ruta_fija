package pe.rutafija.onboarding.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.*;
import pe.rutafija.onboarding.application.ConductorDocumentaryService;
import pe.rutafija.shared.security.TrustedOriginValidator;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/onboarding")
public class ConductorDocumentaryController {
    private final ConductorDocumentaryService service;
    private final TrustedOriginValidator origins;
    public ConductorDocumentaryController(ConductorDocumentaryService service,TrustedOriginValidator origins){this.service=service;this.origins=origins;}
    private ResponseEntity<Dossier> response(Dossier value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}

    @GetMapping("/requests/{id}/documentary-expediente") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> admin(@PathVariable UUID id){return response(service.adminDossier(id));}
    @PutMapping("/requests/{id}/documentary-expediente") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> save(@PathVariable UUID id,@Valid @RequestBody SaveDocuments command){return response(service.saveAdmin(id,command));}
    @PostMapping("/requests/{id}/return") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> returnRequest(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.returnForCorrection(id,command));}
    @PostMapping("/requests/{id}/extend") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> extend(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.extend(id,command));}
    @PostMapping("/requests/{id}/approve") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> approve(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.approve(id,command));}
    @PostMapping("/requests/{id}/reject") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> reject(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.reject(id,command));}
    @PostMapping("/requests/{id}/provision") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> provision(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.provision(id,command));}
    @PostMapping("/requests/{id}/provision-existing") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> provisionExisting(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.provisionExisting(id,command));}
    @PostMapping("/requests/{id}/resubmit-existing") @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Dossier> resubmitExisting(@PathVariable UUID id,@Valid @RequestBody Decision command){return response(service.resubmitExisting(id,command));}

    @GetMapping("/applicant/requests/{id}/documentary-status")
    public ResponseEntity<Dossier> applicant(@PathVariable UUID id,@RequestHeader(value="X-Onboarding-Token",required=false)String token){return response(service.applicantDossier(id,token));}
    @PutMapping("/applicant/requests/{id}/documents")
    public ResponseEntity<Dossier> correct(@PathVariable UUID id,@RequestHeader(value="X-Onboarding-Token",required=false)String token,
            @Valid @RequestBody SaveDocuments command,HttpServletRequest request){origins.validate(request);return response(service.saveApplicant(id,token,command));}
    @PostMapping("/applicant/requests/{id}/resubmit")
    public ResponseEntity<Dossier> resubmit(@PathVariable UUID id,@RequestHeader(value="X-Onboarding-Token",required=false)String token,
            @Valid @RequestBody Decision command,HttpServletRequest request){origins.validate(request);return response(service.resubmit(id,token,command));}
    @PostMapping("/applicant/requests/{id}/reapply")
    public ResponseEntity<Dossier> reapply(@PathVariable UUID id,@RequestHeader(value="X-Onboarding-Token",required=false)String token,
            @Valid @RequestBody Reapply command,HttpServletRequest request){origins.validate(request);return response(service.reapply(id,token,command,request.getRemoteAddr()));}
}
