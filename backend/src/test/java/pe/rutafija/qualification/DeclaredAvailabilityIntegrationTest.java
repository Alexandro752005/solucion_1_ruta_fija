package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** L03 explicit own declarations over real security filters/PostgreSQL, rollback-only. */
class DeclaredAvailabilityIntegrationTest extends QualificationIntegrationTest {
    private void operational()throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());entities.clear();
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
    }
    private ResultActions declare(String state)throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(put("/api/v1/mobile/availability").header("Authorization","Bearer "+mobileToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("status",state))));
    }
    @Test void declarationStartsUnknownAndOnlyExplicitConductorIntentionWritesIt()throws Exception {
        operational();mvc.perform(get("/api/v1/mobile/availability").header("Authorization","Bearer "+mobileToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.declaredStatus").doesNotExist());
        declare("DISPONIBLE").andExpect(status().isOk()).andExpect(jsonPath("$.declaredStatus").value("DISPONIBLE"))
            .andExpect(jsonPath("$.declaredAt").value(initial.toString()));
        clock.set(initial.plusSeconds(10));declare("DISPONIBLE").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select declared_availability_at from driver where id=?",java.sql.Timestamp.class,driver.getId()).toInstant()).isEqualTo(initial.plusSeconds(10));
    }
    @Test void declarationPersistsAcrossReadsNewSessionsAndComputedOccupation()throws Exception {
        operational();declare("DISPONIBLE").andExpect(status().isOk());
        String next=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        for(int i=0;i<2;i++)mvc.perform(get("/api/v1/mobile/availability").header("Authorization","Bearer "+next))
            .andExpect(status().isOk()).andExpect(jsonPath("$.declaredStatus").value("DISPONIBLE"));
        var fresh=drivers.findById(driver.getId()).orElseThrow();fresh.reserveForAssignment();fresh.beginService();drivers.flush();
        assertThat(fresh.getDeclaredAvailabilityStatus().name()).isEqualTo("DISPONIBLE");fresh.completeService();drivers.flush();
        assertThat(fresh.getDeclaredAvailabilityAt()).isEqualTo(initial);
        assertThat(fresh.getAvailabilityStatus().name()).isEqualTo("DISPONIBLE");
    }
    @Test void declarationCannotInvalidateAnAcceptedFutureServiceOrCancelIt()throws Exception {
        operational();declare("DISPONIBLE").andExpect(status().isOk());UUID service=assignment("SCHEDULED",initial.plusSeconds(600),initial.plusSeconds(1200));
        declare("NO_DISPONIBLE").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("AVAILABILITY_INVALID_TRANSITION"));
        mvc.perform(get("/api/v1/mobile/availability").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.canChangeDeclaration").value(false));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,service)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select declared_availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DISPONIBLE");
    }
    @Test void declarationDoesNotGrantEligibilityOrManufactureAssignedStates()throws Exception {
        operational();declare("RESERVADO").andExpect(status().isConflict());declare("EN_SERVICIO").andExpect(status().isConflict());
        declare("DESCANSO").andExpect(status().isOk());
        mutate("/suspend",Map.of("expectedVersion",4,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-SUSPENSION","evidenceReference","SYNTHETIC-REFERENCE")).andExpect(status().isOk());
        declare("DISPONIBLE").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select declared_availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DESCANSO");
    }
    @Test void declarationCannotBeImpersonatedByAdminAndRetainsTenantScope()throws Exception {
        operational();mvc.perform(post("/api/v1/drivers/{id}/availability",driver.getId()).header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/v1/mobile/availability").header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}")).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select declared_availability_status from driver where id=?",String.class,driver.getId())).isNull();
    }
    @Test void declarationDoesNotClearItselfWhenDriverIsDeactivated()throws Exception {
        operational();declare("NO_DISPONIBLE").andExpect(status().isOk());
        var fresh=drivers.findById(driver.getId()).orElseThrow();fresh.deactivate();drivers.flush();
        assertThat(fresh.getDeclaredAvailabilityStatus().name()).isEqualTo("NO_DISPONIBLE");
        assertThat(fresh.getDeclaredAvailabilityAt()).isEqualTo(initial);
    }
    @Test void declarationSurvivesAnActualMobileLoginAndLogout()throws Exception {
        operational();declare("DESCANSO").andExpect(status().isOk());
        var login=json(mvc.perform(post("/api/v1/mobile/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("email",conductor.getEmail(),"password","Synthetic-L02-Password-2026")))).andExpect(status().isOk()));
        String refresh=login.path("refreshToken").asText();assertThat(refresh.isBlank()).isFalse();
        mvc.perform(post("/api/v1/mobile/auth/logout").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("refreshToken",refresh)))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select declared_availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DESCANSO");
        assertThat(jdbc.queryForObject("select declared_availability_at from driver where id=?",java.sql.Timestamp.class,driver.getId()).toInstant()).isEqualTo(initial);
        assertThat(jdbc.queryForObject("select count(*) from refresh_token where user_id=? and revoked_at is null",Integer.class,conductor.getId())).isZero();
        // MockMvc requests share this rollback fixture's persistence context.
        // Bulk revocation is already verified in PG; use a fresh request view,
        // as actual separate HTTP transactions do, before checking reuse.
        entities.flush();entities.clear();
        mvc.perform(post("/api/v1/mobile/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("refreshToken",refresh)))).andExpect(status().isUnauthorized());
    }
    @Test void declarationReloadsAnOccupiedDriverCachedBeforeTheSchedulingLock()throws Exception {
        operational();declare("DISPONIBLE").andExpect(status().isOk());
        var cached=drivers.findById(driver.getId()).orElseThrow();assertThat(cached.getAvailabilityStatus().name()).isEqualTo("DISPONIBLE");
        // Adverse fixture simulates a newer DB state while the identity entity
        // remains cached. This is a cache regression, not a parallel HTTP run.
        jdbc.update("update driver set availability_status='EN_SERVICIO' where id=?",driver.getId());
        mvc.perform(put("/api/v1/mobile/availability").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DESCANSO\"}"))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select declared_availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DISPONIBLE");
    }
}
