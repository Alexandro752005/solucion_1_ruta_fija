package pe.rutafija.operation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationStreamBroadcasterTest {

    @Mock
    AppUserRepository userRepository;

    @Mock
    WebSocketSession tenantAdminSession;

    @Mock
    WebSocketSession conductorSession;

    @Mock
    WebSocketSession otherTenantAdminSession;

    @Test
    void everyActiveAdminInTheTenantReceivesGroupEventsWithoutMembershipLookup() throws Exception {
        Organization organization = Organization.active("Operación Socket", "Socket", "America/Lima");
        Organization otherOrganization = Organization.active("Operación B", "Socket B", "America/Lima");
        UUID organizationId = organization.getId();
        AppUser tenantAdmin = AppUser.organizationUser(
                organization,
                "admin@rutafija.test",
                "hash",
                "Admin del tenant",
                UserRole.ADMIN
        );
        AppUser conductor = AppUser.organizationUser(
                organization,
                "conductor@rutafija.test",
                "hash",
                "Conductor del tenant",
                UserRole.CONDUCTOR
        );
        AppUser otherTenantAdmin = AppUser.organizationUser(
                otherOrganization,
                "admin.b@rutafija.test",
                "hash",
                "Admin de otro tenant",
                UserRole.ADMIN
        );

        when(tenantAdminSession.getId()).thenReturn("tenant-admin-session");
        when(tenantAdminSession.isOpen()).thenReturn(true);
        when(conductorSession.getId()).thenReturn("conductor-session");
        when(otherTenantAdminSession.getId()).thenReturn("other-tenant-session");
        when(userRepository.findByIdAndOrganization_Id(tenantAdmin.getId(), organizationId))
                .thenReturn(Optional.of(tenantAdmin));
        when(userRepository.findByIdAndOrganization_Id(conductor.getId(), organizationId))
                .thenReturn(Optional.of(conductor));

        OperationStreamBroadcaster broadcaster = new OperationStreamBroadcaster(
                new ObjectMapper().findAndRegisterModules(),
                userRepository
        );
        broadcaster.register(organizationId, tenantAdmin.getId(), tenantAdminSession);
        broadcaster.register(organizationId, conductor.getId(), conductorSession);
        broadcaster.register(otherOrganization.getId(), otherTenantAdmin.getId(), otherTenantAdminSession);
        broadcaster.broadcast(new OperationChangedEvent(
                organizationId,
                Set.of(UUID.randomUUID()),
                "assignment.started",
                Instant.parse("2026-09-22T12:00:00Z"),
                Map.of("assignmentId", UUID.randomUUID().toString())
        ));

        verify(tenantAdminSession).sendMessage(any());
        verify(conductorSession, never()).sendMessage(any());
        verify(otherTenantAdminSession, never()).sendMessage(any());
    }
}
