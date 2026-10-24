package pe.rutafija.qualification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pe.rutafija.audit.domain.AuditEvent;
import pe.rutafija.audit.infrastructure.AuditEventRepository;
import pe.rutafija.identity.domain.AppUser;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filters/controllers/PostgreSQL; every fixture rolls back. */
class AuditScopeIntegrationTest extends QualificationIntegrationTest {
    @Autowired AuditEventRepository auditEvents;
    private AppUser owner(){return users.findById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow();}
    private AuditEvent event(AppUser actor,String action){audit.record(actor,action,"APP_USER",actor.getId(),Map.of("status","OPEN"));auditEvents.flush();return auditEvents.findAll().stream().filter(e->e.getAction().equals(action)&&Objects.equals(e.getUserId(),actor.getId())).findFirst().orElseThrow();}
    @Test void auditAdminSeesOnlyOwnOrganization()throws Exception{
        String action="L07_SCOPE_"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);var own=event(admin,action);var platform=event(owner(),action);audit.recordSystem(otherOrganization,action,"ORGANIZATION",otherOrganization.getId(),Map.of());auditEvents.flush();
        var r=json(mvc.perform(get("/api/v1/audit-events").param("action",action).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertThat(r.path("totalItems").asInt()).isEqualTo(1);assertThat(r.path("items").get(0).path("id").asText()).isEqualTo(own.getId().toString());assertThat(r.path("items").get(0).path("scope").asText()).isEqualTo("ORGANIZATION");
        mvc.perform(get("/api/v1/audit-events/"+platform.getId()).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());
    }
    @Test void auditSuperAdminCannotReadTenantOperationalDetail()throws Exception{
        String action="L07_PLATFORM_CASE";var own=event(owner(),action);var operational=event(admin,action);String token=jwt.issueAccessToken(owner());
        var r=json(mvc.perform(get("/api/v1/platform/audit-events").param("action",action).param("organizationId",organization.getId().toString()).header("Authorization","Bearer "+token)).andExpect(status().isOk()));assertThat(r.path("totalItems").asInt()).isEqualTo(1);assertThat(r.path("items").get(0).path("id").asText()).isEqualTo(own.getId().toString());
        mvc.perform(get("/api/v1/platform/audit-events/"+operational.getId()).header("Authorization","Bearer "+token)).andExpect(status().isNotFound());mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer "+token)).andExpect(status().isForbidden());
    }
    @Test void auditAdministrativeLogIsDeniedToConductor()throws Exception{
        for(String path:List.of("/api/v1/audit-events","/api/v1/platform/audit-events"))mvc.perform(get(path).header("Authorization","Bearer "+mobileToken)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/audit-events").header("Authorization","Bearer "+adminToken)).andExpect(status().isForbidden());mvc.perform(get("/api/v1/platform/audit-events")).andExpect(status().isUnauthorized());
    }
    @Test void auditLegacyNestedMetadataIsRedactedWithoutRewriting()throws Exception{
        var e=auditEvents.saveAndFlush(new AuditEvent(organization,admin,"L07_LEGACY","APP_USER",admin.getId(),"FAKE_CORRELATION_INPUT",Map.of("details",Map.of("password","FAKE_SECRET","email","synthetic@example.test","status","OPEN")),initial));
        var r=json(mvc.perform(get("/api/v1/audit-events/"+e.getId()).header("Authorization","Bearer "+adminToken)).andExpect(status().isOk()));assertThat(r.toString()).doesNotContain("FAKE_SECRET","synthetic@example.test",admin.getFullName(),"FAKE_CORRELATION_INPUT").contains("OPEN","LEGACY_RECORDED");
        assertThat(jdbc.queryForObject("select metadata_json::text from audit_event where id=?",String.class,e.getId())).contains("FAKE_SECRET");
    }
    @Test void auditNewWritesRemoveNestedSecretsBeforeStorage()throws Exception{
        audit.record(admin,"L07_PERSISTENCE","APP_USER",admin.getId(),Map.of("details",Map.of("token","FAKE_SECRET","status","OPEN"),"auditOutcome","DENIED"));auditEvents.flush();
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where user_id=? and action='L07_PERSISTENCE'",String.class,admin.getId());assertThat(raw).doesNotContain("FAKE_SECRET","DENIED").contains("SUCCEEDED","OPEN");
    }
    @Test void auditPersonalDifferenceNeverPersistsOriginalValues()throws Exception{
        audit.record(admin,"L07_PERSONAL_CHANGE","APP_USER",conductor.getId(),Map.of("changes",List.of(Map.of("field","email","before","before@example.test","after","after@example.test"))));auditEvents.flush();
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where user_id=? and action='L07_PERSONAL_CHANGE'",String.class,admin.getId());assertThat(raw).doesNotContain("before@example.test","after@example.test").contains("REDACTADO");
    }
    @Test void auditReadRoutesHaveNoMutableEquivalent()throws Exception{
        var e=event(owner(),"L07_READ_ONLY");String token=jwt.issueAccessToken(owner());
        mvc.perform(post("/api/v1/platform/audit-events/"+e.getId()).header("Authorization","Bearer "+token)).andExpect(status().isMethodNotAllowed());mvc.perform(delete("/api/v1/platform/audit-events/"+e.getId()).header("Authorization","Bearer "+token)).andExpect(status().isMethodNotAllowed());assertThat(auditEvents.findById(e.getId())).isPresent();
    }
    @Test void auditPlatformRejectsInvalidDatesAndOversizedPages()throws Exception{
        String token=jwt.issueAccessToken(owner());mvc.perform(get("/api/v1/platform/audit-events?size=101").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());mvc.perform(get("/api/v1/platform/audit-events?from=2026-10-22T00:00:00Z&to=2026-10-21T00:00:00Z").header("Authorization","Bearer "+token)).andExpect(status().isBadRequest());
    }

    @Test void auditActualRoleDenialIsRecordedOnceWithoutRequestSecrets()throws Exception{
        int before=jdbc.queryForObject("select count(*) from audit_event where user_id=? and action='SECURITY_ACCESS_DENIED'",Integer.class,conductor.getId());
        mvc.perform(get("/api/v1/audit-events?private=FAKE_QUERY").header("Authorization","Bearer "+mobileToken).cookie(new jakarta.servlet.http.Cookie("FAKE_COOKIE","FAKE_COOKIE_VALUE"))).andExpect(status().isForbidden());auditEvents.flush();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where user_id=? and action='SECURITY_ACCESS_DENIED'",Integer.class,conductor.getId())).isEqualTo(before+1);
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where user_id=? and action='SECURITY_ACCESS_DENIED' order by occurred_at desc limit 1",String.class,conductor.getId());assertThat(raw).contains("DENIED","403","AUDIT").doesNotContain(mobileToken,"FAKE_QUERY","FAKE_COOKIE_VALUE",conductor.getEmail());
    }
    @Test void auditSuccessfulConsultationsDoNotCreateRefreshNoise()throws Exception{
        int before=jdbc.queryForObject("select count(*) from audit_event",Integer.class);
        mvc.perform(get("/api/v1/audit-events").header("Authorization","Bearer "+adminToken)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select count(*) from audit_event",Integer.class)).isEqualTo(before);
    }
    @Test void auditInvalidBearerBeforeAuthorizationHasOneAnonymousSafeDenial()throws Exception{
        int before=jdbc.queryForObject("select count(*) from audit_event where user_id is null and action='SECURITY_ACCESS_DENIED'",Integer.class);
        mvc.perform(get("/api/v1/platform/audit-events").header("Authorization","Bearer INVALID_SYNTHETIC_NOT_A_TOKEN")).andExpect(status().isUnauthorized());auditEvents.flush();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where user_id is null and action='SECURITY_ACCESS_DENIED'",Integer.class)).isEqualTo(before+1);
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where user_id is null and action='SECURITY_ACCESS_DENIED' order by occurred_at desc limit 1",String.class);assertThat(raw).contains("401","DENIED").doesNotContain("INVALID_SYNTHETIC_NOT_A_TOKEN");
    }
    @Test void auditObjectRejectionDoesNotStoreForeignResourceOrGrantAccess()throws Exception{
        var outside=event(owner(),"L07_FOREIGN_OBJECT");int before=jdbc.queryForObject("select count(*) from audit_event where user_id=? and action='SECURITY_ACCESS_DENIED'",Integer.class,admin.getId());
        mvc.perform(get("/api/v1/audit-events/"+outside.getId()).header("Authorization","Bearer "+adminToken)).andExpect(status().isNotFound());auditEvents.flush();
        assertThat(jdbc.queryForObject("select count(*) from audit_event where user_id=? and action='SECURITY_ACCESS_DENIED'",Integer.class,admin.getId())).isEqualTo(before+1);
        String raw=jdbc.queryForObject("select metadata_json::text from audit_event where user_id=? and action='SECURITY_ACCESS_DENIED' order by occurred_at desc limit 1",String.class,admin.getId());assertThat(raw).contains("404").doesNotContain(outside.getId().toString());
    }
}
