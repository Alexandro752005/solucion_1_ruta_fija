package pe.rutafija.terms.api;

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
import org.springframework.test.web.servlet.MockMvc;
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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "app.security.admin-terms-gate.enabled=true",
        "debug=false",
        "logging.level.root=INFO",
        "logging.level.org.springframework=INFO",
        "logging.level.org.hibernate.SQL=INFO"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminTermsGateFlowIT extends NativePostgresIntegrationTest {

    private static final String VERSION = "SYNTHETIC-TEST-ONLY-GATE-0001";
    private static final String CONTENT = "SYNTHETIC TEST DOCUMENT ONLY. NOT APPROVED. GATE TEST ONLY.";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private AppUser admin;
    private AppUser otherAdmin;
    private String contentHash;

    @BeforeEach
    void seedSyntheticDocumentAndAdmins() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Organization organization = organizationRepository.save(Organization.active(
                "Terms Gate Test " + suffix,
                "Terms Gate " + suffix,
                "America/Lima"
        ));
        admin = saveVerifiedAdmin(organization, "terms-gate." + suffix + "@rutafija.test");

        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Other Terms Gate Test " + suffix,
                "Other Terms Gate " + suffix,
                "America/Lima"
        ));
        otherAdmin = saveVerifiedAdmin(otherOrganization, "terms-gate-other." + suffix + "@rutafija.test");

        contentHash = AdminTermsContentHash.sha256(CONTENT);
        jdbcTemplate.update("""
                insert into admin_terms_document
                    (id, document_key, version, content, content_sha256, synthetic, created_at)
                values (?, ?, ?, ?, ?, true, ?)
                """,
                UUID.randomUUID(),
                AdminTermsService.ADMIN_TERMS_DOCUMENT_KEY,
                VERSION,
                CONTENT,
                contentHash,
                Timestamp.from(Instant.now())
        );
    }

    @Test
    void adminCannotUseOperationalApiUntilExplicitlyAcceptingCurrentDocument() throws Exception {
        mockMvc.perform(get("/api/v1/invitations").with(authenticatedAs(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/me").with(authenticatedAs(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/terms/current").with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.accepted").value(false));

        mockMvc.perform(post("/api/v1/admin/terms/acceptances")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptanceBody()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synthetic").value(true))
                .andExpect(jsonPath("$.documentVersion").value(VERSION))
                .andExpect(jsonPath("$.contentSha256").value(contentHash));

        mockMvc.perform(get("/api/v1/invitations").with(authenticatedAs(admin)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/invitations").with(authenticatedAs(otherAdmin)))
                .andExpect(status().isForbidden());
    }

    @Test
    void newerCurrentDocumentInvalidatesPriorAcceptance() throws Exception {
        mockMvc.perform(post("/api/v1/admin/terms/acceptances")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(acceptanceBody()))
                .andExpect(status().isOk());

        String nextVersion = VERSION + "-2";
        String nextContent = CONTENT + " UPDATED VERSION.";
        jdbcTemplate.update("""
                insert into admin_terms_document
                    (id, document_key, version, content, content_sha256, synthetic, created_at)
                values (?, ?, ?, ?, ?, true, ?)
                """,
                UUID.randomUUID(),
                AdminTermsService.ADMIN_TERMS_DOCUMENT_KEY,
                nextVersion,
                nextContent,
                AdminTermsContentHash.sha256(nextContent),
                Timestamp.from(Instant.now().plusSeconds(1))
        );

        mockMvc.perform(get("/api/v1/invitations").with(authenticatedAs(admin)))
                .andExpect(status().isForbidden());
    }

    private AppUser saveVerifiedAdmin(Organization organization, String email) {
        AppUser user = AppUser.organizationUser(
                organization,
                email,
                passwordEncoder.encode("Synthetic-Gate-Test-Only-Password"),
                "Synthetic Gate Test ADMIN",
                UserRole.ADMIN
        );
        user.verifyEmail(Instant.now());
        return userRepository.save(user);
    }

    private RequestPostProcessor authenticatedAs(AppUser user) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                .jwt(token -> token
                        .subject(user.getId().toString())
                        .claim("organizationId", user.getOrganizationId().toString())
                        .claim("email", user.getEmail())
                        .claim("role", UserRole.ADMIN.name())
                        .claim("sessionVersion", user.getSessionVersion()));
    }

    private String acceptanceBody() {
        return """
                {"version":"%s","contentSha256":"%s","accepted":true}
                """.formatted(VERSION, contentHash);
    }
}
