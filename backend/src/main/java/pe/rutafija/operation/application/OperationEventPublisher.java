package pe.rutafija.operation.application;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OperationEventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final Clock clock;

    public OperationEventPublisher(ApplicationEventPublisher applicationEventPublisher, Clock clock) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.clock = clock;
    }

    /**
     * Publica un cambio visible para administradores y, si corresponde, solo para coordinadores
     * del grupo indicado. Un grupo nulo representa un comunicado visible para toda la organización.
     */
    public void publish(UUID organizationId, UUID visibleGroupId, String event, Map<String, Object> data) {
        applicationEventPublisher.publishEvent(new OperationChangedEvent(
                organizationId,
                visibleGroupId == null ? null : Set.of(visibleGroupId),
                event,
                Instant.now(clock),
                data
        ));
    }
}
