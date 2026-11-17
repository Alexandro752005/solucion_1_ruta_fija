package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.support.L02PostgresIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Complements the archived connected rollback: proves the exact disabled-feature blocker. */
@SpringBootTest(properties={"debug=false","logging.level.root=ERROR","logging.level.org.hibernate.SQL=OFF",
        "app.qualification.validity-sweep=PT24H"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(QualificationIntegrationTest.ClockConfiguration.class)
@TestPropertySource(properties="app.qualification.operations-enabled=false")
class QualificationReturnGateIntegrationTest extends L02PostgresIntegrationTest {
    @Autowired AutowireCapableBeanFactory beanFactory;
    @MockitoBean OtpEmailSender mail;
    private QualificationIntegrationTest fixture;

    @BeforeEach void prepareDisabledFeatureFixture(TestInfo info) throws Exception {
        // Reuse setup, not the enabled-feature @Test methods. Inheriting those
        // methods under this class-wide disabled flag creates contradictory tests.
        fixture=new QualificationIntegrationTest();
        beanFactory.autowireBean(fixture);
        fixture.fixture(info);
    }

    @Test void returnDisabledFeatureIsTheSpecificRemainingBlockerAfterOrdinaryReviewsAndPeriod()throws Exception{
        fixture.reviewAll();fixture.mutate("/periods",fixture.period(2,fixture.vehicle.getId(),"OWN",fixture.initial.minusSeconds(60),null)).andExpect(status().isOk());
        assertThat(fixture.policy.blockers(fixture.driver.getId(),fixture.organization.getId(),fixture.vehicle.getId(),fixture.initial.plusSeconds(60),fixture.initial.plusSeconds(3600),false))
            .containsExactly("OPERATIONAL_FEATURE_DISABLED");
        fixture.mutate("/activate",fixture.basic(3)).andExpect(status().isConflict());
        fixture.mvc.perform(get("/api/v1/drivers/"+fixture.driver.getId()+"/qualification").header("Authorization","Bearer "+fixture.adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,fixture.driver.getId())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,fixture.driver.getId())).isZero();
    }
}
