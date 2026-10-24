package pe.rutafija.operation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import pe.rutafija.support.ApprovedConductorFixture;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * M8 native PostgreSQL contract: the foreground outbox has one durable receipt
 * per event, stops when its FIFO head conflicts, and never overwrites a newer
 * availability state with an old device intention.
 */
@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "app.mobile.offline-sync.enabled=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileOfflineSyncIT extends NativePostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    TransportGroupRepository groupRepository;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Organization organization;
    private AppUser conductor;
    private Driver driver;

    @BeforeEach
    void prepareData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Ruta Fija M8 " + suffix, "M8 " + suffix, "America/Lima"
        ));
        AppUser conductorCandidate = AppUser.organizationUser(
                organization,
                "m8.conductor." + suffix + "@rutafija.test",
                passwordEncoder.encode("Integration-Password-2026"),
                "Conductor M8",
                UserRole.CONDUCTOR
        );
        conductorCandidate.verifyEmail(Instant.now());
        conductor = userRepository.save(conductorCandidate);
        TransportGroup group = groupRepository.save(TransportGroup.active(
                organization, "Grupo M8 " + suffix, "Pruebas de sincronizacion"
        ));
        driver = Driver.register(
                organization, conductor, group, "Conductor M8", "999000111",
                "DNI", "M8-" + suffix, "LIC-M8"
        );
        driver.changeAdministrativeAvailability(DriverAvailabilityStatus.DISPONIBLE);
        driver = driverRepository.saveAndFlush(driver);
        // Historical synthetic fixture for this test-only legacy regression.
        // This does not enable offline routes in any runtime profile.
        AppUser fixtureAdmin = AppUser.organizationUser(organization,
                "m8.admin." + suffix + "@rutafija.test", passwordEncoder.encode("Synthetic-Admin-2026"),
                "Synthetic M8 reviewer", UserRole.ADMIN);
        fixtureAdmin.verifyEmail(Instant.now());
        fixtureAdmin = userRepository.save(fixtureAdmin);
        ApprovedConductorFixture.recordHistoricalApproval(jdbcTemplate, fixtureAdmin, conductor, driver);
    }

    @Test
    void availabilityEventIsAppliedThenReplayedWithoutDuplicatingMutationOrAudit() throws Exception {
        String initialToken = currentAvailabilityUpdatedAt();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = occurredAt();
        String payload = availabilityPayload(eventId, occurredAt, "DESCANSO", initialToken);

        mockMvc.perform(post("/api/v1/mobile/sync/events")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"))
                .andExpect(jsonPath("$.results[0].serverState").value("DESCANSO"));

        mockMvc.perform(post("/api/v1/mobile/sync/events")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.results[0].status").value("ALREADY_APPLIED"))
                .andExpect(jsonPath("$.results[0].serverState").value("DESCANSO"));

        assertThat(driverAvailability()).isEqualTo("DESCANSO");
        assertThat(count("select count(*) from mobile_command_receipt where event_id = ?", eventId)).isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'MOBILE_DRIVER_AVAILABILITY_CHANGED'"))
                .isEqualTo(1);
    }

    @Test
    void changedPayloadForExistingEventIsConflictAndStopsTheFifoBatch() throws Exception {
        String initialToken = currentAvailabilityUpdatedAt();
        UUID appliedEventId = UUID.randomUUID();
        Instant occurredAt = occurredAt();
        String applied = availabilityPayload(appliedEventId, occurredAt, "DESCANSO", initialToken);

        mockMvc.perform(post("/api/v1/mobile/sync/events")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applied))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("APPLIED"));

        String currentToken = currentAvailabilityUpdatedAt();
        UUID secondEventId = UUID.randomUUID();
        String changedHead = availabilityEvent(appliedEventId, occurredAt, "NO_DISPONIBLE", initialToken);
        String secondEvent = availabilityEvent(secondEventId, occurredAt(), "NO_DISPONIBLE", currentToken);
        String batch = "{\"events\":[%s,%s]}".formatted(changedHead, secondEvent);

        mockMvc.perform(post("/api/v1/mobile/sync/events")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(batch))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].eventId").value(appliedEventId.toString()))
                .andExpect(jsonPath("$.results[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].errorCode").value("MOBILE_EVENT_CONFLICT"));

        assertThat(driverAvailability()).isEqualTo("DESCANSO");
        assertThat(count("select count(*) from mobile_command_receipt where event_id = ?", appliedEventId)).isEqualTo(1);
        assertThat(count("select count(*) from mobile_command_receipt where event_id = ?", secondEventId)).isZero();
    }

    @Test
    void staleAvailabilityTokenIsPersistedAsConflictAndCannotOverwriteNewerState() throws Exception {
        String staleToken = currentAvailabilityUpdatedAt();

        mockMvc.perform(put("/api/v1/mobile/availability")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DESCANSO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESCANSO"));

        UUID eventId = UUID.randomUUID();
        String staleEvent = availabilityPayload(eventId, occurredAt(), "NO_DISPONIBLE", staleToken);

        mockMvc.perform(post("/api/v1/mobile/sync/events")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleEvent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.results[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].errorCode").value("RESOURCE_VERSION_CONFLICT"));

        mockMvc.perform(post("/api/v1/mobile/sync/events")
                        .with(mobileToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(staleEvent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].status").value("CONFLICT"))
                .andExpect(jsonPath("$.results[0].errorCode").value("RESOURCE_VERSION_CONFLICT"));

        assertThat(driverAvailability()).isEqualTo("DESCANSO");
        assertThat(count("select count(*) from mobile_command_receipt where event_id = ?", eventId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select result_status from mobile_command_receipt where event_id = ?", String.class, eventId
        )).isEqualTo("CONFLICT");
        assertThat(jdbcTemplate.queryForObject(
                "select server_state from mobile_command_receipt where event_id = ?", String.class, eventId
        )).isEqualTo("DESCANSO");
        assertThat(count("select count(*) from audit_event where action = 'MOBILE_DRIVER_AVAILABILITY_CHANGED'"))
                .isEqualTo(1);
    }

    private String currentAvailabilityUpdatedAt() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/mobile/availability").with(mobileToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn();
        return json(result).path("updatedAt").asText();
    }

    private String availabilityPayload(UUID eventId, Instant occurredAt, String status, String expectedUpdatedAt) {
        return "{\"events\":[%s]}".formatted(availabilityEvent(eventId, occurredAt, status, expectedUpdatedAt));
    }

    private String availabilityEvent(UUID eventId, Instant occurredAt, String status, String expectedUpdatedAt) {
        return """
                {"eventId":"%s","type":"AVAILABILITY_CHANGE","occurredAt":"%s",
                 "targetAvailability":"%s","expectedAvailabilityUpdatedAt":"%s"}
                """.formatted(eventId, occurredAt, status, expectedUpdatedAt);
    }

    private Instant occurredAt() {
        return Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
    }

    private String driverAvailability() {
        return jdbcTemplate.queryForObject(
                "select availability_status from driver where id = ?", String.class, driver.getId()
        );
    }

    private Integer count(String query, Object... arguments) {
        return jdbcTemplate.queryForObject(query, Integer.class, arguments);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private RequestPostProcessor mobileToken() {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_CONDUCTOR"))
                .jwt(token -> token
                        .subject(conductor.getId().toString())
                        .claim("organizationId", organization.getId().toString())
                        .claim("email", conductor.getEmail())
                        .claim("role", UserRole.CONDUCTOR.name())
                        .claim("sessionVersion", conductor.getSessionVersion())
                        .claim("sessionChannel", "MOBILE")
                        .claim("driverId", driver.getId().toString()));
    }
}
