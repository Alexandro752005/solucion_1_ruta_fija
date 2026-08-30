package pe.rutafija.operation.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.WebSocketSession;
import pe.rutafija.fleet.infrastructure.GroupCoordinatorRepository;
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
    GroupCoordinatorRepository groupCoordinatorRepository;

    @Mock
    WebSocketSession visibleCoordinatorSession;

    @Mock
    WebSocketSession hiddenCoordinatorSession;

    @Test
    void onlyCoordinatorAssignedToTheEventGroupReceivesTheEvent() throws Exception {
        UUID organizationId = UUID.randomUUID();
        UUID visibleGroupId = UUID.randomUUID();
        Organization organization = Organization.active("Operación Socket", "Socket", "America/Lima");
        AppUser visibleCoordinator = AppUser.organizationUser(
                organization,
                "visible@rutafija.test",
                "hash",
                "Coordinador visible",
                UserRole.COORDINADOR
        );
        AppUser hiddenCoordinator = AppUser.organizationUser(
                organization,
                "hidden@rutafija.test",
                "hash",
                "Coordinador sin acceso",
                UserRole.COORDINADOR
        );

        when(visibleCoordinatorSession.getId()).thenReturn("visible-session");
        when(visibleCoordinatorSession.isOpen()).thenReturn(true);
        when(hiddenCoordinatorSession.getId()).thenReturn("hidden-session");
        when(userRepository.findByIdAndOrganization_Id(visibleCoordinator.getId(), organizationId))
                .thenReturn(Optional.of(visibleCoordinator));
        when(userRepository.findByIdAndOrganization_Id(hiddenCoordinator.getId(), organizationId))
                .thenReturn(Optional.of(hiddenCoordinator));
        when(groupCoordinatorRepository.existsByGroup_IdAndUser_Id(visibleGroupId, visibleCoordinator.getId()))
                .thenReturn(true);
        when(groupCoordinatorRepository.existsByGroup_IdAndUser_Id(visibleGroupId, hiddenCoordinator.getId()))
                .thenReturn(false);

        OperationStreamBroadcaster broadcaster = new OperationStreamBroadcaster(
                new ObjectMapper().findAndRegisterModules(),
                userRepository,
                groupCoordinatorRepository
        );
        broadcaster.register(organizationId, visibleCoordinator.getId(), visibleCoordinatorSession);
        broadcaster.register(organizationId, hiddenCoordinator.getId(), hiddenCoordinatorSession);
        broadcaster.broadcast(new OperationChangedEvent(
                organizationId,
                Set.of(visibleGroupId),
                "assignment.started",
                Instant.parse("2026-08-29T20:00:00Z"),
                Map.of("assignmentId", UUID.randomUUID().toString())
        ));

        verify(visibleCoordinatorSession).sendMessage(any());
        verify(hiddenCoordinatorSession, never()).sendMessage(any());
    }
}
