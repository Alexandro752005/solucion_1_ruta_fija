package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Normal ADMIN/CONDUCTOR HTTP with real PG and rollback-only synthetic fixtures. */
class ServiceMarginIntegrationTest extends QualificationIntegrationTest {
    private void prepared()throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
    }
    private ResultActions postAdmin(String path,Object command)throws Exception {
        entities.flush();entities.clear();return mvc.perform(post(path).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command)));
    }
    private Map<String,Object> config(int before,int after) {
        return new LinkedHashMap<>(Map.of("name","SYNTHETIC TYPE "+UUID.randomUUID().toString().replaceAll("[0-9]","x"),"beforeMinutes",before,"afterMinutes",after,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-MARGIN-REVIEW"));
    }
    private JsonNode type(int before,int after)throws Exception {return json(postAdmin("/api/v1/service-types",config(before,after)).andExpect(status().isOk()));}
    private Map<String,Object> offer(UUID type,long start,long end)throws Exception {
        var c=new LinkedHashMap<String,Object>();c.put("driverId",driver.getId());c.put("vehicleId",vehicle.getId());c.put("routeVersionId",routeFixture());
        c.put("originText","SYNTHETIC origin");c.put("destinationText","SYNTHETIC destination");c.put("scheduledAt",initial.plusSeconds(start));c.put("scheduledEndAt",initial.plusSeconds(end));
        if(type!=null)c.put("serviceTypeVersionId",type);return c;
    }
    private UUID version(JsonNode type){return UUID.fromString(type.path("currentVersionId").asText());}
    private JsonNode create(UUID type,long start,long end)throws Exception {return json(postAdmin("/api/v1/assignments",offer(type,start,end)).andExpect(status().isCreated()));}
    private ResultActions accept(JsonNode offer)throws Exception {
        entities.flush();entities.clear();return mvc.perform(post("/api/v1/mobile/assignments/"+offer.path("id").asText()+"/accept").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("version",offer.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",clock.instant()))));
    }
    private JsonNode candidates(UUID type,UUID route,long start,long end)throws Exception {
        entities.flush();entities.clear();return json(mvc.perform(get("/api/v1/assignment-candidates").header("Authorization","Bearer "+adminToken)
            .param("serviceTypeVersionId",type.toString()).param("routeVersionId",route.toString()).param("scheduledAt",initial.plusSeconds(start).toString()).param("scheduledEndAt",initial.plusSeconds(end).toString())).andExpect(status().isOk()));
    }
    @Test void marginConfigurationIsExplicitAndMissingValuesCannotBecomeDefaults()throws Exception {
        prepared();var c=config(0,0);c.remove("beforeMinutes");postAdmin("/api/v1/service-types",c).andExpect(status().isBadRequest());
        c=config(-1,0);postAdmin("/api/v1/service-types",c).andExpect(status().isBadRequest());
        postAdmin("/api/v1/assignments",offer(null,3600,7200)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("select count(*) from service_type where organization_id=?",Integer.class,organization.getId())).isZero();
    }
    @Test void marginRevisionsAreImmutableAndOldOffersKeepTheirExactCapture()throws Exception {
        prepared();var original=type(5,10);var assigned=create(version(original),3600,7200);
        var c=config(20,30);c.put("expectedVersion",original.path("version").asLong());
        var next=json(postAdmin("/api/v1/service-types/"+original.path("id").asText()+"/versions",c).andExpect(status().isOk()));
        assertThat(next.path("versions").size()).isEqualTo(2);
        var persisted=jdbc.queryForMap("select service_type_version_id,margin_before_minutes,margin_after_minutes,reservation_starts_at,reservation_ends_at from assignment where id=?",UUID.fromString(assigned.path("id").asText()));
        assertThat(persisted.get("service_type_version_id")).isEqualTo(version(original));assertThat(persisted.get("margin_before_minutes")).isEqualTo(5);assertThat(persisted.get("margin_after_minutes")).isEqualTo(10);
        assertThat(((java.sql.Timestamp)persisted.get("reservation_starts_at")).toInstant()).isEqualTo(initial.plusSeconds(3300));
        assertThat(((java.sql.Timestamp)persisted.get("reservation_ends_at")).toInstant()).isEqualTo(initial.plusSeconds(7800));
        accept(assigned).andExpect(status().isOk());
    }
    @Test void marginRetirementStopsNewOffersButPreservesTheKnownVersionForPendingAcceptance()throws Exception {
        prepared();var t=type(5,5);var a=create(version(t),3600,7200);
        postAdmin("/api/v1/service-types/"+t.path("id").asText()+"/status",Map.of("expectedVersion",t.path("version").asLong(),"active",false,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-RETIRE")).andExpect(status().isOk());
        postAdmin("/api/v1/assignments",offer(version(t),9000,12000)).andExpect(status().isNotFound());accept(a).andExpect(status().isOk());
    }
    @Test void marginIdempotencyVersionAndChangedPayloadAreChecked()throws Exception {
        prepared();var c=config(0,5);var t=json(postAdmin("/api/v1/service-types",c).andExpect(status().isOk()));
        postAdmin("/api/v1/service-types",c).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(t.path("id").asText()));
        c.put("afterMinutes",6);postAdmin("/api/v1/service-types",c).andExpect(status().isConflict());
        var revise=config(5,5);revise.put("expectedVersion",99);postAdmin("/api/v1/service-types/"+t.path("id").asText()+"/versions",revise).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/service-types/operations/"+c.get("operationKey")).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(t.path("id").asText()));
    }
    @Test void marginDatabaseRejectsRevisionOverwriteAndSnapshotTampering()throws Exception {
        prepared();var t=type(5,10);var a=create(version(t),3600,7200);jdbc.execute("SAVEPOINT margin_guard");
        assertThatThrownBy(()->jdbc.update("update service_type_version set before_minutes=0 where id=?",version(t))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT margin_guard");
        assertThatThrownBy(()->jdbc.update("update assignment set margin_before_minutes=0 where id=?",UUID.fromString(a.path("id").asText()))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.execute("ROLLBACK TO SAVEPOINT margin_guard");
        assertThat(jdbc.queryForObject("select margin_before_minutes from assignment where id=?",Integer.class,UUID.fromString(a.path("id").asText()))).isEqualTo(5);
    }
    @Test void marginOverlapBlocksBothResourcesAndExactBoundaryIsAllowed()throws Exception {
        prepared();UUID type=version(type(5,10));create(type,3600,7200);
        postAdmin("/api/v1/assignments",offer(type,8099,10800)).andExpect(status().isConflict());
        postAdmin("/api/v1/assignments",offer(type,8100,10800)).andExpect(status().isCreated());
    }
    @Test void marginCandidatesIncludeOnlyEligibleOwnPairsAndDoNotReserveThem()throws Exception {
        prepared();UUID type=version(type(5,10)),route=routeFixture();var first=candidates(type,route,3600,7200);
        assertThat(first.path("items").size()).isEqualTo(1);assertThat(first.path("items").get(0).path("driverId").asText()).isEqualTo(driver.getId().toString());
        assertThat(first.path("items").get(0).has("email")).isFalse();assertThat(first.path("items").get(0).has("fullName")).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isZero();
        create(type,3600,7200);assertThat(candidates(type,route,8099,10800).path("items").size()).isZero();
        assertThat(candidates(type,route,8100,10800).path("items").size()).isEqualTo(1);
    }
    @Test void marginDocumentsMustCoverTheBufferRatherThanOnlyDriving()throws Exception {
        prepared();UUID type=version(type(5,10)),route=routeFixture();
        seedDocument(personalDocuments(initial.plusSeconds(7500)).get(1),2);
        assertThat(candidates(type,route,3600,7200).path("items").size()).isZero();
        postAdmin("/api/v1/assignments",offer(type,3600,7200)).andExpect(status().isConflict());
    }
    @Test void marginRevalidationRejectsAUnitChangedAfterTheCandidateQuery()throws Exception {
        prepared();UUID type=version(type(5,10)),route=routeFixture();assertThat(candidates(type,route,3600,7200).path("items").size()).isEqualTo(1);
        var suspension=basic(4);suspension.put("evidenceReference","SYNTHETIC-INCIDENT");mutate("/suspend",suspension).andExpect(status().isOk());postAdmin("/api/v1/assignments",offer(type,3600,7200)).andExpect(status().isConflict());
    }
    @Test void marginMultipleAcceptedFutureOffersKeepReservationUntilTheLastOneIsCancelled()throws Exception {
        prepared();UUID type=version(type(5,10));var first=json(accept(create(type,3600,7200)).andExpect(status().isOk()));
        var second=json(accept(create(type,8100,12000)).andExpect(status().isOk()));
        postAdmin("/api/v1/assignments/"+first.path("id").asText()+"/cancel",Map.of("version",first.path("version").asLong(),"reason","SYNTHETIC-CANCEL")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("RESERVADO");
        postAdmin("/api/v1/assignments/"+second.path("id").asText()+"/cancel",Map.of("version",second.path("version").asLong(),"reason","SYNTHETIC-CANCEL")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select availability_status from driver where id=?",String.class,driver.getId())).isEqualTo("DISPONIBLE");
    }
    @Test void marginMissingHistoricalConfigurationCannotBeInventedAtAcceptance()throws Exception {
        prepared();UUID id=assignment("PENDING_RESPONSE",initial.plusSeconds(3600),initial.plusSeconds(7200));
        var old=mapper.createObjectNode().put("id",id.toString()).put("version",0);accept(old).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("márgenes acreditados")));
        assertThat(jdbc.queryForObject("select service_type_version_id is null and accepted_at is null from assignment where id=?",Boolean.class,id)).isTrue();
    }
    @Test void marginRolesProtectTheNewConfigurationEndpoint()throws Exception {
        prepared();mvc.perform(post("/api/v1/service-types").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(config(5,5)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/service-types")).andExpect(status().isUnauthorized());
    }
    @Test void marginMissingCurrentTermsAreRejectedByTheActualTermsGate()throws Exception {
        mvc.perform(get("/api/v1/admin/terms/current").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(false));
        mvc.perform(get("/api/v1/service-types").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
    }
    @Test void marginMissingIdentityIsRejectedByTheActualIdentityGate()throws Exception {
        mvc.perform(get("/api/v1/identity/status").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.identityVerified").value(false));
        mvc.perform(get("/api/v1/service-types").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
    }
    @Test void marginActualForeignConfigurationCannotBeReadSelectedOrEnumerated()throws Exception {
        prepared();UUID org=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a"),actor=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979"),type=UUID.randomUUID(),version=UUID.randomUUID();
        // Separate technical configuration only, inside this rollback fixture. No account, acceptance or historical row is changed.
        jdbc.update("insert into service_type(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,'SYNTHETIC FOREIGN MARGIN',?,?,?)",type,org,version,java.sql.Timestamp.from(initial),java.sql.Timestamp.from(initial));
        jdbc.update("insert into service_type_version(id,type_id,organization_id,revision,name,before_minutes,after_minutes,content_sha256,created_by,created_at) values(?,?,?,1,'SYNTHETIC FOREIGN MARGIN',5,10,?,?,?)",version,type,org,"0".repeat(64),actor,java.sql.Timestamp.from(initial));
        mvc.perform(get("/api/v1/service-types/"+type).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/service-types").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        postAdmin("/api/v1/assignments",offer(version,3600,7200)).andExpect(status().isNotFound());
    }
    @Test void marginUnknownActiveLegacyCommitmentDoesNotBecomeAnImplicitZeroBuffer()throws Exception {
        prepared();UUID type=version(type(5,10)),route=routeFixture();
        assignment("SCHEDULED",initial.plusSeconds(864000),initial.plusSeconds(867600));
        assertThat(candidates(type,route,3600,7200).path("items").size()).isZero();
        postAdmin("/api/v1/assignments",offer(type,3600,7200)).andExpect(status().isConflict());
    }
    @Test void marginAuthorizedPeriodMustCoverTheWholeReservation()throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"COMPANY",initial.minusSeconds(60),initial.plusSeconds(7500))).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();UUID type=version(type(5,10)),route=routeFixture();
        assertThat(candidates(type,route,3600,7200).path("items").size()).isZero();
        postAdmin("/api/v1/assignments",offer(type,3600,7200)).andExpect(status().isConflict());
    }
    @Test void marginCrossingMidnightBlocksMaintenanceAndTheReverseCandidateSearch()throws Exception {
        prepared();var tomorrow=initial.atZone(java.time.ZoneId.of("America/Lima")).toLocalDate().plusDays(1);
        Instant midnight=tomorrow.atStartOfDay(java.time.ZoneId.of("America/Lima")).toInstant();
        long end=java.time.Duration.between(initial,midnight.minusSeconds(300)).getSeconds(),start=end-3600;
        UUID type=version(type(5,10)),route=routeFixture();var assigned=create(type,start,end);
        String path="/api/v1/vehicles/"+vehicle.getId()+"/maintenance";
        mvc.perform(get(path+"/preview").param("startDate",tomorrow.toString()).header("Authorization","Bearer "+adminToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.commitments.length()").value(1));
        var preventive=new LinkedHashMap<String,Object>(Map.of("expectedVersion",0,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-MARGIN-MAINTENANCE","kind","PREVENTIVE","startDate",tomorrow.toString(),"endDate",tomorrow.toString()));
        postAdmin(path,preventive).andExpect(status().isConflict());
        postAdmin("/api/v1/assignments/"+assigned.path("id").asText()+"/cancel",Map.of("version",assigned.path("version").asLong(),"reason","SYNTHETIC-RESOLVE-CONFLICT")).andExpect(status().isOk());
        preventive.put("operationKey","synthetic-"+UUID.randomUUID());postAdmin(path,preventive).andExpect(status().isOk());
        assertThat(candidates(type,route,start,end).path("items").size()).isZero();
        postAdmin("/api/v1/assignments",offer(type,start,end)).andExpect(status().isConflict());
    }
}
