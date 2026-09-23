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
import pe.rutafija.operation.domain.Announcement;
import pe.rutafija.operation.domain.AnnouncementAudienceType;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.infrastructure.AnnouncementRepository;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.application.MobileOperationMaintenanceService;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MobileOperationFlowIT extends NativePostgresIntegrationTest {

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
    AnnouncementRepository announcementRepository;

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
    void prepareMobileData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Ruta Fija Movil " + suffix, "Movil " + suffix, "America/Lima"
        ));
        admin = userRepository.save(user(organization, "admin." + suffix, UserRole.ADMIN));
        conductor = userRepository.save(user(organization, "conductor." + suffix, UserRole.CONDUCTOR));
        group = groupRepository.save(TransportGroup.active(organization, "Grupo movil " + suffix, "Pruebas F3.3"));
        driver = Driver.register(
                organization, conductor, group, "Conductor movil", "999000111", "DNI", "MOV-" + suffix, "LIC-001"
        );
        driver.changeAdministrativeAvailability(pe.rutafija.fleet.domain.DriverAvailabilityStatus.DISPONIBLE);
        driver = driverRepository.save(driver);
        vehicle = vehicleRepository.save(Vehicle.register(organization, "MOV" + suffix.substring(0, 5), "Toyota", "Hiace", null, null));
        driverVehicleLinkRepository.save(DriverVehicleLink.active(driver, vehicle, true, Instant.now()));
    }

    @Test
    void authenticatedDriverConfirmsOwnAssignmentExactlyOnceAndCompletesIt() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Instant scheduledAt = now.plus(90, ChronoUnit.MINUTES);
        Instant scheduledEndAt = scheduledAt.plus(60, ChronoUnit.MINUTES);
        Instant deadline = now.plus(30, ChronoUnit.MINUTES);
        String payload = """
                {
                  "driverId":"%s",
                  "vehicleId":"%s",
                  "originText":"Terminal Norte",
                  "destinationText":"Terminal Sur",
                  "scheduledAt":"%s",
                  "scheduledEndAt":"%s",
                  "notes":"Confirmacion real",
                  "responseMode":"MOBILE_CONFIRMATION",
                  "responseDeadlineAt":"%s"
                }
                """.formatted(driver.getId(), vehicle.getId(), scheduledAt, scheduledEndAt, deadline);

        MvcResult created = mockMvc.perform(post("/api/v1/assignments")
                        .with(webToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_RESPONSE"))
                .andExpect(jsonPath("$.responseMode").value("MOBILE_CONFIRMATION"))
                .andExpect(jsonPath("$.acceptedAt").doesNotExist())
                .andExpect(jsonPath("$.reservedAt").doesNotExist())
                .andReturn();
        String assignmentId = json(created).path("id").asText();
        long version = json(created).path("version").asLong();
        UUID acceptEvent = UUID.randomUUID();
        Instant occurredAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        String acceptPayload = commandPayload(acceptEvent, version, occurredAt);

        MvcResult accepted = mockMvc.perform(post("/api/v1/mobile/assignments/{id}/accept", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty())
                .andExpect(jsonPath("$.reservedAt").isNotEmpty())
                .andReturn();
        long acceptedVersion = json(accepted).path("version").asLong();

        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/accept", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"));
        assertThat(count("select count(*) from mobile_command_receipt where command_type = 'ASSIGNMENT_ACCEPT'"))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select availability_status from driver where id = ?", String.class, driver.getId()
        )).isEqualTo("RESERVADO");
        assertThat(jdbcTemplate.queryForObject(
                "select status from vehicle where id = ?", String.class, vehicle.getId()
        )).isEqualTo("DISPONIBLE");

        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/reject", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload(acceptEvent, acceptedVersion, occurredAt)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MOBILE_EVENT_CONFLICT"));

        UUID startEvent = UUID.randomUUID();
        Instant startOccurredAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        MvcResult started = mockMvc.perform(post("/api/v1/mobile/assignments/{id}/start", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload(startEvent, acceptedVersion, startOccurredAt)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("EN_SERVICIO"))
                .andReturn();
        long startedVersion = json(started).path("version").asLong();

        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/start", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload(startEvent, acceptedVersion, startOccurredAt)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("EN_SERVICIO"));

        UUID completeEvent = UUID.randomUUID();
        Instant completeOccurredAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/complete", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload(completeEvent, startedVersion, completeOccurredAt)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/complete", assignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload(completeEvent, startedVersion, completeOccurredAt)))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
        assertThat(jdbcTemplate.queryForObject(
                "select availability_status from driver where id = ?", String.class, driver.getId()
        )).isEqualTo("DISPONIBLE");
        assertThat(jdbcTemplate.queryForObject(
                "select status from vehicle where id = ?", String.class, vehicle.getId()
        )).isEqualTo("DISPONIBLE");

        String pendingRejectionPayload = """
                {
                  "driverId":"%s",
                  "vehicleId":"%s",
                  "originText":"Terminal Este",
                  "destinationText":"Terminal Oeste",
                  "scheduledAt":"%s",
                  "scheduledEndAt":"%s",
                  "responseMode":"MOBILE_CONFIRMATION",
                  "responseDeadlineAt":"%s"
                }
                """.formatted(
                driver.getId(), vehicle.getId(), now.plus(4, ChronoUnit.HOURS), now.plus(5, ChronoUnit.HOURS),
                now.plus(30, ChronoUnit.MINUTES)
        );
        MvcResult rejectionCreated = mockMvc.perform(post("/api/v1/assignments")
                        .with(webToken(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pendingRejectionPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_RESPONSE"))
                .andReturn();
        String rejectionAssignmentId = json(rejectionCreated).path("id").asText();
        long rejectionVersion = json(rejectionCreated).path("version").asLong();
        UUID rejectEvent = UUID.randomUUID();
        Instant rejectOccurredAt = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        String rejectPayload = rejectCommandPayload(rejectEvent, rejectionVersion, rejectOccurredAt, "No puedo cubrir este turno");

        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/reject", rejectionAssignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "false"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/reject", rejectionAssignmentId)
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectPayload))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Idempotent-Replay", "true"))
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(count("select count(*) from mobile_command_receipt where command_type = 'ASSIGNMENT_REJECT'"))
                .isEqualTo(1);
    }

    @Test
    void mobileScopeUsesServerIdentityAndCurrentLocationNeverBecomesHistory() throws Exception {
        mockMvc.perform(get("/api/v1/mobile/me").with(webToken(conductor)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("MOBILE_SESSION_REQUIRED"));

        mockMvc.perform(get("/api/v1/mobile/me").with(mobileToken(conductor, driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverId").value(driver.getId().toString()))
                .andExpect(jsonPath("$.primaryVehicle.id").value(vehicle.getId().toString()));

        mockMvc.perform(put("/api/v1/mobile/location/consent")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"consent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locationConsent").value(true));

        Instant capturedAt = Instant.now().minusSeconds(2).truncatedTo(ChronoUnit.MILLIS);
        String firstPoint = """
                {"latitude":-12.046374,"longitude":-77.042793,"accuracyM":8.2,
                 "capturedAt":"%s","permissionGranted":true}
                """.formatted(capturedAt);
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPoint))
                .andExpect(status().isNoContent());
        String secondPoint = firstPoint.replace("-12.046374", "-12.046375");
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(secondPoint))
                .andExpect(status().isNoContent());
        assertThat(count("select count(*) from driver_current_location where driver_id = '" + driver.getId() + "'"))
                .isEqualTo(1);
        String auditMetadata = jdbcTemplate.queryForObject(
                "select coalesce(string_agg(metadata_json::text, ''), '') from audit_event where action = 'MOBILE_CURRENT_LOCATION_UPSERTED'",
                String.class
        );
        assertThat(auditMetadata).doesNotContain("latitude").doesNotContain("longitude").doesNotContain("accuracy");

        mockMvc.perform(put("/api/v1/mobile/availability")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DESCANSO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DESCANSO"));
        assertThat(count("select count(*) from driver_current_location where driver_id = '" + driver.getId() + "'"))
                .isZero();
        mockMvc.perform(put("/api/v1/mobile/location/current")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(firstPoint))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOCATION_STATE_NOT_ALLOWED"));
    }

    @Test
    void mobileIncidentsAndAnnouncementsRemainOwnAndAudienceScoped() throws Exception {
        Announcement announcement = announcementRepository.save(Announcement.publish(
                organization,
                admin,
                "Cambio de turno",
                "Revise su programacion actualizada.",
                AnnouncementAudienceType.GROUP,
                group.getId(),
                true
        ));

        mockMvc.perform(get("/api/v1/mobile/announcements").with(mobileToken(conductor, driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(announcement.getId().toString()))
                .andExpect(jsonPath("$.items[0].requireReadAck").value(true));
        mockMvc.perform(post("/api/v1/mobile/announcements/{id}/read", announcement.getId())
                        .with(mobileToken(conductor, driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mockMvc.perform(post("/api/v1/mobile/announcements/{id}/read", announcement.getId())
                        .with(mobileToken(conductor, driver)))
                .andExpect(status().isOk());
        assertThat(count("select count(*) from announcement_receipt where announcement_id = '" + announcement.getId() + "'"))
                .isEqualTo(1);
        assertThat(count("select count(*) from audit_event where action = 'MOBILE_ANNOUNCEMENT_READ'"))
                .isEqualTo(1);

        MvcResult incident = mockMvc.perform(post("/api/v1/mobile/incidents")
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"RETRASO","description":"Retraso informado desde la app","occurredAt":"%s"}
                                """.formatted(Instant.now().minusSeconds(1))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MOBILE_APP"))
                .andReturn();
        String incidentId = json(incident).path("id").asText();
        mockMvc.perform(get("/api/v1/mobile/incidents/{id}", incidentId).with(mobileToken(conductor, driver)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(incidentId));

        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Otra org", "Otra", "America/Lima"
        ));
        AppUser otherConductor = userRepository.save(user(otherOrganization, "other." + UUID.randomUUID(), UserRole.CONDUCTOR));
        TransportGroup otherGroup = groupRepository.save(TransportGroup.active(otherOrganization, "Grupo externo", null));
        Driver otherDriver = Driver.register(otherOrganization, otherConductor, otherGroup, "Otro conductor", null, "DNI", "OTRO-1", null);
        otherDriver.changeAdministrativeAvailability(pe.rutafija.fleet.domain.DriverAvailabilityStatus.DISPONIBLE);
        otherDriver = driverRepository.save(otherDriver);
        Vehicle otherVehicle = vehicleRepository.save(Vehicle.register(otherOrganization, "OTH-001", null, null, null, null));
        driverVehicleLinkRepository.save(DriverVehicleLink.active(otherDriver, otherVehicle, true, Instant.now()));
        AppUser otherAdmin = userRepository.save(user(otherOrganization, "admin.other." + UUID.randomUUID(), UserRole.ADMIN));
        pe.rutafija.operation.domain.Assignment otherAssignment = pe.rutafija.operation.domain.Assignment.schedule(
                otherOrganization, otherDriver, otherVehicle, otherAdmin,
                "Origen", "Destino", Instant.now().plus(2, ChronoUnit.HOURS), Instant.now().plus(3, ChronoUnit.HOURS), null, null
        );
        UUID foreignAssignmentId = jdbcTemplate.queryForObject(
                "insert into assignment (id, organization_id, driver_id, vehicle_id, created_by, status, response_mode, origin_text, destination_text, scheduled_at, scheduled_end_at, version, created_at, updated_at) values (?, ?, ?, ?, ?, 'SCHEDULED', 'ADMIN_DIRECT', ?, ?, ?, ?, 0, now(), now()) returning id",
                UUID.class,
                otherAssignment.getId(), otherOrganization.getId(), otherDriver.getId(), otherVehicle.getId(), otherAdmin.getId(),
                "Origen", "Destino", java.sql.Timestamp.from(otherAssignment.getScheduledAt()), java.sql.Timestamp.from(otherAssignment.getScheduledEndAt())
        );
        mockMvc.perform(get("/api/v1/mobile/assignments/{id}", foreignAssignmentId).with(mobileToken(conductor, driver)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void serverExpiresPendingMobileResponseWithoutChangingFleetAvailability() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Assignment pending = assignmentRepository.saveAndFlush(Assignment.requestMobileConfirmation(
                organization, driver, vehicle, admin,
                "Terminal Norte", "Terminal Sur",
                now.plus(2, ChronoUnit.HOURS), now.plus(3, ChronoUnit.HOURS),
                "Vencimiento administrado por servidor", null, now.minusSeconds(1)
        ));

        mobileOperationMaintenanceService.expireDueMobileResponsesAndLocations();

        Assignment expired = assignmentRepository.findById(pending.getId()).orElseThrow();
        assertThat(expired.getStatus().name()).isEqualTo("EXPIRED");
        assertThat(expired.getExpiredAt()).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "select availability_status from driver where id = ?", String.class, driver.getId()
        )).isEqualTo("DISPONIBLE");
        assertThat(jdbcTemplate.queryForObject(
                "select status from vehicle where id = ?", String.class, vehicle.getId()
        )).isEqualTo("DISPONIBLE");

        mockMvc.perform(post("/api/v1/mobile/assignments/{id}/accept", pending.getId())
                        .with(mobileToken(conductor, driver))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(commandPayload(UUID.randomUUID(), expired.getVersion(), Instant.now().minusSeconds(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_RESPONSE_EXPIRED"));
        assertThat(count("select count(*) from mobile_command_receipt where assignment_id = '" + pending.getId() + "'"))
                .isEqualTo(1);
    }

    private AppUser user(Organization targetOrganization, String localPart, UserRole role) {
        return AppUser.organizationUser(
                targetOrganization,
                localPart + "@rutafija.test",
                passwordEncoder.encode("Integration-Password-2026"),
                role.name() + " de prueba",
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
                """.formatted(eventId, version, occurredAt.truncatedTo(ChronoUnit.MILLIS));
    }

    private String rejectCommandPayload(UUID eventId, long version, Instant occurredAt, String reason) {
        return """
                {"clientEventId":"%s","version":%d,"occurredAt":"%s","reason":"%s"}
                """.formatted(eventId, version, occurredAt.truncatedTo(ChronoUnit.MILLIS), reason);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private Integer count(String query) {
        return jdbcTemplate.queryForObject(query, Integer.class);
    }
}
