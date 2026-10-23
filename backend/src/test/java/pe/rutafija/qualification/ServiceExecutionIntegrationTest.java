package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring/HTTP filters and PostgreSQL, rollback-only synthetic identities; never a device or human GPS trial. */
class ServiceExecutionIntegrationTest extends QualificationIntegrationTest {
    protected void prepared() throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
    }
    protected ResultActions adminPost(String path,Object body)throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(post(path).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    protected ResultActions consent(boolean value)throws Exception {
        return mvc.perform(put("/api/v1/mobile/location/consent").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("consent",value))));
    }
    protected JsonNode offer()throws Exception {
        return offer(routeFixture());
    }
    protected JsonNode offer(UUID routeVersion)throws Exception {
        var body=Map.of("driverId",driver.getId(),"vehicleId",vehicle.getId(),"routeVersionId",routeVersion,"serviceTypeVersionId",serviceTypeFixture(),
            "originText","SYNTHETIC origin","destinationText","SYNTHETIC destination","scheduledAt",initial.plusSeconds(3600),"scheduledEndAt",initial.plusSeconds(7200));
        return json(adminPost("/api/v1/assignments",body).andExpect(status().isCreated()));
    }
    protected Map<String,Object> command(JsonNode a,boolean gps) {
        var body=new LinkedHashMap<String,Object>(Map.of("version",a.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",clock.instant().minusSeconds(60)));
        if(gps)body.put("gps",Map.of("permissionGranted",true,"locationServiceEnabled",true,"foreground",true));
        return body;
    }
    protected ResultActions command(JsonNode a,String action,Object body)throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/"+action).header("Authorization","Bearer "+mobileToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    protected JsonNode accepted()throws Exception { var a=offer();return json(command(a,"accept",command(a,false)).andExpect(status().isOk())); }
    protected JsonNode started()throws Exception { consent(true).andExpect(status().isOk());var a=accepted();return json(command(a,"start",command(a,true)).andExpect(status().isOk())); }
    protected ResultActions point(Instant captured,boolean foreground)throws Exception {
        return mvc.perform(put("/api/v1/mobile/location/current").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("latitude",-6.77,"longitude",-79.84,"accuracyM",5,"capturedAt",captured,
                "permissionGranted",true,"locationServiceEnabled",true,"foreground",foreground))));
    }
    private int segments(){return jdbc.queryForObject("select count(*) from service_execution_segment where organization_id=?",Integer.class,organization.getId());}
    @Test void executionStartRequiresFunctionalConsentWithoutMutatingOccupation()throws Exception {
        prepared();var a=accepted();command(a,"start",command(a,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_CONSENT_REQUIRED"));
        assertThat(segments()).isZero();assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("RESERVADO");
        assertThat(jdbc.queryForObject("select status from scheduled_service where id=?",String.class,UUID.fromString(a.path("serviceId").asText()))).isEqualTo("CONFIRMED");
    }
    @Test void executionStartRequiresExplicitPermissionServiceAndForeground()throws Exception {
        prepared();consent(true).andExpect(status().isOk());var a=accepted();
        command(a,"start",command(a,false)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_PERMISSION_NOT_REPORTED"));
        for(String field:List.of("permissionGranted","locationServiceEnabled","foreground")) {
            var body=command(a,true);var gps=new LinkedHashMap<>(Map.of("permissionGranted",true,"locationServiceEnabled",true,"foreground",true));gps.put(field,false);body.put("gps",gps);
            command(a,"start",body).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_PERMISSION_NOT_REPORTED"));
        }
        assertThat(segments()).isZero();
    }
    @Test void executionStartRecordsServerTimeOnceAndDoesNotAutostartFromAcceptance()throws Exception {
        prepared();consent(true).andExpect(status().isOk());var a=accepted();assertThat(segments()).isZero();
        var body=command(a,true);var ongoing=json(command(a,"start",body).andExpect(status().isOk()));
        assertThat(ongoing.path("startedAt").asText()).isEqualTo(initial.toString());
        command(a,"start",body).andExpect(status().isOk()).andExpect(header().string("X-Idempotent-Replay","true"));
        assertThat(segments()).isEqualTo(1);assertThat(jdbc.queryForObject("select started_at=start_recorded_at and started_at=? from service_execution_segment where assignment_id=?",Boolean.class,java.sql.Timestamp.from(initial),UUID.fromString(a.path("id").asText()))).isTrue();
        var changed=new LinkedHashMap<>(body);changed.put("gps",Map.of("permissionGranted",false,"locationServiceEnabled",true,"foreground",true));
        command(a,"start",changed).andExpect(status().isConflict());
    }
    @Test void executionCompletionClosesOwnSegmentAndStopsCurrentPoint()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());
        clock.set(initial.plusSeconds(30));var body=command(a,false);var complete=json(command(a,"complete",body).andExpect(status().isOk()));
        assertThat(complete.path("status").asText()).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select end_kind from service_execution_segment where assignment_id=?",String.class,UUID.fromString(a.path("id").asText()))).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("select count(*) from driver_current_location where driver_id=?",Integer.class,driver.getId())).isZero();
        command(a,"complete",body).andExpect(status().isOk()).andExpect(header().string("X-Idempotent-Replay","true"));assertThat(segments()).isEqualTo(1);
        point(clock.instant(),true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_STATE_NOT_ALLOWED"));
    }
    @Test void executionAdminCannotDeclareCompletionOrCreateSegments()throws Exception {
        prepared();var a=started();adminPost("/api/v1/assignments/"+a.path("id").asText()+"/complete",Map.of("version",a.path("version").asLong()))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        assertThat(jdbc.queryForObject("select ended_at is null from service_execution_segment where assignment_id=?",Boolean.class,UUID.fromString(a.path("id").asText()))).isTrue();
        mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/start").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command(a,true))))
            .andExpect(status().isForbidden());
    }
    @Test void executionTrackingRejectsAvailabilityAndBackgroundAndOutOfSegmentTime()throws Exception {
        prepared();consent(true).andExpect(status().isOk());point(initial,true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_STATE_NOT_ALLOWED"));
        var a=started();point(initial,false).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_PERMISSION_NOT_REPORTED"));
        point(initial.minusNanos(1),true).andExpect(status().isBadRequest());point(initial.plusSeconds(1),true).andExpect(status().isBadRequest());
        point(initial,true).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select p.execution_segment_id=e.id from driver_current_location p join service_execution_segment e on e.driver_id=p.driver_id where e.assignment_id=?",Boolean.class,UUID.fromString(a.path("id").asText()))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where organization_id=? and metadata_json::text ~ '(latitude|longitude|accuracy_m)'",Integer.class,organization.getId())).isZero();
    }
    @Test void executionConsentWithdrawalStopsPointsButAllowsSafeCompletion()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());consent(false).andExpect(status().isOk());
        point(initial,true).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_CONSENT_REQUIRED"));
        command(a,"complete",command(a,false)).andExpect(status().isOk());assertThat(segments()).isEqualTo(1);
    }
    @Test void executionSummaryIsCodedOwnScopedAndNeverExposesCoordinates()throws Exception {
        prepared();var a=started();String own="/api/v1/mobile/assignments/"+a.path("id").asText()+"/execution";
        mvc.perform(get(own).header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$[0].driverId").value(driver.getId().toString())).andExpect(jsonPath("$[0].latitude").doesNotExist());
        mvc.perform(get("/api/v1/mobile/assignments/"+UUID.randomUUID()+"/execution").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scheduled-services/"+a.path("serviceId").asText()+"/execution").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/scheduled-services/"+a.path("serviceId").asText()+"/execution").header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(get(own)).andExpect(status().isUnauthorized());
    }
    @Test void executionStaleVersionCannotStartOrCloseAndFreshCommandsPreserveTheSameSegment()throws Exception {
        prepared();consent(true).andExpect(status().isOk());var a=accepted();var stale=command(a,true);stale.put("version",a.path("version").asLong()+1);
        command(a,"start",stale).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));assertThat(segments()).isZero();
        var started=json(command(a,"start",command(a,true)).andExpect(status().isOk()));
        command(started,"complete",command(a,false)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));
        assertThat(jdbc.queryForObject("select ended_at is null from service_execution_segment where assignment_id=?",Boolean.class,UUID.fromString(a.path("id").asText()))).isTrue();
        command(started,"complete",command(started,false)).andExpect(status().isOk());assertThat(segments()).isEqualTo(1);
    }
    @Test void executionDatabaseRejectsRewritingOrDeletingOriginalAndClosedSegments()throws Exception {
        prepared();var a=started();UUID id=UUID.fromString(a.path("id").asText());
        assertThat(rejectedSqlState("update service_execution_segment set started_at=started_at+interval '1 second',start_recorded_at=start_recorded_at+interval '1 second' where assignment_id=?",id)).isEqualTo("23514");
        command(a,"complete",command(a,false)).andExpect(status().isOk());
        assertThat(rejectedSqlState("update service_execution_segment set ended_at=null,end_recorded_at=null,ended_by=null,end_event_id=null,end_kind=null where assignment_id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from service_execution_segment where assignment_id=?",id)).isEqualTo("23514");assertThat(segments()).isEqualTo(1);
    }
    protected String rejectedSqlState(String sql,UUID assignment) {
        return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)connection->{
            var savepoint=connection.setSavepoint();
            try(var statement=connection.prepareStatement(sql)) {
                statement.setObject(1,assignment);statement.executeUpdate();return "UNEXPECTED_WRITE";
            } catch(java.sql.SQLException failure) { return failure.getSQLState(); }
            finally { connection.rollback(savepoint);connection.releaseSavepoint(savepoint); }
        });
    }
}
