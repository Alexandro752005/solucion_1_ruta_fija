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
import pe.rutafija.fleet.domain.DriverVehicleLink;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.DriverVehicleLinkRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.operation.application.MobileOperationMaintenanceService;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * F3.4 contract matrix. It intentionally exercises PostgreSQL-native data,
 * not mocks: report totals, scope, state, receipt replay, expiry and privacy.
 */
@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileContractReportIT extends NativePostgresIntegrationTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

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
    VehicleRepository vehicleRepository;

    @Autowired
    DriverVehicleLinkRepository driverVehicleLinkRepository;

    @Autowired
    AssignmentRepository assignmentRepository;

    @Autowired
    MobileOperationMaintenanceService mobileOperationMaintenanceService;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private Organization organization;
    private AppUser admin;
    private AppUser conductor;
    private TransportGroup group;
    private Driver driver;
    private Vehicle vehicle;

    @BeforeEach
    void prepareData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Ruta Fija F3.4 " + suffix, "F34 " + suffix, "America/Lima"
        ));
        admin = userRepository.save(user(organization, "admin." + suffix, UserRole.ADMIN));
        conductor = userRepository.save(user(organization, "conductor." + suffix, UserRole.CONDUCTOR));
        group = groupRepository.save(TransportGroup.active(organization, "Grupo F3.4 " + suffix, "Matriz F3.4"));
        driver = availableDriver(organization, conductor, group, "Conductor F3.4", "F34-" + suffix);
        vehicle = linkedVehicle(driver, organization, "F34" + suffix.substring(0, 5));
    }

    @Test
    void publishedOpenApiMakesMobileAuthorityAndCrmBoundaryExplicit() throws Exception {
        JsonNode document = json(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode paths = document.path("paths");
        assertThat(paths.has("/api/v1/mobile/assignments/{assignmentId}/accept")).isTrue();
        assertThat(paths.has("/api/v1/mobile/assignments/{assignmentId}/reject")).isTrue();
        assertThat(paths.has("/api/v1/mobile/location/current")).isTrue();
        assertThat(paths.has("/api/v1/assignments/{assignmentId}/accept")).isFalse();
        assertThat(paths.has("/api/v1/assignments/{assignmentId}/reject")).isFalse();
        assertThat(document.path("info").path("description").asText()).contains("CRM nunca simula");
        assertThat(document.path("security").toString()).contains("bearerAuth");

        JsonNode schemas = document.path("components").path("schemas");
        assertThat(schemas.path("AssignmentCreateRequest").path("properties").has("responseMode")).isTrue();
        assertThat(schemas.path("AssignmentCreateRequest").path("properties").has("responseDeadlineAt")).isTrue();
        assertThat(schemas.path("AssignmentResponse").path("properties").has("acceptedAt")).isTrue();
        assertThat(schemas.path("AssignmentResponse").path("properties").has("rejectedAt")).isTrue();
        assertThat(schemas.path("AssignmentResponse").path("properties").has("expiredAt")).isTrue();
        assertThat(schemas.path("MobileAssignmentCommandRequest").path("properties").has("clientEventId")).isTrue();
        assertThat(schemas.path("MobileAssignmentCommandRequest").path("properties").has("version")).isTrue();
        assertThat(schemas.path("MobileAssignmentCommandRequest").path("properties").has("occurredAt")).isTrue();
        assertThat(schemas.path("MobileLocationUpdateRequest").path("properties").has("permissionGranted")).isTrue();
    }

    @Test
    void realReportsCountPendingRejectedAndExpiredWithoutCrossTenantData() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant firstStart = now.plus(6, ChronoUnit.HOURS);
        AssignmentCreated pending = createMobileConfirmation(
                admin, driver, vehicle, firstStart, firstStart.plus(45, ChronoUnit.MINUTES), now.plus(10, ChronoUnit.MINUTES)
        );
        Instant secondStart = firstStart.plus(90, ChronoUnit.MINUTES);
        AssignmentCreated rejected = createMobileConfirmation(
                admin, driver, vehicle, secondStart, secondStart.plus(45, ChronoUnit.MINUTES), now.plus(20, ChronoUnit.MINUTES)
        );
        mockMvc.perform(post("/api/v1/mobile/assignments/{assignmentId}/reject", rejected.id())
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectPayload(UUID.randomUUID(), rejected.version(), now, "No disponible para este turno")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));

        Instant thirdStart = secondStart.plus(90, ChronoUnit.MINUTES);
        AssignmentCreated due = createMobileConfirmation(
                admin, driver, vehicle, thirdStart, thirdStart.plus(45, ChronoUnit.MINUTES), now.plus(30, ChronoUnit.MINUTES)
        );
        jdbcTemplate.update(
                "update assignment set response_deadline_at = ? where id = ?",
                Timestamp.from(now.minusSeconds(1)), due.id()
        );
        mobileOperationMaintenanceService.expireDueMobileResponsesAndLocations();
        assertThat(assignmentRepository.findById(due.id()).orElseThrow().getStatus()).isEqualTo(AssignmentStatus.EXPIRED);

        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Ruta Fija Externa", "Externa", "America/Lima"
        ));
        AppUser otherAdmin = userRepository.save(user(otherOrganization, "admin.external", UserRole.ADMIN));
        AppUser otherConductor = userRepository.save(user(otherOrganization, "conductor.external", UserRole.CONDUCTOR));
        TransportGroup otherGroup = groupRepository.save(TransportGroup.active(otherOrganization, "Grupo externo", null));
        Driver otherDriver = availableDriver(otherOrganization, otherConductor, otherGroup, "Conductor externo", "EXT-001");
        Vehicle otherVehicle = linkedVehicle(otherDriver, otherOrganization, "EXT-401");
        createMobileConfirmation(
                otherAdmin,
                otherDriver,
                otherVehicle,
                thirdStart.plus(90, ChronoUnit.MINUTES),
                thirdStart.plus(135, ChronoUnit.MINUTES),
                now.plus(40, ChronoUnit.MINUTES)
        );

        LocalDate from = LocalDate.now(LIMA);
        LocalDate to = from.plusDays(2);
        JsonNode tenantReport = json(mockMvc.perform(get("/api/v1/reports/assignments")
                        .with(webToken(admin))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(tenantReport.path("totalsByStatus").size()).isEqualTo(7);
        assertThat(total(tenantReport.path("totalsByStatus"), "PENDING_RESPONSE")).isEqualTo(1L);
        assertThat(total(tenantReport.path("totalsByStatus"), "REJECTED")).isEqualTo(1L);
        assertThat(total(tenantReport.path("totalsByStatus"), "EXPIRED")).isEqualTo(1L);
        assertThat(total(tenantReport.path("totalsByStatus"), "SCHEDULED")).isZero();
        assertThat(tenantReport.path("items").size()).isEqualTo(3);

        JsonNode otherReport = json(mockMvc.perform(get("/api/v1/reports/assignments")
                        .with(webToken(otherAdmin))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(total(otherReport.path("totalsByStatus"), "PENDING_RESPONSE")).isEqualTo(1L);
        assertThat(total(otherReport.path("totalsByStatus"), "REJECTED")).isZero();
        assertThat(total(otherReport.path("totalsByStatus"), "EXPIRED")).isZero();
        assertThat(otherReport.path("items").size()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/reports/assignments")
                        .with(webToken(conductor))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));

        mockMvc.perform(get("/api/v1/reports/assignments/export")
                        .with(webToken(admin))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .startsWith((byte) 'P', (byte) 'K'));

        // Kept to make explicit that the untouched assignment remains a real pending record.
        assertThat(assignmentRepository.findById(pending.id()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.PENDING_RESPONSE);
    }

    @Test
    void mobileCommandsUseOwnIdentityAndPreserveStateAndReplayBoundaries() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AssignmentCreated own = createMobileConfirmation(
                admin,
                driver,
                vehicle,
                now.plus(6, ChronoUnit.HOURS),
                now.plus(7, ChronoUnit.HOURS),
                now.plus(30, ChronoUnit.MINUTES)
        );
        UUID acceptEvent = UUID.randomUUID();
        String acceptPayload = commandPayload(acceptEvent, own.version(), now);
        mockMvc.perform(post("/api/v1/mobile/assignments/{assignmentId}/accept", own.id())
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        mockMvc.perform(post("/api/v1/mobile/assignments/{assignmentId}/accept", own.id())
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        assertThat(count("select count(*) from mobile_command_receipt where event_id = ?", acceptEvent)).isEqualTo(1);

        AppUser otherConductor = userRepository.save(user(organization, "conductor.other", UserRole.CONDUCTOR));
        Driver otherDriver = availableDriver(organization, otherConductor, group, "Otro conductor", "F34-OTHER");
        Vehicle otherVehicle = linkedVehicle(otherDriver, organization, "F34-OTH");
        AssignmentCreated sameTenantForeign = createMobileConfirmation(
                admin,
                otherDriver,
                otherVehicle,
                now.plus(9, ChronoUnit.HOURS),
                now.plus(10, ChronoUnit.HOURS),
                now.plus(35, ChronoUnit.MINUTES)
        );
        mockMvc.perform(get("/api/v1/mobile/assignments/{assignmentId}", sameTenantForeign.id())
                        .with(mobileToken(conductor, driver)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Ruta Fija Cruzada", "Cruzada", "America/Lima"
        ));
        AppUser crossAdmin = userRepository.save(user(otherOrganization, "admin.cross", UserRole.ADMIN));
        AppUser crossConductor = userRepository.save(user(otherOrganization, "conductor.cross", UserRole.CONDUCTOR));
        TransportGroup crossGroup = groupRepository.save(TransportGroup.active(otherOrganization, "Grupo cruzado", null));
        Driver crossDriver = availableDriver(otherOrganization, crossConductor, crossGroup, "Conductor cruzado", "CROSS-001");
        Vehicle crossVehicle = linkedVehicle(crossDriver, otherOrganization, "CRS-401");
        AssignmentCreated crossTenantForeign = createMobileConfirmation(
                crossAdmin,
                crossDriver,
                crossVehicle,
                now.plus(12, ChronoUnit.HOURS),
                now.plus(13, ChronoUnit.HOURS),
                now.plus(40, ChronoUnit.MINUTES)
        );
        mockMvc.perform(get("/api/v1/mobile/assignments/{assignmentId}", crossTenantForeign.id())
                        .with(mobileToken(conductor, driver)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        AssignmentCreated direct = createAdminDirect(
                admin,
                driver,
                vehicle,
                now.plus(15, ChronoUnit.HOURS),
                now.plus(16, ChronoUnit.HOURS)
        );
        UUID invalidEvent = UUID.randomUUID();
        String invalidPayload = commandPayload(invalidEvent, direct.version(), now);
        mockMvc.perform(post("/api/v1/mobile/assignments/{assignmentId}/accept", direct.id())
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        mockMvc.perform(post("/api/v1/mobile/assignments/{assignmentId}/accept", direct.id())
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        assertThat(count("select count(*) from mobile_command_receipt where event_id = ?", invalidEvent)).isEqualTo(1);
        assertThat(assignmentRepository.findById(direct.id()).orElseThrow().getStatus())
                .isEqualTo(AssignmentStatus.SCHEDULED);
    }

    @Test
    void currentLocationRequiresConsentStaysSingleAndDoesNotLeakCoordinates() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/me").with(webToken(conductor)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MOBILE_SESSION_REQUIRED"));
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(webToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload(Instant.now(), true, "-12.046374")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));

        Instant capturedAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload(capturedAt, true, "-12.046374")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_CONSENT_REQUIRED"));
        mockMvc.perform(put("/api/v1/mobile/location/consent")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationConsent").value(true));
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload(capturedAt, false, "-12.046374")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_PERMISSION_NOT_REPORTED"));
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload(capturedAt, true, "-12.046374")))
                .andExpect(status().isNoContent());
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(locationPayload(capturedAt, true, "-12.046375")))
                .andExpect(status().isNoContent());
        assertThat(count("select count(*) from driver_current_location where driver_id = ?", driver.getId())).isEqualTo(1);
        String auditMetadata = jdbcTemplate.queryForObject(
                "select coalesce(string_agg(metadata_json::text, ''), '') from audit_event where action = 'MOBILE_CURRENT_LOCATION_UPSERTED'",
                String.class
        );
        assertThat(auditMetadata).doesNotContain("latitude").doesNotContain("longitude").doesNotContain("accuracy");
        assertThat(jdbcTemplate.queryForObject("select to_regclass('public.driver_location_history')", String.class)).isNull();
        assertThat(jdbcTemplate.queryForObject("select to_regclass('public.location_history')", String.class)).isNull();

        mockMvc.perform(put("/api/v1/mobile/location/consent")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationConsent").value(false));
        assertThat(count("select count(*) from driver_current_location where driver_id = ?", driver.getId())).isZero();
    }

    private AssignmentCreated createMobileConfirmation(
            AppUser actor,
            Driver targetDriver,
            Vehicle targetVehicle,
            Instant scheduledAt,
            Instant scheduledEndAt,
            Instant responseDeadlineAt
    ) throws Exception {
        return createAssignment(
                actor,
                targetDriver,
                targetVehicle,
                scheduledAt,
                scheduledEndAt,
                "MOBILE_CONFIRMATION",
                responseDeadlineAt
        );
    }

    private AssignmentCreated createAdminDirect(
            AppUser actor,
            Driver targetDriver,
            Vehicle targetVehicle,
            Instant scheduledAt,
            Instant scheduledEndAt
    ) throws Exception {
        return createAssignment(actor, targetDriver, targetVehicle, scheduledAt, scheduledEndAt, "ADMIN_DIRECT", null);
    }

    private AssignmentCreated createAssignment(
            AppUser actor,
            Driver targetDriver,
            Vehicle targetVehicle,
            Instant scheduledAt,
            Instant scheduledEndAt,
            String responseMode,
            Instant responseDeadlineAt
    ) throws Exception {
        String deadlinePart = responseDeadlineAt == null
                ? ""
                : ",\n\"responseDeadlineAt\":\"%s\"".formatted(responseDeadlineAt);
        MvcResult result = mockMvc.perform(post("/api/v1/assignments")
                        .with(webToken(actor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId":"%s",
                                  "vehicleId":"%s",
                                  "originText":"Terminal Norte",
                                  "destinationText":"Terminal Sur",
                                  "scheduledAt":"%s",
                                  "scheduledEndAt":"%s",
                                  "responseMode":"%s"%s
                                }
                                """.formatted(
                                targetDriver.getId(),
                                targetVehicle.getId(),
                                scheduledAt,
                                scheduledEndAt,
                                responseMode,
                                deadlinePart
                        )))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json(result);
        return new AssignmentCreated(UUID.fromString(body.path("id").asText()), body.path("version").asLong());
    }

    private Driver availableDriver(
            Organization targetOrganization,
            AppUser targetConductor,
            TransportGroup targetGroup,
            String fullName,
            String documentNumber
    ) {
        Driver candidate = Driver.register(
                targetOrganization,
                targetConductor,
                targetGroup,
                fullName,
                "999000111",
                "DNI",
                documentNumber,
                "LIC-" + documentNumber
        );
        candidate.changeAdministrativeAvailability(DriverAvailabilityStatus.DISPONIBLE);
        return driverRepository.save(candidate);
    }

    private Vehicle linkedVehicle(Driver targetDriver, Organization targetOrganization, String plate) {
        Vehicle candidate = vehicleRepository.save(Vehicle.register(targetOrganization, plate, "Toyota", "Hiace", null, null));
        driverVehicleLinkRepository.save(DriverVehicleLink.active(targetDriver, candidate, true, Instant.now()));
        return candidate;
    }

    private AppUser user(Organization targetOrganization, String localPart, UserRole role) {
        return AppUser.organizationUser(
                targetOrganization,
                localPart + "@rutafija.test",
                passwordEncoder.encode("Integration-Password-2026"),
                role.name() + " F3.4",
                role
        );
    }

    private RequestPostProcessor webToken(AppUser user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", user.getOrganizationId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name()));
    }

    private RequestPostProcessor mobileToken(AppUser user, Driver targetDriver) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_CONDUCTOR"))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", user.getOrganizationId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", UserRole.CONDUCTOR.name())
                        .claim("sessionChannel", "MOBILE")
                        .claim("driverId", targetDriver.getId().toString()));
    }

    private String commandPayload(UUID eventId, long version, Instant occurredAt) {
        return """
                {"clientEventId":"%s","version":%d,"occurredAt":"%s"}
                """.formatted(eventId, version, occurredAt);
    }

    private String rejectPayload(UUID eventId, long version, Instant occurredAt, String reason) {
        return """
                {"clientEventId":"%s","version":%d,"occurredAt":"%s","reason":"%s"}
                """.formatted(eventId, version, occurredAt, reason);
    }

    private String locationPayload(Instant capturedAt, boolean permissionGranted, String latitude) {
        return """
                {"latitude":%s,"longitude":-77.042793,"accuracyM":8.2,"capturedAt":"%s","permissionGranted":%s}
                """.formatted(latitude, capturedAt, permissionGranted);
    }

    private long total(JsonNode totals, String status) {
        for (JsonNode total : totals) {
            if (status.equals(total.path("status").asText())) {
                return total.path("total").asLong();
            }
        }
        throw new AssertionError("El reporte no publicó el estado " + status);
    }

    private Integer count(String sql, Object... values) {
        return jdbcTemplate.queryForObject(sql, Integer.class, values);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private record AssignmentCreated(UUID id, long version) {
    }
}
