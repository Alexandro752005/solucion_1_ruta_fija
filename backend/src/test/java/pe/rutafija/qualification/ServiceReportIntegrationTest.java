package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Current implementation, real authorization/PG and rollback. No persistent account or global cleanup. */
class ServiceReportIntegrationTest extends ServiceExecutionIntegrationTest {
    private String day(){return initial.atZone(ZoneId.of("America/Lima")).toLocalDate().toString();}
    private String range(){return "?from="+day()+"&to="+day();}
    private org.springframework.test.web.servlet.ResultActions report(boolean own)throws Exception{
        entities.flush();entities.clear();return mvc.perform(get((own?"/api/v1/mobile/reports/services":"/api/v1/reports/services")+range()).header("Authorization","Bearer "+(own?mobileToken:adminToken)));
    }
    private JsonNode issue(boolean own,UUID key)throws Exception{
        entities.flush();entities.clear();return json(mvc.perform(post((own?"/api/v1/mobile/reports/services":"/api/v1/reports/services")+"/emissions").header("Authorization","Bearer "+(own?mobileToken:adminToken)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("operationKey",key,"from",day(),"to",day())))).andExpect(status().isOk()));
    }
    private JsonNode finished()throws Exception{prepared();var a=started();clock.set(initial.plusSeconds(30));return json(command(a,"complete",command(a,false)).andExpect(status().isOk()));}
    private UUID service(JsonNode a){return UUID.fromString(a.path("serviceId").asText());}
    private JsonNode adminRead(UUID id)throws Exception{return json(mvc.perform(get("/api/v1/reports/services/emissions/"+id).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));}
    @Test void reportsZeroDenominatorIsNullAndRangeIsBounded()throws Exception{
        report(false).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.totals.scheduled").value(0)).andExpect(jsonPath("$.totals.completionPercent").isEmpty());
        mvc.perform(get("/api/v1/reports/services?from=2026-01-01&to=2026-04-01").header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("REPORT_RANGE_REQUIRED"));
    }
    @Test void reportsOnlyStartedCompletedServiceCountsAndExportNeverExposesPii()throws Exception{
        var a=finished();var r=json(report(false).andExpect(status().isOk()));assertThat(r.path("totals").path("scheduled").asInt()).isEqualTo(1);assertThat(r.path("totals").path("completed").asInt()).isEqualTo(1);
        assertThat(r.path("items").get(0).path("serviceId").asText()).isEqualTo(service(a).toString());assertThat(r.path("items").get(0).path("originalEndedAt").asText()).isEqualTo(initial.plusSeconds(30).toString());
        assertThat(r.toString()).doesNotContain(conductor.getEmail(),dni,"Synthetic Qualification Person","SYNTHETIC-LICENSE","latitude","longitude");
        assertThat(r.path("items").get(0).path("terminalResult").asText()).isEqualTo("NOT_CORROBORATED_TERMINAL_UNCONFIGURED");
    }
    @Test void reportsAcceptanceIsNotCompletionAndCancelledServiceStaysInDenominator()throws Exception{
        prepared();var a=accepted();report(false).andExpect(status().isOk()).andExpect(jsonPath("$.totals.completed").value(0)).andExpect(jsonPath("$.totals.scheduled").value(1));
        long version=jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service(a));
        adminPost("/api/v1/scheduled-services/"+service(a)+"/cancel",Map.of("operationKey","synthetic-"+UUID.randomUUID(),"expectedVersion",version,"reason","SYNTHETIC_SERVICE_CANCEL")).andExpect(status().isOk());
        report(false).andExpect(status().isOk()).andExpect(jsonPath("$.totals.scheduled").value(1)).andExpect(jsonPath("$.totals.completed").value(0)).andExpect(jsonPath("$.totals.cancelled").value(1));
    }
    @Test void reportsReofferDoesNotMoveTheOriginalServiceCohort()throws Exception{
        prepared();var a=accepted();long version=jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service(a));
        var next=Map.of("driverId",driver.getId(),"vehicleId",vehicle.getId(),"routeVersionId",UUID.fromString(a.path("routeVersionId").asText()),"serviceTypeVersionId",UUID.fromString(a.path("serviceTypeVersionId").asText()),"originText","SYNTHETIC origin","destinationText","SYNTHETIC destination","scheduledAt",initial.plus(Duration.ofDays(2)),"scheduledEndAt",initial.plus(Duration.ofDays(2)).plusSeconds(3600));
        adminPost("/api/v1/scheduled-services/"+service(a)+"/offers",Map.of("operationKey","synthetic-"+UUID.randomUUID(),"expectedVersion",version,"reason","SYNTHETIC_MOVE","offer",next)).andExpect(status().isOk());
        var originalDay=Instant.parse(a.path("scheduledAt").asText()).atZone(ZoneId.of("America/Lima")).toLocalDate();
        mvc.perform(get("/api/v1/reports/services?from="+originalDay+"&to="+originalDay).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totals.scheduled").value(1)).andExpect(jsonPath("$.items[0].cohortScheduledAt").value(a.path("scheduledAt").asText())).andExpect(jsonPath("$.items[0].currentScheduledAt").value(initial.plus(Duration.ofDays(2)).toString()));
        var moved=initial.plus(Duration.ofDays(2)).atZone(ZoneId.of("America/Lima")).toLocalDate();mvc.perform(get("/api/v1/reports/services?from="+moved+"&to="+moved).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totals.scheduled").value(0));
    }
    @Test void reportsEmissionKeyReplaysAndCannotChangePeriod()throws Exception{
        finished();UUID key=UUID.randomUUID();var a=issue(false,key);var b=issue(false,key);assertThat(b).isEqualTo(a);
        mvc.perform(post("/api/v1/reports/services/emissions").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("operationKey",key,"from",day(),"to",LocalDate.parse(day()).plusDays(1).toString())))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from report_emission_series where organization_id=?",Integer.class,organization.getId())).isEqualTo(1);
    }
    @Test void reportsIssuedSnapshotDoesNotChangeWhenServiceLaterCompletes()throws Exception{
        prepared();var a=started();var old=issue(false,UUID.randomUUID());clock.set(initial.plusSeconds(30));command(a,"complete",command(a,false)).andExpect(status().isOk());var next=issue(false,UUID.randomUUID());
        assertThat(old.path("report").path("totals").path("completed").asInt()).isZero();assertThat(next.path("revision").asInt()).isEqualTo(2);assertThat(next.path("previousEmissionId").asText()).isEqualTo(old.path("id").asText());
        assertThat(next.path("report").path("totals").path("completed").asInt()).isEqualTo(1);assertThat(adminRead(UUID.fromString(old.path("id").asText()))).isEqualTo(old);
    }
    @Test void reportsEffectiveCorrectionIsNewVersionAndOriginalRemains()throws Exception{
        var a=finished();var old=issue(false,UUID.randomUUID());UUID segment=jdbc.queryForObject("select id from service_execution_segment where assignment_id=?",UUID.class,UUID.fromString(a.path("id").asText()));
        long version=jdbc.queryForObject("select version from scheduled_service where id=?",Long.class,service(a));
        adminPost("/api/v1/scheduled-services/"+service(a)+"/time-corrections",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",version,"reason","SYNTHETIC timing review","segments",List.of(Map.of("segmentId",segment,"startedAt",initial.plusSeconds(1),"endedAt",initial.plusSeconds(20))))).andExpect(status().isOk());
        var next=issue(false,UUID.randomUUID());assertThat(next.path("report").path("items").get(0).path("effectiveEndedAt").asText()).isEqualTo(initial.plusSeconds(20).toString());assertThat(next.path("report").path("items").get(0).path("originalEndedAt").asText()).isEqualTo(initial.plusSeconds(30).toString());assertThat(next.path("report").path("items").get(0).path("correctionIds").size()).isEqualTo(1);assertThat(adminRead(UUID.fromString(old.path("id").asText()))).isEqualTo(old);
    }
    @Test void reportsAllFormatsUseTheSamePersistedSnapshotHashAndContent()throws Exception{
        finished();var e=issue(false,UUID.randomUUID());String id=e.path("id").asText(),hash=e.path("contentSha256").asText();
        for(String format:List.of("csv","xlsx","pdf")){
            var r=mvc.perform(get("/api/v1/reports/services/emissions/"+id+"/export?format="+format).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(header().string("X-Report-Content-SHA256",hash)).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
            String text;if(format.equals("csv"))text=new String(r.getContentAsByteArray(),StandardCharsets.UTF_8);else if(format.equals("pdf")){try(var pdf=org.apache.pdfbox.Loader.loadPDF(r.getContentAsByteArray())){text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);}}else{try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(r.getContentAsByteArray()))){var b=new StringBuilder();book.forEach(s->s.forEach(row->row.forEach(cell->b.append(cell.toString()).append(' '))));text=b.toString();}}
            assertThat(text).contains(id,hash,"TOTAL_COMPLETED",e.path("report").path("items").get(0).path("serviceId").asText()).doesNotContain(dni,conductor.getEmail(),"Synthetic Qualification Person");
        }
        mvc.perform(get("/api/v1/reports/services/emissions/"+id+"/export?format=exe").header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());
    }
    @Test void reportsDownloadsAreAuditedPerFormatAndIssuerWithoutRefreshNoise()throws Exception{
        finished();var adminEmission=issue(false,UUID.randomUUID());var ownEmission=issue(true,UUID.randomUUID());
        for(boolean own:List.of(false,true)){
            var e=own?ownEmission:adminEmission;UUID id=UUID.fromString(e.path("id").asText());String token=own?mobileToken:adminToken;
            String base=own?"/api/v1/mobile/reports/services":"/api/v1/reports/services";
            for(String path:List.of(base+range(),base+"/emissions"+range(),base+"/emissions/"+id))mvc.perform(get(path).header("Authorization","Bearer "+token)).andExpect(status().isOk());
            assertThat(jdbc.queryForObject("select count(*) from audit_event where action='REPORT_EXPORTED' and entity_id=?",Integer.class,id)).isZero();
            for(String format:List.of("csv","xlsx","pdf"))mvc.perform(get(base+"/emissions/"+id+"/export?format="+format).header("Authorization","Bearer "+token)).andExpect(status().isOk());
            entities.flush();
            var events=jdbc.queryForList("select organization_id,user_id,metadata_json::text metadata from audit_event where action='REPORT_EXPORTED' and entity_id=?",id);
            assertThat(events).hasSize(3);var formats=new HashSet<String>();
            for(var event:events){
                assertThat(event.get("organization_id")).isEqualTo(organization.getId());assertThat(event.get("user_id")).isEqualTo(own?conductor.getId():admin.getId());
                String raw=event.get("metadata").toString();var metadata=mapper.readTree(raw);formats.add(metadata.path("kind").asText());
                assertThat(metadata.path("auditOutcome").asText()).isEqualTo("SUCCEEDED");assertThat(metadata.path("contentSha256").asText()).isEqualTo(e.path("contentSha256").asText());assertThat(metadata.path("revision").asInt()).isEqualTo(e.path("revision").asInt());
                assertThat(raw).doesNotContain(dni,conductor.getEmail(),admin.getEmail(),mobileToken,adminToken);
            }
            assertThat(formats).containsExactlyInAnyOrder("CSV","XLSX","PDF");
        }
        assertThat(adminRead(UUID.fromString(adminEmission.path("id").asText()))).isEqualTo(adminEmission);
    }
    @Test void reportsDeniedOrUnsupportedExportsNeverRecordSuccessfulDownload()throws Exception{
        finished();var e=issue(false,UUID.randomUUID());String id=e.path("id").asText();
        mvc.perform(get("/api/v1/mobile/reports/services/emissions/"+id+"/export?format=csv").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/services/emissions/"+UUID.randomUUID()+"/export?format=csv").header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/services/emissions/"+id+"/export?format=exe").header("Authorization","Bearer "+adminToken)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/services/emissions/"+id+"/export?format=csv")).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from audit_event where action='REPORT_EXPORTED' and entity_id=?",Integer.class,UUID.fromString(id))).isZero();
    }
    @Test void reportsOwnScopeIsDistinctFromAdminAndForeignOrWebSessionsCannotReadIt()throws Exception{
        finished();var adminEmission=issue(false,UUID.randomUUID());var ownEmission=issue(true,UUID.randomUUID());assertThat(ownEmission.path("report").path("scope").asText()).isEqualTo("OWN_PARTICIPATION");
        report(true).andExpect(status().isOk()).andExpect(jsonPath("$.totals.completed").value(1)).andExpect(jsonPath("$.items[0].driverIds[0]").value(driver.getId().toString()));
        mvc.perform(get("/api/v1/mobile/reports/services/emissions/"+adminEmission.path("id").asText()).header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/services/emissions/"+ownEmission.path("id").asText()).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/services"+range()).header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/mobile/reports/services"+range()).header("Authorization","Bearer "+jwt.issueAccessToken(conductor))).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MOBILE_SESSION_REQUIRED"));
        mvc.perform(get("/api/v1/mobile/reports/services"+range()).header("Authorization","Bearer "+adminToken)).andExpect(status().isUnauthorized());
    }
    @Test void reportsSnapshotCoreAndContentCannotBeRewrittenOrDeleted()throws Exception{
        finished();UUID id=UUID.fromString(issue(false,UUID.randomUUID()).path("id").asText());
        assertThat(rejectedSqlState("update report_emission set content_sha256=repeat('a',64) where id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from report_emission where id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("update report_emission_content set body_json='{}'::jsonb where emission_id=?",id)).isEqualTo("23514");
        assertThat(rejectedSqlState("delete from report_emission_content where emission_id=?",id)).isEqualTo("23514");
    }
    @Test void reportsUnrelatedQueryParametersDoNotGrantCrossTenantAccess()throws Exception{
        finished();var e=issue(false,UUID.randomUUID());
        mvc.perform(get("/api/v1/reports/services"+range()+"&organizationId="+otherOrganization.getId()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.totals.scheduled").value(1));
        mvc.perform(get("/api/v1/reports/services/emissions/"+UUID.randomUUID()).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/services/emissions/"+e.path("id").asText())).andExpect(status().isUnauthorized());
    }
    @Test void reportsOwnScopeExcludesAnotherConductorServiceAndResources()throws Exception{
        var own=finished();String originalToken=mobileToken;var other=prepareAdditionalSyntheticConductor();mobileToken=originalToken;
        var another=json(adminPost("/api/v1/assignments",Map.of("driverId",other.driver().getId(),"vehicleId",other.vehicle().getId(),"routeVersionId",routeFixture(),"serviceTypeVersionId",serviceTypeFixture(),"originText","SYNTHETIC origin","destinationText","SYNTHETIC destination","scheduledAt",initial.plusSeconds(3600),"scheduledEndAt",initial.plusSeconds(7200))).andExpect(status().isCreated()));
        var r=json(report(true).andExpect(status().isOk()));assertThat(r.path("totals").path("scheduled").asInt()).isEqualTo(1);assertThat(r.toString()).contains(service(own).toString()).doesNotContain(another.path("serviceId").asText(),other.driver().getId().toString(),other.vehicle().getId().toString());
    }
    @Test void reportsOriginalOfferProvenanceCannotBeMovedToImproveTheCohort()throws Exception{
        var a=finished();assertThat(rejectedSqlState("update assignment set scheduled_at=scheduled_at+interval '1 day',scheduled_end_at=scheduled_end_at+interval '1 day' where id=?",UUID.fromString(a.path("id").asText()))).isEqualTo("23514");
        report(false).andExpect(status().isOk()).andExpect(jsonPath("$.totals.completed").value(1));
    }
    @Test void reportsInterruptedSegmentIsNeverDeclaredAsCompletedRoute()throws Exception{
        prepared();var a=started();clock.set(initial.plusSeconds(30));var c=command(a,false);c.put("reason","UNSAFE_BREAKDOWN");c.put("detail","SYNTHETIC unable to continue safely");c.put("safeStopConfirmed",false);command(a,"interrupt",c).andExpect(status().isOk());
        report(false).andExpect(status().isOk()).andExpect(jsonPath("$.totals.scheduled").value(1)).andExpect(jsonPath("$.totals.completed").value(0)).andExpect(jsonPath("$.totals.interrupted").value(1)).andExpect(jsonPath("$.items[0].declaredClosed").value(false)).andExpect(jsonPath("$.items[0].completionRecordedAt").doesNotExist());
    }
    @Test void reportsExpiredOfferRemainsInCohortAndIsShownForReassignment()throws Exception{
        prepared();offer();clock.set(initial.plusSeconds(601));
        report(false).andExpect(status().isOk()).andExpect(jsonPath("$.totals.scheduled").value(1)).andExpect(jsonPath("$.totals.completed").value(0)).andExpect(jsonPath("$.items[0].status").value("PENDING_REASSIGNMENT"));
    }
}
