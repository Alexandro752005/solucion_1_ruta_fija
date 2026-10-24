package pe.rutafija.operation.api;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.application.*;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import java.util.*;

/** Coded identities and original times only; no coordinates or personal details. */
@RestController
public class ServiceExecutionController {
    private final ScheduledServiceDispatch dispatch;
    private final ServiceExecution execution;
    private final CurrentUserService users;
    private final MobileDriverContextService mobile;
    private final AssignmentRepository assignments;
    public ServiceExecutionController(ScheduledServiceDispatch dispatch,ServiceExecution execution,CurrentUserService users,
        MobileDriverContextService mobile,AssignmentRepository assignments) {
        this.dispatch=dispatch;this.execution=execution;this.users=users;this.mobile=mobile;this.assignments=assignments;
    }
    @GetMapping("/api/v1/scheduled-services/{id}/execution") @PreAuthorize("hasRole('ADMIN')") @Transactional(readOnly=true)
    public ResponseEntity<List<ServiceExecution.Segment>> admin(@PathVariable UUID id) {
        dispatch.read(id); // Same tenant, identity and terms gates as service consultation.
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(execution.segments(id,users.requireTenantActor().getOrganizationId(),null));
    }
    @GetMapping("/api/v1/mobile/assignments/{id}/execution") @PreAuthorize("hasRole('CONDUCTOR')") @Transactional(readOnly=true)
    public ResponseEntity<List<ServiceExecution.Segment>> own(@PathVariable UUID id) {
        var actor=mobile.requireMobileConsultation();
        var a=assignments.findMobileAssignment(id,actor.user().getOrganizationId(),actor.driver().getId())
            .orElseThrow(()->new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"Oferta no encontrada."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(a.getServiceId()==null?List.of():
            execution.segments(a.getServiceId(),a.getOrganizationId(),actor.driver().getId()));
    }
}
