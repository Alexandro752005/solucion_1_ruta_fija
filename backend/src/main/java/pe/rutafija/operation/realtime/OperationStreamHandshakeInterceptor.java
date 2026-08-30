package pe.rutafija.operation.realtime;

import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;
import pe.rutafija.shared.security.TrustedOriginValidator;

import java.util.Map;
import java.util.UUID;

/** Valida origen y consume el ticket antes de aceptar el WebSocket. */
@Component
public class OperationStreamHandshakeInterceptor implements HandshakeInterceptor {

    static final String ORGANIZATION_ID_ATTRIBUTE = "operation.organizationId";
    static final String USER_ID_ATTRIBUTE = "operation.userId";

    private final TrustedOriginValidator trustedOriginValidator;
    private final OperationStreamTicketService ticketService;

    public OperationStreamHandshakeInterceptor(
            TrustedOriginValidator trustedOriginValidator,
            OperationStreamTicketService ticketService
    ) {
        this.trustedOriginValidator = trustedOriginValidator;
        this.ticketService = ticketService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Map<String, Object> attributes
    ) {
        if (!trustedOriginValidator.isAllowedOrigin(request.getHeaders().getOrigin())) {
            return false;
        }
        String ticket = UriComponentsBuilder.fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("ticket");
        return ticketService.consume(ticket)
                .map(streamTicket -> {
                    attributes.put(ORGANIZATION_ID_ATTRIBUTE, streamTicket.organizationId());
                    attributes.put(USER_ID_ATTRIBUTE, streamTicket.userId());
                    return true;
                })
                .orElse(false);
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler webSocketHandler,
            Exception exception
    ) {
        // No se conserva el ticket ni se registra información sensible en logs.
    }

    static UUID organizationId(Map<String, Object> attributes) {
        Object value = attributes.get(ORGANIZATION_ID_ATTRIBUTE);
        return value instanceof UUID organizationId ? organizationId : null;
    }

    static UUID userId(Map<String, Object> attributes) {
        Object value = attributes.get(USER_ID_ATTRIBUTE);
        return value instanceof UUID userId ? userId : null;
    }
}
