package pe.rutafija.qualification;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** V31 HTTP/PG maintenance cases, plus inherited L02 qualification regression.
 * Fixture accounts/ledger exist only in rollback transactions. No SMTP or truncation. */
class MaintenanceIntegrationTest extends QualificationIntegrationTest {
    private static final ZoneId LIMA=ZoneId.of("America/Lima");
    private LocalDate today(){return initial.atZone(LIMA).toLocalDate();}
    private Instant day(LocalDate d){return d.atStartOfDay(LIMA).toInstant();}
    private String path(UUID id){return "/api/v1/vehicles/"+id+"/maintenance";}
    private ResultActions command(String suffix,Object body)throws Exception {
        entities.flush();entities.clear();
        return mvc.perform(post(path(vehicle.getId())+suffix).header("Authorization","Bearer "+adminToken)
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)));
    }
    private Map<String,Object> create(long version,String kind,LocalDate first,LocalDate last){
        var c=basic(version);c.put("kind",kind);c.put("startDate",first);c.put("endDate",last);return c;
    }
    private JsonNode urgent()throws Exception{return json(command("",create(0,"URGENT",null,null)).andExpect(status().isOk()));}
    private ResultActions completed(JsonNode v,int index)throws Exception {
        var c=basic(v.path("version").asLong());c.put("responsibleReference","SYNTHETIC-TECHNICIAN");
        c.put("evidenceReference","SYNTHETIC-VISUAL-CHECK");c.put("workCompleted",true);
        return command("/"+v.path("records").get(index).path("id").asText()+"/work-completed",c);
    }
    private void operational()throws Exception {
        reviewAll();mutate("/periods",period(2,vehicle.getId(),"COMPANY",initial.minusSeconds(60),initial.plus(Duration.ofDays(25)))).andExpect(status().isOk());
        mutate("/activate",basic(3)).andExpect(status().isOk());
    }
    @Test void preventiveUsesFullLimaDaysAndRemainsBlockedAfterEstimate()throws Exception {
        var first=today().plusDays(2);var last=first.plusDays(3);
        var v=json(command("",create(0,"PREVENTIVE",first,last)).andExpect(status().isOk()));
        assertThat(v.path("records").get(0).path("startsAt").asText()).isEqualTo(day(first).toString());
        assertThat(v.path("records").get(0).path("plannedEndsAt").asText()).isEqualTo(day(last.plusDays(1)).toString());
        assertThat(policy.maintenanceBlocks(vehicle.getId(),organization.getId(),day(first).minusSeconds(60),day(first))).isFalse();
        assertThat(policy.maintenanceBlocks(vehicle.getId(),organization.getId(),day(first),day(first).plusSeconds(1))).isTrue();
        clock.set(day(last.plusDays(10)));
        assertThat(policy.maintenanceBlocks(vehicle.getId(),organization.getId(),clock.instant(),clock.instant().plusSeconds(1))).isTrue();
        mvc.perform(get(path(vehicle.getId())).header("Authorization","Bearer "+jwt.issueAccessToken(admin)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.mechanicallyBlockedNow").value(true));
    }
    @Test void previewIncludesEveryFutureCommitmentBeyondTheEstimatedEnd()throws Exception {
        UUID a=assignment("SCHEDULED",day(today().plusDays(3)),day(today().plusDays(3)).plusSeconds(600));
        UUID b=assignment("PENDING_RESPONSE",day(today().plusDays(20)),day(today().plusDays(20)).plusSeconds(600));
        var preview=json(mvc.perform(get(path(vehicle.getId())+"/preview").param("startDate",today().plusDays(2).toString())
            .header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(jsonPath("$.canConfirmPreventive").value(false)));
        assertThat(preview.path("commitments").findValuesAsText("assignmentId")).containsExactly(a.toString(),b.toString());
        command("",create(0,"PREVENTIVE",today().plusDays(2),today().plusDays(2))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from vehicle_maintenance where vehicle_id=?",Integer.class,vehicle.getId())).isZero();
    }
    @Test void urgentPreservesOngoingServiceAndMarksFutureForReassignment()throws Exception {
        operational();UUID active=assignment("EN_SERVICIO",initial.minusSeconds(60),initial.plusSeconds(1200));
        UUID future=assignment("SCHEDULED",initial.plusSeconds(1800),initial.plusSeconds(2400));
        urgent();
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,active)).isEqualTo("EN_SERVICIO");
        assertThat(jdbc.queryForObject("select kind from qualification_service_attention where assignment_id=?",String.class,active)).isEqualTo("SAFE_HANDOVER_REQUIRED");
        assertThat(jdbc.queryForObject("select kind from qualification_service_attention where assignment_id=?",String.class,future)).isEqualTo("PENDING_REASSIGNMENT");
        assertThat(policy.eligible(driver.getId(),organization.getId(),true)).isFalse();verifyNoInteractions(mail);
    }
    @Test void earlyReturnRequiresWorkAndDocumentsThenReleasesOnlyNextLimaDay()throws Exception {
        reviewAll();var first=today();var last=first.plusDays(10);
        var v=json(command("",create(0,"PREVENTIVE",first,last)).andExpect(status().isOk()));
        command("/return",basic(1)).andExpect(status().isConflict());
        v=json(completed(v,0).andExpect(status().isOk()));
        assertThat(policy.maintenanceBlocks(vehicle.getId(),organization.getId(),initial,initial.plusSeconds(1))).isTrue();
        v=json(command("/return",basic(v.path("version").asLong())).andExpect(status().isOk()));
        var r=v.path("records").get(0);Instant next=day(today().plusDays(1));
        assertThat(r.path("status").asText()).isEqualTo("RETURNED");
        assertThat(r.path("plannedEndsAt").asText()).isEqualTo(day(last.plusDays(1)).toString());
        assertThat(r.path("blockingEndsAt").asText()).isEqualTo(next.toString());
        assertThat(r.path("returnedBy").asText()).isEqualTo(admin.getId().toString());
        assertThat(policy.maintenanceBlocks(vehicle.getId(),organization.getId(),next.minusSeconds(1),next)).isTrue();
        assertThat(policy.maintenanceBlocks(vehicle.getId(),organization.getId(),next,next.plusSeconds(1))).isFalse();
    }
    @Test void aSecondUnfinishedJobAndMissingDocumentsPreventReturn()throws Exception {
        var v=urgent();v=json(completed(v,0).andExpect(status().isOk()));
        command("/return",basic(2)).andExpect(status().isConflict()); // no documentary context
        reviewAll();v=json(command("",create(2,"URGENT",null,null)).andExpect(status().isOk()));
        command("/return",basic(3)).andExpect(status().isConflict());
        int open=v.path("records").get(0).path("status").asText().equals("OPEN")?0:1;
        v=json(completed(v,open).andExpect(status().isOk()));
        command("/return",basic(4)).andExpect(status().isOk());
    }
    @Test void documentsMustCoverTheReleaseDayAndOngoingServiceBlocksReturn()throws Exception {
        operational();var v=urgent();v=json(completed(v,0).andExpect(status().isOk()));
        var shortened=reviews("VEHICLE",vehicle.getId(),4,true);
        @SuppressWarnings("unchecked") var documents=(List<Map<String,Object>>)shortened.get("documents");
        documents.forEach(d->d.put("validUntil",initial.plusSeconds(60)));
        mutate("/reviews",shortened).andExpect(status().isOk());
        command("/return",basic(2)).andExpect(status().isConflict());
        mutate("/reviews",reviews("VEHICLE",vehicle.getId(),5,true)).andExpect(status().isOk());
        assignment("EN_SERVICIO",initial.minusSeconds(60),initial.plusSeconds(900));
        command("/return",basic(2)).andExpect(status().isConflict());
    }
    @Test void cancelOnlyBeforeStartAndKeepOriginalRecords()throws Exception {
        var v=json(command("",create(0,"PREVENTIVE",today().plusDays(1),today().plusDays(2))).andExpect(status().isOk()));
        command("/"+v.path("records").get(0).path("id").asText()+"/cancel",basic(1)).andExpect(status().isOk()).andExpect(jsonPath("$.records[0].status").value("CANCELLED"));
        v=json(command("",create(2,"URGENT",null,null)).andExpect(status().isOk()));
        String urgentId="";for(var record:v.path("records"))if(record.path("kind").asText().equals("URGENT"))urgentId=record.path("id").asText();
        command("/"+urgentId+"/cancel",basic(3)).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from vehicle_maintenance where vehicle_id=?",Integer.class,vehicle.getId())).isEqualTo(2);
    }
    @Test void idempotencyVersionsTenantAndRoleAreEnforced()throws Exception {
        var c=create(0,"URGENT",null,null);command("",c).andExpect(status().isOk());command("",c).andExpect(status().isOk()).andExpect(jsonPath("$.version").value(1));
        c.put("reason","SYNTHETIC-CHANGED");command("",c).andExpect(status().isConflict());
        command("",create(0,"URGENT",null,null)).andExpect(status().isConflict());
        mvc.perform(get(path(otherVehicle.getId())).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
        mvc.perform(get(path(vehicle.getId())).header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(post(path(otherVehicle.getId())).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(create(0,"URGENT",null,null)))).andExpect(status().isNotFound());
    }
    @Test void canonicalPlateCannotBeRecreatedAcrossOrganizations()throws Exception {
        String variant=otherVehicle.getPlate().replace("-","").toLowerCase(Locale.ROOT);
        mvc.perform(post("/api/v1/vehicles").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("plate",variant)))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from vehicle where id=?",Integer.class,otherVehicle.getId())).isEqualTo(1);
    }
    @Test void materialUnitCorrectionCannotInvalidateCommittedServicesThroughTheOrdinaryForm()throws Exception {
        operational();UUID service=assignment("SCHEDULED",initial.plusSeconds(600),initial.plusSeconds(1200));
        var update=new LinkedHashMap<String,Object>();update.put("plate",vehicle.getPlate());update.put("brand","SYNTHETIC-CHANGED");update.put("model",vehicle.getModel());update.put("year",2025);update.put("color","YELLOW");
        entities.flush();entities.clear();
        mvc.perform(patch("/api/v1/vehicles/"+vehicle.getId()).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(update))).andExpect(status().isConflict());
        update.put("brand",vehicle.getBrand());
        mvc.perform(patch("/api/v1/vehicles/"+vehicle.getId()).header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(update))).andExpect(status().isOk());
        mvc.perform(post("/api/v1/vehicles/"+vehicle.getId()+"/status").header("Authorization","Bearer "+adminToken).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"INACTIVO\"}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select status from assignment where id=?",String.class,service)).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("select brand from vehicle where id=?",String.class,vehicle.getId())).isEqualTo(vehicle.getBrand());
    }
}
