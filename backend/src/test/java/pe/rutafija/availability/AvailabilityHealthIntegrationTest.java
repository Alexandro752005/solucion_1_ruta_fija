package pe.rutafija.availability;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.*;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.support.L02PostgresIntegrationTest;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual application, security and PostgreSQL. No accounts, mail or DB failure injection. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AvailabilityHealthIntegrationTest extends L02PostgresIntegrationTest {
    @MockitoBean OtpEmailSender simulatedMail;
    @Autowired MockMvc mvc;
    @Autowired ConfigurableApplicationContext context;
    @Autowired HealthContributorRegistry contributors;
    @Autowired ApplicationAvailability availability;
    private ReadinessState previous;
    @BeforeEach void makeReadinessExplicit(){previous=availability.getReadinessState();AvailabilityChangeEvent.publish(context,ReadinessState.ACCEPTING_TRAFFIC);}
    @AfterEach void restoreReadiness(){AvailabilityChangeEvent.publish(context,previous);}
    @Test void readyRequiresActualDatabaseAndRevealsOnlyStatus()throws Exception{
        var db=contributors.getContributor("db");assertThat(db).isInstanceOf(HealthIndicator.class);
        assertThat(((HealthIndicator)db).health().getStatus()).isEqualTo(Status.UP);
        assertThat(context.getEnvironment().getProperty("management.endpoint.health.group.readiness.include")).isEqualTo("readinessState,db");
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
            .andExpect(jsonPath("$.components").doesNotExist()).andExpect(jsonPath("$.details").doesNotExist());
    }
    @Test void refusingTrafficIs503WhileLivenessStillAllowsProcessDiagnosis()throws Exception{
        AvailabilityChangeEvent.publish(context,ReadinessState.REFUSING_TRAFFIC);
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
    @Test void databaseDownContributorMakesReadiness503WithoutLeakingHostOrRestartingLiveness()throws Exception{
        // Explicit indicator double: never disconnect/stop the shared real database.
        var original=contributors.unregisterContributor("db");assertThat(original).isNotNull();
        try{
            contributors.registerContributor("db",(HealthIndicator)()->Health.down().withDetail("host","SYNTHETIC_PRIVATE_HOST").build());
            var response=mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN")).andExpect(jsonPath("$.components").doesNotExist()).andReturn().getResponse();
            assertThat(response.getContentAsString()).doesNotContain("SYNTHETIC_PRIVATE_HOST","jdbc:","password");
            mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        }finally{contributors.unregisterContributor("db");contributors.registerContributor("db",original);}
    }
    @Test void healthDoesNotExposeOrProbeMailAndDoesNotOpenBusinessEndpoints()throws Exception{
        assertThat(context.getEnvironment().getProperty("management.health.mail.enabled",Boolean.class)).isFalse();
        assertThat(contributors.getContributor("mail")).isNull();
        mvc.perform(get("/api/v1/platform/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/dashboard/admin")).andExpect(status().isUnauthorized());
    }
}
