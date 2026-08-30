package pe.rutafija.operation.application;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Evento interno emitido después de confirmar una operación transaccional. */
public record OperationChangedEvent(
        UUID organizationId,
        Set<UUID> visibleGroupIds,
        String event,
        Instant occurredAt,
        Map<String, Object> data
) {
    public OperationChangedEvent {
        visibleGroupIds = visibleGroupIds == null ? null : Set.copyOf(visibleGroupIds);
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
