package pe.rutafija.operation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import pe.rutafija.support.ApprovedConductorFixture;
import pe.rutafija.support.CurrentNativeOperationFixture;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "logging.level.org.hibernate.SQL=INFO"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OperationFlowIT extends NativePostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired DriverRepository drivers;
    @Autowired TransportGroupRepository groups;
    @Autowired VehicleRepository vehicles;
    @Autowired ApplicationContext applicationContext;

    private CurrentNativeOperationFixture current;
    private final Map<String, Driver> fixtureDrivers = new HashMap<>();
    private final Map<String, AppUser> fixtureAccounts = new HashMap<>();

    private Organization organizationA;
    private AppUser administratorA;
    private AppUser coordinatorA;
    private AppUser administratorB;

    @BeforeEach
    void prepareUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organizationA = organizationRepository.save(Organization.active(
                "Operación A " + suffix,
                "Operación A",
                "America/Lima"
        ));
        Organization organizationB = organizationRepository.save(Organization.active(
                "Operación B " + suffix,
                "Operación B",
                "America/Lima"
        ));
        administratorA = userRepository.save(organizationUser(organizationA, "admin.a." + suffix, UserRole.ADMIN));
        coordinatorA = userRepository.save(organizationUser(organizationA, "admin.operations.a." + suffix, UserRole.ADMIN));
        administratorB = userRepository.save(organizationUser(organizationB, "admin.b." + suffix, UserRole.ADMIN));
        current = new CurrentNativeOperationFixture(mockMvc, objectMapper, jdbcTemplate, applicationContext);
        fixtureDrivers.clear();
        fixtureAccounts.clear();
    }

    @Test
    void adminRunsTheWebOperationalFlowAndReportsPersistedFacts() throws Exception {
        String groupId = json(mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grupo Operación","description":"Grupo de prueba Fase 4"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        String driverId = historicalDriver(groupId, "Conductora Operativa", "OPERACION-001", null);

        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plate":"OPR-320","brand":"Toyota","model":"Hiace"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isConflict());
        qualify(driverId, vehicleId);

        mockMvc.perform(post("/api/v1/drivers/{driverId}/availability", driverId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"status\":\"DISPONIBLE\"" + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_INVALID_TRANSITION"));
        declareAvailable(driverId);

        Instant scheduledAt = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Instant scheduledEndAt = scheduledAt.plus(90, ChronoUnit.MINUTES);
        String assignmentPayload = assignmentPayload(driverId, vehicleId, scheduledAt, scheduledEndAt,
                "Programación de coordinación");

        MvcResult created = mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .header("Idempotency-Key", "operation-flow-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING_RESPONSE"))
                .andExpect(jsonPath("$.reservedAt").doesNotExist())
                .andReturn();
        String assignmentId = json(created).path("id").asText();
        long version = json(created).path("version").asLong();

        mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .header("Idempotency-Key", "operation-flow-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId));

        mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload.replace("Programación de coordinación", "Otra solicitud del mismo intervalo")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_SCHEDULE_CONFLICT"));

        // The same database exclusion must reject a parallel historical row.
        // Assert SQLSTATE, so an unrelated NOT NULL/CHECK failure cannot satisfy this test.
        assertThatThrownBy(() -> jdbcTemplate.update("""
                        insert into assignment (
                            id, organization_id, driver_id, vehicle_id, created_by, status,
                            origin_text, destination_text, scheduled_at, scheduled_end_at,
                            version, created_at, updated_at
                        ) values (?, ?, ?, ?, ?, 'SCHEDULED', ?, ?, ?, ?, 0, now(), now())
                        """,
                UUID.randomUUID(),
                organizationA.getId(),
                UUID.fromString(driverId),
                UUID.fromString(vehicleId),
                coordinatorA.getId(),
                "Terminal paralelo",
                "Terminal paralelo destino",
                Timestamp.from(scheduledAt.plus(5, ChronoUnit.MINUTES)),
                Timestamp.from(scheduledEndAt.plus(5, ChronoUnit.MINUTES))
        )).isInstanceOf(DataIntegrityViolationException.class).satisfies(failure -> {
            var cause = ((DataIntegrityViolationException) failure).getMostSpecificCause();
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23P01");
        });

        mockMvc.perform(post("/api/v1/assignments/{assignmentId}/reserve", assignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        MvcResult reserved = conductorCommand(driverId, assignmentId, "accept", version, false);
        assertThat(json(reserved).path("status").asText()).isEqualTo("SCHEDULED");
        assertThat(json(reserved).path("reservedAt").asText()).isNotBlank();
        version = json(reserved).path("version").asLong();

        mockMvc.perform(get("/api/v1/drivers/{driverId}", driverId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.availabilityStatus").value("RESERVADO"));
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}", vehicleId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIBLE"));

        mockMvc.perform(post("/api/v1/assignments/{assignmentId}/start", assignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        mockMvc.perform(put("/api/v1/mobile/location/consent").with(mobile(driverId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"consent\":true}"))
                .andExpect(status().isOk());
        MvcResult started = conductorCommand(driverId, assignmentId, "start", version, true);
        assertThat(json(started).path("status").asText()).isEqualTo("EN_SERVICIO");
        version = json(started).path("version").asLong();

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}", vehicleId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_SERVICIO"));

        mockMvc.perform(post("/api/v1/assignments/{assignmentId}/complete", assignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"version\":" + version + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        MvcResult completed = conductorCommand(driverId, assignmentId, "complete", version, false);
        assertThat(json(completed).path("status").asText()).isEqualTo("COMPLETED");

        mockMvc.perform(get("/api/v1/drivers/{driverId}", driverId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.availabilityStatus").value("DISPONIBLE"));
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}", vehicleId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIBLE"));

        MvcResult incident = mockMvc.perform(post("/api/v1/incidents")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "driverId":"%s",
                                  "assignmentId":"%s",
                                  "category":"RETRASO",
                                  "description":"Retraso informado por coordinación",
                                  "severity":"LOW",
                                  "operationKey":"%s"
                                }
                                """.formatted(driverId, assignmentId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andReturn();
        String incidentId = json(incident).path("id").asText();
        long incidentVersion = json(incident).path("version").asLong();

        LocalDate from = LocalDate.now(ZoneId.of("America/Lima")).minusDays(1);
        LocalDate to = from.plusDays(2);
        mockMvc.perform(get("/api/v1/reports/assignments")
                        .with(authenticatedAs(administratorA))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(assignmentId));
        mockMvc.perform(get("/api/v1/reports/incidents")
                        .with(authenticatedAs(administratorA))
                        .param("from", from.toString())
                        .param("to", to.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(incidentId));
        mockMvc.perform(get("/api/v1/reports/availability").with(authenticatedAs(administratorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openIncidents").value(1));

        MvcResult assignmentsXlsx = mockMvc.perform(get("/api/v1/reports/assignments/export")
                        .with(authenticatedAs(administratorA))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("format", "xlsx"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(assignmentsXlsx.getResponse().getContentAsByteArray())
                .startsWith((byte) 'P', (byte) 'K');

        MvcResult incidentsPdf = mockMvc.perform(get("/api/v1/reports/incidents/export")
                        .with(authenticatedAs(administratorA))
                        .param("from", from.toString())
                        .param("to", to.toString())
                        .param("format", "pdf"))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(incidentsPdf.getResponse().getContentAsByteArray())
                .startsWith((byte) '%', (byte) 'P', (byte) 'D', (byte) 'F');

        MvcResult auditListing = mockMvc.perform(get("/api/v1/audit-events")
                        .with(authenticatedAs(administratorA))
                        .param("entityType", "ASSIGNMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").isNotEmpty())
                .andReturn();
        String auditEventId = json(auditListing).path("items").get(0).path("id").asText();
        mockMvc.perform(get("/api/v1/audit-events/{auditEventId}", auditEventId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(auditEventId));
        mockMvc.perform(get("/api/v1/audit-events").with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/audit-events/{auditEventId}", auditEventId)
                        .with(authenticatedAs(administratorB)))
                .andExpect(status().isNotFound());

        var responsibility = current.post(coordinatorA, "/api/v1/incidents/" + incidentId + "/responsibility",
                Map.of("operationKey", UUID.randomUUID(), "expectedVersion", incidentVersion,
                        "adminId", coordinatorA.getId(), "reason", "Atención sintética de coordinación"), 200);
        incidentVersion = responsibility.path("version").asLong();
        mockMvc.perform(patch("/api/v1/incidents/{incidentId}/follow-up", incidentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"note":"Caso cerrado por coordinación","resolve":true,"operationKey":"%s"}
                                """.formatted(incidentVersion, UUID.randomUUID())))
                .andExpect(status().isBadRequest());
        var closedIncident = current.post(coordinatorA, "/api/v1/incidents/" + incidentId + "/closure",
                Map.of("operationKey", UUID.randomUUID(), "expectedVersion", incidentVersion,
                        "diagnosis", "Retraso sintético revisado", "actions", "Coordinación revisó la programación",
                        "result", "Caso cerrado por coordinación", "evidenceRequired", false,
                        "evidenceMode", "NOT_REQUIRED", "evidenceExplanation", "Ensayo sintético sin evidencia externa"), 200);
        assertThat(closedIncident.path("status").asText()).isEqualTo("RESOLVED");

        String announcementId = json(mockMvc.perform(post("/api/v1/announcements")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Cambio de turno",
                                  "body":"Revise la programación actualizada.",
                                  "audienceType":"GROUP",
                                  "audienceId":"%s",
                                  "requireReadAck":false,
                                  "operationKey":"%s"
                                }
                                """.formatted(groupId, UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requireReadAck").value(false))
                .andReturn()).path("id").asText();

        mockMvc.perform(get("/api/v1/announcements").with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(announcementId));

        mockMvc.perform(post("/api/v1/operations/stream-ticket").with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/assignments/{assignmentId}", assignmentId)
                        .with(authenticatedAs(administratorB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(json(completed).path("version").asLong()).isGreaterThan(json(started).path("version").asLong());
    }

    @Test
    void cancellingAnUnreservedAssignmentDoesNotReleaseAnotherReservation() throws Exception {
        String groupId = json(mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grupo de reservas","description":"Prueba de cancelación"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        String driverId = historicalDriver(groupId, "Conductor de reservas", "RESERVA-001", null);
        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"plate\":\"RSV-401\",\"brand\":\"Toyota\",\"model\":\"Hiace\"}"))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isConflict());
        qualify(driverId, vehicleId);
        mockMvc.perform(post("/api/v1/drivers/{driverId}/availability", driverId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISPONIBLE\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AVAILABILITY_INVALID_TRANSITION"));
        declareAvailable(driverId);

        Instant firstStart = Instant.now().plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant firstEnd = firstStart.plus(60, ChronoUnit.MINUTES);
        MvcResult firstCreated = mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload(driverId, vehicleId, firstStart, firstEnd, "Reserva A")))
                .andExpect(status().isCreated())
                .andReturn();
        String firstAssignmentId = json(firstCreated).path("id").asText();
        mockMvc.perform(post("/api/v1/assignments/{assignmentId}/reserve", firstAssignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_INVALID_TRANSITION"));
        MvcResult firstReserved = conductorCommand(driverId, firstAssignmentId, "accept",
                json(firstCreated).path("version").asLong(), false);

        Instant secondStart = firstEnd.plus(30, ChronoUnit.MINUTES);
        Instant secondEnd = secondStart.plus(60, ChronoUnit.MINUTES);
        MvcResult secondCreated = mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload(driverId, vehicleId, secondStart, secondEnd, "Reserva B")))
                .andExpect(status().isCreated())
                .andReturn();
        String secondAssignmentId = json(secondCreated).path("id").asText();
        long secondVersion = json(secondCreated).path("version").asLong();

        mockMvc.perform(post("/api/v1/assignments/{assignmentId}/cancel", secondAssignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + secondVersion + ",\"reason\":\"Reprogramación B\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/drivers/{driverId}", driverId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.availabilityStatus").value("RESERVADO"));
        assertThat(json(firstReserved).path("reservedAt").asText()).isNotBlank();
    }

    @Test
    void roleIsImmutableAndAStaleSessionIsRejectedAfterDeactivation() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser staleCoordinator = userRepository.save(organizationUser(
                organizationA,
                "stale." + suffix,
                UserRole.ADMIN
        ));
        RequestPostProcessor staleCoordinatorToken = authenticatedAs(staleCoordinator);

        // DC-02B makes role immutable; retain stale-session coverage via revocation.
        assertThatThrownBy(() -> staleCoordinator.updateDetails(
                staleCoordinator.getEmail(),
                "Rol sustituido",
                staleCoordinator.getPhone(),
                UserRole.CONDUCTOR
        )).isInstanceOf(IllegalArgumentException.class);
        staleCoordinator.deactivate();
        userRepository.save(staleCoordinator);

        mockMvc.perform(get("/api/v1/assignments").with(staleCoordinatorToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void activeScheduledAssignmentBlocksDeactivationOfItsLinkedDriverUser() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser conductorUser = userRepository.save(organizationUser(
                organizationA,
                "driver." + suffix,
                UserRole.CONDUCTOR
        ));
        String groupId = json(mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grupo conductor activo","description":"Prueba de desactivación"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        String driverId = historicalDriver(groupId, "Conductor asignado", "ACTIVO-001", conductorUser);
        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"plate\":\"ACT-501\",\"brand\":\"Toyota\",\"model\":\"Hiace\"}"))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isConflict());
        qualify(driverId, vehicleId);
        declareAvailable(driverId);

        Instant scheduledAt = Instant.now().plus(8, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        MvcResult protectedOffer = mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload(driverId, vehicleId, scheduledAt, scheduledAt.plus(60, ChronoUnit.MINUTES), "Servicio protegido")))
                .andExpect(status().isCreated()).andReturn();
        conductorCommand(driverId, json(protectedOffer).path("id").asText(), "accept",
                json(protectedOffer).path("version").asLong(), false);

        mockMvc.perform(post("/api/v1/users/{userId}/deactivate", conductorUser.getId())
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    private String historicalDriver(String groupId, String name, String document, AppUser existingUser) {
        // Explicit synthetic historical approval, solely for operational regression fixtures.
        AppUser conductor = existingUser != null ? existingUser : userRepository.save(organizationUser(
                organizationA, "fixture." + UUID.randomUUID(), UserRole.CONDUCTOR));
        Driver driver = drivers.save(Driver.register(organizationA, conductor,
                groups.findById(UUID.fromString(groupId)).orElseThrow(), name, null, "DNI", document, null));
        ApprovedConductorFixture.recordHistoricalApproval(jdbcTemplate, administratorA, conductor, driver);
        fixtureDrivers.put(driver.getId().toString(), driver);
        fixtureAccounts.put(driver.getId().toString(), conductor);
        return driver.getId().toString();
    }

    private String assignmentPayload(String driverId, String vehicleId, Instant start, Instant end, String notes) throws Exception {
        var body = current.offerFields(coordinatorA);
        body.put("driverId", driverId);
        body.put("vehicleId", vehicleId);
        body.put("originText", "Terminal Norte");
        body.put("destinationText", "Terminal Sur");
        body.put("scheduledAt", start);
        body.put("scheduledEndAt", end);
        body.put("notes", notes);
        return objectMapper.writeValueAsString(body);
    }

    private void qualify(String driverId, String vehicleId) throws Exception {
        current.qualify(administratorA, fixtureAccounts.get(driverId), fixtureDrivers.get(driverId),
                vehicles.findById(UUID.fromString(vehicleId)).orElseThrow());
    }

    private RequestPostProcessor mobile(String driverId) {
        return current.mobile(fixtureAccounts.get(driverId), fixtureDrivers.get(driverId));
    }

    private void declareAvailable(String driverId) throws Exception {
        mockMvc.perform(put("/api/v1/mobile/availability").with(mobile(driverId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIBLE"));
    }

    private MvcResult conductorCommand(String driverId, String assignmentId, String action,
            long version, boolean gps) throws Exception {
        var body = new LinkedHashMap<String, Object>();
        body.put("clientEventId", UUID.randomUUID());
        body.put("version", version);
        body.put("occurredAt", Instant.now().minusSeconds(1));
        if (gps) body.put("gps", Map.of("permissionGranted", true, "locationServiceEnabled", true, "foreground", true));
        return mockMvc.perform(post("/api/v1/mobile/assignments/{id}/{action}", assignmentId, action)
                        .with(mobile(driverId)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(body)))
                .andExpect(status().isOk()).andReturn();
    }

    private AppUser organizationUser(Organization organization, String localPart, UserRole role) {
        AppUser user = AppUser.organizationUser(
                organization,
                localPart + "@rutafija.test",
                passwordEncoder.encode("Integration-Password-2026"),
                role.name() + " de integración",
                role
        );
        user.verifyEmail(Instant.now());
        return user;
    }

    private RequestPostProcessor authenticatedAs(AppUser user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", user.getOrganizationId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name())
                        .claim("sessionVersion", user.getSessionVersion()));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
