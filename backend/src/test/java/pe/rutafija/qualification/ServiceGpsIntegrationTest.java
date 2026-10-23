package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import pe.rutafija.operation.application.ServiceGpsRetention;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring/PG rollback fixture; clocks/coordinates/passwords/mail are exclusively synthetic. */
class ServiceGpsIntegrationTest extends ServiceExecutionIntegrationTest {
    private static final String SYNTHETIC_PASSWORD="Synthetic-L02-Password-2026";
    @Autowired ServiceGpsRetention retention;
    private ResultActions mobilePost(String path,Object body)throws Exception {
        entities.flush();entities.clear();return mvc.perform(post(path).header("Authorization","Bearer "+mobileToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    private JsonNode requested(JsonNode a)throws Exception {
        return json(mobilePost("/api/v1/mobile/gps/access-requests",Map.of("requestKey",UUID.randomUUID(),"serviceId",a.path("serviceId").asText(),"fromAt",initial,"toAt",clock.instant())).andExpect(status().isOk()));
    }
    private Map<String,Object> review(JsonNode r,boolean approve,String password) {
        return new LinkedHashMap<>(Map.of("expectedVersion",r.path("version").asLong(),"approved",approve,"identityConfirmed",true,
            "verificationReference","SYNTHETIC_RESTRICTED_IDENTITY_MATCH","currentPassword",password));
    }
    private Map<String,Object> delivery(){return Map.of("currentPassword",SYNTHETIC_PASSWORD,"limit",100,"offset",0);}
    private String reviewPath(JsonNode r){return "/api/v1/gps/access-requests/"+r.path("id").asText()+"/review";}
    private String pointsPath(JsonNode r){return "/api/v1/mobile/gps/access-requests/"+r.path("id").asText()+"/points";}
    private int points(){return jdbc.queryForObject("select count(*) from service_gps_point where organization_id=?",Integer.class,organization.getId());}
    private UUID pointId(){return jdbc.queryForObject("select id from service_gps_point where driver_id=? order by captured_at limit 1",UUID.class,driver.getId());}
    private JsonNode startedWithTerminal(double terminalLatitude)throws Exception {
        var stops=List.of(Map.of("key","start","kind","START","name","SYNTHETIC origin"),
            Map.of("key","middle","kind","INTERMEDIATE","name","SYNTHETIC middle"),
            Map.of("key","end","kind","END","name","SYNTHETIC destination","latitude",terminalLatitude,"longitude",-79.84));
        var route=json(adminPost("/api/v1/routes",Map.of("name","SYNTHETIC GPS route "+UUID.randomUUID().toString().replaceAll("[0-9]","x"),
            "operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC_GPS_TERMINAL","stops",stops)).andExpect(status().isOk()));
        consent(true).andExpect(status().isOk());var a=offer(UUID.fromString(route.path("currentVersionId").asText()));
        a=json(command(a,"accept",command(a,false)).andExpect(status().isOk()));return json(command(a,"start",command(a,true)).andExpect(status().isOk()));
    }
    @Test void gpsTerminalCorroborationUsesItsOwnStoredPointAndRetainsTheOriginalDeclaration()throws Exception {
        prepared();var a=startedWithTerminal(-6.77);point(initial,true).andExpect(status().isNoContent());
        command(a,"complete",command(a,false)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select terminal_result from assignment_route_declaration where assignment_id=?",String.class,UUID.fromString(a.path("id").asText()))).isEqualTo("CORROBORATED");
        assertThat(jdbc.queryForObject("select count(*) from service_closure_notice where organization_id=?",Integer.class,organization.getId())).isZero();
    }
    @Test void gpsTerminalDiscrepancyCreatesAnObservationWithoutInferringFraudOrCopyingCoordinates()throws Exception {
        prepared();var a=startedWithTerminal(-6.78);point(initial,true).andExpect(status().isNoContent());
        command(a,"complete",command(a,false)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select terminal_result from assignment_route_declaration where assignment_id=?",String.class,UUID.fromString(a.path("id").asText()))).isEqualTo("DISCREPANCY");
        assertThat(jdbc.queryForObject("select kind from service_closure_notice where assignment_id=?",String.class,UUID.fromString(a.path("id").asText()))).isEqualTo("TERMINAL_DISCREPANCY");
        assertThat(jdbc.queryForObject("select status from scheduled_service where id=?",String.class,UUID.fromString(a.path("serviceId").asText()))).isEqualTo("COMPLETED");
    }
    @Test void gpsHistorySurvivesCompletionWithoutAVisibleCurrentSnapshotAndHasFixedSevenDayExpiry()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());command(a,"complete",command(a,false)).andExpect(status().isOk());
        assertThat(points()).isEqualTo(1);assertThat(jdbc.queryForObject("select expires_at from service_gps_point where driver_id=?",Timestamp.class,driver.getId()).toInstant()).isEqualTo(initial.plus(Duration.ofDays(7)));
        assertThat(jdbc.queryForObject("select count(*) from driver_current_location where driver_id=?",Integer.class,driver.getId())).isZero();
        assertThat(jdbc.queryForObject("select evidence_point_id from assignment_route_declaration where assignment_id=?",UUID.class,UUID.fromString(a.path("id").asText()))).isEqualTo(pointId());
    }
    @Test void gpsDuplicateCaptureDoesNotExtendRetentionAndDifferentCoordinatesCannotRewriteIt()throws Exception {
        prepared();started();point(initial,true).andExpect(status().isNoContent());clock.set(initial.plusSeconds(1));point(initial,true).andExpect(status().isNoContent());assertThat(points()).isEqualTo(1);
        mvc.perform(put("/api/v1/mobile/location/current").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("latitude",-6.78,"longitude",-79.84,"accuracyM",5,"capturedAt",initial,"permissionGranted",true,"locationServiceEnabled",true,"foreground",true))))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_INVALID_COORDINATES"));
        assertThat(points()).isEqualTo(1);assertThat(jdbc.queryForObject("select expires_at from service_gps_point where driver_id=?",Timestamp.class,driver.getId()).toInstant()).isEqualTo(initial.plus(Duration.ofDays(7)));
    }
    @Test void gpsOwnRequestIsIdempotentButCannotRequestAnotherService()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());
        var c=new LinkedHashMap<String,Object>(Map.of("requestKey",UUID.randomUUID(),"serviceId",a.path("serviceId").asText(),"fromAt",initial,"toAt",initial));
        var r=json(mobilePost("/api/v1/mobile/gps/access-requests",c).andExpect(status().isOk()));
        mobilePost("/api/v1/mobile/gps/access-requests",c).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(r.path("id").asText()));
        c.put("fromAt",initial.minusSeconds(1));mobilePost("/api/v1/mobile/gps/access-requests",c).andExpect(status().isConflict());
        c.put("requestKey",UUID.randomUUID());c.put("serviceId",UUID.randomUUID());mobilePost("/api/v1/mobile/gps/access-requests",c).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from gps_access_request where organization_id=?",Integer.class,organization.getId())).isEqualTo(1);
    }
    @Test void gpsOnlyApprovedReauthenticatedOwnerReceivesPointsAndGrantExpiresExactlyAtFifteenMinutes()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());var r=requested(a);
        mobilePost(pointsPath(r),delivery()).andExpect(status().isConflict());
        adminPost(reviewPath(r),review(r,true,"SYNTHETIC-WRONG")).andExpect(status().isUnauthorized());
        var approved=json(adminPost(reviewPath(r),review(r,true,SYNTHETIC_PASSWORD)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")));
        assertThat(java.time.Instant.parse(approved.path("grantExpiresAt").asText())).isEqualTo(initial.plusSeconds(900));
        mobilePost(pointsPath(r),delivery()).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].latitude").exists());
        clock.set(initial.plusSeconds(900));mobilePost(pointsPath(r),delivery()).andExpect(status().isConflict());
        assertThat(points()).isEqualTo(1); // Authorization expiry does not falsely claim physical point deletion.
    }
    @Test void gpsReviewRequiresIdentityConfirmationAndCannotBeReusedToExtendTheGrant()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());var r=requested(a);var c=review(r,true,SYNTHETIC_PASSWORD);c.put("identityConfirmed",false);
        adminPost(reviewPath(r),c).andExpect(status().isConflict());c.put("identityConfirmed",true);
        adminPost(reviewPath(r),c).andExpect(status().isOk());adminPost(reviewPath(r),c).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select grant_expires_at from gps_access_request where id=?",Timestamp.class,UUID.fromString(r.path("id").asText())).toInstant()).isEqualTo(initial.plusSeconds(900));
    }
    @Test void gpsAnotherConductorAndForeignAdminCannotReadTheRequestOrDeliverItsPoints()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());var r=requested(a);adminPost(reviewPath(r),review(r,true,SYNTHETIC_PASSWORD)).andExpect(status().isOk());
        var other=prepareAdditionalSyntheticConductor();
        mvc.perform(get("/api/v1/mobile/gps/access-requests/"+r.path("id").asText()).header("Authorization","Bearer "+other.mobileToken())).andExpect(status().isNotFound());
        mvc.perform(post(pointsPath(r)).header("Authorization","Bearer "+other.mobileToken()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(delivery()))).andExpect(status().isNotFound());
        var historical=users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow();String foreign=jwt.issueAccessToken(historical);
        mvc.perform(get("/api/v1/gps/access-requests").header("Authorization","Bearer "+foreign)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/v1/gps/access-requests").header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mobile/gps/access-requests/"+r.path("id").asText())).andExpect(status().isUnauthorized());
    }
    @Test void gpsSuperAdminDoesNotGainGlobalCoordinatesOrRequestReviewByItsPlatformRole()throws Exception {
        var owner=users.findById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow();var token=jwt.issueAccessToken(owner);
        mvc.perform(get("/api/v1/gps/access-requests").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mobile/gps/access-requests/"+UUID.randomUUID()).header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void gpsMissingTermsUsesTheActualGate()throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(true));
        mvc.perform(get("/api/v1/admin/terms/current").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
        mvc.perform(get("/api/v1/gps/access-requests").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
    }
    @Test void gpsMissingIdentityUsesTheActualGate()throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(false));
        mvc.perform(get("/api/v1/gps/access-requests").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
    }
    @Test void gpsIncidentCannotAdoptAnotherServicesPointEvenForTheSameConductorAndOrganization()throws Exception {
        prepared();var first=started();point(initial,true).andExpect(status().isNoContent());UUID point=pointId();command(first,"complete",command(first,false)).andExpect(status().isOk());
        var second=offer();var incident=json(mobilePost("/api/v1/mobile/incidents",Map.of("assignmentId",second.path("id").asText(),"category","OTRO","description","SYNTHETIC separate service case","occurredAt",initial)).andExpect(status().isCreated()));
        adminPost("/api/v1/incidents/"+incident.path("id").asText()+"/gps-evidence",Map.of("pointId",point,"justification","SERVICE_DISCREPANCY","currentPassword",SYNTHETIC_PASSWORD)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select count(*) from gps_incident_evidence where organization_id=?",Integer.class,organization.getId())).isZero();
    }
    @Test void gpsReviewDeadlineIsVisibleButNeverExtendsPointRetentionOrInventsADecision()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());var r=requested(a);clock.set(initial.plus(Duration.ofHours(48)));
        mvc.perform(get("/api/v1/mobile/gps/access-requests/"+r.path("id").asText()).header("Authorization","Bearer "+mobileToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.effectiveStatus").value("OVERDUE"));
        assertThat(jdbc.queryForObject("select expires_at from service_gps_point where driver_id=?",Timestamp.class,driver.getId()).toInstant()).isEqualTo(initial.plus(Duration.ofDays(7)));
    }
    @Test void gpsExpiryDuringReviewCannotBeExtendedAndPurgeRemovesCoordinatesButPreservesSummary()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());command(a,"complete",command(a,false)).andExpect(status().isOk());
        var r=requested(a);clock.set(initial.plus(Duration.ofDays(7)).minusSeconds(20));
        var approved=json(adminPost(reviewPath(r),review(r,true,SYNTHETIC_PASSWORD)).andExpect(status().isOk()));
        assertThat(java.time.Instant.parse(approved.path("grantExpiresAt").asText())).isEqualTo(initial.plus(Duration.ofDays(7)));
        mobilePost(pointsPath(r),delivery()).andExpect(status().isOk());clock.set(initial.plus(Duration.ofDays(7)));
        mobilePost(pointsPath(r),delivery()).andExpect(status().isConflict());assertThat(retention.purgeExpired(organization.getId())).isEqualTo(1);assertThat(points()).isZero();
        assertThat(jdbc.queryForObject("select evidence_point_id is null from assignment_route_declaration where assignment_id=?",Boolean.class,UUID.fromString(a.path("id").asText()))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from service_execution_segment where organization_id=?",Integer.class,organization.getId())).isEqualTo(1);
    }
    @Test void gpsNoDataReviewDoesNotReconstructOrFabricatePoints()throws Exception {
        prepared();var a=started();var r=requested(a);
        adminPost(reviewPath(r),review(r,true,SYNTHETIC_PASSWORD)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("NO_DATA")).andExpect(jsonPath("$.grantExpiresAt").doesNotExist());
        mobilePost(pointsPath(r),delivery()).andExpect(status().isConflict());assertThat(points()).isZero();
    }
    @Test void gpsServerDefaultIntervalAndOwnListRemainStableWhenTheRequestIsConsultedAgain()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());UUID key=UUID.randomUUID();
        var c=Map.of("requestKey",key,"serviceId",a.path("serviceId").asText());
        var r=json(mobilePost("/api/v1/mobile/gps/access-requests",c).andExpect(status().isOk()));clock.set(initial.plusSeconds(30));
        mobilePost("/api/v1/mobile/gps/access-requests",c).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(r.path("id").asText())).andExpect(jsonPath("$.toAt").value(initial.toString()));
        mvc.perform(get("/api/v1/mobile/gps/access-requests").param("serviceId",a.path("serviceId").asText()).header("Authorization","Bearer "+mobileToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].requestKey").value(key.toString())).andExpect(jsonPath("$[0].latitude").doesNotExist());
    }
    @Test void gpsQualificationLossPreservesOwnConsultationCaptureAndSafeCloseWithoutPermittingNewStart()throws Exception {
        prepared();var a=started();var suspension=basic(4);suspension.put("evidenceReference","SYNTHETIC reviewed safety issue");mutate("/suspend",suspension).andExpect(status().isOk());
        mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"INCORPORATION");
        mvc.perform(get("/api/v1/mobile/me").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mobile/assignments").param("status","EN_SERVICIO").header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        a=json(mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()).header("Authorization","Bearer "+mobileToken)).andExpect(status().isOk()));
        point(initial,true).andExpect(status().isNoContent());command(a,"start",command(a,true)).andExpect(status().isForbidden());
        command(a,"complete",command(a,false)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("NO_DISPONIBLE");
    }
    @Test void gpsDeclarationCannotBeDetachedBeforeDeletionOrRewrittenAfterExpiry()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());command(a,"complete",command(a,false)).andExpect(status().isOk());UUID assignment=UUID.fromString(a.path("id").asText());
        assertThat(rejectedSqlState("update assignment_route_declaration set evidence_point_id=null where assignment_id=?",assignment)).isEqualTo("23514");
        clock.set(initial.plus(Duration.ofDays(7)));assertThat(retention.purgeExpired(organization.getId())).isEqualTo(1);
        assertThat(rejectedSqlState("update assignment_route_declaration set terminal_result='CORROBORATED' where assignment_id=?",assignment)).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from assignment_route_declaration where assignment_id=?",assignment)).isEqualTo("23514");
    }
    @Test void gpsIncidentEvidenceUsesTheSameExpiryAndIsUnlinkedWhenOriginalPointIsPurged()throws Exception {
        prepared();var a=started();point(initial,true).andExpect(status().isNoContent());UUID point=pointId();
        var incident=json(mobilePost("/api/v1/mobile/incidents",Map.of("assignmentId",a.path("id").asText(),"category","OTRO","description","SYNTHETIC GPS incident review","occurredAt",initial)).andExpect(status().isCreated()));
        var evidence=json(adminPost("/api/v1/incidents/"+incident.path("id").asText()+"/gps-evidence",Map.of("pointId",point,"justification","SERVICE_DISCREPANCY","currentPassword",SYNTHETIC_PASSWORD)).andExpect(status().isOk()).andExpect(jsonPath("$.pointAvailable").value(true)));
        clock.set(initial.plus(Duration.ofDays(7)));assertThat(retention.purgeExpired(organization.getId())).isEqualTo(1);
        mvc.perform(get("/api/v1/gps/incident-evidence/"+evidence.path("id").asText()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.pointAvailable").value(false));
        assertThat(jdbc.queryForObject("select point_id is null from gps_incident_evidence where id=?",Boolean.class,UUID.fromString(evidence.path("id").asText()))).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where organization_id=? and metadata_json::text ~ '(latitude|longitude|accuracy_m)'",Integer.class,organization.getId())).isZero();
    }
    @Test void gpsPurgeIsBoundedToTheRequestedTenantEvenWhenAnotherTenantsPointsHaveExpired()throws Exception {
        prepared();started();point(initial,true).andExpect(status().isNoContent());clock.set(initial.plus(Duration.ofDays(7)));
        assertThat(retention.purgeExpired(otherOrganization.getId())).isZero();assertThat(points()).isEqualTo(1);
        assertThat(retention.purgeExpired(organization.getId())).isEqualTo(1);assertThat(points()).isZero();
    }
}
