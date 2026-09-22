package pe.rutafija.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
class AuthFlowIT extends NativePostgresIntegrationTest {

    private static final String EMAIL = "admin.integration@rutafija.test";
    private static final String PASSWORD = "Integration-Password-2026";

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

    private AppUser testUser;

    @BeforeEach
    void prepareTenantAndUser() {
        testUser = userRepository.findByEmailIgnoreCase(EMAIL).orElseGet(() -> {
            Organization organization = organizationRepository.save(Organization.active(
                    "Integration Test Organization",
                    "Integration Test",
                    "America/Lima"
            ));
            return userRepository.save(AppUser.organizationUser(
                    organization,
                    EMAIL,
                    passwordEncoder.encode(PASSWORD),
                    "Integration Admin",
                    UserRole.ADMIN
            ));
        });
    }

    @Test
    void loginReturnsShortAccessTokenAndKeepsRefreshTokenOnlyInHttpOnlyCookie() throws Exception {
        MvcResult login = login();
        String accessToken = json(login).path("accessToken").asText();

        assertThat(accessToken).isNotBlank();
        assertThat(login.getResponse().getContentAsString()).doesNotContain("refreshToken");

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header("X-Correlation-ID", "integration-me"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Correlation-ID", "integration-me"))
                .andExpect(jsonPath("$.id").value(testUser.getId().toString()))
                .andExpect(jsonPath("$.organizationId").value(testUser.getOrganizationId().toString()))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void invalidPasswordUsesNormalizedErrorContract() throws Exception {
        Integer auditEventsBefore = countAuditEvents("LOGIN_FAILED");

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin.integration@rutafija.test","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/login"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());

        assertThat(countAuditEvents("LOGIN_FAILED")).isEqualTo(auditEventsBefore + 1);
    }

    @Test
    void cookieEndpointsRejectAnUntrustedBrowserOrigin() throws Exception {
        Cookie refreshCookie = login().getResponse().getCookie("rf_refresh");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(refreshCookie)
                        .header(HttpHeaders.ORIGIN, "https://attacker.invalid"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CROSS_ORIGIN_REQUEST_DENIED"))
                .andExpect(jsonPath("$.path").value("/api/v1/auth/refresh"));
    }

    @Test
    void commonHttpFailuresKeepTheNormalizedContract() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("MEDIA_TYPE_NOT_SUPPORTED"));

        mockMvc.perform(get("/api/v1/missing-resource")
                        .with(jwt().jwt(jwt -> jwt
                                .subject(testUser.getId().toString())
                                .claim("organizationId", testUser.getOrganizationId().toString())
                                .claim("email", EMAIL)
                                .claim("role", "ADMIN"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void refreshRotatesCookieAndReuseRevokesTheWholeTokenFamily() throws Exception {
        Cookie firstCookie = login().getResponse().getCookie("rf_refresh");
        assertThat(firstCookie).isNotNull();

        MvcResult refresh = mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
                .andExpect(status().isOk())
                .andReturn();
        Cookie successorCookie = refresh.getResponse().getCookie("rf_refresh");
        assertThat(successorCookie).isNotNull();
        assertThat(successorCookie.getValue()).isNotEqualTo(firstCookie.getValue());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(firstCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_REFRESH_REUSE_DETECTED"));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(successorCookie))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void logoutRevokesSessionAndClearsCookie() throws Exception {
        MvcResult login = login();
        Cookie refreshCookie = login.getResponse().getCookie("rf_refresh");
        mockMvc.perform(post("/api/v1/auth/logout")
                        .cookie(refreshCookie))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("rf_refresh", 0));

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRejectsAUserIdCombinedWithAnotherTenantClaim() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .with(jwt().jwt(jwt -> jwt
                                        .subject(testUser.getId().toString())
                                        .claim("organizationId", UUID.randomUUID().toString())
                                        .claim("email", EMAIL)
                                        .claim("role", "ADMIN"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
    }

    @Test
    void openApiReflectsTheAdminRoleContractAndRetiresCoordinatorEndpoints() throws Exception {
        JsonNode document = json(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(document.at("/components/schemas/UserUpdateRequest/properties/role/enum").toString())
                .isEqualTo("[\"SUPER_ADMIN\",\"ADMIN\",\"CONDUCTOR\"]");
        assertThat(document.at("/paths/~1api~1v1~1groups~1{groupId}~1coordinators").isMissingNode())
                .isTrue();
        assertThat(document.path("info").path("description").asText()).contains("ADMIN");
    }

    private MvcResult login() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "admin.integration@rutafija.test",
                                  "password": "Integration-Password-2026"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(cookie().httpOnly("rf_refresh", true))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
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
