package pe.rutafija.dashboard;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.rutafija.dashboard.application.DashboardService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DashboardContextTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final Instant cutoff = Instant.parse("2026-10-26T04:30:00Z");
    private final DashboardService service = new DashboardService(jdbc, users, Clock.fixed(cutoff, ZoneOffset.UTC));

    private AppUser admin(String zone) {
        var user = AppUser.organizationUser(Organization.active("Synthetic organization", null, zone),
                "dashboard@example.invalid", "synthetic-not-a-real-password-hash", "Synthetic ADMIN", UserRole.ADMIN);
        when(users.requireTenantActor()).thenReturn(user);
        return user;
    }

    @Test
    void operationalDayUsesTheActorsLimaZoneAndTheSameCutoff() {
        admin("America/Lima");
        var result = service.admin();
        assertThat(result.generatedAt()).isEqualTo(cutoff);
        assertThat(result.zoneId()).isEqualTo("America/Lima");
        assertThat(result.operationalDate()).isEqualTo(LocalDate.of(2026, 10, 25));
        assertThat(result.priorities()).hasSize(8);
    }

    @Test
    void anotherOrganizationZoneDoesNotInheritLimaOrTheServerDefault() {
        admin("Europe/Madrid");
        var result = service.admin();
        assertThat(result.zoneId()).isEqualTo("Europe/Madrid");
        assertThat(result.operationalDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(result.generatedAt()).isEqualTo(cutoff);
    }

    @Test
    void canonicalServiceCounterKeepsTheActorOrganizationAndItsExistingMeaning() {
        var actor = admin("America/Lima");
        String sql = "select count(*) from scheduled_service where organization_id=? and status='IN_SERVICE'";
        when(jdbc.queryForObject(sql, Long.class, actor.getOrganizationId())).thenReturn(2L);
        assertThat(service.admin().servicesInProgress()).isEqualTo(2);
        verify(jdbc).queryForObject(sql, Long.class, actor.getOrganizationId());
    }

    @Test
    void globalRoleDoesNotGainOperationalContextOrExecuteTenantQueries() {
        when(users.requireTenantActor()).thenReturn(AppUser.superAdmin(
                "platform@example.invalid", "synthetic-not-a-real-password-hash", "Synthetic platform account"));
        assertThatThrownBy(service::admin).isInstanceOf(ApplicationException.class);
        verifyNoInteractions(jdbc);
    }
}
