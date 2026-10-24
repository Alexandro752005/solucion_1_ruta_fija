package pe.rutafija.adminlifecycle.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.adminlifecycle.application.AdminLifecycleService;
import pe.rutafija.adminlifecycle.api.AdminLifecycleContract.*;
import pe.rutafija.identity.application.EmailOtpTicket;
import pe.rutafija.shared.security.TrustedOriginValidator;
import pe.rutafija.terms.api.dto.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AdminLifecycleController {
    private final AdminLifecycleService service;
    private final TrustedOriginValidator origins;
    public AdminLifecycleController(AdminLifecycleService service,TrustedOriginValidator origins){this.service=service;this.origins=origins;}
    private <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/platform/admins") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<Summary>> list(){return response(service.list());}
    @PostMapping("/platform/admins") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Created> create(@Valid @RequestBody Create command){return response(service.create(command));}
    @PostMapping("/platform/admins/{outgoingId}/replacement") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Created> replacement(@PathVariable UUID outgoingId,@Valid @RequestBody Replacement command){return response(service.replacement(outgoingId,command));}
    @PostMapping("/platform/admins/{candidateId}/activation-credential") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Created> rotate(@PathVariable UUID candidateId,@Valid @RequestBody Rotate command){return response(service.rotate(candidateId,command));}
    @PostMapping("/platform/admins/{adminId}/revoke") @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Summary> revoke(@PathVariable UUID adminId,@Valid @RequestBody Rotate command){return response(service.revoke(adminId,command));}
    @PostMapping("/admin-activation/claim")
    public ResponseEntity<Claimed> claim(@Valid @RequestBody Claim command,HttpServletRequest request){origins.validate(request);return response(service.claim(command));}
    @GetMapping("/admin-activation/status")
    public ResponseEntity<Status> status(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token){return response(service.status(token));}
    @PostMapping("/admin-activation/otp/begin")
    public ResponseEntity<EmailOtpTicket> begin(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token,HttpServletRequest request){origins.validate(request);return response(service.beginOtp(token));}
    @PostMapping("/admin-activation/otp/resend")
    public ResponseEntity<EmailOtpTicket> resend(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token,@Valid @RequestBody OtpToken command,HttpServletRequest request){origins.validate(request);return response(service.resendOtp(token,command));}
    @PostMapping("/admin-activation/otp/verify")
    public ResponseEntity<Status> verify(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token,@Valid @RequestBody VerifyOtp command,HttpServletRequest request){origins.validate(request);return response(service.verifyOtp(token,command));}
    @GetMapping("/admin-activation/terms")
    public ResponseEntity<AdminTermsCurrentResponse> terms(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token){return response(service.currentTerms(token));}
    @PostMapping("/admin-activation/terms")
    public ResponseEntity<AdminTermsAcceptanceResponse> accept(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token,@Valid @RequestBody AcceptAdminTermsRequest command,HttpServletRequest request){origins.validate(request);return response(service.acceptTerms(token,command));}
    @PostMapping("/admin-activation/complete")
    public ResponseEntity<Status> complete(@RequestHeader(value="X-Admin-Activation-Token",required=false)String token,HttpServletRequest request){origins.validate(request);return response(service.complete(token));}
}
