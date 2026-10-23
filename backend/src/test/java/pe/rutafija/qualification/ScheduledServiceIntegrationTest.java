package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Normal HTTP + native PG, rollback fixtures and simulated mail, never a human/SMTP trial. */
class ScheduledServiceIntegrationTest extends QualificationIntegrationTest {
    private void prepared()throws Exception{
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        syntheticTrackingConsent();
    }
    private Map<String,Object> offer(long seconds)throws Exception{
        var b=new LinkedHashMap<String,Object>();b.put("driverId",driver.getId());b.put("vehicleId",vehicle.getId());
        b.put("routeVersionId",routeFixture());b.put("serviceTypeVersionId",serviceTypeFixture());
        b.put("originText","SYNTHETIC origin");b.put("destinationText","SYNTHETIC destination");
        b.put("scheduledAt",initial.plusSeconds(seconds));b.put("scheduledEndAt",initial.plusSeconds(seconds+3600));return b;
    }
    private ResultActions postAdmin(String path,Object body)throws Exception{
        entities.flush();entities.clear();return mvc.perform(post(path).header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    private ResultActions getAdmin(String path)throws Exception{
        entities.flush();entities.clear();return mvc.perform(get(path).header("Authorization","Bearer "+adminToken));
    }
    private JsonNode create()throws Exception{return json(postAdmin("/api/v1/assignments",offer(3600)).andExpect(status().isCreated()));}
    private String path(JsonNode offer){return "/api/v1/scheduled-services/"+offer.path("serviceId").asText();}
    private JsonNode service(JsonNode offer)throws Exception{return json(getAdmin(path(offer)).andExpect(status().isOk()));}
    private ResultActions mobile(JsonNode offer,String action)throws Exception{
        entities.flush();entities.clear();return mvc.perform(post("/api/v1/mobile/assignments/"+offer.path("id").asText()+"/"+action)
            .header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("version",offer.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",clock.instant(),"reason","SYNTHETIC-RESPONSE","gps",Map.of("permissionGranted",true,"locationServiceEnabled",true,"foreground",true)))));
    }
    private Map<String,Object> reoffer(JsonNode s,long seconds)throws Exception{
        return new LinkedHashMap<>(Map.of("expectedVersion",s.path("service").path("version").asLong(),
            "operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-REASSIGNMENT","offer",offer(seconds)));
    }
    private Map<String,Object> cancel(JsonNode s){return Map.of("expectedVersion",s.path("service").path("version").asLong(),
        "operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-SERVICE-CANCEL");}
    private long count(String table){return jdbc.queryForObject("select count(*) from "+table+" where organization_id=?",Long.class,organization.getId());}
    @Test void serviceInitialOfferCreatesOneDurableProgramAndNoAcceptance()throws Exception{
        prepared();var a=create();var s=service(a);
        assertThat(s.path("service").path("status").asText()).isEqualTo("PENDING_RESPONSE");
        assertThat(s.path("offers").size()).isEqualTo(1);assertThat(count("scheduled_service")).isEqualTo(1);
        assertThat(s.path("offers").get(0).hasNonNull("acceptedAt")).isFalse();
    }
    @Test void serviceExpiryLeavesSchedulePendingWithoutRemovingTheUnitPeriod()throws Exception{
        prepared();var a=create();clock.set(initial.plusSeconds(600));
        var observed=service(a);assertThat(observed.path("service").path("effectiveStatus").asText()).isEqualTo("PENDING_REASSIGNMENT");
        mobile(a,"accept").andExpect(status().isConflict());
        var s=service(a);assertThat(s.path("service").path("status").asText()).isEqualTo("PENDING_REASSIGNMENT");
        assertThat(s.path("service").path("scheduledAt")).isEqualTo(a.path("scheduledAt"));
        assertThat(s.path("offers").get(0).path("status").asText()).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and revoked_at is null",Integer.class,driver.getId())).isEqualTo(1);
        assertThat(count("assignment")).isEqualTo(1);
    }
    @Test void serviceRejectionReleasesOfferOnlyAndNewOfferNeedsFreshAcceptance()throws Exception{
        prepared();var a=create();mobile(a,"reject").andExpect(status().isOk());
        var s=service(a);assertThat(s.path("service").path("status").asText()).isEqualTo("PENDING_REASSIGNMENT");
        var next=json(postAdmin(path(a)+"/offers",reoffer(s,3600)).andExpect(status().isOk()));
        assertThat(next.path("service").path("id")).isEqualTo(a.path("serviceId"));
        assertThat(next.path("offers").size()).isEqualTo(2);
        var b=json(getAdmin("/api/v1/assignments/"+next.path("service").path("currentOfferId").asText()).andExpect(status().isOk()));
        assertThat(b.path("status").asText()).isEqualTo("PENDING_RESPONSE");mobile(b,"start").andExpect(status().isConflict());
        mobile(b,"accept").andExpect(status().isOk());
        assertThat(service(a).path("service").path("status").asText()).isEqualTo("CONFIRMED");
    }
    @Test void serviceCancelledAcceptedOfferPreservesAcceptanceAndProgramWithoutAutoReoffer()throws Exception{
        prepared();var a=json(mobile(create(),"accept").andExpect(status().isOk()));String accepted=a.path("acceptedAt").asText();
        postAdmin("/api/v1/assignments/"+a.path("id").asText()+"/cancel",Map.of("version",a.path("version").asLong(),"reason","SYNTHETIC-OFFER-CANCEL"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedAt").value(accepted));
        var s=service(a);assertThat(s.path("service").path("status").asText()).isEqualTo("PENDING_REASSIGNMENT");
        assertThat(count("assignment")).isEqualTo(1);assertThat(count("scheduled_service_operation")).isZero();
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DISPONIBLE");
    }
    @Test void serviceMaterialTimeReplacementPreservesOldConditionsAndHasReplayReceipt()throws Exception{
        prepared();var a=json(mobile(create(),"accept").andExpect(status().isOk()));var s=service(a);var c=reoffer(s,7200);
        var next=json(postAdmin(path(a)+"/offers",c).andExpect(status().isOk()));
        postAdmin(path(a)+"/offers",c).andExpect(status().isOk()).andExpect(jsonPath("$.service.currentOfferId").value(next.path("service").path("currentOfferId").asText()));
        var old=json(getAdmin("/api/v1/assignments/"+a.path("id").asText()).andExpect(status().isOk()));
        assertThat(old.path("acceptedAt")).isEqualTo(a.path("acceptedAt"));assertThat(old.path("scheduledAt")).isEqualTo(a.path("scheduledAt"));
        assertThat(old.path("status").asText()).isEqualTo("CANCELLED");
        assertThat(next.path("service").path("status").asText()).isEqualTo("PENDING_RESPONSE");
        getAdmin("/api/v1/scheduled-services/operations/"+c.get("operationKey")).andExpect(status().isOk()).andExpect(jsonPath("$.action").value("REOFFER"));
        assertThat(count("assignment")).isEqualTo(2);assertThat(count("scheduled_service")).isEqualTo(1);assertThat(count("scheduled_service_operation")).isEqualTo(1);
    }
    @Test void serviceExplicitCancellationIsSeparateMotivatedAndIdempotent()throws Exception{
        prepared();var a=json(mobile(create(),"accept").andExpect(status().isOk()));var s=service(a);var c=cancel(s);
        postAdmin(path(a)+"/cancel",c).andExpect(status().isOk()).andExpect(jsonPath("$.service.status").value("CANCELLED"))
            .andExpect(jsonPath("$.service.cancelledAt").exists()).andExpect(jsonPath("$.offers[0].acceptedAt").value(a.path("acceptedAt").asText()));
        postAdmin(path(a)+"/cancel",c).andExpect(status().isOk());
        postAdmin(path(a)+"/offers",reoffer(service(a),7200)).andExpect(status().isConflict());
        assertThat(count("scheduled_service_operation")).isEqualTo(1);
    }
    @Test void serviceStaleCommandCannotRetireTheCurrentOffer()throws Exception{
        prepared();var a=create();var c=reoffer(service(a),7200);c.put("expectedVersion",999);
        postAdmin(path(a)+"/offers",c).andExpect(status().isConflict());
        assertThat(service(a).path("service").path("currentOfferId")).isEqualTo(a.path("id"));assertThat(count("assignment")).isEqualTo(1);
    }
    @Test void serviceOperationKeyCannotBeRepurposedForDifferentConditions()throws Exception{
        prepared();var a=create();var s=service(a);var c=reoffer(s,7200);postAdmin(path(a)+"/offers",c).andExpect(status().isOk());
        c.put("offer",offer(10800));postAdmin(path(a)+"/offers",c).andExpect(status().isConflict());assertThat(count("assignment")).isEqualTo(2);
    }
    @Test void serviceConductorCannotReadOrDispatchTenantPrograms()throws Exception{
        prepared();var a=create();mvc.perform(get(path(a)).header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(post(path(a)+"/offers").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(reoffer(service(a),7200)))).andExpect(status().isForbidden());
    }
    @Test void serviceAnotherOrganizationCannotReadCancelOrReoffer()throws Exception{
        prepared();UUID id=UUID.randomUUID(),org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
        UUID actor=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
        UUID route=jdbc.queryForObject("select id from route_version where organization_id=? order by id limit 1",UUID.class,org);
        UUID type=jdbc.queryForObject("select id from service_type_version where organization_id=? order by id limit 1",UUID.class,org);
        // Foreign technical header only; no account, identity, acceptance or existing object is changed.
        jdbc.update("insert into scheduled_service(id,organization_id,created_by,created_at,updated_at,scheduled_at,scheduled_end_at,route_version_id,service_type_version_id) values(?,?,?,?,?,?,?,?,?)",
            id,org,actor,java.sql.Timestamp.from(initial),java.sql.Timestamp.from(initial),java.sql.Timestamp.from(initial.plusSeconds(3600)),java.sql.Timestamp.from(initial.plusSeconds(7200)),route,type);
        String path="/api/v1/scheduled-services/"+id;
        getAdmin(path).andExpect(status().isNotFound());
        postAdmin(path+"/cancel",Map.of("expectedVersion",0,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-FOREIGN")).andExpect(status().isNotFound());
        postAdmin(path+"/offers",Map.of("expectedVersion",0,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-FOREIGN","offer",offer(3600))).andExpect(status().isNotFound());
        getAdmin("/api/v1/scheduled-services").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }
    @Test void serviceAcceptedOfferCannotBeRetroactivelyRejected()throws Exception{
        prepared();var a=json(mobile(create(),"accept").andExpect(status().isOk()));
        mobile(a,"reject").andExpect(status().isConflict());var s=service(a);
        assertThat(s.path("service").path("status").asText()).isEqualTo("CONFIRMED");assertThat(s.path("offers").get(0).path("acceptedAt")).isEqualTo(a.path("acceptedAt"));
    }
    @Test void serviceStartedCannotBeCancelledOrReassignedAndCompletionIsDistinct()throws Exception{
        prepared();var a=json(mobile(create(),"accept").andExpect(status().isOk()));
        a=json(mobile(a,"start").andExpect(status().isOk()));var s=service(a);
        assertThat(s.path("service").path("status").asText()).isEqualTo("IN_SERVICE");
        postAdmin(path(a)+"/cancel",cancel(s)).andExpect(status().isConflict());
        postAdmin(path(a)+"/offers",reoffer(s,7200)).andExpect(status().isConflict());
        postAdmin("/api/v1/assignments/"+a.path("id").asText()+"/cancel",Map.of("version",a.path("version").asLong(),"reason","SYNTHETIC-NO-UNSAFE-RELEASE"))
            .andExpect(status().isBadRequest());
        a=json(mobile(a,"complete").andExpect(status().isOk()));s=service(a);
        assertThat(s.path("service").path("status").asText()).isEqualTo("COMPLETED");
        postAdmin(path(a)+"/offers",reoffer(s,7200)).andExpect(status().isConflict());
    }
    @Test void serviceCandidateReplacementExcludesOnlyItsOwnHoldWithoutReleasingIt()throws Exception{
        prepared();var a=json(mobile(create(),"accept").andExpect(status().isOk()));var offer=offer(3600);
        var builder=get("/api/v1/assignment-candidates").header("Authorization","Bearer "+adminToken)
            .param("serviceTypeVersionId",offer.get("serviceTypeVersionId").toString()).param("routeVersionId",offer.get("routeVersionId").toString())
            .param("scheduledAt",offer.get("scheduledAt").toString()).param("scheduledEndAt",offer.get("scheduledEndAt").toString());
        mvc.perform(builder).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(builder.param("serviceId",a.path("serviceId").asText())).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        assertThat(service(a).path("service").path("status").asText()).isEqualTo("CONFIRMED");assertThat(count("assignment")).isEqualTo(1);
    }
    @Test void serviceReofferAfterExpiryNormalizesAndDoesNotRenewTheOldDeadline()throws Exception{
        prepared();var a=create();var s=service(a);var c=reoffer(s,7200);clock.set(initial.plusSeconds(601));
        var next=json(postAdmin(path(a)+"/offers",c).andExpect(status().isOk()));
        var old=json(getAdmin("/api/v1/assignments/"+a.path("id").asText()).andExpect(status().isOk()));
        assertThat(old.path("status").asText()).isEqualTo("EXPIRED");assertThat(old.path("responseDeadlineAt")).isEqualTo(a.path("responseDeadlineAt"));
        assertThat(next.path("service").path("status").asText()).isEqualTo("PENDING_RESPONSE");assertThat(count("scheduled_service")).isEqualTo(1);
    }
    @Test void serviceCancellingAFutureCommitmentCannotReleaseAnOngoingPhysicalOccupation()throws Exception{
        prepared();var first=json(mobile(create(),"accept").andExpect(status().isOk()));
        var second=json(postAdmin("/api/v1/assignments",offer(10800)).andExpect(status().isCreated()));
        second=json(mobile(second,"accept").andExpect(status().isOk()));
        first=json(mobile(first,"start").andExpect(status().isOk()));
        postAdmin("/api/v1/assignments/"+second.path("id").asText()+"/cancel",Map.of("version",second.path("version").asLong(),"reason","SYNTHETIC-FUTURE-CANCEL"))
            .andExpect(status().isOk());
        assertThat(service(first).path("service").path("status").asText()).isEqualTo("IN_SERVICE");
        assertThat(service(second).path("service").path("status").asText()).isEqualTo("PENDING_REASSIGNMENT");
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select status from vehicle where id=?",String.class,vehicle.getId())).isEqualTo("EN_SERVICIO");
    }
    @Test void serviceNewDriverAndUnitRequireThatRecipientsOwnAcceptanceAndRetainBothPeriods()throws Exception{
        prepared();var old=json(mobile(create(),"accept").andExpect(status().isOk()));
        var additional=prepareAdditionalSyntheticConductor();var s=service(old);var c=reoffer(s,3600);
        var body=offer(3600);body.put("driverId",additional.driver().getId());body.put("vehicleId",additional.vehicle().getId());c.put("offer",body);
        var result=json(postAdmin(path(old)+"/offers",c).andExpect(status().isOk()));
        String newId=result.path("service").path("currentOfferId").asText();
        mvc.perform(get("/api/v1/mobile/assignments/"+newId).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        var next=json(getAdmin("/api/v1/assignments/"+newId).andExpect(status().isOk()));
        assertThat(next.path("acceptedAt").isMissingNode()).isTrue();mobile(next,"accept").andExpect(status().isNotFound());
        mobileToken=additional.mobileToken();next=json(mobile(next,"accept").andExpect(status().isOk()));
        assertThat(next.path("driverId").asText()).isEqualTo(additional.driver().getId().toString());
        var previous=json(getAdmin("/api/v1/assignments/"+old.path("id").asText()).andExpect(status().isOk()));
        assertThat(previous.path("acceptedAt")).isEqualTo(old.path("acceptedAt"));assertThat(previous.path("vehicleId")).isEqualTo(old.path("vehicleId"));
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id in (?,?) and revoked_at is null",Integer.class,driver.getId(),additional.driver().getId())).isEqualTo(2);
    }
    @Test void serviceCreationOperationIsReadOnlyAndDoesNotRenewItsDeadline()throws Exception{
        prepared();String key="synthetic-"+UUID.randomUUID();var b=offer(3600);
        var a=json(mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken).header("Idempotency-Key",key)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(b))).andExpect(status().isCreated()));
        clock.set(initial.plusSeconds(100));getAdmin("/api/v1/assignments/creation-operations/"+key).andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(a.path("id").asText())).andExpect(jsonPath("$.responseDeadlineAt").value(a.path("responseDeadlineAt").asText()));
        assertThat(count("assignment")).isEqualTo(1);assertThat(count("scheduled_service")).isEqualTo(1);
    }
    @Test void serviceMissingTermsUsesTheActualGate()throws Exception{
        mvc.perform(get("/api/v1/admin/terms/current").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
        getAdmin("/api/v1/scheduled-services").andExpect(status().isForbidden());
        getAdmin("/api/v1/assignments/creation-operations/synthetic-missing").andExpect(status().isForbidden());
    }
    @Test void serviceMissingIdentityUsesTheActualGate()throws Exception{
        getAdmin("/api/v1/identity/status").andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(false));
        getAdmin("/api/v1/scheduled-services").andExpect(status().isForbidden());
        getAdmin("/api/v1/scheduled-services/operations/synthetic-missing").andExpect(status().isForbidden());
    }
    @Test void serviceMissedPlannedStartCanBeExplicitlyReassignedBeforeActualStart()throws Exception{
        prepared();var original=json(postAdmin("/api/v1/assignments",offer(300)).andExpect(status().isCreated()));
        original=json(mobile(original,"accept").andExpect(status().isOk()));clock.set(initial.plusSeconds(301));
        var s=service(original);var next=json(postAdmin(path(original)+"/offers",reoffer(s,7200)).andExpect(status().isOk()));
        assertThat(next.path("service").path("status").asText()).isEqualTo("PENDING_RESPONSE");
        var old=json(getAdmin("/api/v1/assignments/"+original.path("id").asText()).andExpect(status().isOk()));
        assertThat(old.path("acceptedAt")).isEqualTo(original.path("acceptedAt"));assertThat(old.path("startedAt").isMissingNode()).isTrue();
        assertThat(old.path("status").asText()).isEqualTo("CANCELLED");
    }
}
