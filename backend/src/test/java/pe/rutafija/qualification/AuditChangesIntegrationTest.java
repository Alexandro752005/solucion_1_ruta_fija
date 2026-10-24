package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuditChangesIntegrationTest extends QualificationIntegrationTest {
    @Test void changesVehicleFieldsAndStatusPreserveBeforeAfterWithoutFreeText()throws Exception{
        mvc.perform(patch("/api/v1/vehicles/"+vehicle.getId()).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("plate",vehicle.getPlate(),"brand","SYNTHETIC_REVISED_BRAND","model","SYNTHETIC_REVISED_MODEL","year",2026,"color","SYNTHETIC_COLOR")))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vehicles/"+vehicle.getId()+"/status").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVO\"}")).andExpect(status().isOk());entities.flush();
        var rows=jdbc.queryForList("select metadata_json::text from audit_event where entity_id=? and action in('VEHICLE_UPDATED','VEHICLE_STATUS_CHANGED') order by occurred_at",String.class,vehicle.getId());assertThat(rows).hasSize(2);
        assertThat(rows.toString()).contains("before","after","2025","2026","DISPONIBLE","INACTIVO","brand","REDACTED").doesNotContain("SYNTHETIC_REVISED_BRAND","SYNTHETIC_REVISED_MODEL",dni);
    }
    @Test void changesPlatformOrganizationUsesMaskedFieldDifferencesAndNoTenantAuditAccess()throws Exception{
        var owner=users.findById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow();
        mvc.perform(patch("/api/v1/organizations/"+organization.getId()).header("Authorization","Bearer "+jwt.issueAccessToken(owner)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("legalName","SYNTHETIC_REVISED_ORGANIZATION_"+UUID.randomUUID(),"tradeName","SYNTHETIC_REVISED_TRADE","timezone","America/Lima","status","ACTIVE")))).andExpect(status().isOk());entities.flush();
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where entity_id=? and action='ORGANIZATION_UPDATED'",String.class,organization.getId());assertThat(raw).contains("legalName","before","after","REDACTADO").doesNotContain("SYNTHETIC_REVISED_ORGANIZATION","SYNTHETIC_REVISED_TRADE");
    }
    @Test void changesGroupNameDoesNotPersistItsPersonalOrFreeValue()throws Exception{
        UUID group=driver.getGroup().getId();var page=json(mvc.perform(get("/api/v1/groups?size=100").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));var view=page.path("items").get(0);assertThat(view.path("id").asText()).isEqualTo(group.toString());
        mvc.perform(patch("/api/v1/groups/"+group).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("expectedVersion",view.path("version").asLong(),"name","SYNTHETIC_REVISED_GROUP","description","SYNTHETIC_PERSONAL_FREE_TEXT","active",true)))).andExpect(status().isOk());entities.flush();
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where entity_id=? and action='GROUP_UPDATED'",String.class,group);assertThat(raw).contains("changes","before","after","REDACTADO").doesNotContain("SYNTHETIC_REVISED_GROUP","SYNTHETIC_PERSONAL_FREE_TEXT");
    }
}
