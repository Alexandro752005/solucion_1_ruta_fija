package pe.rutafija.operation.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;
import pe.rutafija.operation.application.OperationChangedEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Difunde cambios únicamente a ADMIN activos dentro de la organización.
 */
@Component
public class OperationStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(OperationStreamBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final OperationStreamAuthorization authorization;
    private final Map<UUID, ConcurrentMap<String, StreamSession>> sessionsByOrganization = new ConcurrentHashMap<>();

    public OperationStreamBroadcaster(
            ObjectMapper objectMapper,
            OperationStreamAuthorization authorization
    ) {
        this.objectMapper = objectMapper;
        this.authorization = authorization;
    }

    void register(OperationStreamIdentity identity, WebSocketSession session) {
        if (!authorization.allows(identity)) {
            reject(identity.organizationId(), session);
            return;
        }
        sessionsByOrganization
                .computeIfAbsent(identity.organizationId(), ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), new StreamSession(identity, session));
    }

    public void unregister(UUID organizationId, WebSocketSession session) {
        ConcurrentMap<String, StreamSession> sessions = sessionsByOrganization.get(organizationId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session.getId());
        if (sessions.isEmpty()) {
            sessionsByOrganization.remove(organizationId, sessions);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(OperationChangedEvent event) {
        ConcurrentMap<String, StreamSession> sessions = sessionsByOrganization.get(event.organizationId());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(new StreamEvent(event.event(), event.occurredAt(), event.data()));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize operational WebSocket event");
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (StreamSession recipient : sessions.values()) {
            send(event.organizationId(), recipient, message);
        }
    }

    public void sendReady(UUID organizationId, WebSocketSession session) {
        ConcurrentMap<String, StreamSession> sessions = sessionsByOrganization.get(organizationId);
        StreamSession recipient = sessions == null ? null : sessions.get(session.getId());
        if (recipient == null || recipient.session() != session) {
            // Registration may already have rejected and closed the socket.
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(new StreamEvent("stream.ready", Instant.now(), Map.of()));
            send(organizationId, recipient, new TextMessage(payload));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize operational WebSocket readiness event");
        }
    }

    private void send(UUID organizationId, StreamSession recipient, TextMessage message) {
        WebSocketSession session = recipient.session();
        try {
            synchronized (session) {
                // Revalidate readiness and every event; never adopt a newer login's version.
                if (!authorization.allows(recipient.identity())) {
                    reject(organizationId, session);
                    return;
                }
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            }
        } catch (Exception exception) {
            unregister(organizationId, session);
            try {
                session.close();
            } catch (Exception ignored) {
                // La sesión ya no es utilizable; no se vuelve a propagar el error.
            }
        }
    }

    private void reject(UUID organizationId, WebSocketSession session) {
        unregister(organizationId, session);
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
            // A rejected authorization must never leak an event or a technical failure.
        }
    }

    private record StreamSession(OperationStreamIdentity identity, WebSocketSession session) {
    }

    private record StreamEvent(String event, Instant occurredAt, Map<String, Object> data) {
    }
}
