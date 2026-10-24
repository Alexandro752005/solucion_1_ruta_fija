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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;
import pe.rutafija.support.ApprovedConductorFixture;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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
class ConductorRecoveryFlowIT extends NativePostgresIntegrationTest {

    private static final String PASSWORD = "Conductor-Recovery-Password-2026";
    private static final String NEW_PASSWORD = "Conductor-Recovered-Password-2026";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    TransportGroupRepository groupRepository;

    @Autowired
    DriverRepository driverRepository;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    OpaqueTokenService opaqueTokenService;

    @MockitoBean
    OtpEmailSender emailSender;

    private Organization organization;
    private AppUser admin;
    private AppUser conductor;
    private String adminEmail;
    private String conductorEmail;
    private String suffix;

    @BeforeEach
    void prepareLinkedTenantAccounts() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        organization = organizationRepository.save(Organization.active(
                "Organización de recuperación " + suffix,
                "Org recuperación",
                "America/Lima"
        ));
        TransportGroup group = groupRepository.save(TransportGroup.active(
                organization,
                "Grupo recuperación " + suffix,
                "Prueba de recuperación asistida"
        ));
        adminEmail = "recovery.admin." + suffix + "@rutafija.test";
        conductorEmail = "recovery.driver." + suffix + "@rutafija.test";
        admin = userRepository.save(verifiedUser(AppUser.organizationUser(
                organization,
                adminEmail,
                passwordEncoder.encode(PASSWORD),
                "ADMIN de prueba",
                UserRole.ADMIN
        )));
        conductor = userRepository.save(verifiedUser(AppUser.organizationUser(
                organization,
                conductorEmail,
                passwordEncoder.encode(PASSWORD),
                "CONDUCTOR de prueba",
                UserRole.CONDUCTOR
        )));
        Driver approvedDriver = driverRepository.saveAndFlush(Driver.register(
                organization,
                conductor,
                group,
                "CONDUCTOR de prueba",
                "999111222",
                "DNI",
                "REC-" + suffix,
                "LIC-" + suffix
        ));
        // Historical synthetic approval allows exercising recovery of the same account.
        ApprovedConductorFixture.recordHistoricalApproval(jdbcTemplate, admin, conductor, approvedDriver);
    }

    @Test
    void adminMustConfirmBothEvidenceChecksAndCannotRecoverAnotherTenantAccount() throws Exception {
        String accessToken = adminAccessToken();
        String request = requestJson("false", "true", false);

        mockMvc.perform(post(recoveryPath(conductor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        Organization otherOrganization = organizationRepository.save(Organization.active(
                "Organización aislada " + suffix,
                "Org aislada",
                "America/Lima"
        ));
        TransportGroup otherGroup = groupRepository.save(TransportGroup.active(
                otherOrganization,
                "Grupo aislado " + suffix,
                "Cuenta de otro tenant"
        ));
        AppUser otherConductor = userRepository.save(verifiedUser(AppUser.organizationUser(
                otherOrganization,
                "other.driver." + suffix + "@rutafija.test",
                passwordEncoder.encode(PASSWORD),
                "CONDUCTOR externo",
                UserRole.CONDUCTOR
        )));
        driverRepository.saveAndFlush(Driver.register(
                otherOrganization,
                otherConductor,
                otherGroup,
                "CONDUCTOR externo",
                null,
                "DNI",
                "OTHER-" + suffix,
                null
        ));

        mockMvc.perform(post(recoveryPath(otherConductor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("true", "true", false)))
                .andExpect(status().isNotFound());

        Integer requests = jdbcTemplate.queryForObject(
                "select count(*) from conductor_recovery_request",
                Integer.class
        );
        assertThat(requests).isZero();
    }

    @Test
    void newEmailStaysPendingUntilOtpAndResendsKeepTheThreePerHourLimit() throws Exception {
        String accessToken = adminAccessToken();
        String newEmail = "recovered." + suffix + "@rutafija.test";
        String conductorRefresh = loginConductorAndReadRefreshToken();

        JsonNode started = json(mockMvc.perform(post(recoveryPath(conductor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("true", "true", false)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn());
        UUID recoveryId = UUID.fromString(started.path("recoveryId").asText());
        String verificationToken = started.path("verificationToken").asText();
        assertThat(started.path("emailSent").asBoolean()).isTrue();
        assertThat(started.path("passwordResetToken").isMissingNode()).isTrue();
        assertThat(userRepository.findById(conductor.getId()).orElseThrow().getEmail()).isEqualTo(conductorEmail);

        String otpHash = jdbcTemplate.queryForObject(
                "select otp_hash from email_otp_challenge where user_id = ?",
                String.class,
                conductor.getId()
        );
        String firstOtp = capturedCode(newEmail, 1);
        assertThat(otpHash).matches("[0-9a-f]{64}").isNotEqualTo(firstOtp);
        assertThat(jdbcTemplate.queryForObject(
                "select purpose from email_otp_challenge where user_id = ?",
                String.class,
                conductor.getId()
        )).isEqualTo("CONDUCTOR_RECOVERY");
        assertThat(jdbcTemplate.queryForObject(
                "select purpose_reference_id from email_otp_challenge where user_id = ?",
                UUID.class,
                conductor.getId()
        )).isEqualTo(recoveryId);

        for (int i = 2; i <= 3; i++) {
            JsonNode resent = json(mockMvc.perform(post(recoveryPath(conductor.getId()) + "/" + recoveryId + "/otp/resend")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"verificationToken\":\"%s\"}".formatted(verificationToken)))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andReturn());
            verificationToken = resent.path("verificationToken").asText();
            assertThat(capturedCode(newEmail, i)).matches("[0-9]{6}");
        }

        mockMvc.perform(post(recoveryPath(conductor.getId()) + "/" + recoveryId + "/otp/resend")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"%s\"}".formatted(verificationToken)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_RATE_LIMITED"));
        verify(emailSender, times(3)).sendVerificationCode(eq(newEmail), any(String.class), any(Instant.class));

        String currentOtp = capturedCode(newEmail, 3);
        mockMvc.perform(post("/api/v1/auth/conductor-recovery/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recoveryId":"%s","verificationToken":"%s","code":"%s"}
                                """.formatted(recoveryId, verificationToken, currentOtp)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.passwordResetRequired").value(false))
                .andExpect(jsonPath("$.passwordResetToken").doesNotExist());

        AppUser recovered = userRepository.findById(conductor.getId()).orElseThrow();
        assertThat(recovered.getEmail()).isEqualTo(newEmail);
        assertThat(recovered.isEmailVerified()).isTrue();
        assertThat(recovered.getSessionVersion()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where token_hash = ? and revoked_at is not null",
                Integer.class,
                opaqueTokenService.hash(conductorRefresh)
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from audit_event where entity_id = ? and action = 'CONDUCTOR_RECOVERY_COMPLETED'",
                Integer.class,
                recoveryId
        )).isEqualTo(1);

        JsonNode status = json(mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                recoveryPath(conductor.getId()) + "/" + recoveryId
                        )
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn());
        assertThat(status.path("evidenceMethod").asText()).isEqualTo("PRIOR_SECURE_CONTACT");
        assertThat(status.hasNonNull("evidenceReference")).isFalse();
        assertThat(status.hasNonNull("reason")).isFalse();
    }

    @Test
    void passwordLostFlowReturnsAHashedOneTimeTokenOnlyAfterOtp() throws Exception {
        String accessToken = adminAccessToken();
        String newEmail = "recovered." + suffix + "@rutafija.test";
        String conductorRefresh = loginConductorAndReadRefreshToken();
        JsonNode started = json(mockMvc.perform(post(recoveryPath(conductor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("true", "true", true)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID recoveryId = UUID.fromString(started.path("recoveryId").asText());
        String verificationToken = started.path("verificationToken").asText();
        String otp = capturedCode(newEmail, 1);
        assertThat(started.path("passwordResetToken").isMissingNode()).isTrue();

        JsonNode verified = json(mockMvc.perform(post("/api/v1/auth/conductor-recovery/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recoveryId":"%s","verificationToken":"%s","code":"%s"}
                                """.formatted(recoveryId, verificationToken, otp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PASSWORD_RESET_PENDING"))
                .andExpect(jsonPath("$.requestedEmail").isEmpty())
                .andExpect(jsonPath("$.passwordResetToken").isNotEmpty())
                .andReturn());
        String rawResetToken = verified.path("passwordResetToken").asText();
        String storedHash = jdbcTemplate.queryForObject(
                "select password_reset_token_hash from conductor_recovery_request where id = ?",
                String.class,
                recoveryId
        );
        assertThat(storedHash).matches("[0-9a-f]{64}").isEqualTo(opaqueTokenService.hash(rawResetToken));
        assertThat(storedHash).isNotEqualTo(rawResetToken);
        AppUser pendingReset = userRepository.findById(conductor.getId()).orElseThrow();
        assertThat(pendingReset.getEmail()).isEqualTo(conductorEmail);
        assertThat(pendingReset.getSessionVersion()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where token_hash = ? and revoked_at is null",
                Integer.class,
                opaqueTokenService.hash(conductorRefresh)
        )).isEqualTo(1);

        String resetBody = """
                {"passwordResetToken":"%s","newPassword":"%s"}
                """.formatted(rawResetToken, NEW_PASSWORD);
        mockMvc.perform(post("/api/v1/auth/conductor-recovery/{id}/password-reset", recoveryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        AppUser recovered = userRepository.findById(conductor.getId()).orElseThrow();
        assertThat(recovered.getEmail()).isEqualTo(newEmail);
        assertThat(recovered.getSessionVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, recovered.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(PASSWORD, recovered.getPasswordHash())).isFalse();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where token_hash = ? and revoked_at is not null",
                Integer.class,
                opaqueTokenService.hash(conductorRefresh)
        )).isEqualTo(1);
        mockMvc.perform(post("/api/v1/auth/conductor-recovery/{id}/password-reset", recoveryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resetBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
    }

    @Test
    void expiredResetTokenCanBeCancelledWithoutChangingTheAccountOrItsSession() throws Exception {
        String accessToken = adminAccessToken();
        String conductorRefresh = loginConductorAndReadRefreshToken();
        JsonNode started = json(mockMvc.perform(post(recoveryPath(conductor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("true", "true", true)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID recoveryId = UUID.fromString(started.path("recoveryId").asText());
        JsonNode verification = json(mockMvc.perform(post("/api/v1/auth/conductor-recovery/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recoveryId":"%s","verificationToken":"%s","code":"%s"}
                                """.formatted(
                                recoveryId,
                                started.path("verificationToken").asText(),
                                capturedCode("recovered." + suffix + "@rutafija.test", 1)
                        )))
                .andExpect(status().isOk())
                .andReturn());
        String rawResetToken = verification.path("passwordResetToken").asText();

        jdbcTemplate.update(
                "update conductor_recovery_request set password_reset_expires_at = ? where id = ?",
                java.sql.Timestamp.from(Instant.now().minusSeconds(1)),
                recoveryId
        );
        mockMvc.perform(post("/api/v1/auth/conductor-recovery/{id}/password-reset", recoveryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"passwordResetToken":"%s","newPassword":"%s"}
                                """.formatted(
                                rawResetToken,
                                NEW_PASSWORD
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));

        mockMvc.perform(post(recoveryPath(conductor.getId()) + "/" + recoveryId + "/cancel")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        AppUser unchanged = userRepository.findById(conductor.getId()).orElseThrow();
        assertThat(unchanged.getEmail()).isEqualTo(conductorEmail);
        assertThat(unchanged.getSessionVersion()).isZero();
        assertThat(passwordEncoder.matches(PASSWORD, unchanged.getPasswordHash())).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from refresh_token where token_hash = ? and revoked_at is null",
                Integer.class,
                opaqueTokenService.hash(conductorRefresh)
        )).isEqualTo(1);
    }

    @Test
    void twoIncorrectCodesPersistTheThirtyMinuteVerificationBlock() throws Exception {
        String accessToken = adminAccessToken();
        JsonNode started = json(mockMvc.perform(post(recoveryPath(conductor.getId()))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson("true", "true", false)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID recoveryId = UUID.fromString(started.path("recoveryId").asText());
        String verificationToken = started.path("verificationToken").asText();
        String actualOtp = capturedCode("recovered." + suffix + "@rutafija.test", 1);
        String incorrectOtp = "000000".equals(actualOtp) ? "000001" : "000000";
        String invalidBody = """
                {"recoveryId":"%s","verificationToken":"%s","code":"%s"}
                """.formatted(recoveryId, verificationToken, incorrectOtp);

        mockMvc.perform(post("/api/v1/auth/conductor-recovery/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
        mockMvc.perform(post("/api/v1/auth/conductor-recovery/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));

        Short failures = jdbcTemplate.queryForObject(
                "select failed_attempts from email_otp_challenge where user_id = ?",
                Short.class,
                conductor.getId()
        );
        Instant blockedUntil = jdbcTemplate.queryForObject(
                "select blocked_until from email_otp_challenge where user_id = ?",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(),
                conductor.getId()
        );
        assertThat(failures).isEqualTo((short) 2);
        assertThat(blockedUntil).isAfter(Instant.now().plusSeconds(29 * 60));
        assertThat(userRepository.findById(conductor.getId()).orElseThrow().getEmail()).isEqualTo(conductorEmail);
    }

    private String adminAccessToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(adminEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(login).path("accessToken").asText();
    }

    private String loginConductorAndReadRefreshToken() throws Exception {
        MvcResult login = mockMvc.perform(post("/api/v1/mobile/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(conductorEmail, PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return json(login).path("refreshToken").asText();
    }

    private String requestJson(String expedienteReviewed, String independentEvidenceConfirmed, boolean resetRequired) {
        return """
                {"newEmail":"recovered.%s@rutafija.test","evidenceMethod":"PRIOR_SECURE_CONTACT",
                 "evidenceReference":"contacto previo validado","reason":"Pérdida declarada del canal anterior",
                 "expedienteReviewed":%s,"independentEvidenceConfirmed":%s,"passwordResetRequired":%s}
                """.formatted(suffix, expedienteReviewed, independentEvidenceConfirmed, resetRequired);
    }

    private String recoveryPath(UUID userId) {
        return "/api/v1/users/" + userId + "/conductor-recovery";
    }

    private AppUser verifiedUser(AppUser user) {
        user.verifyEmail(Instant.now());
        return user;
    }

    private String capturedCode(String recipient, int sendCount) {
        org.mockito.ArgumentCaptor<String> code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(sendCount)).sendVerificationCode(eq(recipient), code.capture(), any(Instant.class));
        String captured = code.getAllValues().get(sendCount - 1);
        assertThat(captured).matches("[0-9]{6}");
        return captured;
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
