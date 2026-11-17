package pe.rutafija.livemap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.livemap.api.LiveMapContract;
import pe.rutafija.livemap.application.LiveMapService;
import pe.rutafija.livemap.infrastructure.LiveMapReadDao;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LiveMapServiceTest {
    private final Instant now = Instant.parse("2026-11-11T15:00:00Z");
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final LiveMapReadDao dao = mock(LiveMapReadDao.class);
    private final LiveMapService service = new LiveMapService(current, dao, Clock.fixed(now, ZoneOffset.UTC));

    private AppUser actor(UserRole role) {
        var organization = Organization.active("SYNTHETIC MAP", null, "America/Lima");
        var user = AppUser.organizationUser(organization, "synthetic@example.invalid", "synthetic-hash", "Synthetic actor", role);
        when(current.requireTenantActor()).thenReturn(user);
        return user;
    }

    @ParameterizedTest @ValueSource(ints = {5, 10, 15, 20})
    void allowedLimitsKeepTheServerTenantAndSingleCutoff(int limit) {
        var admin = actor(UserRole.ADMIN);
        when(dao.vehicles(admin.getOrganizationId(), limit, null, "", now)).thenReturn(List.of());
        var response = service.vehicles(limit, null, null);
        assertThat(response.serverTime()).isEqualTo(now);
        assertThat(response.limit()).isEqualTo(limit);
        assertThat(response.items()).isEmpty();
        verify(dao).vehicles(admin.getOrganizationId(), limit, null, "", now);
    }

    @ParameterizedTest @ValueSource(ints = {-1, 0, 1, 6, 19, 21, 100})
    void arbitraryLimitsCannotReachTheDatabase(int limit) {
        actor(UserRole.ADMIN);
        assertThatThrownBy(() -> service.vehicles(limit, null, null)).isInstanceOf(ApplicationException.class)
                .extracting("status").isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
        verifyNoInteractions(dao);
    }

    @Test void nameIsBoundedAndLiteralSpecialCharactersArePreserved() {
        var admin = actor(UserRole.ADMIN); var group = UUID.randomUUID();
        service.vehicles(5, group, "  50%_\\' Conductor  ");
        verify(dao).vehicles(admin.getOrganizationId(), 5, group, "50%_\\' Conductor", now);
        assertThatThrownBy(() -> service.vehicles(5, null, "x".repeat(101))).isInstanceOf(ApplicationException.class);
    }

    @Test void conductorCannotReadTheFeedOrGroupsEvenWhenCallingServiceDirectly() {
        actor(UserRole.CONDUCTOR);
        assertThatThrownBy(() -> service.vehicles(5, null, null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(service::groups).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(dao);
    }

    @Test void platformRoleCannotReadTenantCoordinatesEvenWhenCallingServiceDirectly() {
        when(current.requireTenantActor()).thenReturn(AppUser.superAdmin("platform@example.invalid", "synthetic-hash", "Synthetic platform"));
        assertThatThrownBy(() -> service.vehicles(5, null, null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(service::groups).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(dao);
    }

    @Test void groupsRemainATenantBoundMinimalProjection() {
        var admin = actor(UserRole.ADMIN);
        var group = new LiveMapContract.Group(UUID.randomUUID(), "Synthetic group");
        when(dao.groups(admin.getOrganizationId())).thenReturn(List.of(group));
        assertThat(service.groups().items()).containsExactly(group);
    }
}
