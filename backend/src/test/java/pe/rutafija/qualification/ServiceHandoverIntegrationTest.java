package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Two participants solely inside a rollback fixture: no persistent account, SMTP or reopened closed case. */
class ServiceHandoverIntegrationTest extends ServiceExecutionIntegrationTest {
    private JsonNode outgoing;private AdditionalParticipant replacement;private JsonNode preparedView;
    private UUID service(){return UUID.fromString(outgoing.path("serviceId").asText());}
    private void scenario()throws Exception {prepared();outgoing=started();replacement=prepareAdditionalSyntheticConductor();clock.set(initial.plusSeconds(30));}
    private long version(){return jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service());}
    private int segments(){return jdbc.queryForObject("select count(*) from service_execution_segment where organization_id=?",Integer.class,organization.getId());}
    private Map<String,Object> prepareBody(){return new LinkedHashMap<>(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",version(),"driverId",replacement.driver().getId(),"vehicleId",replacement.vehicle().getId(),"scheduledAt",initial.plusSeconds(1800),"scheduledEndAt",initial.plusSeconds(7200),"reason","SYNTHETIC safe handover","pointReference","SYNTHETIC_TRANSFER_POINT","safetyAndTransferConfirmed",true));}
    private ResultActions prepare(Object body)throws Exception{return adminPost("/api/v1/scheduled-services/"+service()+"/handovers",body);}
    private JsonNode preparedHandover()throws Exception {preparedView=json(prepare(prepareBody()).andExpect(status().isOk()));return preparedView.path("handovers").get(preparedView.path("handovers").size()-1);}
    private JsonNode incoming(JsonNode h)throws Exception{return json(mvc.perform(get("/api/v1/assignments/"+h.path("incomingAssignmentId").asText()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));}
    private ResultActions incomingCommand(JsonNode a,String action,Object body)throws Exception {entities.flush();entities.clear();return mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/"+action).header("Authorization","Bearer "+replacement.mobileToken()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));}
    private JsonNode acceptedIncoming(JsonNode h)throws Exception{var i=incoming(h);return json(incomingCommand(i,"accept",command(i,false)).andExpect(status().isOk()));}
    private void incomingConsent()throws Exception{mvc.perform(put("/api/v1/mobile/location/consent").header("Authorization","Bearer "+replacement.mobileToken()).contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}")).andExpect(status().isOk());}
    private Map<String,Object> assumeBody(JsonNode i){var c=command(i,true);c.put("readyConfirmed",true);return c;}
    private JsonNode assume(JsonNode i)throws Exception{incomingConsent();clock.set(clock.instant().plusSeconds(30));return json(incomingCommand(i,"assume-handover",assumeBody(i)).andExpect(status().isOk()));}
    private JsonNode view()throws Exception{return json(mvc.perform(get("/api/v1/scheduled-services/"+service()+"/handovers").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));}
    @Test void handoverPreparationAndAcceptanceDoNotStartOrReplaceTheCurrentExecution()throws Exception{
        scenario();var c=prepareBody();var h=json(prepare(c).andExpect(status().isOk())).path("handovers").get(0);prepare(c).andExpect(status().isOk());
        var i=acceptedIncoming(h);assertThat(i.path("status").asText()).isEqualTo("SCHEDULED");assertThat(i.path("handoverId").asText()).isEqualTo(h.path("id").asText());
        var header=view().path("service");assertThat(header.path("status").asText()).isEqualTo("IN_SERVICE");assertThat(header.path("currentOfferId").asText()).isEqualTo(outgoing.path("id").asText());assertThat(segments()).isEqualTo(1);
        incomingConsent();incomingCommand(i,"start",command(i,true)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverAssumptionAtomicallyTransfersAndOpensOriginalSegmentsWithoutRestartingService()throws Exception{
        scenario();var h=preparedHandover();var i=acceptedIncoming(h);var active=assume(i);
        var segments=jdbc.queryForList("select ordinal,driver_id,vehicle_id,started_at,ended_at,ended_by,end_kind from service_execution_segment where service_id=? order by ordinal",service());assertThat(segments).hasSize(2);
        assertThat(segments.get(0).get("end_kind")).isEqualTo("TRANSFERRED");assertThat(segments.get(0).get("ended_by")).isEqualTo(replacement.account().getId());assertThat(segments.get(0).get("ended_at")).isEqualTo(segments.get(1).get("started_at"));
        assertThat(segments.get(1).get("driver_id")).isEqualTo(replacement.driver().getId());assertThat(active.path("status").asText()).isEqualTo("EN_SERVICIO");var v=view();assertThat(v.path("service").path("originalStartedAt").asText()).isEqualTo(initial.toString());assertThat(v.path("service").path("scheduledAt").asText()).isEqualTo(outgoing.path("scheduledAt").asText());assertThat(v.path("handovers").get(0).path("pauseSeconds").asLong()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from service_execution_segment where service_id=? and ended_at is null",Integer.class,service())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select completed_at is null from assignment where id=?",Boolean.class,UUID.fromString(outgoing.path("id").asText()))).isTrue();
    }
    @Test void handoverVersionReplayAndChangedPayloadConserveOneEffectiveTransfer()throws Exception{
        scenario();var i=acceptedIncoming(preparedHandover());incomingConsent();var stale=assumeBody(i);stale.put("version",i.path("version").asLong()+1);incomingCommand(i,"assume-handover",stale).andExpect(status().isConflict());assertThat(segments()).isEqualTo(1);
        var c=assumeBody(i);incomingCommand(i,"assume-handover",c).andExpect(status().isOk());incomingCommand(i,"assume-handover",c).andExpect(status().isOk()).andExpect(header().string("X-Idempotent-Replay","true"));
        c.put("readyConfirmed",false);incomingCommand(i,"assume-handover",c).andExpect(status().isBadRequest());assertThat(segments()).isEqualTo(2);
    }
    @Test void handoverRequiresPersonalAcceptancePermissionAndCurrentEligibilityBeforeAnyWrites()throws Exception{
        scenario();var h=preparedHandover();var i=incoming(h);incomingCommand(i,"assume-handover",assumeBody(i)).andExpect(status().isConflict());i=acceptedIncoming(h);
        incomingCommand(i,"assume-handover",assumeBody(i)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_CONSENT_REQUIRED"));incomingConsent();var bad=assumeBody(i);bad.put("gps",Map.of("permissionGranted",false,"locationServiceEnabled",true,"foreground",true));incomingCommand(i,"assume-handover",bad).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_PERMISSION_NOT_REPORTED"));
        entities.clear();var unit=vehicles.findById(replacement.vehicle().getId()).orElseThrow();unit.deactivate();vehicles.flush();incomingCommand(i,"assume-handover",assumeBody(i)).andExpect(status().isConflict());assertThat(segments()).isEqualTo(1);assertThat(jdbc.queryForObject("select count(*) from service_handover_resolution where handover_id=?",Integer.class,UUID.fromString(h.path("id").asText()))).isZero();
    }
    @Test void handoverFromInterruptionRetainsItsPauseOriginalStatusAndSeparateResourceAttention()throws Exception{
        scenario();var interrupt=command(outgoing,false);interrupt.put("reason","UNSAFE_BREAKDOWN");interrupt.put("detail","SYNTHETIC safe stop");interrupt.put("safeStopConfirmed",true);command(outgoing,"interrupt",interrupt).andExpect(status().isOk());Instant interruptedAt=clock.instant();
        var i=acceptedIncoming(preparedHandover());clock.set(clock.instant().plusSeconds(60));assume(i);
        assertThat(view().path("handovers").get(0).path("pauseSeconds").asLong()).isEqualTo(Duration.between(interruptedAt,clock.instant()).getSeconds());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(outgoing.path("id").asText()))).isEqualTo("INTERRUPTED");assertThat(policy.safetyDriverBlocks(driver.getId(),organization.getId())).isTrue();assertThat(policy.safetyVehicleBlocks(vehicle.getId(),organization.getId())).isTrue();assertThat(jdbc.queryForObject("select count(*) from service_attention_release where organization_id=?",Integer.class,organization.getId())).isZero();
    }
    @Test void handoverExplicitWithdrawalReleasesOnlyTheUnstartedStandbyAndKeepsOutgoingExecution()throws Exception{
        scenario();var h=preparedHandover();acceptedIncoming(h);var c=Map.of("operationKey",UUID.randomUUID(),"expectedVersion",version(),"reason","SYNTHETIC standby withdrawn");adminPost("/api/v1/service-handovers/"+h.path("id").asText()+"/withdraw",c).andExpect(status().isOk()).andExpect(jsonPath("$.handovers[0].state").value("WITHDRAWN"));adminPost("/api/v1/service-handovers/"+h.path("id").asText()+"/withdraw",c).andExpect(status().isOk());
        assertThat(view().path("service").path("status").asText()).isEqualTo("IN_SERVICE");assertThat(incoming(h).path("status").asText()).isEqualTo("CANCELLED");assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,replacement.driver().getId())).isEqualTo("DISPONIBLE");assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverOwnCompletionClosesAnAcceptedStandbyWithoutImpersonatingAdmin()throws Exception{
        scenario();var h=preparedHandover();acceptedIncoming(h);clock.set(initial.plusSeconds(90));command(outgoing,"complete",command(outgoing,false)).andExpect(status().isOk());
        assertThat(view().path("service").path("status").asText()).isEqualTo("COMPLETED");assertThat(view().path("handovers").get(0).path("state").asText()).isEqualTo("SERVICE_ENDED");assertThat(view().path("handovers").get(0).path("resolvedBy").asText()).isEqualTo(conductor.getId().toString());assertThat(incoming(h).path("status").asText()).isEqualTo("CANCELLED");assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverCancellingInterruptedContinuityWithdrawsStandbyButPreservesSafetyHolds()throws Exception{
        scenario();var h=preparedHandover();acceptedIncoming(h);var c=command(outgoing,false);c.put("reason","IMPOSSIBLE_SERVICE");c.put("detail","SYNTHETIC interrupted continuity");c.put("safeStopConfirmed",true);command(outgoing,"interrupt",c).andExpect(status().isOk());
        adminPost("/api/v1/scheduled-services/"+service()+"/cancel",Map.of("operationKey","synthetic-cancel-"+UUID.randomUUID(),"expectedVersion",version(),"reason","SYNTHETIC cancel continuity")).andExpect(status().isOk());assertThat(view().path("service").path("status").asText()).isEqualTo("CANCELLED");assertThat(incoming(h).path("status").asText()).isEqualTo("CANCELLED");assertThat(policy.safetyDriverBlocks(driver.getId(),organization.getId())).isTrue();assertThat(policy.safetyVehicleBlocks(vehicle.getId(),organization.getId())).isTrue();
    }
    @Test void handoverExpiredOrRejectedStandbyDoesNotCancelExecutionAndAllowsAnotherExplicitPreparation()throws Exception{
        scenario();var h=preparedHandover();clock.set(initial.plusSeconds(700));var stale=incoming(h);incomingCommand(stale,"accept",command(stale,false)).andExpect(status().isConflict());assertThat(view().path("service").path("status").asText()).isEqualTo("IN_SERVICE");var next=preparedHandover();assertThat(next.path("id").asText()).isNotEqualTo(h.path("id").asText());assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverPreparationRejectsSelfMissingSafetyAndDuplicatePendingIntent()throws Exception{
        scenario();var c=prepareBody();c.put("safetyAndTransferConfirmed",false);prepare(c).andExpect(status().isBadRequest());c=prepareBody();c.put("driverId",driver.getId());prepare(c).andExpect(status().isConflict());preparedHandover();prepare(prepareBody()).andExpect(status().isConflict());assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverRolesAndForeignTenantCannotReadOrAssumeTheSameCase()throws Exception{
        scenario();var h=preparedHandover();var i=acceptedIncoming(h);mvc.perform(post("/api/v1/mobile/assignments/"+i.path("id").asText()+"/assume-handover").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(assumeBody(i)))).andExpect(status().isForbidden());command(i,"assume-handover",assumeBody(i)).andExpect(status().isNotFound());
        String foreign=jwt.issueAccessToken(users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow());mvc.perform(get("/api/v1/scheduled-services/"+service()+"/handovers").header("Authorization","Bearer "+foreign)).andExpect(status().isNotFound());assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverDatabaseRejectsForgedTransferAndRewritingPreparedIntent()throws Exception{
        scenario();assertThat(rejectedSqlState("update assignment set status='TRANSFERRED',transferred_at=started_at where id=?",UUID.fromString(outgoing.path("id").asText()))).isEqualTo("23514");var h=preparedHandover();assertThat(rejectedSqlState("update service_handover set point_reference='SYNTHETIC_OTHER' where id=?",UUID.fromString(h.path("id").asText()))).isEqualTo("23514");assertThat(rejectedSqlState("delete from service_handover where id=?",UUID.fromString(h.path("id").asText()))).isEqualTo("23514");
    }
    @Test void handoverMultipleSegmentsCanBeReviewedWithoutRewritingOriginalTimesAndOnlyOwnRequestsAreVisible()throws Exception{
        scenario();var i=acceptedIncoming(preparedHandover());var active=assume(i);clock.set(initial.plusSeconds(120));incomingCommand(active,"complete",command(active,false)).andExpect(status().isOk());
        var originals=jdbc.queryForList("select id,started_at,ended_at from service_execution_segment where service_id=? order by ordinal",service());var values=new ArrayList<Map<String,Object>>();for(var r:originals)values.add(Map.of("segmentId",r.get("id"),"startedAt",((java.sql.Timestamp)r.get("started_at")).toInstant().plusSeconds(1),"endedAt",((java.sql.Timestamp)r.get("ended_at")).toInstant().minusSeconds(1)));
        var overlap=new ArrayList<>(values);var second=new LinkedHashMap<>(overlap.get(1));second.put("startedAt",initial);overlap.set(1,second);
        adminPost("/api/v1/scheduled-services/"+service()+"/time-corrections",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",version(),"reason","SYNTHETIC rejected overlap","segments",overlap)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from service_time_correction where service_id=?",Integer.class,service())).isZero();
        adminPost("/api/v1/scheduled-services/"+service()+"/time-corrections",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",version(),"reason","SYNTHETIC multisegment review","segments",values)).andExpect(status().isOk()).andExpect(jsonPath("$.segments.length()").value(2));assertThat(jdbc.queryForList("select id,started_at,ended_at from service_execution_segment where service_id=? order by ordinal",service())).isEqualTo(originals);
        mvc.perform(get("/api/v1/mobile/assignments/"+outgoing.path("id").asText()+"/time-history").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.segments.length()").value(1));
    }
    @Test void handoverMissedStartIsAnExplicitAlertWithoutStartingOrCancellingTheConfirmedService()throws Exception{
        prepared();outgoing=accepted();clock.set(initial.plusSeconds(3601));var s=json(mvc.perform(get("/api/v1/scheduled-services/"+service()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk())).path("service");assertThat(s.path("status").asText()).isEqualTo("CONFIRMED");assertThat(s.path("startOverdue").asBoolean()).isTrue();assertThat(segments()).isZero();
    }
    @Test void handoverCandidatesAreTenantBoundQualifiedPairsAndDoNotOfferTheExecutingParticipant()throws Exception{
        scenario();var result=json(mvc.perform(get("/api/v1/scheduled-services/"+service()+"/handover-candidates").param("from",initial.plusSeconds(1800).toString()).param("to",initial.plusSeconds(7200).toString()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));
        assertThat(result.path("items").toString()).contains(replacement.driver().getId().toString()).doesNotContain(driver.getId().toString());assertThat(segments()).isEqualTo(1);
    }
    @Test void handoverClearsSnapshotsSeparatesNewGpsAndRetainsImmutableTransferEvidence()throws Exception{
        scenario();point(initial,true).andExpect(status().isNoContent());var h=preparedHandover();var i=acceptedIncoming(h);assume(i);
        assertThat(jdbc.queryForObject("select count(*) from driver_current_location where organization_id=?",Integer.class,organization.getId())).isZero();assertThat(jdbc.queryForObject("select count(*) from service_gps_point where organization_id=?",Integer.class,organization.getId())).isEqualTo(1);
        point(clock.instant(),true).andExpect(status().isConflict());mvc.perform(put("/api/v1/mobile/location/current").header("Authorization","Bearer "+replacement.mobileToken()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("latitude",-6.77,"longitude",-79.84,"accuracyM",5,"capturedAt",clock.instant(),"permissionGranted",true,"locationServiceEnabled",true,"foreground",true)))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(distinct segment_id) from service_gps_point where organization_id=?",Integer.class,organization.getId())).isEqualTo(2);
        assertThat(rejectedSqlState("update assignment set transferred_at=transferred_at+interval '1 second' where id=?",UUID.fromString(outgoing.path("id").asText()))).isEqualTo("23514");assertThat(rejectedSqlState("delete from service_handover_resolution where handover_id=?",UUID.fromString(h.path("id").asText()))).isEqualTo("23514");
    }
    @Test void handoverGroupCannotBeRetiredWhileInterruptedContinuityOrSafetyAttentionIsPending()throws Exception{
        scenario();var c=command(outgoing,false);c.put("reason","IMPOSSIBLE_SERVICE");c.put("detail","SYNTHETIC interrupted continuity");c.put("safeStopConfirmed",true);command(outgoing,"interrupt",c).andExpect(status().isOk());
        UUID group=driver.getGroup().getId();var resources=json(mvc.perform(get("/api/v1/groups/"+group+"/resources").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));assertThat(resources.path("commitments").toString()).contains(outgoing.path("id").asText()).contains("SAFETY_ATTENTION");
        mvc.perform(patch("/api/v1/groups/"+group).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("name",driver.getGroup().getName(),"active",false,"expectedVersion",resources.path("version").asLong())))).andExpect(status().isConflict());
        adminPost("/api/v1/scheduled-services/"+service()+"/cancel",Map.of("operationKey","synthetic-cancel-"+UUID.randomUUID(),"expectedVersion",version(),"reason","SYNTHETIC continuity cancelled")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/groups/"+group+"/resources").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.commitments[0].attentionKind").value("SAFETY_ATTENTION"));
    }
}
