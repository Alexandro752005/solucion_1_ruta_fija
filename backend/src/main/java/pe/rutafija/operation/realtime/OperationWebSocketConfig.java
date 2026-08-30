package pe.rutafija.operation.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import pe.rutafija.shared.config.SecurityProperties;

@Configuration
@EnableWebSocket
public class OperationWebSocketConfig implements WebSocketConfigurer {

    private final OperationWebSocketHandler handler;
    private final OperationStreamHandshakeInterceptor handshakeInterceptor;
    private final SecurityProperties securityProperties;

    public OperationWebSocketConfig(
            OperationWebSocketHandler handler,
            OperationStreamHandshakeInterceptor handshakeInterceptor,
            SecurityProperties securityProperties
    ) {
        this.handler = handler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.securityProperties = securityProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/operations")
                .addInterceptors(handshakeInterceptor)
                .setAllowedOrigins(securityProperties.cors().allowedOrigins().toArray(String[]::new));
    }
}
