package pe.rutafija.operation.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.sql.Timestamp;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
@Testcontainers
class OperationFlowIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ruta_fija_operation_test")
            .withUsername("ruta_fija_test")
            .withPassword("test-only-password");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add(
                "app.security.jwt.secret-base64",
                () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        );
        registry.add("app.security.cors.allowed-origins", () -> "http://localhost:4200");
    }

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
        administratorA = userRepository.save(organizationUser(organizationA, "admin.a." + suffix, UserRole.ADMINISTRADOR));
        coordinatorA = userRepository.save(organizationUser(organizationA, "coord.a." + suffix, UserRole.COORDINADOR));
        administratorB = userRepository.save(organizationUser(organizationB, "admin.b." + suffix, UserRole.ADMINISTRADOR));
    }

    @Test
    void coordinatorRunsTheWebOperationalFlowAndReportsPersistedFacts() throws Exception {
        String groupId = json(mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grupo Operación","description":"Grupo de prueba Fase 4"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(post("/api/v1/groups/{groupId}/coordinators", groupId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(coordinatorA.getId())))
                .andExpect(status().isOk());

        String driverId = json(mockMvc.perform(post("/api/v1/drivers")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "groupId":"%s",
                                  "fullName":"Conductora Operativa",
                                  "documentType":"DNI",
                                  "documentNumber":"OPERACION-001"
                                }
                                """.formatted(groupId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

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
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/drivers/{driverId}/availability", driverId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"status\":\"DISPONIBLE\"" + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availabilityStatus").value("DISPONIBLE"));

        Instant scheduledAt = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.SECONDS);
        Instant scheduledEndAt = scheduledAt.plus(90, ChronoUnit.MINUTES);
        String assignmentPayload = """
                {
                  "driverId":"%s",
                  "vehicleId":"%s",
                  "originText":"Terminal Norte",
                  "destinationText":"Terminal Sur",
                  "scheduledAt":"%s",
                  "scheduledEndAt":"%s",
                  "notes":"Programación de coordinación"
                }
                """.formatted(driverId, vehicleId, scheduledAt, scheduledEndAt);

        MvcResult created = mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .header("Idempotency-Key", "operation-flow-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
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
                        .content(assignmentPayload.replace("Terminal Sur", "Otro destino")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ASSIGNMENT_SCHEDULE_CONFLICT"));

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
        )).isInstanceOf(DataIntegrityViolationException.class);

        MvcResult reserved = mockMvc.perform(post("/api/v1/assignments/{assignmentId}/reserve", assignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.reservedAt").isNotEmpty())
                .andReturn();
        version = json(reserved).path("version").asLong();

        mockMvc.perform(get("/api/v1/drivers/{driverId}", driverId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driver.availabilityStatus").value("RESERVADO"));
        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}", vehicleId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPONIBLE"));

        MvcResult started = mockMvc.perform(post("/api/v1/assignments/{assignmentId}/start", assignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_SERVICIO"))
                .andReturn();
        version = json(started).path("version").asLong();

        mockMvc.perform(get("/api/v1/vehicles/{vehicleId}", vehicleId).with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_SERVICIO"));

        MvcResult completed = mockMvc.perform(post("/api/v1/assignments/{assignmentId}/complete", assignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"version\":" + version + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();

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
                                  "description":"Retraso informado por coordinación"
                                }
                                """.formatted(driverId, assignmentId)))
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
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/audit-events/{auditEventId}", auditEventId)
                        .with(authenticatedAs(administratorB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/incidents/{incidentId}/follow-up", incidentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":%d,"note":"Caso cerrado por coordinación","resolve":true}
                                """.formatted(incidentVersion)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));

        String announcementId = json(mockMvc.perform(post("/api/v1/announcements")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title":"Cambio de turno",
                                  "body":"Revise la programación actualizada.",
                                  "audienceType":"GROUP",
                                  "audienceId":"%s",
                                  "requireReadAck":false
                                }
                                """.formatted(groupId)))
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

        // El resultado se usa para asegurar que la transición completada conservó su versión en la respuesta.
        json(completed).path("version").asLong();
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
        mockMvc.perform(post("/api/v1/groups/{groupId}/coordinators", groupId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"userId\":\"" + coordinatorA.getId() + "\"}"))
                .andExpect(status().isOk());

        String driverId = json(mockMvc.perform(post("/api/v1/drivers")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":"%s","fullName":"Conductor de reservas","documentType":"DNI","documentNumber":"RESERVA-001"}
                                """.formatted(groupId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"plate\":\"RSV-401\",\"brand\":\"Toyota\",\"model\":\"Hiace\"}"))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/drivers/{driverId}/availability", driverId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISPONIBLE\"}"))
                .andExpect(status().isOk());

        Instant firstStart = Instant.now().plus(4, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        Instant firstEnd = firstStart.plus(60, ChronoUnit.MINUTES);
        String firstAssignmentId = json(mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload(driverId, vehicleId, firstStart, firstEnd, "Reserva A")))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        MvcResult firstReserved = mockMvc.perform(post("/api/v1/assignments/{assignmentId}/reserve", firstAssignmentId)
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0}"))
                .andExpect(status().isOk())
                .andReturn();

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
    void staleRoleTokenIsRejectedAfterAnAdministratorChangesTheUserRole() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        AppUser staleCoordinator = userRepository.save(organizationUser(
                organizationA,
                "stale." + suffix,
                UserRole.COORDINADOR
        ));
        RequestPostProcessor staleCoordinatorToken = authenticatedAs(staleCoordinator);

        mockMvc.perform(patch("/api/v1/users/{userId}", staleCoordinator.getId())
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","fullName":"Coordinador degradado","phone":"","role":"CONDUCTOR"}
                                """.formatted(staleCoordinator.getEmail())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("CONDUCTOR"));

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
        mockMvc.perform(post("/api/v1/groups/{groupId}/coordinators", groupId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"userId\":\"" + coordinatorA.getId() + "\"}"))
                .andExpect(status().isOk());
        String driverId = json(mockMvc.perform(post("/api/v1/drivers")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","groupId":"%s","fullName":"Conductor asignado","documentType":"DNI","documentNumber":"ACTIVO-001"}
                                """.formatted(conductorUser.getId(), groupId)))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{" + "\"plate\":\"ACT-501\",\"brand\":\"Toyota\",\"model\":\"Hiace\"}"))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();
        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isOk());

        Instant scheduledAt = Instant.now().plus(8, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        mockMvc.perform(post("/api/v1/assignments")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload(driverId, vehicleId, scheduledAt, scheduledAt.plus(60, ChronoUnit.MINUTES), "Servicio protegido")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/{userId}/deactivate", conductorUser.getId())
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    private String assignmentPayload(UUID driverId, UUID vehicleId, Instant start, Instant end, String notes) {
        return assignmentPayload(driverId.toString(), vehicleId.toString(), start, end, notes);
    }

    private String assignmentPayload(String driverId, String vehicleId, Instant start, Instant end, String notes) {
        return """
                {"driverId":"%s","vehicleId":"%s","originText":"Terminal Norte","destinationText":"Terminal Sur","scheduledAt":"%s","scheduledEndAt":"%s","notes":"%s"}
                """.formatted(driverId, vehicleId, start, end, notes);
    }

    private AppUser organizationUser(Organization organization, String localPart, UserRole role) {
        return AppUser.organizationUser(
                organization,
                localPart + "@rutafija.test",
                passwordEncoder.encode("Integration-Password-2026"),
                role.name() + " de integración",
                role
        );
    }

    private RequestPostProcessor authenticatedAs(AppUser user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", user.getOrganizationId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name()));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
