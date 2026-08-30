package pe.rutafija.operation.api.dto;

import java.time.Instant;

/** Ticket de un solo uso para abrir el canal WebSocket del CRM. */
public record OperationStreamTicketResponse(String ticket, Instant expiresAt) {
}
