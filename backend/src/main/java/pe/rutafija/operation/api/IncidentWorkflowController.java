package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.application.IncidentWorkflow;
import pe.rutafija.operation.api.IncidentWorkflowContract.*;
import pe.rutafija.operation.api.dto.IncidentResponse;
import java.util.*;

@RestController @RequestMapping("/api/v1/incidents") @PreAuthorize("hasRole('ADMIN')")
public class IncidentWorkflowController {
    private final IncidentWorkflow workflow;
    public IncidentWorkflowController(IncidentWorkflow workflow){this.workflow=workflow;}
    private <T> ResponseEntity<T> ok(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping("/responsible-candidates") public ResponseEntity<List<Candidate>> candidates(){return ok(workflow.responsibleCandidates());}
    @GetMapping("/operations/{key}") public ResponseEntity<Operation> operation(@PathVariable UUID key){return ok(workflow.operation(key));}
    @GetMapping("/{id}/history") public ResponseEntity<List<Action>> history(@PathVariable UUID id){return ok(workflow.history(id));}
    @PostMapping("/{id}/responsibility") public ResponseEntity<IncidentResponse> assign(@PathVariable UUID id,@Valid @RequestBody Responsibility body){return ok(workflow.responsibility(id,body));}
    @PostMapping("/{id}/assessment") public ResponseEntity<IncidentResponse> assess(@PathVariable UUID id,@Valid @RequestBody Assessment body){return ok(workflow.assess(id,body));}
    @PostMapping("/{id}/closure") public ResponseEntity<IncidentResponse> close(@PathVariable UUID id,@Valid @RequestBody Closure body){return ok(workflow.close(id,body));}
}
