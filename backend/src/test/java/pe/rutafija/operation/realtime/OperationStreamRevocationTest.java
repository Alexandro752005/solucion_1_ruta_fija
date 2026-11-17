package pe.rutafija.operation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.operation.application.OperationChangedEvent;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Behavioral regression: an established stream must not outlive account authorization. */
class OperationStreamRevocationTest {
    private final Organization organization = Organization.active("Synthetic stream tenant", "Test", "America/Lima");
    private final AppUser admin = AppUser.organizationUser(organization, "stream@example.test", "hash", "Synthetic Admin", UserRole.ADMIN);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final WebSocketSession socket = mock(WebSocketSession.class);
    private OperationStreamBroadcaster broadcaster;

    @BeforeEach
    void setup() {
        admin.verifyEmail(Instant.parse("2026-11-13T10:00:00Z"));
        when(socket.getId()).thenReturn("synthetic-stream");
        lenient().when(socket.isOpen()).thenReturn(true);
        when(users.findByIdAndOrganization_Id(admin.getId(), organization.getId())).thenReturn(Optional.of(admin));
        broadcaster = new OperationStreamBroadcaster(new ObjectMapper().findAndRegisterModules(),
                RealtimeAuthorizationFixture.authorization(users));
    }

    @Test
    void unchangedAuthorizedSessionReceivesItsTenantEvent() throws Exception {
        broadcaster.register(OperationStreamIdentity.from(admin), socket);
        broadcaster.broadcast(event());
        verify(socket).sendMessage(any());
        verify(socket, never()).close(any(CloseStatus.class));
    }

    @Test
    void sessionVersionRevokedAfterRegistrationReceivesNoEventAndCloses() throws Exception {
        broadcaster.register(OperationStreamIdentity.from(admin), socket);
        revokeSession();
        broadcaster.broadcast(event());
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void sessionRevokedAfterRegistrationReceivesNoReadinessMessage() throws Exception {
        broadcaster.register(OperationStreamIdentity.from(admin), socket);
        revokeSession();
        broadcaster.sendReady(organization.getId(), socket);
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void changedEmailRequiringVerificationStopsExistingStream() throws Exception {
        broadcaster.register(OperationStreamIdentity.from(admin), socket);
        admin.updateDetails("changed@example.test", "Synthetic Admin", null, UserRole.ADMIN);
        broadcaster.broadcast(event());
        verify(socket, never()).sendMessage(any());
        verify(socket).close(CloseStatus.POLICY_VIOLATION);
    }

    private void revokeSession() {
        // Models the persisted session-version increment used by account recovery/revocation.
        ReflectionTestUtils.setField(admin, "sessionVersion", admin.getSessionVersion() + 1);
    }

    private OperationChangedEvent event() {
        return new OperationChangedEvent(organization.getId(), Set.of(), "assignment.started",
                Instant.parse("2026-11-13T12:00:00Z"), Map.of());
    }
}
