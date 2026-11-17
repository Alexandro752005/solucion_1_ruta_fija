package pe.rutafija.dashboard;

import pe.rutafija.support.IsolatedAuditSpecializedPostgres;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;
import pe.rutafija.identity.application.JwtService;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real PostgreSQL/HTTP filters, isolated port/database only, synthetic fixtures rolled back. */
@SpringBootTest(properties = {"debug=false", "logging.level.root=ERROR", "spring.flyway.enabled=false",
        "app.seed.enabled=false", "app.qualification.maintenance.enabled=false", "app.mobile.maintenance-enabled=false",
        "app.security.admin-terms-gate.enabled=true", "app.security.person-identity-gate.enabled=true"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named = "RF_DASHBOARD_QA_URL", matches = "jdbc:postgresql://127\\.0\\.0\\.1:(55442/ruta_fija_dashboard_qa|55441/ruta_fija_test)")
class DashboardQaIsolatedIT {
    private static final String URL = "jdbc:postgresql://127.0.0.1:55442/ruta_fija_dashboard_qa";
    @DynamicPropertySource
    static void isolated(DynamicPropertyRegistry properties) {
        if(IsolatedAuditSpecializedPostgres.enabled()) {IsolatedAuditSpecializedPostgres.register(properties);return;}
        if (!URL.equals(System.getenv("RF_DASHBOARD_QA_URL"))) throw new IllegalStateException("EXACT_ISOLATED_DASHBOARD_QA_TARGET_REQUIRED");
        String password = System.getenv("RF_DASHBOARD_QA_PASSWORD");
        if (password == null || password.length() < 20) throw new IllegalStateException("ISOLATED_QA_CREDENTIAL_REQUIRED");
        byte[] random = new byte[64]; new java.security.SecureRandom().nextBytes(random);
        String secret = Base64.getEncoder().encodeToString(random);
        properties.add("spring.datasource.url", () -> URL);
        properties.add("spring.datasource.username", () -> "rf_test");
        properties.add("spring.datasource.password", () -> password);
        properties.add("RF_TEST_DATASOURCE_URL", () -> URL);
        properties.add("RF_TEST_DATASOURCE_USERNAME", () -> "rf_test");
        properties.add("RF_TEST_DATASOURCE_PASSWORD", () -> password);
        properties.add("RF_TEST_MIGRATOR_URL", () -> URL);
        properties.add("RF_TEST_MIGRATOR_USERNAME", () -> "rf_migrator");
        properties.add("RF_TEST_MIGRATOR_PASSWORD", () -> password);
        properties.add("JWT_SECRET_BASE64", () -> secret);
        properties.add("app.security.jwt.secret-base64", () -> secret);
        for (String logger : List.of("org.springframework.web", "org.springframework.security", "org.springframework.jdbc.core", "org.hibernate.SQL", "org.hibernate.orm.jdbc.bind"))
            properties.add("logging.level." + logger, () -> "OFF");
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles;
    @Autowired TransportGroupRepository groups;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired InvitationCodeHasher hasher;
    @MockitoBean OtpEmailSender mail;
    private Actor a, b;
    private record Actor(Organization organization, AppUser admin, Driver driver, String token) { }

    @BeforeEach
    void fixture(TestInfo test) {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(IsolatedAuditSpecializedPostgres.databaseName("ruta_fija_dashboard_qa"));
        assertThat(jdbc.queryForObject("select inet_server_port()", Integer.class)).isEqualTo(IsolatedAuditSpecializedPostgres.port(55442));
        String method = test.getTestMethod().orElseThrow().getName();
        a = actor("A", "Europe/Madrid", !method.equals("missingTermsRemainRejectedByTheRealGate"), !method.equals("missingIdentityRemainsRejectedByTheRealGate"));
        b = actor("B", "Europe/Madrid", true, true);
        vehicles.saveAndFlush(Vehicle.register(b.organization(), "QB-" + UUID.randomUUID().toString().substring(0, 6), null, null, null, null));
    }
    private Actor actor(String label, String zone, boolean accepted, boolean reviewed) {
        String suffix = UUID.randomUUID().toString();
        boolean existing = label.equals("B");
        var base = existing ? users.findById(UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979")).orElseThrow() : null;
        var organization = existing ? base.getOrganization() : organizations.saveAndFlush(Organization.active("SYNTHETIC Dashboard " + label + suffix, "SYNTHETIC " + label, zone));
        var admin = existing ? base : AppUser.organizationUser(organization, "qa." + suffix + "@example.invalid", passwords.encode("Synthetic-Dashboard-QA-Only-2026"), "SYNTHETIC REVIEWER", UserRole.ADMIN);
        admin.verifyEmail(Instant.now()); admin = users.saveAndFlush(admin);
        if (reviewed && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from person_identity where user_id=?)", Boolean.class, admin.getId()))) jdbc.update("insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at) values(?,?,'DNI',?,'IN_PERSON','SYNTHETIC isolated QA only',now())",
                hasher.hashOrigin("conductor-person:DNI:" + suffix), admin.getId(), admin.getId());
        if (accepted && !Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from admin_terms_acceptance where admin_user_id=?)", Boolean.class, admin.getId()))) jdbc.update("insert into admin_terms_acceptance(id,admin_user_id,organization_id,document_id,document_version,content_sha256,accepted_at) select ?,?,?,id,version,content_sha256,now() from admin_terms_document where document_key='ADMIN_TERMS' and version='1.0.0' and not synthetic", UUID.randomUUID(), admin.getId(), organization.getId());
        var group = groups.saveAndFlush(TransportGroup.active(organization, "SYNTHETIC " + suffix, null));
        var driver = drivers.saveAndFlush(Driver.register(organization, null, group, "PRIVATE_SYNTHETIC_DRIVER", null, "DNI", String.format(java.util.Locale.ROOT, "%08d", Math.floorMod(suffix.hashCode(), 100_000_000)), null));
        vehicles.saveAndFlush(Vehicle.register(organization, "QA-" + suffix.substring(0, 6), null, null, null, null));
        entities.flush();
        return new Actor(organization, admin, driver, jwt.issueAccessToken(admin));
    }
    private JsonNode getJson(String path, Actor actor) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", "Bearer " + actor.token())).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store")).andReturn().getResponse().getContentAsByteArray());
    }
    private void incident(Actor actor) {
        jdbc.update("insert into incident(id,organization_id,driver_id,reported_by,category,status,source,description,reported_at,created_at,updated_at,severity) values(?,?,?,null,'OTRO','OPEN','SYSTEM','PRIVATE_SYNTHETIC_INCIDENT',now(),now(),now(),'CRITICAL')", UUID.randomUUID(), actor.organization().getId(), actor.driver().getId());
    }
    @Test void twoAdminsReceiveOnlyTheirOrganizationEvenWithAForeignQueryParameter() throws Exception {
        incident(a); incident(b); incident(b);
        for (var actor : List.of(a, b)) {
            var foreign = actor == a ? b : a;
            var view = getJson("/api/v1/dashboard/admin?organizationId=" + foreign.organization().getId(), actor);
            assertThat(view.path("priorities").get(0).path("count").asInt()).isEqualTo(jdbc.queryForObject("select count(*) from incident where organization_id=? and severity='CRITICAL' and status in('OPEN','FOLLOW_UP')", Integer.class, actor.organization().getId()));
            assertThat(view.path("activeVehicles").asInt()).isEqualTo(jdbc.queryForObject("select count(*) from vehicle where organization_id=? and active", Integer.class, actor.organization().getId()));
            assertThat(view.path("zoneId").asText()).isEqualTo(actor.organization().getTimezone());
            assertThat(view.path("operationalDate").asText()).isEqualTo(Instant.parse(view.path("generatedAt").asText()).atZone(ZoneId.of(actor.organization().getTimezone())).toLocalDate().toString());
            assertThat(view.toString()).doesNotContain("PRIVATE_SYNTHETIC", foreign.organization().getId().toString(), actor.admin().getEmail());
        }
    }
    @Test void missingTermsRemainRejectedByTheRealGate() throws Exception {
        mvc.perform(get("/api/v1/dashboard/admin").header("Authorization", "Bearer " + a.token())).andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }
    @Test void missingIdentityRemainsRejectedByTheRealGate() throws Exception {
        mvc.perform(get("/api/v1/dashboard/admin").header("Authorization", "Bearer " + a.token())).andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }
    @Test void anonymousConductorAndPlatformRolesDoNotGainAdminOperations() throws Exception {
        mvc.perform(get("/api/v1/dashboard/admin")).andExpect(status().isUnauthorized());
        var owner = users.findById(UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0")).orElseThrow();
        String platform = jwt.issueAccessToken(owner);
        mvc.perform(get("/api/v1/dashboard/admin").header("Authorization", "Bearer " + platform)).andExpect(status().isForbidden());
        var conductor = AppUser.organizationUser(a.organization(), "conductor." + UUID.randomUUID() + "@example.invalid", "synthetic-test-only", "SYNTHETIC", UserRole.CONDUCTOR);
        conductor.verifyEmail(Instant.now()); users.saveAndFlush(conductor);
        mvc.perform(get("/api/v1/dashboard/admin").header("Authorization", "Bearer " + jwt.issueAccessToken(conductor))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/dashboard").header("Authorization", "Bearer " + a.token())).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/platform/dashboard").header("Authorization", "Bearer " + platform)).andExpect(status().isOk());
    }
    @Test void currentSourcesRemainReadOnlyAndKeepTheZeroServiceDenominatorNull() throws Exception {
        long audit = jdbc.queryForObject("select count(*) from audit_event", Long.class);
        String day = LocalDate.now(ZoneId.of("America/Lima")).toString();
        getJson("/api/v1/dashboard/admin", a);
        getJson("/api/v1/reports/availability", a);
        getJson("/api/v1/reports/incidents?from=" + day + "&to=" + day, a);
        var report = getJson("/api/v1/reports/services?from=" + day + "&to=" + day, a);
        assertThat(report.path("totals").path("completionPercent").isNull()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from audit_event", Long.class)).isEqualTo(audit);
    }
    @Test void persistedReportRecoveryAndForeignReadUseTheActualScope() throws Exception {
        String day = LocalDate.now(ZoneId.of("America/Lima")).toString();
        byte[] command = json.writeValueAsBytes(Map.of("operationKey", UUID.randomUUID(), "from", day, "to", day));
        JsonNode first = null;
        for (int i = 0; i < 2; i++) {
            var current = json.readTree(mvc.perform(post("/api/v1/reports/services/emissions").header("Authorization", "Bearer " + a.token()).contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            if (first == null) first = current; else assertThat(current).isEqualTo(first);
        }
        assertThat(jdbc.queryForObject("select count(*) from report_emission_series where organization_id=?", Integer.class, a.organization().getId())).isEqualTo(1);
        mvc.perform(get("/api/v1/reports/services/emissions/" + first.path("id").asText()).header("Authorization", "Bearer " + b.token())).andExpect(status().isNotFound());
        String csv = mvc.perform(get("/api/v1/reports/services/emissions/" + first.path("id").asText() + "/export?format=csv").header("Authorization", "Bearer " + a.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).contains(first.path("id").asText(), first.path("contentSha256").asText()).doesNotContain("PRIVATE_SYNTHETIC", a.admin().getEmail(), "latitude", "longitude");
    }
}
