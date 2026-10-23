package pe.rutafija.qualification.api;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.Requirement;
import pe.rutafija.qualification.application.DriverQualificationService;
import pe.rutafija.qualification.api.QualificationContract.*;
import pe.rutafija.shared.security.MobileDriverContextService;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class DriverQualificationController {
    private final DriverQualificationService service;private final MobileDriverContextService mobile;
    public DriverQualificationController(DriverQualificationService service,MobileDriverContextService mobile){this.service=service;this.mobile=mobile;}
    private <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/drivers/{driverId}/qualification") public ResponseEntity<Qualification> status(@PathVariable UUID driverId){return response(service.status(driverId));}
    @GetMapping("/drivers/{driverId}/qualification/expediente") public ResponseEntity<Qualification> expediente(@PathVariable UUID driverId){return response(service.expediente(driverId));}
    @PostMapping("/drivers/{driverId}/qualification/reviews") public ResponseEntity<Qualification> reviews(@PathVariable UUID driverId,@Valid @RequestBody ReviewCommand c){return response(service.reviews(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/periods") public ResponseEntity<Qualification> period(@PathVariable UUID driverId,@Valid @RequestBody PeriodCommand c){return response(service.period(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/periods/{periodId}/revoke") public ResponseEntity<Qualification> revoke(@PathVariable UUID driverId,@PathVariable UUID periodId,@Valid @RequestBody Basic c){return response(service.revoke(driverId,periodId,c));}
    @PostMapping("/drivers/{driverId}/qualification/suspend") public ResponseEntity<Qualification> suspend(@PathVariable UUID driverId,@Valid @RequestBody Basic c){return response(service.suspend(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/resume") public ResponseEntity<Qualification> resume(@PathVariable UUID driverId,@Valid @RequestBody Basic c){return response(service.resume(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/activate") public ResponseEntity<Qualification> activate(@PathVariable UUID driverId,@Valid @RequestBody Basic c){return response(service.activate(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/personal-documents") public ResponseEntity<Qualification> personal(@PathVariable UUID driverId,@Valid @RequestBody PersonalCommand c){return response(service.personal(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/vehicle-correction") public ResponseEntity<Qualification> vehicleCorrection(@PathVariable UUID driverId,@Valid @RequestBody VehicleCorrection c){return response(service.vehicleCorrection(driverId,c));}
    @PostMapping("/drivers/{driverId}/qualification/unit-requests/{requestId}/decision") public ResponseEntity<Qualification> decision(@PathVariable UUID driverId,@PathVariable UUID requestId,@Valid @RequestBody UnitDecision c){return response(service.unitDecision(driverId,requestId,c));}
    @GetMapping("/mobile/qualification") public ResponseEntity<Qualification> mobileStatus(){return response(service.mobileStatus());}
    @GetMapping("/mobile/qualification/personal-catalog") public ResponseEntity<List<Requirement>> mobileCatalog(){mobile.requireMobileConsultation();return response(ConductorDocumentaryCatalog.REQUIREMENTS);}
    @PostMapping("/mobile/qualification/documents") public ResponseEntity<Qualification> mobileDocuments(@Valid @RequestBody PersonalCommand c){return response(service.mobileDocuments(c));}
    @PostMapping("/mobile/qualification/unit-requests") public ResponseEntity<Qualification> request(@Valid @RequestBody UnitCommand c){return response(service.unitRequest(c));}
}
