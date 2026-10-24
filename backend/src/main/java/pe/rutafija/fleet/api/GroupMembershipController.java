package pe.rutafija.fleet.api;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.fleet.application.GroupMembershipService;
import pe.rutafija.fleet.api.dto.GroupResourceContract.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/groups/{groupId}")
@PreAuthorize("hasRole('ADMIN')")
public class GroupMembershipController {
    private final GroupMembershipService service;
    public GroupMembershipController(GroupMembershipService service){this.service=service;}
    @GetMapping("/resources")
    public ResponseEntity<Resources> read(@PathVariable UUID groupId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.read(groupId));}
    @PostMapping("/memberships")
    public ResponseEntity<Resources> change(@PathVariable UUID groupId,@Valid @RequestBody MembershipCommand command){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.change(groupId,command));}
}
