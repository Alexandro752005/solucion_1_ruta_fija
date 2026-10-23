package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.rutafija.operation.domain.IncidentCategory;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** L06 filters/controllers and PostgreSQL. Inherited fixtures always roll back; no global cleanup. */
class IncidentWorkflowIntegrationTest extends QualificationIntegrationTest {
    private Map<String,Object> body(){var b=new LinkedHashMap<String,Object>();b.put("driverId",driver.getId());b.put("category","OTRO");b.put("description","SYNTHETIC incident report");b.put("severity","LOW");b.put("operationKey",UUID.randomUUID());return b;}
    private ResultActions create(Map<String,Object> body)throws Exception{return mvc.perform(post("/api/v1/incidents").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));}
    private JsonNode report()throws Exception{return json(create(body()).andExpect(status().isCreated()));}
    private ResultActions action(JsonNode incident,String path,Map<String,Object> command)throws Exception{return mvc.perform(post("/api/v1/incidents/"+incident.path("id").asText()+"/"+path).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)));}
    private Map<String,Object> base(JsonNode incident){return new LinkedHashMap<>(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",incident.path("version").asLong()));}
    private JsonNode assigned(JsonNode incident)throws Exception{var c=base(incident);c.put("adminId",admin.getId());c.put("reason","SYNTHETIC explicit responsibility");return json(action(incident,"responsibility",c).andExpect(status().isOk()));}
    private Map<String,Object> closure(JsonNode incident){var c=base(incident);c.put("diagnosis","SYNTHETIC diagnosis");c.put("actions","SYNTHETIC measures");c.put("result","SYNTHETIC result");c.put("evidenceRequired",false);c.put("evidenceMode","NOT_REQUIRED");c.put("evidenceExplanation","SYNTHETIC nature does not require an attachment");return c;}
    private JsonNode closed()throws Exception{var r=assigned(report());return json(action(r,"closure",closure(r)).andExpect(status().isOk()));}
    private JsonNode history(JsonNode incident)throws Exception{return json(mvc.perform(get("/api/v1/incidents/"+incident.path("id").asText()+"/history").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));}
    private void mobileReady()throws Exception{reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");}
    private JsonNode mobileReport()throws Exception{var b=body();b.remove("driverId");b.put("occurredAt",initial);return json(mvc.perform(post("/api/v1/mobile/incidents").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(b))).andExpect(status().isCreated()));}

    @ParameterizedTest @EnumSource(IncidentCategory.class)
    void incidentCatalogPreservesExplicitSeverityWithoutManufacturingInterruption(IncidentCategory category)throws Exception{
        var b=body();b.put("category",category);b.put("severity","CRITICAL");
        create(b).andExpect(status().isCreated()).andExpect(jsonPath("$.category").value(category.name())).andExpect(jsonPath("$.severity").value("CRITICAL")).andExpect(jsonPath("$.operationalEffect").value("NO_INTERRUPTION_RECORDED"));
        assertThat(jdbc.queryForObject("select count(*) from service_attention where driver_id=?",Integer.class,driver.getId())).isZero();
    }
    @Test void incidentMissingOrUnassessedHumanSeverityCannotBeRecordedAsKnown()throws Exception{
        var b=body();b.remove("severity");create(b).andExpect(status().isBadRequest());b.put("severity","UNASSESSED");create(b).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from incident where driver_id=?",Integer.class,driver.getId())).isZero();
    }
    @Test void incidentSameCreationKeyReturnsTheSameReportAndChangedPayloadIsRejected()throws Exception{
        var b=body();var first=json(create(b).andExpect(status().isCreated()));create(b).andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(first.path("id").asText()));
        b.put("description","SYNTHETIC changed body");create(b).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from incident where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);assertThat(history(first)).hasSize(1);
    }
    @Test void incidentResponsibilityIsExplicitAndChangedRoundRetainsHistory()throws Exception{
        var r=report();assertThat(r.path("responsibleAdminId").isNull()||r.path("responsibleAdminId").isMissingNode()).isTrue();
        var c=base(r);c.put("adminId",admin.getId());c.put("reason","SYNTHETIC first designation");var first=json(action(r,"responsibility",c).andExpect(status().isOk()));
        action(r,"responsibility",c).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(first.path("version").asLong()));
        var changed=base(first);changed.put("adminId",admin.getId());changed.put("reason","SYNTHETIC reviewed designation");var second=json(action(first,"responsibility",changed).andExpect(status().isOk()));
        assertThat(second.path("version").asLong()).isGreaterThan(first.path("version").asLong());var rows=history(second);assertThat(rows).hasSize(3);
        assertThat(rows.get(2).path("detail").path("previousAdminId").asText()).isEqualTo(admin.getId().toString());
    }
    @Test void incidentCannotAssignConductorForeignAdminOrStaleState()throws Exception{
        var r=report();var c=base(r);c.put("reason","SYNTHETIC checked assignment");c.put("adminId",conductor.getId());action(r,"responsibility",c).andExpect(status().isNotFound());
        c.put("adminId",UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979"));action(r,"responsibility",c).andExpect(status().isNotFound());
        var assigned=assigned(r);c.put("adminId",admin.getId());action(r,"responsibility",c).andExpect(status().isConflict());assertThat(assigned.path("responsibleAdminId").asText()).isEqualTo(admin.getId().toString());
    }
    @Test void incidentAttentionRequiresResponsibleAndGenericResolveCannotReplaceStructuredClosure()throws Exception{
        var r=report();mvc.perform(patch("/api/v1/incidents/"+r.path("id").asText()+"/follow-up").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",r.path("version").asLong(),"operationKey",UUID.randomUUID(),"note","SYNTHETIC note","resolve",false)))).andExpect(status().isConflict());
        var a=assigned(r);mvc.perform(patch("/api/v1/incidents/"+a.path("id").asText()+"/follow-up").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",a.path("version").asLong(),"operationKey",UUID.randomUUID(),"note","SYNTHETIC only one note","resolve",true)))).andExpect(status().isBadRequest());
    }
    @Test void incidentFollowUpKeepsEveryRoundAndOnlyTheLatestProjection()throws Exception{
        var r=assigned(report());
        for(int i=0;i<2;i++)r=json(mvc.perform(patch("/api/v1/incidents/"+r.path("id").asText()+"/follow-up").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",r.path("version").asLong(),"operationKey",UUID.randomUUID(),"note","SYNTHETIC note "+i,"resolve",false)))).andExpect(status().isOk()));
        var rows=history(r);assertThat(rows).hasSize(4);assertThat(rows.get(2).path("detail").path("note").asText()).isEqualTo("SYNTHETIC note 0");assertThat(rows.get(3).path("detail").path("note").asText()).isEqualTo("SYNTHETIC note 1");assertThat(r.path("followUpNote").asText()).isEqualTo("SYNTHETIC note 1");
    }
    @Test void incidentClosureValidatesEvidenceAndRecordsServerResultWithoutResourceRelease()throws Exception{
        var r=assigned(report());var c=closure(r);c.put("evidenceRequired",true);action(r,"closure",c).andExpect(status().isBadRequest());
        c.put("evidenceMode","REFERENCE");action(r,"closure",c).andExpect(status().isBadRequest());c.put("evidenceReference","SYNTHETIC_CHECK_01");
        var end=json(action(r,"closure",c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED")).andExpect(jsonPath("$.resolvedAt").isNotEmpty()));
        action(r,"closure",c).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(end.path("version").asLong()));
        var h=history(end).get(2);assertThat(h.path("kind").asText()).isEqualTo("RESOLUTION");assertThat(h.path("detail").path("resourcesReleased").asBoolean()).isFalse();assertThat(h.path("detail").path("diagnosis").asText()).isEqualTo("SYNTHETIC diagnosis");
    }
    @Test void incidentNoEvidenceMustBeExplainedAndPermanentNotesCannotContainPiiOrCoordinates()throws Exception{
        var r=assigned(report());var c=closure(r);c.remove("evidenceExplanation");action(r,"closure",c).andExpect(status().isBadRequest());
        c.put("evidenceMode","UNAVAILABLE_JUSTIFIED");c.put("evidenceRequired",true);c.put("evidenceExplanation","SYNTHETIC cannot obtain the original");c.put("diagnosis","No email person@example.test");action(r,"closure",c).andExpect(status().isBadRequest());
        c.put("diagnosis","SYNTHETIC diagnosis");c.put("actions","Point -6.77, -79.84");action(r,"closure",c).andExpect(status().isBadRequest());
        c.put("actions","SYNTHETIC measures");action(r,"closure",c).andExpect(status().isOk());
    }
    @Test void incidentClosedOriginalCannotBeReopenedButMayHaveANewLinkedCase()throws Exception{
        var original=closed();var a=base(original);a.put("adminId",admin.getId());a.put("reason","SYNTHETIC attempted reopen");action(original,"responsibility",a).andExpect(status().isBadRequest());
        var b=body();b.put("previousIncidentId",original.path("id").asText());b.put("relationReason","CLOSURE_ERROR");var next=json(create(b).andExpect(status().isCreated()));
        assertThat(next.path("status").asText()).isEqualTo("OPEN");assertThat(next.path("previousIncidentId").asText()).isEqualTo(original.path("id").asText());
        assertThat(jdbc.queryForObject("select status from incident where id=?",String.class,UUID.fromString(original.path("id").asText()))).isEqualTo("RESOLVED");
    }
    @Test void incidentPriorCaseMustBeClosedAndCannotCrossOrganizations()throws Exception{
        var open=report();var b=body();b.put("previousIncidentId",open.path("id").asText());b.put("relationReason","RECURRENCE");create(b).andExpect(status().isConflict());
        UUID foreign=jdbc.queryForObject("insert into incident(id,organization_id,driver_id,reported_by,category,status,description,reported_at,created_at,updated_at,source) values(gen_random_uuid(),?,?,null,'OTRO','OPEN','SYNTHETIC foreign report',now(),now(),now(),'SYSTEM') returning id",UUID.class,otherOrganization.getId(),otherDriver.getId());
        b.put("previousIncidentId",foreign);create(b).andExpect(status().isNotFound());
    }
    @Test void incidentConductorCanContributeAfterQualificationLossButNotReopenTheirClosedReport()throws Exception{
        mobileReady();var r=mobileReport();driver.deactivate();drivers.flush();entities.clear();
        var c=Map.of("operationKey",UUID.randomUUID(),"expectedVersion",r.path("version").asLong(),"note","SYNTHETIC own additional fact");
        var next=json(mvc.perform(post("/api/v1/mobile/incidents/"+r.path("id").asText()+"/information").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c))).andExpect(status().isOk()));
        var end=json(mvc.perform(post("/api/v1/mobile/incidents/"+r.path("id").asText()+"/close").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",next.path("version").asLong())))).andExpect(status().isOk()));
        action(end,"responsibility",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",end.path("version").asLong(),"adminId",admin.getId(),"reason","SYNTHETIC cannot reopen")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/mobile/incidents/"+r.path("id").asText()+"/history").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3));
    }
    @Test void incidentHistoryAndOperationReceiptsAreScopedAndDoNotExposeIdentity()throws Exception{
        var b=body();var r=json(create(b).andExpect(status().isCreated()));var result=mvc.perform(get("/api/v1/incidents/operations/"+b.get("operationKey")).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.incidentId").value(r.path("id").asText())).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(admin.getEmail(),conductor.getEmail(),dni,conductor.getFullName());
        mvc.perform(get("/api/v1/incidents/"+r.path("id").asText()+"/history").header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mobile/incidents/operations/"+b.get("operationKey")).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
    }
    @Test void incidentDatabaseProtectsClosedOriginalsAndAppendOnlyActionHistory()throws Exception{
        var r=closed();UUID id=UUID.fromString(r.path("id").asText());
        assertThat(deniedSql("update incident set status='OPEN',resolved_at=null where id=?",id)).contains("23514").contains("cannot be reopened");
        assertThat(deniedSql("update incident set severity='HIGH' where id=?",id)).contains("23514");
        assertThat(deniedSql("delete from incident where id=?",id)).contains("23514");
        assertThat(deniedSql("update incident_action set detail='{}'::jsonb where incident_id=?",id)).contains("23514");
        assertThat(deniedSql("delete from incident_action where incident_id=?",id)).contains("23514");
        assertThat(history(r)).hasSize(3);
    }
    @Test void incidentForeignAdminAndConductorCannotReadOrContributeAcrossOrganizations()throws Exception{
        var own=report();
        var foreignAdmin=users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();
        mvc.perform(get("/api/v1/incidents/"+own.path("id").asText()+"/history").header("Authorization","Bearer "+jwt.issueAccessToken(foreignAdmin))).andExpect(status().isNotFound());
        UUID foreign=jdbc.queryForObject("insert into incident(id,organization_id,driver_id,reported_by,category,status,description,reported_at,created_at,updated_at,source) values(gen_random_uuid(),?,?,null,'OTRO','OPEN','SYNTHETIC foreign report',now(),now(),now(),'SYSTEM') returning id",UUID.class,otherOrganization.getId(),otherDriver.getId());
        mvc.perform(get("/api/v1/mobile/incidents/"+foreign+"/history").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/mobile/incidents/"+foreign+"/information").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",0,"note","SYNTHETIC forbidden fact")))).andExpect(status().isNotFound());
    }
    private String deniedSql(String sql,Object parameter)throws Exception{
        var connection=org.springframework.jdbc.datasource.DataSourceUtils.getConnection(jdbc.getDataSource());var savepoint=connection.setSavepoint();
        try{jdbc.update(sql,parameter);return "NOT_REJECTED";}catch(Exception failure){Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();return cause instanceof java.sql.SQLException s?s.getSQLState()+":"+s.getMessage():cause.getClass().getSimpleName();}
        finally{connection.rollback(savepoint);connection.releaseSavepoint(savepoint);entities.clear();}
    }
}
