package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Approved interruption on native PG/real filters; synthetic fixture rolls back without global cleanup. */
class ServiceAttentionIntegrationTest extends ServiceExecutionIntegrationTest {
    private UUID service(JsonNode a){return UUID.fromString(a.path("serviceId").asText());}
    private Map<String,Object> ownBody(JsonNode a) {
        var c=command(a,false);c.put("reason","UNSAFE_BREAKDOWN");c.put("detail","SYNTHETIC inability to continue safely");c.put("safeStopConfirmed",false);return c;
    }
    private Map<String,Object> adminBody(JsonNode a) {return new LinkedHashMap<>(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",a.path("version").asLong(),"reason","ACCIDENT_RISK","detail","SYNTHETIC communicated and checked stop","safeStopConfirmed",true,"verificationReference","SYNTHETIC_SAFE_STOP_CHECK"));}
    private JsonNode interrupted()throws Exception {prepared();var a=started();clock.set(initial.plusSeconds(30));return json(command(a,"interrupt",ownBody(a)).andExpect(status().isOk()));}
    private JsonNode attention(JsonNode a)throws Exception {return json(mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()+"/attention").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk())).get(0);}
    private Map<String,Object> resolveBody(JsonNode r,boolean driver,String vehicle) {return new LinkedHashMap<>(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",r.path("version").asLong(),"releaseDriver",driver,"vehicleDecision",vehicle,"safetyAndTransferConfirmed",true,"reason","SYNTHETIC checked safe resolution","verificationReference","SYNTHETIC_SAFE_RESOLUTION"));}
    private org.springframework.test.web.servlet.ResultActions resolve(JsonNode r,Object body)throws Exception {return adminPost("/api/v1/service-attention/"+r.path("id").asText()+"/resolve",body);}
    private int count(){return jdbc.queryForObject("select count(*) from service_attention where organization_id=?",Integer.class,organization.getId());}
    @Test void safetyOwnInterruptionRetainsServerOriginalsIncidentAndGpsWithoutCompletion()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());clock.set(initial.plusSeconds(30));var body=ownBody(a);
        var result=json(command(a,"interrupt",body).andExpect(status().isOk()).andExpect(header().string("X-Idempotent-Replay","false")));
        assertThat(result.path("status").asText()).isEqualTo("INTERRUPTED");assertThat(result.path("interruptedAt").asText()).isEqualTo(clock.instant().toString());
        assertThat(jdbc.queryForObject("select completed_at is null from assignment where id=?",Boolean.class,UUID.fromString(a.path("id").asText()))).isTrue();
        var r=attention(a);assertThat(r.path("source").asText()).isEqualTo("CONDUCTOR");assertThat(r.path("recordedBy").asText()).isEqualTo(conductor.getId().toString());assertThat(r.path("safeStopConfirmed").asBoolean()).isFalse();
        assertThat(r.path("driverPending").asBoolean()).isTrue();assertThat(r.path("vehiclePending").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject("select end_kind from service_execution_segment where id=?",String.class,UUID.fromString(r.path("segmentId").asText()))).isEqualTo("INTERRUPTED");
        assertThat(jdbc.queryForObject("select count(*) from assignment_route_declaration where assignment_id=?",Integer.class,UUID.fromString(a.path("id").asText()))).isZero();
        assertThat(jdbc.queryForObject("select execution_segment_id from incident where id=?",UUID.class,UUID.fromString(r.path("incidentId").asText())).toString()).isEqualTo(r.path("segmentId").asText());
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where organization_id=?",Integer.class,organization.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from driver_current_location where driver_id=?",Integer.class,driver.getId())).isZero();
        command(a,"interrupt",body).andExpect(status().isOk()).andExpect(header().string("X-Idempotent-Replay","true"));assertThat(count()).isEqualTo(1);
    }
    @Test void safetyChangedReplayAndStaleVersionNeverCreateAnotherInterruption()throws Exception {
        prepared();var a=started();var stale=ownBody(a);stale.put("version",a.path("version").asLong()+1);command(a,"interrupt",stale).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));assertThat(count()).isZero();
        var body=ownBody(a);command(a,"interrupt",body).andExpect(status().isOk());body.put("detail","SYNTHETIC changed payload");command(a,"interrupt",body).andExpect(status().isConflict());assertThat(count()).isEqualTo(1);
    }
    @Test void safetyPermissionLossAndInactiveDriverDoNotPreventRecordingTheSafeStop()throws Exception {
        prepared();var a=started();consent(false).andExpect(status().isOk());entities.clear();var d=drivers.findById(driver.getId()).orElseThrow();d.deactivate();drivers.flush();
        command(a,"interrupt",ownBody(a)).andExpect(status().isOk());assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driver.getId())).isFalse();
        var r=attention(a);resolve(r,resolveBody(r,true,"NO_CHANGE")).andExpect(status().isOk());assertThat(jdbc.queryForObject("select active from driver where id=?",Boolean.class,driver.getId())).isFalse();assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("NO_DISPONIBLE");
    }
    @Test void safetyIncidentClosureDoesNotReleaseEitherInterruptedResource()throws Exception {
        var a=interrupted();var attention=attention(a);String path="/api/v1/incidents/"+attention.path("incidentId").asText();
        var incident=json(mvc.perform(get(path).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.operationalEffect").value("INTERRUPTION_RECORDED")));
        incident=json(adminPost(path+"/responsibility",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",incident.path("version").asLong(),"adminId",admin.getId(),"reason","SYNTHETIC explicit incident responsibility")).andExpect(status().isOk()));
        var pendingClosure=Map.of("operationKey",UUID.randomUUID(),"expectedVersion",incident.path("version").asLong(),"diagnosis","SYNTHETIC diagnosis","actions","SYNTHETIC checked actions","result","SYNTHETIC incident closed separately","evidenceRequired",true,"evidenceMode","REFERENCE","evidenceReference","SYNTHETIC_SAFETY_REVIEW");
        adminPost(path+"/closure",pendingClosure).andExpect(status().isBadRequest());
        incident=json(adminPost(path+"/assessment",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",incident.path("version").asLong(),"severity","HIGH","reason","SYNTHETIC explicit severity review")).andExpect(status().isOk()));
        var closure=new LinkedHashMap<String,Object>(pendingClosure);closure.put("operationKey",UUID.randomUUID());closure.put("expectedVersion",incident.path("version").asLong());
        adminPost(path+"/closure",closure).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        var unchanged=attention(a);assertThat(unchanged.path("driverPending").asBoolean()).isTrue();assertThat(unchanged.path("vehiclePending").asBoolean()).isTrue();assertThat(unchanged.path("resolutions")).isEmpty();
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("ATENCION");
        assertThat(jdbc.queryForObject("select status from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("ATENCION");
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where segment_id=?",Integer.class,UUID.fromString(attention.path("segmentId").asText()))).isZero();
    }
    @Test void safetyQualificationCancellationPersistsNoticeWithoutTouchingStartedExecution()throws Exception {
        prepared();var a=accepted();var c=basic(4);c.put("evidenceReference","SYNTHETIC_SUSPENSION_REVIEW");mutate("/suspend",c).andExpect(status().isOk());
        UUID offer=UUID.fromString(a.path("id").asText());assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,offer)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("select count(*) from operation_notice where user_id=? and event_type='assignment.cancelled' and references_json->>'assignmentId'=?",Integer.class,conductor.getId(),offer.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from operation_notice where user_id=? and event_type='driver.suspended'",Integer.class,conductor.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from service_execution_segment where assignment_id=?",Integer.class,offer)).isZero();
    }
    @Test void safetyQualificationLossRecordsAutomaticIncidentNoticeOnlyOnce()throws Exception {
        prepared();var a=started();var c=basic(4);c.put("evidenceReference","SYNTHETIC_SUSPENSION_REVIEW");mutate("/suspend",c).andExpect(status().isOk());
        UUID offer=UUID.fromString(a.path("id").asText());qualification.effects(drivers.findById(driver.getId()).orElseThrow(),"OPERATIONAL_VALIDITY_LOST");
        assertThat(jdbc.queryForObject("select count(*) from incident where assignment_id=?",Integer.class,offer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from operation_notice where user_id=? and event_type='incident.reported' and references_json->>'assignmentId'=?",Integer.class,conductor.getId(),offer.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,offer)).isEqualTo("EN_SERVICIO");
    }
    @Test void safetyNoStartNoInterruptionAndDelayIsNotACause()throws Exception {
        prepared();var a=accepted();command(a,"interrupt",ownBody(a)).andExpect(status().isConflict());assertThat(count()).isZero();
        clock.set(initial.plusSeconds(3601));mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()).header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));assertThat(count()).isZero();
        var bad=ownBody(a);bad.put("reason","RETRASO");command(a,"interrupt",bad).andExpect(status().isBadRequest());assertThat(count()).isZero();
    }
    @Test void safetyAdminRecordsOnlyAConfirmedCommunicatedStopWithActualAuthorship()throws Exception {
        prepared();var a=started();var c=adminBody(a);c.put("safeStopConfirmed",false);adminPost("/api/v1/assignments/"+a.path("id").asText()+"/interrupt",c).andExpect(status().isBadRequest());assertThat(count()).isZero();
        c.put("safeStopConfirmed",true);c.put("recordedBy",conductor.getId());c.put("source","CONDUCTOR");
        var r=json(adminPost("/api/v1/assignments/"+a.path("id").asText()+"/interrupt",c).andExpect(status().isOk()));
        assertThat(r.path("source").asText()).isEqualTo("ADMIN_VERIFIED_REPORT");assertThat(r.path("recordedBy").asText()).isEqualTo(admin.getId().toString());
        assertThat(jdbc.queryForObject("select ended_by from service_execution_segment where id=?",UUID.class,UUID.fromString(r.path("segmentId").asText()))).isEqualTo(admin.getId());
        adminPost("/api/v1/assignments/"+a.path("id").asText()+"/interrupt",c).andExpect(status().isOk());assertThat(count()).isEqualTo(1);
    }
    @Test void safetyRolesAndObjectScopeDoNotLetAnotherSurfaceImpersonateTheReporter()throws Exception {
        prepared();var a=started();mvc.perform(post("/api/v1/assignments/"+a.path("id").asText()+"/interrupt").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(adminBody(a)))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/interrupt").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(ownBody(a)))).andExpect(status().isForbidden());
        adminPost("/api/v1/assignments/"+UUID.randomUUID()+"/interrupt",adminBody(a)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/mobile/assignments/"+UUID.randomUUID()+"/attention").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());assertThat(count()).isZero();
    }
    @Test void safetyForeignAdminCannotReadInterruptOrResolveTheSameOtherOrganizationCase()throws Exception {
        var a=interrupted();var r=attention(a);
        // Only read an already eligible synthetic ADMIN; no account/case is reopened or changed.
        var foreignAccount=users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();
        assertThat(foreignAccount.getOrganizationId()).isNotEqualTo(organization.getId());String foreign=jwt.issueAccessToken(foreignAccount);
        mvc.perform(get("/api/v1/scheduled-services/"+service(a)+"/attention").header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/assignments/"+a.path("id").asText()+"/interrupt").header("Authorization","Bearer "+foreign).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(adminBody(a)))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/service-attention/"+r.path("id").asText()+"/resolve").header("Authorization","Bearer "+foreign).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(resolveBody(r,true,"READY")))).andExpect(status().isNotFound());
        assertThat(attention(a).path("version").asLong()).isZero();assertThat(count()).isEqualTo(1);
    }
    @Test void safetyAttentionBlocksNewOperationAndPointsButKeepsOwnConsultation()throws Exception {
        var a=interrupted();assertThat(policy.eligible(driver.getId(),organization.getId(),true)).isFalse();
        mvc.perform(get("/api/v1/mobile/me").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.availabilityStatus").value("ATENCION"));
        point(clock.instant(),true).andExpect(status().isConflict());command(a,"complete",command(a,false)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("ATENCION");
    }
    @Test void safetyResolutionNeedsFreshStateSafetyConfirmationAndAdmin()throws Exception {
        var r=attention(interrupted());var body=resolveBody(r,true,"READY");body.put("safetyAndTransferConfirmed",false);resolve(r,body).andExpect(status().isBadRequest());
        body.put("safetyAndTransferConfirmed",true);body.put("expectedVersion",1);resolve(r,body).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/service-attention/"+r.path("id").asText()+"/resolve").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(resolveBody(r,true,"READY")))).andExpect(status().isForbidden());
        assertThat(attention(interruptedView(r)).path("version").asLong()).isZero();
    }
    private JsonNode interruptedView(JsonNode r){var node=mapper.createObjectNode();node.put("id",r.path("assignmentId").asText());return node;}
    @Test void safetyIndependentDriverReleaseAndWithdrawalKeepTheUnitBlockedUntilExplicitReadyReview()throws Exception {
        var a=interrupted();var r=attention(a);var declared=jdbc.queryForObject("select declared_availability_at from driver where id=?",java.sql.Timestamp.class,driver.getId());
        var c=resolveBody(r,true,"KEEP_WITHDRAWN");var withdrawn=json(resolve(r,c).andExpect(status().isOk()));
        assertThat(withdrawn.path("driverPending").asBoolean()).isFalse();assertThat(withdrawn.path("vehiclePending").asBoolean()).isTrue();assertThat(withdrawn.path("version").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select active from vehicle where id=?",Boolean.class,vehicle.getId())).isFalse();assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("NO_DISPONIBLE");
        resolve(r,c).andExpect(status().isOk());adminPost("/api/v1/vehicles/"+vehicle.getId()+"/status",Map.of("status","DISPONIBLE")).andExpect(status().isConflict());
        resolve(withdrawn,resolveBody(withdrawn,false,"READY")).andExpect(status().isOk()).andExpect(jsonPath("$.vehiclePending").value(false));
        assertThat(jdbc.queryForObject("select active from vehicle where id=?",Boolean.class,vehicle.getId())).isFalse();assertThat(jdbc.queryForObject("select declared_availability_at from driver where id=?",java.sql.Timestamp.class,driver.getId())).isEqualTo(declared);
    }
    @Test void safetyReadyReleaseRestoresOnlyEligibilityAndTheExistingAvailabilityDeclaration()throws Exception {
        var a=interrupted();var r=attention(a);var declared=jdbc.queryForObject("select declared_availability_at from driver where id=?",java.sql.Timestamp.class,driver.getId());clock.set(initial.plusSeconds(60));
        var c=resolveBody(r,true,"READY");resolve(r,c).andExpect(status().isOk()).andExpect(jsonPath("$.driverPending").value(false)).andExpect(jsonPath("$.vehiclePending").value(false));resolve(r,c).andExpect(status().isOk());
        assertThat(policy.eligible(driver.getId(),organization.getId(),true)).isTrue();assertThat(jdbc.queryForObject("select declared_availability_at from driver where id=?",java.sql.Timestamp.class,driver.getId())).isEqualTo(declared);
        assertThat(jdbc.queryForObject("select status from scheduled_service where id=?",String.class,service(a))).isEqualTo("INTERRUPTED");
    }
    @Test void safetyCancellationPreservesInterruptedParticipationAndHoldsAndAllowsSeparateTimeReview()throws Exception {
        var a=interrupted();var r=attention(a);long version=jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service(a));
        adminPost("/api/v1/scheduled-services/"+service(a)+"/cancel",Map.of("operationKey","synthetic-cancel-"+UUID.randomUUID(),"expectedVersion",version,"reason","SYNTHETIC cancelled continuation")).andExpect(status().isOk()).andExpect(jsonPath("$.service.status").value("CANCELLED"));
        assertThat(attention(a).path("driverPending").asBoolean()).isTrue();assertThat(attention(a).path("vehiclePending").asBoolean()).isTrue();
        long currentVersion=jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service(a));
        adminPost("/api/v1/scheduled-services/"+service(a)+"/time-corrections",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",currentVersion,"reason","SYNTHETIC reviewed interruption timing","segments",List.of(Map.of("segmentId",r.path("segmentId").asText(),"startedAt",initial.plusSeconds(1),"endedAt",initial.plusSeconds(29))))).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(jdbc.queryForObject("select count(*) from assignment_route_declaration where assignment_id=?",Integer.class,UUID.fromString(a.path("id").asText()))).isZero();
    }
    @Test void safetyLateOwnIncidentRetainsTheOccurrenceSegmentWithoutNewOperationalEligibility()throws Exception {
        var a=interrupted();var r=attention(a);
        mvc.perform(post("/api/v1/mobile/incidents").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("assignmentId",a.path("id").asText(),"category","OTRO","description","SYNTHETIC report after interruption","occurredAt",initial,"severity","LOW","operationKey",UUID.randomUUID())))).andExpect(status().isCreated()).andExpect(jsonPath("$.executionSegmentId").value(r.path("segmentId").asText()));
    }
    @Test void safetyDatabaseRejectsRewritingAttentionOrReleasingItsResourcesThroughOrdinaryState()throws Exception {
        var a=interrupted();var r=attention(a);UUID id=UUID.fromString(r.path("id").asText());
        assertThat(rejectedSqlState("update service_attention set safe_stop_confirmed=true where id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from service_attention where id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("update driver set availability_status='DISPONIBLE' where id=?",driver.getId())).isEqualTo("23514");
        assertThat(rejectedSqlState("update vehicle set status='DISPONIBLE' where id=?",vehicle.getId())).isEqualTo("23514");
        var resolved=json(resolve(r,resolveBody(r,true,"READY")).andExpect(status().isOk()));UUID decision=UUID.fromString(resolved.path("resolutions").get(0).path("id").asText());
        assertThat(rejectedSqlState("delete from service_attention_release where id=?",decision)).isEqualTo("23514");
    }
    @Test void safetyRejectsCoordinatesOrPersonalDataInPermanentSafetyNotes()throws Exception {
        prepared();var a=started();var body=ownBody(a);body.put("detail","SYNTHETIC -6.77, -79.84");command(a,"interrupt",body).andExpect(status().isConflict());assertThat(count()).isZero();
        body=ownBody(a);body.put("detail","SYNTHETIC@example.test");command(a,"interrupt",body).andExpect(status().isConflict());assertThat(count()).isZero();
    }
    private JsonNode maintenance()throws Exception {return json(mvc.perform(get("/api/v1/vehicles/"+vehicle.getId()+"/maintenance").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));}
    private Map<String,Object> maintenanceCommand(JsonNode view) {return new LinkedHashMap<>(Map.of("operationKey","synthetic-"+UUID.randomUUID(),"expectedVersion",view.path("version").asLong(),"reason","SYNTHETIC actual maintenance review"));}
    @Test void safetyMaintenanceReturnRecordsItsOwnCheckWithoutReactivatingAWithdrawnUnitOrRemovingAttention()throws Exception {
        var a=interrupted();var r=attention(a);var create=maintenanceCommand(maintenance());create.put("kind","URGENT");
        var m=json(adminPost("/api/v1/vehicles/"+vehicle.getId()+"/maintenance",create).andExpect(status().isOk()));String record=m.path("records").get(0).path("id").asText();
        var withdrawn=json(resolve(r,resolveBody(r,true,"KEEP_WITHDRAWN")).andExpect(status().isOk()));
        resolve(withdrawn,resolveBody(withdrawn,false,"READY")).andExpect(status().isConflict());
        var work=maintenanceCommand(m);work.put("responsibleReference","SYNTHETIC_TECHNICAL_ROLE");work.put("evidenceReference","SYNTHETIC_WORK_REVIEW");work.put("workCompleted",true);
        m=json(adminPost("/api/v1/vehicles/"+vehicle.getId()+"/maintenance/"+record+"/work-completed",work).andExpect(status().isOk()));
        var returned=json(adminPost("/api/v1/vehicles/"+vehicle.getId()+"/maintenance/return",maintenanceCommand(m)).andExpect(status().isOk()));
        assertThat(returned.path("records").get(0).path("status").asText()).isEqualTo("RETURNED");assertThat(policy.safetyVehicleBlocks(vehicle.getId(),organization.getId())).isTrue();
        assertThat(jdbc.queryForObject("select active from vehicle where id=?",Boolean.class,vehicle.getId())).isFalse();
        resolve(withdrawn,resolveBody(withdrawn,false,"READY")).andExpect(status().isConflict());
        Instant nextDay=initial.atZone(ZoneId.of("America/Lima")).toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).toInstant();clock.set(nextDay);
        resolve(withdrawn,resolveBody(withdrawn,false,"READY")).andExpect(status().isOk()).andExpect(jsonPath("$.vehiclePending").value(false));
        assertThat(jdbc.queryForObject("select active from vehicle where id=?",Boolean.class,vehicle.getId())).isFalse();assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("NO_DISPONIBLE");
        adminPost("/api/v1/vehicles/"+vehicle.getId()+"/status",Map.of("status","DISPONIBLE")).andExpect(status().isOk());
        entities.flush(); // Native HTTP joins this outer rollback transaction; JDBC must see the ordinary JPA status write.
        assertThat(policy.eligible(driver.getId(),organization.getId(),true)).isTrue();
    }
}
