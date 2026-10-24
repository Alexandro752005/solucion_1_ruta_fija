package pe.rutafija.operation.api;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.application.OperationNoticeService;
import pe.rutafija.operation.api.OperationNoticeContract.Notice;
import pe.rutafija.shared.api.PageResponse;
import java.util.UUID;
@RestController @Validated
public class OperationNoticeController {
    private final OperationNoticeService service;public OperationNoticeController(OperationNoticeService service){this.service=service;}
    @GetMapping("/api/v1/notices") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<PageResponse<Notice>> admin(@RequestParam(defaultValue="false") boolean failures,@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return noStore(service.inbox(false,failures,page,size));}
    @GetMapping("/api/v1/mobile/notices") @PreAuthorize("hasRole('CONDUCTOR')") public ResponseEntity<PageResponse<Notice>> own(@RequestParam(defaultValue="0") @Min(0) int page,@RequestParam(defaultValue="20") @Min(1) @Max(100) int size){return noStore(service.inbox(true,false,page,size));}
    @PostMapping("/api/v1/notices/{id}/read") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Notice> adminRead(@PathVariable UUID id){return noStore(service.markRead(false,id));}
    @PostMapping("/api/v1/mobile/notices/{id}/read") @PreAuthorize("hasRole('CONDUCTOR')") public ResponseEntity<Notice> ownRead(@PathVariable UUID id){return noStore(service.markRead(true,id));}
    private <T> ResponseEntity<T> noStore(T v){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(v);}
}
