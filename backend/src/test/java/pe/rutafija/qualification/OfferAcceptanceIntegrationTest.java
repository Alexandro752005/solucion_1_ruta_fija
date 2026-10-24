package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** L04 normal HTTP/PG acceptance boundary. Uses the inherited rollback fixture, never cleanup. */
class OfferAcceptanceIntegrationTest extends QualificationIntegrationTest {
    private void prepared() throws Exception {
        reviewAll();
        mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
        declareAvailable();
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        syntheticTrackingConsent();
    }

    private Map<String,Object> offer(long secondsUntilStart) throws Exception {
        var body=new LinkedHashMap<String,Object>();
        body.put("driverId",driver.getId());body.put("vehicleId",vehicle.getId());
        body.put("routeVersionId",routeFixture());body.put("serviceTypeVersionId",serviceTypeFixture());
        body.put("originText","SYNTHETIC origin");body.put("destinationText","SYNTHETIC destination");
        body.put("scheduledAt",initial.plusSeconds(secondsUntilStart));
        body.put("scheduledEndAt",initial.plusSeconds(secondsUntilStart+3600));
        return body;
    }

    private ResultActions create(Map<String,Object> body,String key) throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken)
            .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }

    private ResultActions command(JsonNode assignment,String action,UUID event) throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(post("/api/v1/mobile/assignments/"+assignment.path("id").asText()+"/"+action)
            .header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("version",assignment.path("version").asLong(),"clientEventId",event,"occurredAt",clock.instant(),"gps",Map.of("permissionGranted",true,"locationServiceEnabled",true,"foreground",true)))));
    }

    private ResultActions adminCommand(JsonNode assignment,String action) throws Exception {
        return mvc.perform(post("/api/v1/assignments/"+assignment.path("id").asText()+"/"+action)
            .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("version",assignment.path("version").asLong()))));
    }

    @Test void offerOmissionAlwaysCreatesPendingWithoutManufacturingAcceptance() throws Exception {
        prepared();
        create(offer(3600),"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.responseMode").value("MOBILE_CONFIRMATION"))
            .andExpect(jsonPath("$.status").value("PENDING_RESPONSE"))
            .andExpect(jsonPath("$.acceptedAt").doesNotExist()).andExpect(jsonPath("$.reservedAt").doesNotExist());
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DISPONIBLE");
    }

    @Test void offerRestrictedMobileSessionIsForbiddenWithoutPoisoningTheCommandTransaction() throws Exception {
        prepared();var saved=json(create(offer(3600),"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"INCORPORATION");
        command(saved,"accept",UUID.randomUUID()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DRIVER_INACTIVE"));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(saved.path("id").asText()))).isEqualTo("PENDING_RESPONSE");
        assertThat(jdbc.queryForObject("select accepted_at is null from assignment where id=?",Boolean.class,UUID.fromString(saved.path("id").asText()))).isTrue();
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        command(saved,"accept",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"));
    }

    @Test void offerExplicitAdminDirectCannotCreateAnyAssignment() throws Exception {
        prepared();var body=offer(3600);body.put("responseMode","ADMIN_DIRECT");
        create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("aceptación expresa")));
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isZero();
    }

    @Test void offerConductorCannotEmitAndAdminCannotCallTheConductorAcceptanceRoute() throws Exception {
        prepared();var body=offer(3600);
        mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+mobileToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))).andExpect(status().isForbidden());
        var saved=json(create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        mvc.perform(post("/api/v1/mobile/assignments/"+saved.path("id").asText()+"/accept")
            .header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("version",saved.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",initial))))
            .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("select accepted_at is null from assignment where id=?",Boolean.class,UUID.fromString(saved.path("id").asText()))).isTrue();
    }

    @Test void offerForeignDriverOrVehicleAreHiddenAndCannotCreateATenantCrossingHold() throws Exception {
        prepared();var body=offer(3600);body.put("driverId",otherDriver.getId());
        create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isNotFound());
        body.put("driverId",driver.getId());body.put("vehicleId",otherVehicle.getId());
        create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isZero();
    }

    @Test void offerDeadlineComesFromServerDespiteClientAttemptToExtendIt() throws Exception {
        prepared();var body=offer(7200);body.put("responseDeadlineAt",initial.plusSeconds(7000));
        var saved=json(create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        assertThat(java.time.Instant.parse(saved.path("responseDeadlineAt").asText())).isEqualTo(initial.plusSeconds(600));
    }

    @Test void offerDeadlineEqualsEarlierServiceStartWithoutInventedMicrosecondGap() throws Exception {
        prepared();var saved=json(create(offer(300),"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        assertThat(java.time.Instant.parse(saved.path("responseDeadlineAt").asText())).isEqualTo(initial.plusSeconds(300));
        clock.set(initial.plusSeconds(300));
        command(saved,"accept",UUID.randomUUID()).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(saved.path("id").asText()))).isEqualTo("EXPIRED");
    }

    @Test void offerIdempotentCreationPreservesOriginalDeadlineAsServerTimeAdvances() throws Exception {
        prepared();var body=offer(3600);String key="synthetic-"+UUID.randomUUID();
        var first=json(create(body,key).andExpect(status().isCreated()));clock.set(initial.plusSeconds(120));
        var repeated=json(create(body,key).andExpect(status().isOk()));
        assertThat(repeated.path("id")).isEqualTo(first.path("id"));
        assertThat(repeated.path("responseDeadlineAt")).isEqualTo(first.path("responseDeadlineAt"));
    }

    @Test void offerNewHoldExpiresTheOldReservationWithoutDependingOnTheSweep() throws Exception {
        prepared();var body=offer(3600);
        var old=json(create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        clock.set(initial.plusSeconds(600));
        var next=json(create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        assertThat(next.path("id")).isNotEqualTo(old.path("id"));
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(old.path("id").asText()))).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and revoked_at is null",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void offerIdempotentReplayAfterExpiryReturnsTheExpiredRecordWithoutRenewingIt() throws Exception {
        prepared();var body=offer(3600);String key="synthetic-"+UUID.randomUUID();
        var first=json(create(body,key).andExpect(status().isCreated()));clock.set(initial.plusSeconds(601));
        var repeated=json(create(body,key).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EXPIRED")));
        assertThat(repeated.path("id")).isEqualTo(first.path("id"));
        assertThat(repeated.path("responseDeadlineAt")).isEqualTo(first.path("responseDeadlineAt"));
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void offerFirstPendingReservationPreventsAnotherIncompatibleHold() throws Exception {
        prepared();var body=offer(3600);
        var first=json(create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        create(body,"synthetic-"+UUID.randomUUID()).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(first.path("id").asText()))).isEqualTo("PENDING_RESPONSE");
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void offerAcceptanceAtTheExactTenMinuteDeadlineExpiresWithoutAScheduler() throws Exception {
        prepared();var saved=json(create(offer(3600),"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        clock.set(initial.plusSeconds(600));command(saved,"accept",UUID.randomUUID()).andExpect(status().isConflict());
        var id=UUID.fromString(saved.path("id").asText());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,id)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("select accepted_at is null from assignment where id=?",Boolean.class,id)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from driver_unit_period where driver_id=? and revoked_at is null",Integer.class,driver.getId())).isEqualTo(1);
    }

    @Test void offerAuthenticAcceptanceJustBeforeDeadlineDoesNotStartTheService() throws Exception {
        prepared();var saved=json(create(offer(3600),"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        clock.set(initial.plusSeconds(599));
        command(saved,"accept",UUID.randomUUID()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.acceptedAt").exists()).andExpect(jsonPath("$.startedAt").doesNotExist());
    }

    @Test void offerAdminCannotReserveOrStartInPlaceOfConductor() throws Exception {
        prepared();var saved=json(create(offer(3600),"synthetic-"+UUID.randomUUID()).andExpect(status().isCreated()));
        adminCommand(saved,"reserve").andExpect(status().isConflict());adminCommand(saved,"start").andExpect(status().isConflict());
        saved=json(command(saved,"accept",UUID.randomUUID()).andExpect(status().isOk()));
        adminCommand(saved,"start").andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select started_at is null from assignment where id=?",Boolean.class,UUID.fromString(saved.path("id").asText()))).isTrue();
    }

    @Test void offerLegacyDirectRecordCannotBeStartedThroughMobileEndpoint() throws Exception {
        prepared();UUID id=assignment("SCHEDULED",initial.plusSeconds(3600),initial.plusSeconds(7200));
        var saved=mapper.createObjectNode().put("id",id.toString()).put("version",0);
        command(saved,"start",UUID.randomUUID()).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select accepted_at is null and started_at is null from assignment where id=?",Boolean.class,id)).isTrue();
    }
}
