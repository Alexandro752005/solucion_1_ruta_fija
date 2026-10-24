package pe.rutafija.operation.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pure domain checks: no Spring context, network, PostgreSQL or persistent identity. */
class IncidentClosureContractTest {
    private final Organization organization=mock(Organization.class);
    private final Driver driver=mock(Driver.class);
    private final AppUser reporter=actor(),admin=actor();
    private final Instant at=Instant.parse("2026-10-20T12:00:00Z");
    private static AppUser actor(){var user=mock(AppUser.class);when(user.getId()).thenReturn(UUID.randomUUID());return user;}
    private Incident incident(){return Incident.reportFromMobile(organization,driver,null,reporter,IncidentCategory.OTRO,"Synthetic report",at);}

    @ParameterizedTest @EnumSource(IncidentCategory.class)
    void catalogCategoryDoesNotInferSeverityOrInterruptAService(IncidentCategory category){
        var report=Incident.reportFromMobile(organization,driver,null,reporter,category,"Synthetic explicit report",at);
        assertThat(report.getCategory()).isEqualTo(category);assertThat(report.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(report.getAssignment()).isNull();assertThat(report.getResolvedAt()).isNull();
    }
    @Test void conductorClosureCannotBeMovedBackToFollowUpByAdmin(){
        var report=incident();report.closeByMobileReporter(reporter,at.plusSeconds(1));
        assertThatThrownBy(()->report.followUp("Attempted reopening",false,admin,at.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
        assertThat(report.getStatus()).isEqualTo(IncidentStatus.CLOSED_BY_DRIVER);assertThat(report.getDriverClosedAt()).isEqualTo(at.plusSeconds(1));
        assertThat(report.getFollowUpNote()).isNull();assertThat(report.getFollowedUpBy()).isNull();
    }
    @Test void conductorClosureCannotBeOverwrittenByAdminResolution(){
        var report=incident();report.closeByMobileReporter(reporter,at.plusSeconds(1));
        assertThatThrownBy(()->report.followUp("Attempted second closure",true,admin,at.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
        assertThat(report.getResolvedAt()).isNull();assertThat(report.getDriverClosedBy()).isSameAs(reporter);
    }
    @Test void repeatOwnClosurePreservesTheFirstServerTime(){
        var report=incident();assertThat(report.closeByMobileReporter(reporter,at.plusSeconds(1))).isTrue();
        assertThat(report.closeByMobileReporter(reporter,at.plusSeconds(2))).isFalse();
        assertThat(report.getDriverClosedAt()).isEqualTo(at.plusSeconds(1));
        assertThatThrownBy(()->report.closeByMobileReporter(admin,at.plusSeconds(3))).isInstanceOf(IllegalStateException.class);
    }
    @Test void adminResolutionIsTerminalAndPreservesOriginalReport(){
        var report=incident();report.followUp("Synthetic resolution",true,admin,at.plusSeconds(1));
        assertThatThrownBy(()->report.followUp("Attempted reopening",false,admin,at.plusSeconds(2))).isInstanceOf(IllegalStateException.class);
        assertThat(report.getStatus()).isEqualTo(IncidentStatus.RESOLVED);assertThat(report.getResolvedAt()).isEqualTo(at.plusSeconds(1));
        assertThat(report.getDescription()).isEqualTo("Synthetic report");assertThat(report.getReportedAt()).isEqualTo(at);
    }
}
