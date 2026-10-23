package pe.rutafija.onboarding.api;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "debug=false", "logging.level.root=INFO", "logging.level.org.springframework=INFO",
        "logging.level.org.hibernate.SQL=INFO", "app.security.login-rate-limit.enabled=true",
        "app.security.login-rate-limit.account-max-attempts=5"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ConductorOnboardingAccessIT extends NativePostgresIntegrationTest {

    private static final String PASSWORD = "Synthetic-Applicant-Password-2026";
    private static final String TOKEN_HEADER = "X-Onboarding-Token";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrganizationRepository organizationRepository;
    @Autowired AppUserRepository appUserRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired InvitationCodeHasher invitationCodeHasher;
    @Autowired OpaqueTokenService opaqueTokenService;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean OtpEmailSender emailSender;

    private Organization organization;
    private AppUser admin;
    private Applicant applicant;

    @BeforeEach
    void prepareApplicant() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Synthetic Applicant Organization " + suffix, "Synthetic Applicant " + suffix, "America/Lima"));
        AppUser adminCandidate = AppUser.organizationUser(organization,
                "applicant.admin." + suffix + "@rutafija.test", passwordEncoder.encode(PASSWORD),
                "Synthetic ADMIN", UserRole.ADMIN);
        adminCandidate.verifyEmail(Instant.now());
        admin = appUserRepository.save(adminCandidate);
        applicant = createApplicant(true, true);
    }

    @Test
    void verifiedApplicantReadsOnlyOwnStatusWithHashedAbsoluteThirtyMinuteCredential() throws Exception {
        JsonNode access = login(applicant.email(), PASSWORD);
        String token = access.path("accessToken").asText();
        assertThat(token).matches("oa1\\.[A-Za-z0-9_-]{43}");
        assertThat(access.has("refreshToken")).isFalse();
        assertThat(access.has("role")).isFalse();

        Map<String, Object> stored = jdbc.queryForMap("""
                select token_hash, created_at, expires_at from conductor_onboarding_access_token
                 where identity_id = ?
                """, applicant.identityId());
        assertThat(stored.get("token_hash").toString().trim()).isEqualTo(opaqueTokenService.hash(token));
        assertThat(stored.get("token_hash")).isNotEqualTo(token);
        Instant createdAt = ((Timestamp) stored.get("created_at")).toInstant();
        Instant expiresAt = ((Timestamp) stored.get("expires_at")).toInstant();
        assertThat(Duration.between(createdAt, expiresAt)).isEqualTo(Duration.ofMinutes(30));
        assertThat(Duration.between(expiresAt, Instant.parse(access.path("expiresAt").asText())).abs())
                .isLessThan(Duration.ofMillis(1));

        mockMvc.perform(get("/api/v1/onboarding/applicant/requests").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(applicant.requestId().toString()))
                .andExpect(jsonPath("$[0].organizationId").value(organization.getId().toString()))
                .andExpect(jsonPath("$[0].status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$[0].reviewRounds").value(0))
                .andExpect(jsonPath("$[0].submittedAt").exists())
                .andExpect(jsonPath("$[0].fullName").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist())
                .andExpect(jsonPath("$[0].phone").doesNotExist());
        assertThat(jdbc.queryForObject("""
                select expires_at from conductor_onboarding_access_token where identity_id = ?
                """, Timestamp.class, applicant.identityId()).toInstant()).isEqualTo(expiresAt);
        assertNoOperationalAccount();
        verifyNoInteractions(emailSender);
    }

    @Test
    void wrongUnknownUnverifiedOrRequestlessCredentialsAreRejectedWithoutTokenCreation() throws Exception {
        assertLoginRejected(applicant.email(), "wrong-password");
        assertLoginRejected("unknown." + UUID.randomUUID() + "@rutafija.test", PASSWORD);
        Applicant unverified = createApplicant(false, true);
        assertLoginRejected(unverified.email(), PASSWORD);
        Applicant requestless = createApplicant(true, false);
        assertLoginRejected(requestless.email(), PASSWORD);
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_access_token", Integer.class))
                .isZero();
        verifyNoInteractions(emailSender);
    }

    @Test
    void matchingLegacyAccountDoesNotCreateAnImplicitApplicantAccountLink() throws Exception {
        AppUser legacy=AppUser.organizationUser(organization,applicant.email(),passwordEncoder.encode(PASSWORD),
                "Synthetic unrelated legacy account",UserRole.CONDUCTOR);
        legacy.verifyEmail(Instant.now());appUserRepository.saveAndFlush(legacy);
        assertLoginRejected(applicant.email(),PASSWORD);
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_access_token",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select linked_user_id from conductor_onboarding_request where id=?",UUID.class,
                applicant.requestId())).isNull();
        verifyNoInteractions(emailSender);
    }

    @Test
    void anotherApplicantTokenCannotSelectTheFirstIdentityOrRequest() throws Exception {
        Applicant another = createApplicant(true, true);
        String token = login(another.email(), PASSWORD).path("accessToken").asText();
        mockMvc.perform(get("/api/v1/onboarding/applicant/requests")
                        .header(TOKEN_HEADER, token)
                        .param("identityId", applicant.identityId().toString())
                        .param("requestId", applicant.requestId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].requestId").value(another.requestId().toString()));
        for (String invalid : new String[]{applicant.identityId().toString(), applicant.requestId().toString(),
                "fixture-operation-" + applicant.identityId(), "oa1." + opaqueTokenService.generate()}) {
            mockMvc.perform(get("/api/v1/onboarding/applicant/requests").header(TOKEN_HEADER, invalid))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        }
        mockMvc.perform(get("/api/v1/onboarding/applicant/requests"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredAndRevokedTokensAreRejectedAndLogoutIsIdempotent() throws Exception {
        String expired = login(applicant.email(), PASSWORD).path("accessToken").asText();
        Instant now = Instant.now();
        jdbc.update("""
                update conductor_onboarding_access_token set created_at = ?, expires_at = ?
                 where token_hash = ?
                """, Timestamp.from(now.minus(Duration.ofMinutes(31))),
                Timestamp.from(now.minus(Duration.ofMinutes(1))), opaqueTokenService.hash(expired));
        mockMvc.perform(get("/api/v1/onboarding/applicant/requests").header(TOKEN_HEADER, expired))
                .andExpect(status().isUnauthorized());

        String revoked = login(applicant.email(), PASSWORD).path("accessToken").asText();
        mockMvc.perform(post("/api/v1/onboarding/access/logout").header(TOKEN_HEADER, revoked))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        mockMvc.perform(get("/api/v1/onboarding/applicant/requests").header(TOKEN_HEADER, revoked))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/onboarding/access/logout").header(TOKEN_HEADER, revoked))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("""
                select revoked_at from conductor_onboarding_access_token where token_hash = ?
                """, Timestamp.class, opaqueTokenService.hash(revoked))).isNotNull();
    }

    @Test
    void applicantCredentialDoesNotAuthenticateOperationalEndpointsOrRefresh() throws Exception {
        String token = login(applicant.email(), PASSWORD).path("accessToken").asText();
        mockMvc.perform(get("/api/v1/auth/me").header(TOKEN_HEADER, token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/onboarding/requests").header(TOKEN_HEADER, token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/mobile/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("refreshToken", token))))
                .andExpect(status().isUnauthorized());
        assertLoginRejectedAt("/api/v1/auth/login", applicant.email(), PASSWORD);
        assertLoginRejectedAt("/api/v1/mobile/auth/login", applicant.email(), PASSWORD);
        assertNoOperationalAccount();
    }

    @Test
    void sixthFailedAttemptIsRateLimitedAndLookupDoesNotSendAnOtp() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertLoginRejected(applicant.email(), "incorrect-password");
        }
        mockMvc.perform(post("/api/v1/onboarding/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "email", applicant.email(), "password", PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
        verifyNoInteractions(emailSender);
    }

    private Applicant createApplicant(boolean verified, boolean withRequest) {
        UUID identityId = UUID.randomUUID();
        String email = "synthetic.applicant." + identityId + "@rutafija.test";
        Instant now = Instant.now();
        jdbc.update("""
                insert into conductor_onboarding_identity
                    (id, email, password_hash, full_name, phone, email_verified_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, identityId, email, passwordEncoder.encode(PASSWORD), "Synthetic Applicant", "+51999999999",
                verified ? Timestamp.from(now) : null, Timestamp.from(now), Timestamp.from(now));
        if (!withRequest) {
            return new Applicant(identityId, null, email);
        }
        UUID invitationId = UUID.randomUUID();
        jdbc.update("""
                insert into organization_invitation
                    (id, organization_id, issuer_user_id, recipient_email, code_hash, issued_at, expires_at, used_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, invitationId, organization.getId(), admin.getId(), email,
                invitationCodeHasher.hashCode("123456", email), Timestamp.from(now),
                Timestamp.from(now.plus(Duration.ofHours(120))), Timestamp.from(now));
        UUID requestId = UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_request
                    (id, identity_id, organization_id, invitation_id, idempotency_key, status, submitted_at)
                values (?, ?, ?, ?, ?, 'PENDING_REVIEW', ?)
                """, requestId, identityId, organization.getId(), invitationId,
                "fixture-operation-" + identityId, Timestamp.from(now));
        return new Applicant(identityId, requestId, email);
    }

    private JsonNode login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/onboarding/access/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private void assertLoginRejected(String email, String password) throws Exception {
        assertLoginRejectedAt("/api/v1/onboarding/access/login", email, password);
    }

    private void assertLoginRejectedAt(String path, String email, String password) throws Exception {
        mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    private void assertNoOperationalAccount() {
        assertThat(jdbc.queryForObject("select count(*) from app_user where email = ?", Integer.class,
                applicant.email())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from refresh_token", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from driver", Integer.class)).isZero();
    }

    private record Applicant(UUID identityId, UUID requestId, String email) {
    }
}
