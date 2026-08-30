package pe.rutafija.operation.realtime;

import java.time.Instant;
import java.util.UUID;

record OperationStreamTicket(UUID organizationId, UUID userId, Instant expiresAt) {
    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
