package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Normal route/assignment HTTP transitions and real PG, synthetic rollback-only fixtures. */
class RouteAssignmentIntegrationTest extends QualificationIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired jakarta.persistence.EntityManagerFactory entityManagerFactory;
    private void operational()throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"OWN",initial.minusSeconds(60),null)).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());declareAvailable();mobileToken=jwt.issueMobileAccessToken(conductor,driver.getId(),"OPERATIONS");
        syntheticTrackingConsent();
    }
    private Map<String,Object> creation(UUID version)throws Exception {
        var c=new LinkedHashMap<String,Object>();c.put("serviceTypeVersionId",serviceTypeFixture());c.put("driverId",driver.getId());c.put("vehicleId",vehicle.getId());c.put("originText","SYNTHETIC origin");c.put("destinationText","SYNTHETIC destination");
        c.put("scheduledAt",initial.plusSeconds(3600));c.put("scheduledEndAt",initial.plusSeconds(7200));c.put("responseMode","MOBILE_CONFIRMATION");c.put("responseDeadlineAt",initial.plusSeconds(1800));if(version!=null)c.put("routeVersionId",version);return c;
    }
    private ResultActions postAdmin(String path,Object c)throws Exception {entities.flush();entities.clear();return mvc.perform(post(path).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c)));}
    private ResultActions mobile(JsonNode a,String action)throws Exception {entities.flush();entities.clear();return mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/"+action).header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(Map.of("version",a.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",clock.instant(),"gps",Map.of("permissionGranted",true,"locationServiceEnabled",true,"foreground",true)))));}
    private UUID revise(UUID version)throws Exception {
        UUID id=jdbc.queryForObject("select route_id from route_version where id=?",UUID.class,version);
        var stops=List.of(Map.of("key","start","name","SYNTHETIC origin","kind","START"),Map.of("key","middle","name","SYNTHETIC other middle","kind","INTERMEDIATE"),Map.of("key","end","name","SYNTHETIC destination","kind","END"));
        var v=json(postAdmin("/api/v1/routes/"+id+"/versions",Map.of("expectedVersion",1,"name","SYNTHETIC revised "+id.toString().replaceAll("[0-9]","x"),"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-REVISION","stops",stops)).andExpect(status().isOk()));return UUID.fromString(v.path("currentVersionId").asText());
    }
    @Test void routeIsRequiredAndOriginMustMatchItsCapturedVersion()throws Exception {
        operational();postAdmin("/api/v1/assignments",creation(null)).andExpect(status().isBadRequest());
        var c=creation(routeFixture());c.put("originText","SYNTHETIC forged origin");postAdmin("/api/v1/assignments",c).andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("El origen y el terminal deben corresponder a la versión elegida."));
        assertThat(jdbc.queryForObject("select count(*) from assignment where driver_id=?",Integer.class,driver.getId())).isZero();
    }
    @Test void routeVersionRemainsCapturedAfterCatalogRevisionAndRetirement()throws Exception {
        operational();UUID original=routeFixture();var a=json(postAdmin("/api/v1/assignments",creation(original)).andExpect(status().isCreated()));UUID next=revise(original);
        UUID route=jdbc.queryForObject("select route_id from route_version where id=?",UUID.class,next);
        postAdmin("/api/v1/routes/"+route+"/status",Map.of("expectedVersion",2,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-RETIREMENT","active",false)).andExpect(status().isOk());
        mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()+"/route").header("Authorization","Bearer "+mobileToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.versionId").value(original.toString())).andExpect(jsonPath("$.route.revision").value(1));
        assertThat(jdbc.queryForObject("select count(*) from assignment_route_history where assignment_id=?",Integer.class,UUID.fromString(a.path("id").asText()))).isEqualTo(1);
    }
    @Test void routeReplacementPreservesOldAcceptanceAndRequiresFreshConductorAcceptance()throws Exception {
        operational();UUID v1=routeFixture();var old=json(postAdmin("/api/v1/assignments",creation(v1)).andExpect(status().isCreated()));old=json(mobile(old,"accept").andExpect(status().isOk()));
        String accepted=old.path("acceptedAt").asText();UUID v2=revise(v1);var c=Map.of("expectedVersion",old.path("version").asLong(),"routeVersionId",v2,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-ROUTE-CHANGE");
        String path="/api/v1/assignments/"+old.path("id").asText()+"/route-replacement";
        var next=json(postAdmin(path,c).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_RESPONSE")).andExpect(jsonPath("$.acceptedAt").doesNotExist()));
        assertThat(next.path("serviceId")).isEqualTo(old.path("serviceId"));
        postAdmin(path,c).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(next.path("id").asText()));
        mvc.perform(get("/api/v1/assignments/"+old.path("id").asText()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.acceptedAt").value(accepted)).andExpect(jsonPath("$.routeVersionId").value(v1.toString()));
        mobile(next,"start").andExpect(status().isConflict());
        next=json(mobile(next,"accept").andExpect(status().isOk()));
        assertThat(next.path("acceptedAt").asText()).isNotBlank();assertThat(next.path("routeVersionId").asText()).isEqualTo(v2.toString());
        assertThat(jdbc.queryForObject("select count(*) from assignment_route_replacement where previous_assignment_id=?",Integer.class,UUID.fromString(old.path("id").asText()))).isEqualTo(1);
        mvc.perform(get("/api/v1/assignment-route-operations/"+c.get("operationKey")).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(next.path("id").asText()));
    }
    @Test void routeChangeRejectsStartedAndStaleServicesWithoutReplacingThem()throws Exception {
        operational();UUID version=routeFixture();var a=json(postAdmin("/api/v1/assignments",creation(version)).andExpect(status().isCreated()));a=json(mobile(a,"accept").andExpect(status().isOk()));a=json(mobile(a,"start").andExpect(status().isOk()));
        UUID revised=revise(version);postAdmin("/api/v1/assignments/"+a.path("id").asText()+"/route-replacement",Map.of("expectedVersion",a.path("version").asLong(),"routeVersionId",revised,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-INVALID-CHANGE")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from assignment_route_replacement where organization_id=?",Integer.class,organization.getId())).isZero();
    }
    @Test void routeFinalizedPersistsOwnDeclarationWithoutInventingTerminalEvidence()throws Exception {
        operational();var a=json(postAdmin("/api/v1/assignments",creation(routeFixture())).andExpect(status().isCreated()));
        mobile(a,"complete").andExpect(status().isConflict());a=json(mobile(a,"accept").andExpect(status().isOk()));a=json(mobile(a,"start").andExpect(status().isOk()));
        a=json(mobile(a,"complete").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")));
        mvc.perform(get("/api/v1/mobile/assignments/"+a.path("id").asText()+"/route").header("Authorization","Bearer "+mobileToken))
            .andExpect(status().isOk()).andExpect(jsonPath("$.declaration.actorId").value(conductor.getId().toString()))
            .andExpect(jsonPath("$.declaration.terminalResult").value("NOT_CORROBORATED_TERMINAL_UNCONFIGURED"));
        assertThat(jdbc.queryForObject("select declared_at from assignment_route_declaration where assignment_id=?",java.sql.Timestamp.class,UUID.fromString(a.path("id").asText())).toInstant()).isEqualTo(initial);
    }
    @Test void routeReadsAreTenantScopedAndConductorCannotEditTheCatalog()throws Exception {
        operational();UUID v=routeFixture();var a=json(postAdmin("/api/v1/assignments",creation(v)).andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/mobile/assignments/"+UUID.randomUUID()+"/route").header("Authorization","Bearer "+mobileToken)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/assignments/"+a.path("id").asText()+"/route").header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        var catalog=Map.of("name","SYNTHETIC-UNAUTHORIZED","operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-UNAUTHORIZED","stops",List.of(Map.of("key","s","kind","START","name","Start"),Map.of("key","m","kind","INTERMEDIATE","name","Middle"),Map.of("key","e","kind","END","name","End")));
        mvc.perform(post("/api/v1/routes").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(catalog))).andExpect(status().isForbidden());
    }
    @Test void routeCreationIdempotencyRejectsDifferentPayload()throws Exception {
        operational();var c=creation(routeFixture());String key="synthetic-"+UUID.randomUUID();
        var request=post("/api/v1/assignments").header("Authorization","Bearer "+adminToken).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON);
        String first=json(mvc.perform(request.content(mapper.writeValueAsBytes(c))).andExpect(status().isCreated())).path("id").asText();
        mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(first));
        c.put("notes","SYNTHETIC-OTHER");mvc.perform(post("/api/v1/assignments").header("Authorization","Bearer "+adminToken).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(c))).andExpect(status().isConflict());
    }
    @Test void routeReplacementFailureAfterCancellationMarksTheWholePhysicalTransactionForRollback()throws Exception {
        operational();UUID original=routeFixture();var a=json(postAdmin("/api/v1/assignments",creation(original)).andExpect(status().isCreated()));a=json(mobile(a,"accept").andExpect(status().isOk()));UUID next=revise(original);
        // Synthetic adverse setup: the unit becomes unavailable, without pre-cancelling
        // the service. The requested replacement reaches cancellation, then its normal
        // creation validation fails. This does not force a successful application state.
        var unit=vehicles.findById(vehicle.getId()).orElseThrow();unit.deactivate();vehicles.flush();entities.clear();
        var holder=(org.springframework.orm.jpa.EntityManagerHolder)org.springframework.transaction.support.TransactionSynchronizationManager.getResource(entityManagerFactory);
        assertThat(holder).isNotNull();assertThat(holder.getEntityManager().getTransaction().getRollbackOnly()).isFalse();
        postAdmin("/api/v1/assignments/"+a.path("id").asText()+"/route-replacement",Map.of("expectedVersion",a.path("version").asLong(),"routeVersionId",next,"operationKey","synthetic-"+UUID.randomUUID(),"reason","SYNTHETIC-ROLLBACK-CHECK"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("El vehículo debe estar activo y DISPONIBLE para programarlo"));
        assertThat(holder.getEntityManager().getTransaction().getRollbackOnly()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from assignment_route_replacement where organization_id=?",Integer.class,organization.getId())).isZero();
        // The fixture's rollback-only transaction is intentionally never committed.
        // This proves the real Spring/JPA rollback marker, not two committed TCP cases.
    }
    @Test void routeAcceptanceReloadsOccupationCachedBeforeTheSchedulingLock()throws Exception {
        operational();var a=json(postAdmin("/api/v1/assignments",creation(routeFixture())).andExpect(status().isCreated()));
        var cached=drivers.findById(driver.getId()).orElseThrow();assertThat(cached.getAvailabilityStatus().name()).isEqualTo("DISPONIBLE");
        jdbc.update("update driver set availability_status='RESERVADO' where id=?",driver.getId());
        mvc.perform(post("/api/v1/mobile/assignments/"+a.path("id").asText()+"/accept").header("Authorization","Bearer "+mobileToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("version",a.path("version").asLong(),"clientEventId",UUID.randomUUID(),"occurredAt",initial))))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,UUID.fromString(a.path("id").asText()))).isEqualTo("PENDING_RESPONSE");
    }
}
