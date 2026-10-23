package pe.rutafija.onboarding.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
@Import(ConductorOnboardingFlowIT.ClockConfiguration.class)
class ConductorOnboardingFlowIT extends NativePostgresIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    AppUserRepository appUserRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    InvitationCodeHasher invitationCodeHasher;

    @MockitoBean
    OtpEmailSender emailSender;

    @Autowired
    MutableClock clock;

    private Instant testNow;

    private Organization organization;
    private AppUser admin;
    private AppUser otherTenantAdmin;
    private AppUser conductor;
    private String invitationCode;
    private String recipientEmail;
    private String sentOtp;

    @BeforeEach
    void prepareInvitation() {
        testNow = Instant.now().truncatedTo(ChronoUnit.MICROS);
        clock.set(testNow);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Onboarding Test Organization " + suffix,
                "Onboarding Test " + suffix,
                "America/Lima"
        ));
        AppUser adminCandidate = AppUser.organizationUser(
                organization,
                "onboarding.admin." + suffix + "@rutafija.test",
                passwordEncoder.encode("Onboarding-Test-Admin-2026"),
                "Test ADMIN",
                UserRole.ADMIN
        );
        adminCandidate.verifyEmail(Instant.now());
        admin = appUserRepository.save(adminCandidate);
        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Other Onboarding Test Organization " + suffix,
                "Other Onboarding Test " + suffix,
                "America/Lima"
        ));
        AppUser otherAdminCandidate = AppUser.organizationUser(
                otherOrganization,
                "other.onboarding.admin." + suffix + "@rutafija.test",
                passwordEncoder.encode("Onboarding-Test-Other-Admin-2026"),
                "Other Test ADMIN",
                UserRole.ADMIN
        );
        otherAdminCandidate.verifyEmail(Instant.now());
        otherTenantAdmin = appUserRepository.save(otherAdminCandidate);
        conductor = appUserRepository.save(AppUser.organizationUser(
                organization,
                "onboarding.conductor." + suffix + "@rutafija.test",
                passwordEncoder.encode("Onboarding-Test-Conductor-2026"),
                "Test CONDUCTOR",
                UserRole.CONDUCTOR
        ));
        recipientEmail = "onboarding.driver." + suffix + "@rutafija.test";
        invitationCode = "004207";
        UUID invitationId = UUID.randomUUID();
        Instant issuedAt = testNow;
        jdbc.update("""
                insert into organization_invitation (
                    id, organization_id, issuer_user_id, recipient_email, code_hash, issued_at, expires_at
                ) values (?, ?, ?, ?, ?, ?, ?)
                """, invitationId, organization.getId(), admin.getId(), recipientEmail,
                invitationCodeHasher.hashCode(invitationCode, recipientEmail),
                java.sql.Timestamp.from(issuedAt),
                java.sql.Timestamp.from(issuedAt.plusSeconds(120 * 60 * 60)));
        doAnswer(invocation -> {
            sentOtp = invocation.getArgument(1);
            return null;
        }).when(emailSender).sendVerificationCode(eq(recipientEmail), any(String.class), any(Instant.class));
    }

    @Test
    void verifiedInvitationAcceptanceCreatesOnlyPendingRequestAndReplaysIdempotently() throws Exception {
        String idempotencyKey = "onboarding-op-" + UUID.randomUUID();
        JsonNode challenge = json(mockMvc.perform(startRequest(idempotencyKey, invitationCode))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.emailSent").value(true))
                .andReturn());
        String verificationToken = challenge.path("verificationToken").asText();

        assertThat(sentOtp).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject(
                "select count(*) from app_user where email = ?",
                Integer.class,
                recipientEmail
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select used_at from organization_invitation where recipient_email = ?",
                java.sql.Timestamp.class,
                recipientEmail
        )).isNull();
        assertThat(jdbc.queryForObject(
                "select otp_hash from conductor_onboarding_email_challenge where identity_id = "
                        + "(select id from conductor_onboarding_identity where email = ?)",
                String.class,
                recipientEmail
        )).isNotEqualTo(sentOtp);

        JsonNode accepted = json(mockMvc.perform(verifyRequest(verificationToken, sentOtp))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andReturn());
        UUID requestId = UUID.fromString(accepted.path("requestId").asText());

        assertThat(jdbc.queryForObject(
                "select count(*) from conductor_onboarding_request where id = ? and status = 'PENDING_REVIEW'",
                Integer.class,
                requestId
        )).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select used_at from organization_invitation where recipient_email = ?",
                java.sql.Timestamp.class,
                recipientEmail
        )).isNotNull();
        assertThat(jdbc.queryForObject(
                "select count(*) from app_user where email = ?",
                Integer.class,
                recipientEmail
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from driver where user_id = (select id from app_user where email = ?)",
                Integer.class,
                recipientEmail
        )).isZero();
        assertNoOperationalLogin();

        mockMvc.perform(verifyRequest(verificationToken, sentOtp))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()));
        assertThat(jdbc.queryForObject(
                "select count(*) from conductor_onboarding_request where identity_id = "
                        + "(select id from conductor_onboarding_identity where email = ?)",
                Integer.class,
                recipientEmail
        )).isEqualTo(1);
        mockMvc.perform(get("/api/v1/onboarding/requests").with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].requestId").value(requestId.toString()))
                .andExpect(jsonPath("$[0].fullName").doesNotExist())
                .andExpect(jsonPath("$[0].email").doesNotExist());
        mockMvc.perform(get("/api/v1/onboarding/requests/{id}/expediente", requestId)
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Synthetic Applicant"))
                .andExpect(jsonPath("$.email").value(recipientEmail));
        mockMvc.perform(get("/api/v1/onboarding/requests/{id}/expediente", requestId)
                        .with(authenticatedAs(otherTenantAdmin)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/onboarding/requests/{id}/expediente", requestId)
                        .with(authenticatedAs(conductor)))
                .andExpect(status().isForbidden());
        verify(emailSender, times(1)).sendVerificationCode(eq(recipientEmail), any(String.class), any(Instant.class));
    }

    @Test
    void mismatchedInvitationDoesNotCreateIdentityOrSendOtpAndFifthFailureStartsPause() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            var result = mockMvc.perform(startRequest(
                            "bad-onboarding-" + UUID.randomUUID(),
                            "999999"
                    ));
            if (attempt == 5) {
                result.andExpect(status().isTooManyRequests())
                        .andExpect(jsonPath("$.code").value("INVITATION_VALIDATION_LIMITED"));
            } else {
                result.andExpect(status().isBadRequest());
            }
        }
        assertThat(jdbc.queryForObject(
                "select count(*) from conductor_onboarding_identity where email = ?",
                Integer.class,
                recipientEmail
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from conductor_onboarding_invitation_failure",
                Integer.class
        )).isEqualTo(5);
        verify(emailSender, times(0)).sendVerificationCode(any(String.class), any(String.class), any(Instant.class));
    }

    @Test
    void twoInvalidOtpEntriesBlockFurtherVerificationWithoutConsumingInvitation() throws Exception {
        JsonNode challenge = json(mockMvc.perform(startRequest(
                        "otp-block-" + UUID.randomUUID(),
                        invitationCode
                ))
                .andExpect(status().isAccepted())
                .andReturn());
        String token = challenge.path("verificationToken").asText();

        mockMvc.perform(verifyRequest(token, wrongOtp()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
        mockMvc.perform(verifyRequest(token, wrongOtp()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));
        mockMvc.perform(verifyRequest(token, sentOtp))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));

        assertThat(jdbc.queryForObject(
                "select count(*) from conductor_onboarding_request",
                Integer.class
        )).isZero();
        assertThat(jdbc.queryForObject(
                "select used_at from organization_invitation where recipient_email = ?",
                java.sql.Timestamp.class,
                recipientEmail
        )).isNull();
    }

    @Test
    void otpResendRotatesTheTokenAndEnforcesThreeSendsPerRollingHour() throws Exception {
        JsonNode initial = json(mockMvc.perform(startRequest(
                        "otp-resend-" + UUID.randomUUID(),
                        invitationCode
                ))
                .andExpect(status().isAccepted())
                .andReturn());
        String firstToken = initial.path("verificationToken").asText();
        JsonNode second = json(mockMvc.perform(resendRequest(firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailSent").value(true))
                .andReturn());
        String secondToken = second.path("verificationToken").asText();

        mockMvc.perform(verifyRequest(firstToken, sentOtp))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));

        JsonNode third = json(mockMvc.perform(resendRequest(secondToken))
                .andExpect(status().isOk())
                .andReturn());
        String thirdToken = third.path("verificationToken").asText();

        mockMvc.perform(resendRequest(thirdToken))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_RATE_LIMITED"));
        verify(emailSender, times(3)).sendVerificationCode(eq(recipientEmail), any(String.class), any(Instant.class));

        mockMvc.perform(verifyRequest(thirdToken, sentOtp))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
    }

    @Test
    void resendPreservesTheFirstInvalidAttempt() throws Exception {
        JsonNode initial = json(mockMvc.perform(startRequest("retry-errors-" + UUID.randomUUID(), invitationCode))
                .andExpect(status().isAccepted()).andReturn());
        String token = initial.path("verificationToken").asText();
        mockMvc.perform(verifyRequest(token, wrongOtp()))
                .andExpect(status().isBadRequest());
        JsonNode resent = json(mockMvc.perform(resendRequest(token)).andExpect(status().isOk()).andReturn());
        mockMvc.perform(verifyRequest(resent.path("verificationToken").asText(), wrongOtp()))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));
    }

    @Test
    void challengeExpiryDoesNotEndAnOtpBlockEarly() throws Exception {
        String operation = "expiry-block-" + UUID.randomUUID();
        JsonNode initial = json(mockMvc.perform(startRequest(operation, invitationCode))
                .andExpect(status().isAccepted()).andReturn());
        String token = initial.path("verificationToken").asText();
        clock.set(testNow.plusSeconds(4 * 60));
        mockMvc.perform(verifyRequest(token, wrongOtp())).andExpect(status().isBadRequest());
        mockMvc.perform(verifyRequest(token, wrongOtp())).andExpect(status().isTooManyRequests());
        clock.set(testNow.plusSeconds(31 * 60));
        mockMvc.perform(startRequest(operation, invitationCode))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));
        verify(emailSender, times(1)).sendVerificationCode(eq(recipientEmail), any(String.class), any(Instant.class));
    }

    @Test
    void verifiedIdentityCannotBeOverwrittenByAnInvitationFromAnotherOrganization() throws Exception {
        JsonNode initial = json(mockMvc.perform(startRequest("original-" + UUID.randomUUID(), invitationCode))
                .andExpect(status().isAccepted()).andReturn());
        mockMvc.perform(verifyRequest(initial.path("verificationToken").asText(), sentOtp))
                .andExpect(status().isAccepted());
        String originalHash = jdbc.queryForObject(
                "select password_hash from conductor_onboarding_identity where email = ?", String.class, recipientEmail);
        String nextCode = "006789";
        jdbc.update("""
                insert into organization_invitation
                    (id, organization_id, issuer_user_id, recipient_email, code_hash, issued_at, expires_at)
                values (?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), otherTenantAdmin.getOrganizationId(), otherTenantAdmin.getId(), recipientEmail,
                invitationCodeHasher.hashCode(nextCode, recipientEmail), java.sql.Timestamp.from(testNow),
                java.sql.Timestamp.from(testNow.plusSeconds(3600)));
        mockMvc.perform(startRequest("overwrite-" + UUID.randomUUID(), nextCode))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject(
                "select password_hash from conductor_onboarding_identity where email = ?", String.class, recipientEmail))
                .isEqualTo(originalHash);
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_request", Integer.class)).isEqualTo(1);
        verify(emailSender, times(1)).sendVerificationCode(eq(recipientEmail), any(String.class), any(Instant.class));
    }

    @Test
    void existingAccountIsNotDuplicatedAsAPreliminaryIdentity() throws Exception {
        appUserRepository.save(AppUser.organizationUser(organization, recipientEmail,
                passwordEncoder.encode("Synthetic-Existing-2026"), "Synthetic Existing", UserRole.CONDUCTOR));
        mockMvc.perform(startRequest("existing-account-" + UUID.randomUUID(), invitationCode))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_identity", Integer.class)).isZero();
        verify(emailSender, times(0)).sendVerificationCode(any(String.class), any(String.class), any(Instant.class));
    }

    @Test
    void inactiveOrganizationCannotConsumeAnInvitation() throws Exception {
        JsonNode initial = json(mockMvc.perform(startRequest("inactive-org-" + UUID.randomUUID(), invitationCode))
                .andExpect(status().isAccepted()).andReturn());
        jdbc.update("update organization set status = 'INACTIVE' where id = ?", organization.getId());
        mockMvc.perform(verifyRequest(initial.path("verificationToken").asText(), sentOtp))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_request", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select used_at from organization_invitation where recipient_email = ?",
                java.sql.Timestamp.class, recipientEmail)).isNull();
    }

    @Test
    void concurrentAcceptanceConsumesOnlyOneInvitationAndCreatesOneRequest() throws Exception {
        JsonNode initial = json(mockMvc.perform(startRequest("concurrent-" + UUID.randomUUID(), invitationCode))
                .andExpect(status().isAccepted()).andReturn());
        String token = initial.path("verificationToken").asText();
        String otp = sentOtp;
        CountDownLatch ready = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { ready.await(); return json(mockMvc.perform(verifyRequest(token, otp))
                    .andExpect(status().isAccepted()).andReturn()); });
            var second = executor.submit(() -> { ready.await(); return json(mockMvc.perform(verifyRequest(token, otp))
                    .andExpect(status().isAccepted()).andReturn()); });
            ready.countDown();
            assertThat(first.get(15, TimeUnit.SECONDS).path("requestId").asText())
                    .isEqualTo(second.get(15, TimeUnit.SECONDS).path("requestId").asText());
        }
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_request", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from organization_invitation where used_at is not null", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void otpExpiresAtExactlyFiveMinutesWithoutConsumingInvitation() throws Exception {
        JsonNode initial = json(mockMvc.perform(startRequest("otp-boundary-" + UUID.randomUUID(), invitationCode))
                .andExpect(status().isAccepted()).andReturn());
        clock.set(testNow.plusSeconds(5 * 60));
        mockMvc.perform(verifyRequest(initial.path("verificationToken").asText(), sentOtp))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("EMAIL_OTP_EXPIRED"));
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_request", Integer.class)).isZero();
    }

    private String wrongOtp() {
        return "000000".equals(sentOtp) ? "999999" : "000000";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @Primary
        MutableClock onboardingTestClock() {
            return new MutableClock();
        }
    }

    static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current =
                new AtomicReference<>(Instant.now().truncatedTo(ChronoUnit.MICROS));

        void set(Instant instant) {
            current.set(instant);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(current.get(), zone); }
        @Override public Instant instant() { return current.get(); }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder startRequest(
            String idempotencyKey,
            String code
    ) throws Exception {
        return post("/api/v1/onboarding/requests")
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "email", recipientEmail,
                        "password", "Onboarding-Test-Password-2026",
                        "fullName", "Synthetic Applicant",
                        "phone", "+51999999999",
                        "invitationCode", code
                )));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder verifyRequest(
            String token,
            String code
    ) throws Exception {
        return post("/api/v1/onboarding/otp/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of(
                        "verificationToken", token,
                        "code", code
                )));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder resendRequest(
            String token
    ) throws Exception {
        return post("/api/v1/onboarding/otp/resend")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("verificationToken", token)));
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

    private void assertNoOperationalLogin() throws Exception {
        byte[] credentials = objectMapper.writeValueAsBytes(Map.of(
                "email", recipientEmail,
                "password", "Onboarding-Test-Password-2026"
        ));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
        mockMvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(credentials))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
