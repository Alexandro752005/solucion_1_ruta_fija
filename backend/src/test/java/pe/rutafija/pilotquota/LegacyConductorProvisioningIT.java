package pe.rutafija.pilotquota;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LegacyConductorProvisioningIT extends NativePostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationRepository organizations;
    @Autowired AppUserRepository users;
    @Autowired TransportGroupRepository groups;
    @Autowired DriverRepository drivers;
    private AppUser admin;
    private AppUser conductor;
    private TransportGroup group;
    private Driver driver;

    @BeforeEach
    void prepareUnapprovedLegacyAccount() {
        Organization organization = organizations.save(Organization.active("SYNTHETIC LEGACY", null, "America/Lima"));
        AppUser adminCandidate = AppUser.organizationUser(organization,
                "admin.synthetic@rutafija.test", "synthetic-only", "SYNTHETIC ADMIN", UserRole.ADMIN);
        adminCandidate.verifyEmail(java.time.Instant.now());
        admin = users.saveAndFlush(adminCandidate);
        conductor = AppUser.organizationUser(organization, "unapproved.synthetic@rutafija.test",
                "synthetic-only", "SYNTHETIC CONDUCTOR", UserRole.CONDUCTOR);
        conductor.deactivate();
        conductor = users.saveAndFlush(conductor);
        group = groups.saveAndFlush(TransportGroup.active(organization, "SYNTHETIC GROUP", null));
        driver = Driver.register(organization, conductor, group, "SYNTHETIC CONDUCTOR", null,
                "DNI", "SYNTHETIC-01", "SYNTHETIC-LIC");
        driver.deactivate();
        driver = drivers.saveAndFlush(driver);
    }

    @Test
    void legacyUserCreationCannotSkipOnboarding() throws Exception {
        mvc.perform(post("/api/v1/users").with(actor()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", "new.synthetic@rutafija.test",
                                "password", "Synthetic-Test-Only-2026", "fullName", "SYNTHETIC NEW", "role", "CONDUCTOR"))))
                .andExpect(status().isConflict());
        assertThat(users.findByEmailIgnoreCase("new.synthetic@rutafija.test")).isEmpty();
    }

    @Test
    void legacyDriverCreationCannotSkipOnboarding() throws Exception {
        mvc.perform(post("/api/v1/drivers").with(actor()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("groupId", group.getId(),
                                "fullName", "SYNTHETIC NEW DRIVER", "documentType", "DNI", "documentNumber", "SYNTHETIC-02"))))
                .andExpect(status().isConflict());
        assertThat(drivers.count()).isEqualTo(1);
    }

    @Test
    void unapprovedUserAndDriverCannotBeActivated() throws Exception {
        mvc.perform(post("/api/v1/users/{id}/activate", conductor.getId()).with(actor()))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/v1/drivers/{id}/activate", driver.getId()).with(actor()))
                .andExpect(status().isConflict());
        assertThat(users.findById(conductor.getId()).orElseThrow().isActive()).isFalse();
        assertThat(drivers.findById(driver.getId()).orElseThrow().isActive()).isFalse();
    }

    @Test
    void identityCannotBeSubstitutedThroughGeneralUserOrDriverEditor() throws Exception {
        mvc.perform(patch("/api/v1/users/{id}", conductor.getId()).with(actor()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", conductor.getEmail(),
                                "fullName", "SYNTHETIC SUBSTITUTE", "role", "CONDUCTOR"))))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/v1/drivers/{id}", driver.getId()).with(actor()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("userId", conductor.getId(), "groupId", group.getId(),
                                "fullName", "SYNTHETIC SUBSTITUTE", "documentType", "DNI", "documentNumber", "SYNTHETIC-03"))))
                .andExpect(status().isConflict());
        assertThat(users.findById(conductor.getId()).orElseThrow().getFullName()).isEqualTo("SYNTHETIC CONDUCTOR");
        assertThat(drivers.findById(driver.getId()).orElseThrow().getDocumentNumber()).isEqualTo("SYNTHETIC-01");
    }

    private RequestPostProcessor actor() {
        return jwt().jwt(builder -> builder.subject(admin.getId().toString())
                        .claim("organizationId", admin.getOrganizationId().toString())
                        .claim("role", "ADMIN"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
