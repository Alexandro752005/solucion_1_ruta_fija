package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MobilePreferencesIntegrationTest extends QualificationIntegrationTest {
    @Test void preferencesGetDefaultsDoesNotWriteAndMandatoryNoticesAreAlwaysVisible()throws Exception{
        mvc.perform(get("/api/v1/mobile/preferences").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.mandatoryNoticesAlwaysVisible").value(true));
        assertThat(jdbc.queryForObject("select count(*) from mobile_preferences where user_id=?",Integer.class,conductor.getId())).isZero();
    }
    @Test void preferencesUpdateIsOwnVersionedAndAuditHasBeforeAfterWithoutIdentity()throws Exception{
        var result=json(mvc.perform(put("/api/v1/mobile/preferences").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"optionalNoticeAlerts\":false,\"textSize\":\"LARGE\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.mandatoryNoticesAlwaysVisible").value(true)));
        mvc.perform(put("/api/v1/mobile/preferences").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content("{\"version\":0,\"optionalNoticeAlerts\":true,\"textSize\":\"NORMAL\"}")).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));
        entities.flush();String raw=jdbc.queryForObject("select metadata_json::text from audit_event where user_id=? and action='MOBILE_PREFERENCES_CHANGED'",String.class,conductor.getId());assertThat(raw).contains("before","after","NORMAL","LARGE").doesNotContain(dni,conductor.getEmail());
        assertThat(jdbc.queryForObject("select count(*) from mobile_preferences where user_id=? and organization_id=?",Integer.class,conductor.getId(),organization.getId())).isEqualTo(1);
    }
    @Test void preferencesRejectOtherRolesWebSessionAndInvalidBody()throws Exception{
        mvc.perform(get("/api/v1/mobile/preferences").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mobile/preferences").header("Authorization","Bearer "+jwt.issueAccessToken(conductor))).andExpect(status().isUnauthorized());
        for(String body:List.of("{}","{\"version\":-1,\"optionalNoticeAlerts\":true,\"textSize\":\"NORMAL\"}","{\"version\":0,\"optionalNoticeAlerts\":true,\"textSize\":\"HIDDEN\"}"))mvc.perform(put("/api/v1/mobile/preferences").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
}
