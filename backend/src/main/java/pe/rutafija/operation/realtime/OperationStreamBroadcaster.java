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
import pe.rutafija.fleet.infrastructure.GroupCoordinatorRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.operation.application.OperationChangedEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Difunde cambios únicamente a usuarios todavía autorizados dentro de la organización.
 * Los administradores ven todo el tenant; los coordinadores solo eventos de sus grupos.
 */
@Component
public class OperationStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(OperationStreamBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final AppUserRepository userRepository;
    private final GroupCoordinatorRepository groupCoordinatorRepository;
    private final Map<UUID, ConcurrentMap<String, StreamSession>> sessionsByOrganization = new ConcurrentHashMap<>();

    public OperationStreamBroadcaster(
            ObjectMapper objectMapper,
            AppUserRepository userRepository,
            GroupCoordinatorRepository groupCoordinatorRepository
    ) {
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.groupCoordinatorRepository = groupCoordinatorRepository;
    }

    public void register(UUID organizationId, UUID userId, WebSocketSession session) {
        sessionsByOrganization
                .computeIfAbsent(organizationId, ignored -> new ConcurrentHashMap<>())
                .put(session.getId(), new StreamSession(userId, session));
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
            if (canReceive(event, recipient.userId())) {
                send(event.organizationId(), recipient.session(), message);
            }
        }
    }

    public void sendReady(UUID organizationId, WebSocketSession session) {
        try {
            String payload = objectMapper.writeValueAsString(new StreamEvent("stream.ready", Instant.now(), Map.of()));
            send(organizationId, session, new TextMessage(payload));
        } catch (JsonProcessingException exception) {
            log.warn("Unable to serialize operational WebSocket readiness event");
        }
    }

    private boolean canReceive(OperationChangedEvent event, UUID userId) {
        AppUser user = userRepository.findByIdAndOrganization_Id(userId, event.organizationId()).orElse(null);
        if (user == null || !user.isActive() || !user.getOrganization().isActive()) {
            return false;
        }
        if (user.getRole() == UserRole.ADMINISTRADOR) {
            return true;
        }
        if (user.getRole() != UserRole.COORDINADOR) {
            return false;
        }
        if (event.visibleGroupIds() == null) {
            return true;
        }
        return event.visibleGroupIds().stream()
                .anyMatch(groupId -> groupCoordinatorRepository.existsByGroup_IdAndUser_Id(groupId, userId));
    }

    private void send(UUID organizationId, WebSocketSession session, TextMessage message) {
        try {
            synchronized (session) {
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

    private record StreamSession(UUID userId, WebSocketSession session) {
    }

    private record StreamEvent(String event, Instant occurredAt, Map<String, Object> data) {
    }
}
