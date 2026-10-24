package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import pe.rutafija.identity.domain.AppUser;
import java.sql.Timestamp;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filters and PostgreSQL, mail double; rollback only, not connected browser evidence. */
class DashboardIntegrationTest extends ServiceExecutionIntegrationTest {
    private AppUser owner(){ return users.findById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow(); }
    private void incident(UUID org, UUID targetDriver, String severity){
        jdbc.update("""
            insert into incident(id,organization_id,driver_id,reported_by,category,status,source,description,
              reported_at,created_at,updated_at,severity) values(?,?,?,null,'OTRO','OPEN','SYSTEM','SYNTHETIC_PRIVATE_DETAIL',?,?,?,?)
            """,UUID.randomUUID(),org,targetDriver,Timestamp.from(initial),Timestamp.from(initial),Timestamp.from(initial),severity);
    }
    @Test void dashboardAdminCountsOnlyOwnTenantAndKeepsPriorityOrder() throws Exception {
        incident(organization.getId(),driver.getId(),"CRITICAL"); incident(otherOrganization.getId(),otherDriver.getId(),"CRITICAL"); incident(otherOrganization.getId(),otherDriver.getId(),"CRITICAL");
        var result=json(mvc.perform(get("/api/v1/dashboard/admin").param("organizationId",otherOrganization.getId().toString()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertThat(result.path("priorities").get(0).path("code").asText()).isEqualTo("CRITICAL_INCIDENTS");
        assertThat(result.path("priorities").get(0).path("count").asLong()).isEqualTo(1);
        assertThat(result.path("activeVehicles").asLong()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain(admin.getEmail(),driver.getFullName(),dni,"SYNTHETIC_PRIVATE_DETAIL",otherOrganization.getId().toString());
        var order=new ArrayList<Integer>();result.path("priorities").forEach(p->order.add(p.path("order").asInt()));assertThat(order).isSorted();
    }
    @Test void dashboardPlatformContainsOnlyPlatformAggregates() throws Exception {
        var result=json(mvc.perform(get("/api/v1/platform/dashboard").header("Authorization","Bearer "+jwt.issueAccessToken(owner()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertThat(result.fieldNames()).toIterable().containsExactlyInAnyOrder("generatedAt","organizations","activeOrganizations","activeAdmins","pendingAdmins");
        assertThat(result.path("organizations").asLong()).isEqualTo(jdbc.queryForObject("select count(*) from organization",Long.class));
        assertThat(result.toString()).doesNotContain(driver.getId().toString(),admin.getEmail(),"priorities","incidents");
    }
    @Test void dashboardEnforcesRolesAndDoesNotGrantSuperAdminOperations() throws Exception {
        mvc.perform(get("/api/v1/dashboard/admin").header("Authorization","Bearer "+jwt.issueAccessToken(owner()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/dashboard").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());
        for(var path:List.of("/api/v1/dashboard/admin","/api/v1/platform/dashboard"))mvc.perform(get(path).header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
    }
    @Test void dashboardRejectsAnonymousRequestsAndMutation() throws Exception {
        mvc.perform(get("/api/v1/dashboard/admin")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/dashboard/admin").header("Authorization","Bearer "+adminToken)).andExpect(status().isMethodNotAllowed());
    }
    @Test void dashboardEmptyQueueIsExplicitAndRefreshDoesNotWriteAudit() throws Exception {
        long before=jdbc.queryForObject("select count(*) from audit_event",Long.class);
        var result=json(mvc.perform(get("/api/v1/dashboard/admin").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));
        assertThat(result.path("priorities").get(0).path("count").asLong()).isZero();
        assertThat(result.path("priorities").get(7).path("count").asLong()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_event",Long.class)).isEqualTo(before);
    }
    @Test void dashboardExpiredPendingOfferIsForReassignmentWithoutAReadMutation()throws Exception{
        prepared();offer();clock.set(initial.plusSeconds(601));entities.flush();long before=jdbc.queryForObject("select count(*) from audit_event",Long.class);
        var result=json(mvc.perform(get("/api/v1/dashboard/admin").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));
        assertThat(result.path("priorities").get(2).path("count").asInt()).isZero();assertThat(result.path("priorities").get(3).path("count").asInt()).isEqualTo(1);assertThat(result.path("priorities").get(4).path("count").asInt()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from audit_event",Long.class)).isEqualTo(before);
    }
}
