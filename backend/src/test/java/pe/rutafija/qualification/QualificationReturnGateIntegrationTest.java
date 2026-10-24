package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

/** Complements the archived connected rollback: proves the exact disabled-feature blocker. */
@TestPropertySource(properties="app.qualification.operations-enabled=false")
class QualificationReturnGateIntegrationTest extends QualificationIntegrationTest {
    @Test void returnDisabledFeatureIsTheSpecificRemainingBlockerAfterOrdinaryReviewsAndPeriod()throws Exception{
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        assertThat(policy.blockers(driver.getId(),organization.getId(),vehicle.getId(),initial.plusSeconds(60),initial.plusSeconds(3600),false))
            .containsExactly("OPERATIONAL_FEATURE_DISABLED");
        mutate("/activate",basic(3)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/drivers/"+driver.getId()+"/qualification").header("Authorization","Bearer "+adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.operationalEnabled").value(false));
        assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driver.getId())).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isZero();
    }
}
