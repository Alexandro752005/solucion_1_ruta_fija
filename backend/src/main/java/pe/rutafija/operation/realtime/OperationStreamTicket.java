package pe.rutafija.operation.realtime;

import java.time.Instant;
record OperationStreamTicket(OperationStreamIdentity identity, Instant expiresAt) {
    boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
