package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import pe.rutafija.operation.application.ServiceGpsRetention;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PG/filtros; append-only effective times, no migration or cleanup inside tests. */
class ServiceTimeIntegrationTest extends ServiceExecutionIntegrationTest {
    @Autowired ServiceGpsRetention retention;
    private UUID service(JsonNode a){return UUID.fromString(a.path("serviceId").asText());}
    private UUID segment(JsonNode a){return jdbc.queryForObject("select id from service_execution_segment where assignment_id=?",UUID.class,UUID.fromString(a.path("id").asText()));}
    private long version(JsonNode a){return jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service(a));}
    private Map<String,Object> correction(JsonNode a,Instant start,Instant end) {
        return new LinkedHashMap<>(Map.of("operationKey",UUID.randomUUID(),"expectedVersion",version(a),"reason","SYNTHETIC verified timing",
            "segments",List.of(Map.of("segmentId",segment(a),"startedAt",start,"endedAt",end))));
    }
    private org.springframework.test.web.servlet.ResultActions correct(JsonNode a,Object body)throws Exception {return adminPost("/api/v1/scheduled-services/"+service(a)+"/time-corrections",body);}
    private JsonNode finished(boolean gps)throws Exception {
        prepared();JsonNode a;
        if(gps) {
            var stops=List.of(Map.of("key","start","kind","START","name","SYNTHETIC origin"),Map.of("key","middle","kind","INTERMEDIATE","name","SYNTHETIC middle"),Map.of("key","end","kind","END","name","SYNTHETIC destination","latitude",-6.77,"longitude",-79.84));
            var route=json(adminPost("/api/v1/routes",Map.of("name","SYNTHETIC timing route "+UUID.randomUUID().toString().replaceAll("[0-9]","x"),"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC_TERMINAL","stops",stops)).andExpect(status().isOk()));
            consent(true).andExpect(status().isOk());a=offer(UUID.fromString(route.path("currentVersionId").asText()));a=json(command(a,"accept",command(a,false)).andExpect(status().isOk()));a=json(command(a,"start",command(a,true)).andExpect(status().isOk()));
            clock.set(initial.plusSeconds(25));point(clock.instant(),true).andExpect(status().isNoContent());
        } else a=started();
        clock.set(initial.plusSeconds(30));return json(command(a,"complete",command(a,false)).andExpect(status().isOk()));
    }
    private int revisions(){return jdbc.queryForObject("select count(*) from service_time_correction where organization_id=?",Integer.class,organization.getId());}
    @Test void timesAdminCorrectionPreservesOriginalsAndGpsAndReportsEffectiveCorroboration()throws Exception {
        var a=finished(true);String original=jdbc.queryForObject("select terminal_result from assignment_route_declaration where assignment_id=?",String.class,UUID.fromString(a.path("id").asText()));
        var v=json(correct(a,correction(a,initial.plusSeconds(1),initial.plusSeconds(20))).andExpect(status().isOk()));
        assertThat(v.path("segments").get(0).path("originalStartedAt").asText()).isEqualTo(initial.toString());
        assertThat(v.path("segments").get(0).path("endedAt").asText()).isEqualTo(initial.plusSeconds(20).toString());
        assertThat(v.path("corrections").get(0).path("originalTerminalResult").asText()).isEqualTo(original);
        assertThat(v.path("corrections").get(0).path("effectiveTerminalResult").asText()).isEqualTo("NOT_CORROBORATED_STALE_OR_IMPRECISE");
        assertThat(jdbc.queryForObject("select ended_at from service_execution_segment where id=?",java.sql.Timestamp.class,segment(a)).toInstant()).isEqualTo(initial.plusSeconds(30));
        assertThat(jdbc.queryForObject("select terminal_result from assignment_route_declaration where assignment_id=?",String.class,UUID.fromString(a.path("id").asText()))).isEqualTo(original);
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where organization_id=?",Integer.class,organization.getId())).isEqualTo(1);
        entities.flush(); // JDBC does not trigger JPA's deferred audit insert in this rollback fixture.
        assertThat(jdbc.queryForObject("select count(*) from audit_event where organization_id=? and action='SERVICE_TIMES_CORRECTED'",Integer.class,organization.getId())).isEqualTo(1);
    }
    @Test void timesCorrectionsAreIdempotentVersionedAndPreserveThePreviousEffectiveValues()throws Exception {
        var a=finished(false);var c=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));correct(a,c).andExpect(status().isOk());correct(a,c).andExpect(status().isOk());assertThat(revisions()).isEqualTo(1);
        var altered=new LinkedHashMap<>(c);altered.put("reason","SYNTHETIC different");correct(a,altered).andExpect(status().isConflict());
        var stale=correction(a,initial.plusSeconds(2),initial.plusSeconds(21));stale.put("expectedVersion",((Number)c.get("expectedVersion")).longValue());correct(a,stale).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));
        var v=json(correct(a,correction(a,initial.plusSeconds(2),initial.plusSeconds(21))).andExpect(status().isOk()));
        assertThat(revisions()).isEqualTo(2);assertThat(v.path("corrections").get(1).path("segments").get(0).path("beforeEndedAt").asText()).isEqualTo(initial.plusSeconds(20).toString());
        assertThat(v.path("segments").get(0).path("originalEndedAt").asText()).isEqualTo(initial.plusSeconds(30).toString());
    }
    @Test void timesRejectFutureInvertedUnchangedMissingAndDuplicateSegmentsWithoutMutation()throws Exception {
        var a=finished(false);
        correct(a,correction(a,initial,clock.instant().plusSeconds(1))).andExpect(status().isConflict());
        correct(a,correction(a,initial.plusSeconds(20),initial.plusSeconds(10))).andExpect(status().isConflict());
        correct(a,correction(a,initial,initial.plusSeconds(30))).andExpect(status().isConflict());
        var c=correction(a,initial,initial.plusSeconds(20));c.put("segments",List.of(Map.of("segmentId",UUID.randomUUID(),"startedAt",initial,"endedAt",initial.plusSeconds(20))));correct(a,c).andExpect(status().isConflict());
        c=correction(a,initial,initial.plusSeconds(20));c.put("segments",List.of(((List<?>)c.get("segments")).getFirst(),((List<?>)c.get("segments")).getFirst()));correct(a,c).andExpect(status().isConflict());
        assertThat(revisions()).isZero();
    }
    @Test void timesCannotCorrectAnOngoingServiceOrRequestBeforeFinalization()throws Exception {
        prepared();var a=started();correct(a,correction(a,initial.minusSeconds(10),initial)).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/time-correction-requests").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("requestKey",UUID.randomUUID(),"reason","SYNTHETIC request")))).andExpect(status().isConflict());assertThat(revisions()).isZero();
    }
    @Test void timesConductorOnlyRequestsAdminAppliesAndRequestContentIsIdempotent()throws Exception {
        var a=finished(false);UUID key=UUID.randomUUID();var body=Map.of("requestKey",key,"reason","SYNTHETIC timing request");String path="/api/v1/mobile/assignments/"+a.path("id").asText()+"/time-correction-requests";
        var result=json(mvc.perform(post(path).header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isOk()));
        mvc.perform(post(path).header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(result.path("id").asText()));
        mvc.perform(post(path).header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("requestKey",key,"reason","SYNTHETIC changed")))).andExpect(status().isConflict());
        var c=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));c.put("requestId",UUID.fromString(result.path("id").asText()));
        mvc.perform(post("/api/v1/scheduled-services/"+service(a)+"/time-corrections").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c))).andExpect(status().isForbidden());
        correct(a,c).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()+"/time-history").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.requests[0].correctionId").isNotEmpty()).andExpect(jsonPath("$.segments[0].latitude").doesNotExist());
    }
    @Test void timesRequestMustBelongToTheServiceAndCannotBeReused()throws Exception {
        var a=finished(false);var c=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));c.put("requestId",UUID.randomUUID());correct(a,c).andExpect(status().isConflict());assertThat(revisions()).isZero();
    }
    @Test void timesNoPointIsReconstructedAfterItsOriginalRetentionExpires()throws Exception {
        var a=finished(true);clock.set(initial.plus(Duration.ofDays(7)).plusSeconds(26));retention.purgeExpired(organization.getId());
        correct(a,correction(a,initial.plusSeconds(1),initial.plusSeconds(20))).andExpect(status().isOk()).andExpect(jsonPath("$.corrections[0].effectiveTerminalResult").value("NOT_REASSESSABLE_ORIGINAL_POINT_EXPIRED_OR_ABSENT"));
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where organization_id=?",Integer.class,organization.getId())).isZero();
    }
    @Test void timesRejectOverlapWithTheSameResourcesExecutingAnotherService()throws Exception {
        var a=finished(false);clock.set(initial.plusSeconds(40));var b=started();clock.set(initial.plusSeconds(60));command(b,"complete",command(b,false)).andExpect(status().isOk());
        correct(a,correction(a,initial.plusSeconds(1),initial.plusSeconds(50))).andExpect(status().isConflict());assertThat(revisions()).isZero();
    }
    @Test void timesCorrectionAndItsDetailsCannotBeRewrittenOrDeleted()throws Exception {
        var a=finished(false);var v=json(correct(a,correction(a,initial.plusSeconds(1),initial.plusSeconds(20))).andExpect(status().isOk()));UUID id=UUID.fromString(v.path("corrections").get(0).path("id").asText());
        assertThat(rejectedSqlState("update service_time_correction set reason='SYNTHETIC overwritten' where id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from service_time_correction where id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("update service_time_correction_segment set corrected_ended_at=corrected_ended_at+interval '1 second' where correction_id=?",id)).isEqualTo("23514");
    }
    @Test void timesMissingTermsUsesTheActualGate()throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true));
        mvc.perform(get("/api/v1/admin/terms/current").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
        adminPost("/api/v1/scheduled-services/"+UUID.randomUUID()+"/time-corrections",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",0,"reason","SYNTHETIC","segments",List.of(Map.of("segmentId",UUID.randomUUID(),"startedAt",initial,"endedAt",initial)))).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }
    @Test void timesMissingIdentityUsesTheActualGate()throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(false));
        mvc.perform(get("/api/v1/scheduled-services/"+UUID.randomUUID()+"/time-history").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));
    }
    @Test void timesForeignAndUnauthenticatedConsultationAreRejected()throws Exception {
        var a=finished(false);mvc.perform(get("/api/v1/scheduled-services/"+UUID.randomUUID()+"/time-history").header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/mobile/assignments/"+UUID.randomUUID()+"/time-history").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/scheduled-services/"+service(a)+"/time-history")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()+"/time-history").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
    }
    @Test void timesARevokedSessionCannotCorrectEvenWithAnActorEntityAlreadyCached()throws Exception {
        var a=finished(false);var body=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));users.findOneById(admin.getId()).orElseThrow();
        jdbc.update("update app_user set session_version=session_version+1 where id=?",admin.getId());
        mvc.perform(post("/api/v1/scheduled-services/"+service(a)+"/time-corrections").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isUnauthorized());assertThat(revisions()).isZero();
    }
    @Test void timesInactiveTenantCannotCorrectDespiteCachedOrganization()throws Exception {
        var a=finished(false);var body=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));
        jdbc.update("update organization set status='INACTIVE' where id=?",organization.getId());
        mvc.perform(post("/api/v1/scheduled-services/"+service(a)+"/time-corrections").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isUnauthorized());assertThat(revisions()).isZero();
    }
    @Test void timesRequireNonPersonalReasonAndRejectNullSegmentEntries()throws Exception {
        var a=finished(false);var c=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));c.put("reason","SYNTHETIC@example.test");correct(a,c).andExpect(status().isConflict());
        c=correction(a,initial.plusSeconds(1),initial.plusSeconds(20));c.put("segments",Arrays.asList((Object)null));correct(a,c).andExpect(status().isBadRequest());assertThat(revisions()).isZero();
    }
}
