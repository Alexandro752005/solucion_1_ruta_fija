package pe.rutafija.terms.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
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
import pe.rutafija.terms.application.AdminTermsService;
import pe.rutafija.terms.domain.AdminTermsContentHash;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class AdminTermsFlowIT extends NativePostgresIntegrationTest {

    private static final String SYNTHETIC_VERSION = "SYNTHETIC-TEST-ONLY-0001";
    private static final String SYNTHETIC_CONTENT = """
            SYNTHETIC TEST DOCUMENT ONLY.
            NOT APPROVED. NOT LEGAL TERMS. NOT FOR REAL ORGANIZATION ACCEPTANCE.
            This content exists only to exercise the versioned terms API in automated tests.
            """;

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

    private AppUser admin;
    private AppUser secondAdmin;
    private AppUser conductor;
    private UUID documentId;
    private String contentSha256;

    @BeforeEach
    void createTenantUsersAndSyntheticDocument() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Organization organization = organizationRepository.save(Organization.active(
                "Terms Test Organization " + suffix,
                "Terms Test " + suffix,
                "America/Lima"
        ));
        admin = saveVerifiedUser(
                organization,
                "terms.admin." + suffix + "@rutafija.test",
                "Terms Test ADMIN " + suffix,
                UserRole.ADMIN
        );
        conductor = saveVerifiedUser(
                organization,
                "terms.driver." + suffix + "@rutafija.test",
                "Terms Test CONDUCTOR " + suffix,
                UserRole.CONDUCTOR
        );

        Organization secondOrganization = organizationRepository.save(Organization.active(
                "Second Terms Test Organization " + suffix,
                "Second Terms Test " + suffix,
                "America/Lima"
        ));
        secondAdmin = saveVerifiedUser(
                secondOrganization,
                "terms.other-admin." + suffix + "@rutafija.test",
                "Second Terms Test ADMIN " + suffix,
                UserRole.ADMIN
        );

        documentId = UUID.randomUUID();
        contentSha256 = AdminTermsContentHash.sha256(SYNTHETIC_CONTENT);
        jdbcTemplate.update("""
                insert into admin_terms_document
                    (id, document_key, version, content, content_sha256, synthetic, created_at)
                values (?, ?, ?, ?, ?, true, ?)
                """,
                documentId,
                AdminTermsService.ADMIN_TERMS_DOCUMENT_KEY,
                SYNTHETIC_VERSION,
                SYNTHETIC_CONTENT,
                contentSha256,
                Timestamp.from(Instant.now())
        );
    }

    @Test
    void currentDocumentIsClearlySyntheticAndHasAComputedSha256() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/admin/terms/current")
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.documentKey").value("ADMIN_TERMS"))
                .andExpect(jsonPath("$.version").value(SYNTHETIC_VERSION))
                .andExpect(jsonPath("$.contentSha256").value(contentSha256))
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.accepted").value(false))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(body.path("content").asText()).startsWith("SYNTHETIC TEST DOCUMENT ONLY.");
        assertThat(AdminTermsContentHash.sha256(body.path("content").asText())).isEqualTo(contentSha256);
        assertThat(body.path("acceptedAt").isMissingNode() || body.path("acceptedAt").isNull()).isTrue();
        assertThat(acceptanceCount()).isZero();
    }

    @Test
    void concurrentAcceptancesByTheSameAdminRemainIdempotentWithoutLockUpgradeDeadlock() throws Exception {
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> acceptOnce = () -> {
                release.await();
                var result = accept(admin, SYNTHETIC_VERSION, contentSha256, true)
                        .andExpect(status().isOk()).andReturn();
                return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                        .path("acceptanceId").asText();
            };
            var first = executor.submit(acceptOnce);
            var second = executor.submit(acceptOnce);
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(acceptanceCount()).isEqualTo(1);
    }

    @Test
    void differentAdminsCanAcceptForTheSameOrganizationWithoutUpgradingItsLock() throws Exception {
        Organization organization = organizationRepository.findById(admin.getOrganizationId()).orElseThrow();
        AppUser colleague = saveVerifiedUser(organization,
                "terms.colleague." + UUID.randomUUID() + "@rutafija.test",
                "Synthetic colleague ADMIN", UserRole.ADMIN);
        var release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<String> firstAcceptance = () -> {
                release.await();
                var result = accept(admin, SYNTHETIC_VERSION, contentSha256, true)
                        .andExpect(status().isOk()).andReturn();
                return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                        .path("acceptanceId").asText();
            };
            java.util.concurrent.Callable<String> secondAcceptance = () -> {
                release.await();
                var result = accept(colleague, SYNTHETIC_VERSION, contentSha256, true)
                        .andExpect(status().isOk()).andReturn();
                return objectMapper.readTree(result.getResponse().getContentAsByteArray())
                        .path("acceptanceId").asText();
            };
            var first = executor.submit(firstAcceptance);
            var second = executor.submit(secondAcceptance);
            release.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS)).isNotEqualTo(second.get(15, TimeUnit.SECONDS));
        }
        assertThat(acceptanceCount()).isEqualTo(2);
    }

    @Test
    void explicitAcceptancePersistsActorTenantVersionHashAndServerTimeOnce() throws Exception {
        Instant before = Instant.now().minusSeconds(1);
        MvcResult first = accept(admin, SYNTHETIC_VERSION, contentSha256, true)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.adminUserId").value(admin.getId().toString()))
                .andExpect(jsonPath("$.organizationId").value(admin.getOrganizationId().toString()))
                .andExpect(jsonPath("$.documentVersion").value(SYNTHETIC_VERSION))
                .andExpect(jsonPath("$.contentSha256").value(contentSha256))
                .andExpect(jsonPath("$.synthetic").value(true))
                .andReturn();
        Instant after = Instant.now().plusSeconds(1);

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsByteArray());
        String acceptanceId = firstBody.path("acceptanceId").asText();
        Instant acceptedAt = Instant.parse(firstBody.path("acceptedAt").asText());
        assertThat(acceptanceId).isNotBlank();
        assertThat(acceptedAt).isBetween(before, after);
        assertThat(acceptanceCount()).isEqualTo(1);

        UUID storedId = UUID.fromString(acceptanceId);
        assertThat(jdbcTemplate.queryForObject(
                "select admin_user_id from admin_terms_acceptance where id = ?",
                UUID.class,
                storedId
        )).isEqualTo(admin.getId());
        assertThat(jdbcTemplate.queryForObject(
                "select organization_id from admin_terms_acceptance where id = ?",
                UUID.class,
                storedId
        )).isEqualTo(admin.getOrganizationId());
        assertThat(jdbcTemplate.queryForObject(
                "select document_id from admin_terms_acceptance where id = ?",
                UUID.class,
                storedId
        )).isEqualTo(documentId);
        assertThat(jdbcTemplate.queryForObject(
                "select document_version from admin_terms_acceptance where id = ?",
                String.class,
                storedId
        )).isEqualTo(SYNTHETIC_VERSION);
        assertThat(jdbcTemplate.queryForObject(
                "select content_sha256 from admin_terms_acceptance where id = ?",
                String.class,
                storedId
        )).isEqualTo(contentSha256);

        MvcResult retry = accept(admin, SYNTHETIC_VERSION, contentSha256, true)
                .andExpect(status().isOk())
                .andReturn();
        assertThat(objectMapper.readTree(retry.getResponse().getContentAsByteArray())
                .path("acceptanceId").asText()).isEqualTo(acceptanceId);
        assertThat(acceptanceCount()).isEqualTo(1);
        assertThat(auditCount()).isEqualTo(1);

        mockMvc.perform(get("/api/v1/admin/terms/current")
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(true))
                .andExpect(jsonPath("$.acceptedAt").isNotEmpty());
    }

    @Test
    void falseAcceptanceAndMissingConfirmationDoNotCreateRecords() throws Exception {
        accept(admin, SYNTHETIC_VERSION, contentSha256, false)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/admin/terms/acceptances")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":"%s","contentSha256":"%s"}
                                """.formatted(SYNTHETIC_VERSION, contentSha256)))
                .andExpect(status().isBadRequest());

        assertThat(acceptanceCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void staleVersionOrHashIsRejectedWithoutAcceptance() throws Exception {
        accept(admin, "SYNTHETIC-TEST-ONLY-OLD", contentSha256, true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));

        accept(admin, SYNTHETIC_VERSION, "0".repeat(64), true)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_VERSION_CONFLICT"));

        assertThat(acceptanceCount()).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void acceptanceIsScopedToAuthenticatedAdminAndOrganization() throws Exception {
        accept(admin, SYNTHETIC_VERSION, contentSha256, true)
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/terms/current")
                        .with(authenticatedAs(secondAdmin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(false))
                .andExpect(jsonPath("$.acceptedAt").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/terms/acceptances")
                        .with(authenticatedAs(admin, secondAdmin.getOrganizationId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptanceBody(SYNTHETIC_VERSION, contentSha256, true)))
                .andExpect(status().isUnauthorized());

        assertThat(acceptanceCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select organization_id from admin_terms_acceptance",
                UUID.class
        )).isEqualTo(admin.getOrganizationId());
    }

    @Test
    void onlyAdminCanUseTheAcceptanceEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/terms/current")
                        .with(authenticatedAs(conductor)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/terms/acceptances")
                        .with(authenticatedAs(conductor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptanceBody(SYNTHETIC_VERSION, contentSha256, true)))
                .andExpect(status().isForbidden());

        assertThat(acceptanceCount()).isZero();
    }

    @Test
    void publishedDocumentAndAcceptanceRowsCannotBeUpdated() throws Exception {
        MvcResult accepted = accept(admin, SYNTHETIC_VERSION, contentSha256, true)
                .andExpect(status().isOk())
                .andReturn();
        UUID acceptanceId = UUID.fromString(objectMapper.readTree(
                accepted.getResponse().getContentAsByteArray()
        ).path("acceptanceId").asText());

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update admin_terms_document set content = ? where id = ?",
                "ALTERED SYNTHETIC CONTENT",
                documentId
        )).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update admin_terms_acceptance set content_sha256 = ? where id = ?",
                "0".repeat(64),
                acceptanceId
        )).isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject(
                "select content from admin_terms_document where id = ?",
                String.class,
                documentId
        )).isEqualTo(SYNTHETIC_CONTENT);
        assertThat(jdbcTemplate.queryForObject(
                "select content_sha256 from admin_terms_acceptance where id = ?",
                String.class,
                acceptanceId
        )).isEqualTo(contentSha256);
    }

    private AppUser saveVerifiedUser(Organization organization, String email, String name, UserRole role) {
        AppUser user = AppUser.organizationUser(
                organization,
                email,
                passwordEncoder.encode("Synthetic-Terms-Test-Only-Password"),
                name,
                role
        );
        user.verifyEmail(Instant.now());
        return userRepository.save(user);
    }

    private org.springframework.test.web.servlet.ResultActions accept(
            AppUser user,
            String version,
            String sha256,
            boolean explicitlyAccepted
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/terms/acceptances")
                .with(authenticatedAs(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content(acceptanceBody(version, sha256, explicitlyAccepted)));
    }

    private String acceptanceBody(String version, String sha256, boolean explicitlyAccepted) {
        return """
                {"version":"%s","contentSha256":"%s","accepted":%s}
                """.formatted(version, sha256, explicitlyAccepted);
    }

    private RequestPostProcessor authenticatedAs(AppUser user) {
        return authenticatedAs(user, user.getOrganizationId());
    }

    private RequestPostProcessor authenticatedAs(AppUser user, UUID organizationIdClaim) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", organizationIdClaim.toString())
                        .claim("email", user.getEmail())
                        .claim("role", user.getRole().name())
                        .claim("sessionVersion", user.getSessionVersion()));
    }

    private int acceptanceCount() {
        return jdbcTemplate.queryForObject("select count(*) from admin_terms_acceptance", Integer.class);
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject(
                "select count(*) from audit_event where action = 'ADMIN_TERMS_ACCEPTED'",
                Integer.class
        );
    }
}

