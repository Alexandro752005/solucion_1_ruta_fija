package pe.rutafija.operation.api;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pe.rutafija.operation.api.AnnouncementAttentionContract.*;
import pe.rutafija.operation.application.AnnouncementDelivery;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/announcements") @PreAuthorize("hasRole('ADMIN')")
public class AnnouncementAttentionController {
    private final AnnouncementDelivery delivery;
    public AnnouncementAttentionController(AnnouncementDelivery delivery){this.delivery=delivery;}
    @GetMapping("/{id}/attention") public ResponseEntity<Attention> attention(@PathVariable UUID id){return noStore(delivery.attention(id));}
    @GetMapping("/operations/{key}") public ResponseEntity<Publication> operation(@PathVariable UUID key){return noStore(delivery.publication(key));}
    @PostMapping("/{id}/recipients/{userId}/withdraw") public ResponseEntity<Attention> withdraw(@PathVariable UUID id,@PathVariable UUID userId,@Valid @RequestBody Withdrawal command){return noStore(delivery.withdraw(id,userId,command));}
    private <T> ResponseEntity<T> noStore(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
}
