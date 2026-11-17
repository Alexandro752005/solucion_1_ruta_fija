package pe.rutafija.operation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.UUID;

/** Canal de solo lectura: los cambios operativos siempre pasan por la API autenticada. */
@Component
public class OperationWebSocketHandler extends TextWebSocketHandler {

    private final OperationStreamBroadcaster broadcaster;

    public OperationWebSocketHandler(OperationStreamBroadcaster broadcaster, ObjectMapper objectMapper) {
        this.broadcaster = broadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID organizationId = OperationStreamHandshakeInterceptor.organizationId(session.getAttributes());
        OperationStreamIdentity identity = OperationStreamHandshakeInterceptor.identity(session.getAttributes());
        if (organizationId == null || identity == null || !organizationId.equals(identity.organizationId())) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        broadcaster.register(identity, session);
        broadcaster.sendReady(organizationId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // El cliente no puede efectuar operaciones a través del socket.
        session.close(CloseStatus.POLICY_VIOLATION);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID organizationId = OperationStreamHandshakeInterceptor.organizationId(session.getAttributes());
        if (organizationId != null) {
            broadcaster.unregister(organizationId, session);
        }
    }
}
