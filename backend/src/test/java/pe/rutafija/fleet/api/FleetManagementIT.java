package pe.rutafija.fleet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
class FleetManagementIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ruta_fija_fleet_test")
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
    private Organization organizationB;
    private AppUser administratorA;
    private AppUser coordinatorA;
    private AppUser administratorB;

    @BeforeEach
    void prepareUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organizationA = organizationRepository.save(Organization.active(
                "Organización A " + suffix,
                "Org A",
                "America/Lima"
        ));
        organizationB = organizationRepository.save(Organization.active(
                "Organización B " + suffix,
                "Org B",
                "America/Lima"
        ));
        administratorA = userRepository.save(organizationUser(
                organizationA,
                "admin.a." + suffix + "@rutafija.test",
                UserRole.ADMINISTRADOR
        ));
        coordinatorA = userRepository.save(organizationUser(
                organizationA,
                "coord.a." + suffix + "@rutafija.test",
                UserRole.COORDINADOR
        ));
        administratorB = userRepository.save(organizationUser(
                organizationB,
                "admin.b." + suffix + "@rutafija.test",
                UserRole.ADMINISTRADOR
        ));
    }

    @Test
    void administratorCreatesResourcesAndCoordinatorOnlySeesAssignedGroup() throws Exception {
        String groupId = json(mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grupo Operativo Norte","description":"Grupo para integración"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()).path("id").asText();

        mockMvc.perform(post("/api/v1/groups/{groupId}/coordinators", groupId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s"}
                                """.formatted(coordinatorA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.coordinators[0].userId").value(coordinatorA.getId().toString()));

        String conductorUserId = json(mockMvc.perform(post("/api/v1/users")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"conductor.integration@rutafija.test",
                                  "password":"Conductor-Password-2026",
                                  "fullName":"Conductor de Integración",
                                  "phone":"999111222",
                                  "role":"CONDUCTOR"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("CONDUCTOR"))
                .andReturn()).path("id").asText();

        String driverId = json(mockMvc.perform(post("/api/v1/drivers")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId":"%s",
                                  "groupId":"%s",
                                  "fullName":"Conductor de Integración",
                                  "phone":"999111222",
                                  "documentType":"DNI",
                                  "documentNumber":"DOC-INTEGRACION-001",
                                  "licenseNumber":"LIC-INTEGRACION-001"
                                }
                                """.formatted(conductorUserId, groupId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andReturn()).path("id").asText();

        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "plate":"RF1-2026",
                                  "brand":"Toyota",
                                  "model":"Hiace",
                                  "year":2024,
                                  "color":"Blanco"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DISPONIBLE"))
                .andReturn()).path("id").asText();

        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles[0].vehicleId").value(vehicleId));

        mockMvc.perform(post("/api/v1/drivers/{driverId}/vehicles/{vehicleId}/primary", driverId, vehicleId)
                        .with(authenticatedAs(administratorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.vehicles[0].primary").value(true));

        mockMvc.perform(get("/api/v1/groups")
                        .with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(groupId));

        mockMvc.perform(get("/api/v1/drivers")
                        .with(authenticatedAs(coordinatorA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(driverId));

        mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(coordinatorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plate":"NO-ROLE","brand":"Marca","model":"Modelo"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN_ROLE"));

        mockMvc.perform(get("/api/v1/drivers/{driverId}", driverId)
                        .with(authenticatedAs(administratorB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(countAuditEvents("GROUP_CREATED")).isGreaterThanOrEqualTo(1);
        assertThat(countAuditEvents("DRIVER_VEHICLE_LINKED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void resourceConflictsAndOperationalVehicleStateAreRejected() throws Exception {
        String groupId = json(mockMvc.perform(post("/api/v1/groups")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Grupo Duplicados","description":"Control de unicidad"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        String driverPayload = """
                {
                  "groupId":"%s",
                  "fullName":"Conductor Duplicado",
                  "documentType":"DNI",
                  "documentNumber":"DUPLICADO-2026"
                }
                """.formatted(groupId);

        mockMvc.perform(post("/api/v1/drivers")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(driverPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/drivers")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(driverPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));

        String vehicleId = json(mockMvc.perform(post("/api/v1/vehicles")
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"plate":"RF2-2026","brand":"Marca","model":"Modelo"}
                                """))
                .andExpect(status().isCreated())
                .andReturn()).path("id").asText();

        mockMvc.perform(post("/api/v1/vehicles/{vehicleId}/status", vehicleId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"EN_SERVICIO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VEHICLE_NOT_ELIGIBLE"));

        mockMvc.perform(patch("/api/v1/groups/{groupId}", groupId)
                        .with(authenticatedAs(administratorA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name":"Grupo Duplicados",
                                  "description":"Control de unicidad",
                                  "active":false
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_CONFLICT"));
    }

    private AppUser organizationUser(Organization organization, String email, UserRole role) {
        return AppUser.organizationUser(
                organization,
                email,
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

    private Integer countAuditEvents(String action) {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_event where action = ?",
                Integer.class,
                action
        );
    }
}
