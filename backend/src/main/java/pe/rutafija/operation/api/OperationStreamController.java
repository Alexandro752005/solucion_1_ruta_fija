package pe.rutafija.operation.api;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.operation.api.dto.OperationStreamTicketResponse;
import pe.rutafija.operation.realtime.OperationStreamTicketService;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationStreamController {

    private final OperationStreamTicketService ticketService;

    public OperationStreamController(OperationStreamTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/stream-ticket")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Emitir un ticket de un solo uso para el canal operativo")
    public ResponseEntity<OperationStreamTicketResponse> issueTicket() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ticketService.issue());
    }
}
