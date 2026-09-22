package pe.rutafija.identity.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.rutafija.audit.domain.AuditEvent;
import pe.rutafija.audit.infrastructure.AuditEventRepository;
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

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class MobileAuthFlowIT extends NativePostgresIntegrationTest {

    private static final String DRIVER_PASSWORD = "Mobile-Driver-Password-2026";
    private static final String ADMIN_PASSWORD = "Mobile-Admin-Password-2026";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtDecoder jwtDecoder;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    TransportGroupRepository transportGroupRepository;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    AuditEventRepository auditEventRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private Organization organization;
    private AppUser driverUser;
    private AppUser adminUser;
    private AppUser unlinkedDriverUser;
    private Driver driver;

    @BeforeEach
    void prepareMobileUsers() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Organización móvil " + suffix,
                "Org móvil",
                "America/Lima"
        ));
        TransportGroup group = transportGroupRepository.save(TransportGroup.active(
                organization,
                "Grupo móvil " + suffix,
                "Grupo de integración móvil"
        ));
        driverUser = userRepository.save(AppUser.organizationUser(
                organization,
                "driver." + suffix + "@rutafija.test",
                passwordEncoder.encode(DRIVER_PASSWORD),
                "Conductora móvil",
                UserRole.CONDUCTOR
        ));
        adminUser = userRepository.save(AppUser.organizationUser(
                organization,
                "admin." + suffix + "@rutafija.test",
                passwordEncoder.encode(ADMIN_PASSWORD),
                "Administradora móvil",
                UserRole.ADMIN
        ));
        unlinkedDriverUser = userRepository.save(AppUser.organizationUser(
                organization,
                "unlinked." + suffix + "@rutafija.test",
                passwordEncoder.encode(DRIVER_PASSWORD),
                "Conductor sin vínculo",
                UserRole.CONDUCTOR
        ));
        driver = driverRepository.saveAndFlush(Driver.register(
                organization,
                driverUser,
                group,
                "Conductora móvil",
                "999111222",
                "DNI",
                "MOBILE-" + suffix,
                "LIC-" + suffix
        ));
    }

    @Test
    void linkedDriverGetsNativeSessionWithoutBrowserCookieAndWithBoundClaims() throws Exception {
        MvcResult login = mobileLogin("mobile-login-correlation");
        JsonNode body = json(login);
        String accessToken = body.path("accessToken").asText();

        assertThat(login.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(body.path("refreshToken").asText()).startsWith("m1.");
        assertThat(body.path("refreshExpiresIn").asLong()).isPositive();
        assertThat(body.at("/user/id").asText()).isEqualTo(driverUser.getId().toString());
        assertThat(body.at("/user/driverId").asText()).isEqualTo(driver.getId().toString());
        assertThat(body.at("/user/organizationId").asText()).isEqualTo(organization.getId().toString());
        assertThat(body.at("/user/role").asText()).isEqualTo("CONDUCTOR");
        assertThat(body.at("/user/email").isMissingNode()).isTrue();

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(driverUser.getId().toString());
        assertThat(jwt.getClaimAsString("organizationId")).isEqualTo(organization.getId().toString());
        assertThat(jwt.getClaimAsString("driverId")).isEqualTo(driver.getId().toString());
        assertThat(jwt.getClaimAsString("sessionChannel")).isEqualTo("MOBILE");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("CONDUCTOR");

        AuditEvent auditEvent = auditEventRepository.findAll().stream()
                .filter(event -> driverUser.getId().equals(event.getUserId()))
                .filter(event -> "MOBILE_LOGIN_SUCCESS".equals(event.getAction()))
                .findFirst()
                .orElseThrow();
        assertThat(auditEvent.getCorrelationId()).isEqualTo("mobile-login-correlation");
        assertThat(auditEvent.getMetadata().toString()).doesNotContain(body.path("refreshToken").asText());
    }

    @Test
    void rejectsAnAdminOrAnUnlinkedDriverWithUniformFunctionalErrors() throws Exception {
        mobileLoginFailure(adminUser.getEmail(), ADMIN_PASSWORD, "MOBILE_USER_NOT_DRIVER", "mobile-admin-denied");
        mobileLoginFailure(unlinkedDriverUser.getEmail(), DRIVER_PASSWORD, "MOBILE_USER_NOT_DRIVER", "mobile-unlinked-denied");

        mockMvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "mobile-invalid-password")
                        .content("""
                                {"email":"%s","password":"not-the-password"}
                                """.formatted(driverUser.getEmail())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.path").value("/api/v1/mobile/auth/login"))
                .andExpect(jsonPath("$.correlationId").value("mobile-invalid-password"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    @Test
    void refreshRotatesNativeTokenAndReuseRevokesItsFamily() throws Exception {
        String firstRefresh = json(mobileLogin("mobile-refresh-login")).path("refreshToken").asText();

        MvcResult refresh = mobileRefresh(firstRefresh, "mobile-refresh-success");
        String successor = json(refresh).path("refreshToken").asText();
        assertThat(successor).startsWith("m1.").isNotEqualTo(firstRefresh);
        assertThat(refresh.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();

        mobileRefreshFailure(firstRefresh, "AUTH_REFRESH_REUSE_DETECTED", "mobile-refresh-reuse");
        mobileRefreshFailure(successor, "AUTH_TOKEN_INVALID", "mobile-refresh-family-revoked");
    }

    @Test
    void refreshChannelsCannotBeCrossedOrDamageTheOriginalSession() throws Exception {
        MvcResult webLogin = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(adminUser.getEmail(), ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String webRefresh = webLogin.getResponse().getCookie("rf_refresh").getValue();

        mobileRefreshFailure(webRefresh, "AUTH_TOKEN_INVALID", "mobile-web-token");
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(webLogin.getResponse().getCookie("rf_refresh")))
                .andExpect(status().isOk());

        String mobileRefresh = json(mobileLogin("mobile-cross-login")).path("refreshToken").asText();
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new jakarta.servlet.http.Cookie("rf_refresh", mobileRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mobileRefresh(mobileRefresh, "mobile-remains-valid");
    }

    @Test
    void logoutRevokesMobileFamilyWithoutTouchingBrowserCookies() throws Exception {
        String refresh = json(mobileLogin("mobile-logout-login")).path("refreshToken").asText();

        mockMvc.perform(post("/api/v1/mobile/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", "mobile-logout")
                        .content(refreshBody(refresh)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string("X-Correlation-ID", "mobile-logout"));

        mobileRefreshFailure(refresh, "AUTH_TOKEN_INVALID", "mobile-after-logout");
    }

    @Test
    void inactiveDriverCannotRefreshAndItsMobileFamilyIsRevoked() throws Exception {
        String refresh = json(mobileLogin("mobile-before-driver-deactivate")).path("refreshToken").asText();
        driver.deactivate();
        driverRepository.saveAndFlush(driver);

        mobileRefreshForbidden(refresh, "DRIVER_INACTIVE", "mobile-driver-inactive");
        driver.activate();
        driverRepository.saveAndFlush(driver);
        mobileRefreshFailure(refresh, "AUTH_TOKEN_INVALID", "mobile-family-after-driver-inactive");
    }

    @Test
    void concurrentNativeRefreshesSerializeAndRevokeOnReuse() throws Exception {
        String refresh = json(mobileLogin("mobile-concurrent-login")).path("refreshToken").asText();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MvcResult> first = executor.submit(() -> concurrentRefresh(refresh, ready, start));
            Future<MvcResult> second = executor.submit(() -> concurrentRefresh(refresh, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().map(result -> result.getResponse().getStatus()).toList())
                    .containsExactlyInAnyOrder(200, 401);
            MvcResult successful = results.stream()
                    .filter(result -> result.getResponse().getStatus() == 200)
                    .findFirst()
                    .orElseThrow();
            String successor = json(successful).path("refreshToken").asText();
            mobileRefreshFailure(successor, "AUTH_TOKEN_INVALID", "mobile-concurrent-family-revoked");
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private MvcResult mobileLogin(String correlationId) throws Exception {
        return mockMvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(driverUser.getEmail(), DRIVER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Correlation-ID", correlationId))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn();
    }

    private void mobileLoginFailure(String email, String password, String code, String correlationId) throws Exception {
        mockMvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value("/api/v1/mobile/auth/login"))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private MvcResult mobileRefresh(String refreshToken, String correlationId) throws Exception {
        return mockMvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string("X-Correlation-ID", correlationId))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andReturn();
    }

    private void mobileRefreshFailure(String refreshToken, String code, String correlationId) throws Exception {
        mockMvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value("/api/v1/mobile/auth/refresh"))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private void mobileRefreshForbidden(String refreshToken, String code, String correlationId) throws Exception {
        mockMvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Correlation-ID", correlationId)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.path").value("/api/v1/mobile/auth/refresh"))
                .andExpect(jsonPath("$.correlationId").value(correlationId))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    private MvcResult concurrentRefresh(String refreshToken, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("La prueba concurrente no recibió señal de inicio");
        }
        return mockMvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andReturn();
    }

    private String refreshBody(String refreshToken) {
        return """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
