package pe.rutafija.operation.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import com.fasterxml.jackson.databind.ObjectMapper;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.shared.security.TrustedOriginValidator;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.config.OperationProperties;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OperationStreamTicketRevocationTest {
    private final Organization organization = Organization.active("Synthetic ticket tenant", "Test", "America/Lima");
    private final AppUser admin = AppUser.organizationUser(organization, "ticket@example.test", "hash", "Synthetic Admin", UserRole.ADMIN);

    @Test
    void ticketIssuedBeforeSessionRevocationCannotBeConsumed() {
        OperationStreamTicketService service = service();
        String ticket = service.issue().ticket();
        ReflectionTestUtils.setField(admin, "sessionVersion", admin.getSessionVersion() + 1);
        assertTrue(service.consume(ticket).isEmpty(), "Revoked bearer session must revoke its outstanding stream ticket");
    }

    @Test
    void unchangedAuthorizedTicketIsConsumedOnlyOnce() {
        OperationStreamTicketService service = service();
        String ticket = service.issue().ticket();
        assertTrue(service.consume(ticket).isPresent());
        assertTrue(service.consume(ticket).isEmpty());
    }

    @Test
    void revocationBetweenHandshakeAndRegistrationCannotAdoptNewSessionVersion() throws Exception {
        OperationStreamTicketService tickets = service();
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByIdAndOrganization_Id(admin.getId(), organization.getId())).thenReturn(Optional.of(admin));
        OperationStreamBroadcaster broadcaster = new OperationStreamBroadcaster(new ObjectMapper().findAndRegisterModules(),
                RealtimeAuthorizationFixture.authorization(users));
        OperationWebSocketHandler handler = new OperationWebSocketHandler(broadcaster, new ObjectMapper());
        TrustedOriginValidator origins = mock(TrustedOriginValidator.class);
        when(origins.isAllowedOrigin("http://localhost")).thenReturn(true);
        OperationStreamHandshakeInterceptor handshake = new OperationStreamHandshakeInterceptor(origins, tickets);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/ws/operations");
        request.addHeader("Origin", "http://localhost");
        request.setQueryString("ticket=" + tickets.issue().ticket());
        Map<String, Object> attributes = new HashMap<>();
        assertTrue(handshake.beforeHandshake(new ServletServerHttpRequest(request),
                new ServletServerHttpResponse(new MockHttpServletResponse()), handler, attributes));
        ReflectionTestUtils.setField(admin, "sessionVersion", admin.getSessionVersion() + 1);
        WebSocketSession socket = mock(WebSocketSession.class);
        when(socket.getAttributes()).thenReturn(attributes);
        lenient().when(socket.getId()).thenReturn("synthetic-handshake-race");
        lenient().when(socket.isOpen()).thenReturn(true);
        handler.afterConnectionEstablished(socket);
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    private OperationStreamTicketService service() {
        admin.verifyEmail(Instant.parse("2026-11-13T10:00:00Z"));
        CurrentUserService current = mock(CurrentUserService.class);
        when(current.requireTenantActor()).thenReturn(admin);
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByIdAndOrganization_Id(admin.getId(), organization.getId())).thenReturn(Optional.of(admin));
        return new OperationStreamTicketService(current, new OperationProperties(Duration.ofSeconds(30)),
                Clock.fixed(Instant.parse("2026-11-13T12:00:00Z"), ZoneOffset.UTC),
                RealtimeAuthorizationFixture.authorization(users));
    }
}
