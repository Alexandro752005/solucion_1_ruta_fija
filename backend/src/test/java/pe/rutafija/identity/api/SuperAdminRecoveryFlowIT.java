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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
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
class SuperAdminRecoveryFlowIT extends NativePostgresIntegrationTest {

    private static final String OLD_PASSWORD = "Original-SuperAdmin-Password-2026";
    private static final String NEW_PASSWORD = "Recovered-SuperAdmin-Password-2026";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    OrganizationRepository organizationRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @MockitoBean
    OtpEmailSender emailSender;

    private AppUser owner;
    private String ownerEmail;

    @BeforeEach
    void createOwner() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        ownerEmail = "owner." + suffix + "@rutafija.test";
        owner = AppUser.superAdmin(
                ownerEmail,
                passwordEncoder.encode(OLD_PASSWORD),
                "Ruta Fija Owner"
        );
        owner.verifyEmail(Instant.now());
        owner = userRepository.save(owner);
    }

    @Test
    void enrollmentValidRecoveryRotatesTheEmergencyCodeAndRevokesBothSessionTypes() throws Exception {
        KeyPair ownerKey = ed25519KeyPair();
        MvcResult login = login(ownerEmail, OLD_PASSWORD);
        String accessToken = json(login).path("accessToken").asText();
        Cookie refreshCookie = login.getResponse().getCookie("rf_refresh");

        JsonNode enrollment = enroll(ownerKey, accessToken, OLD_PASSWORD);
        String emergencyCode = enrollment.path("emergencyCodeShownOnce").asText();
        UUID installationId = UUID.fromString(enrollment.path("installationId").asText());
        assertThat(emergencyCode).matches("[A-Za-z0-9_-]{43}");

        String newEmail = "owner.recovered." + UUID.randomUUID().toString().substring(0, 8) + "@rutafija.test";
        JsonNode challenge = json(mockMvc.perform(post("/api/v1/auth/super-admin-recovery/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountEmail":"%s","newEmail":"%s"}
                                """.formatted(ownerEmail, newEmail)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn());
        UUID challengeId = UUID.fromString(challenge.path("challengeId").asText());
        assertThat(challenge.path("installationId").asText()).isEqualTo(installationId.toString());

        String signature = sign(ownerKey, challenge.path("challengeToSignUtf8").asText());
        JsonNode proof = json(mockMvc.perform(post(
                                "/api/v1/auth/super-admin-recovery/challenges/{challengeId}/proof",
                                challengeId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emergencyCode":"%s","signatureBase64Url":"%s"}
                                """.formatted(emergencyCode, signature)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn());

        String verificationToken = proof.path("verificationToken").asText();
        String otp = capturedOtp(newEmail);
        assertThat(verificationToken).isNotBlank();

        JsonNode completed = json(mockMvc.perform(post(
                                "/api/v1/auth/super-admin-recovery/challenges/{challengeId}/complete",
                                challengeId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"verificationToken":"%s","code":"%s","newPassword":"%s"}
                                """.formatted(verificationToken, otp, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn());

        AppUser recovered = userRepository.findById(owner.getId()).orElseThrow();
        assertThat(recovered.getEmail()).isEqualTo(newEmail);
        assertThat(recovered.isEmailVerified()).isTrue();
        assertThat(recovered.getSessionVersion()).isEqualTo(1);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, recovered.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, recovered.getPasswordHash())).isFalse();

        String newEmergencyCode = completed.path("newEmergencyCodeShownOnce").asText();
        assertThat(newEmergencyCode).matches("[A-Za-z0-9_-]{43}").isNotEqualTo(emergencyCode);
        String storedEmergencyHash = jdbcTemplate.queryForObject(
                "select emergency_code_hash from super_admin_recovery_credential where id = 1",
                String.class
        );
        String storedPublicKey = jdbcTemplate.queryForObject(
                "select public_key_x509 from super_admin_recovery_credential where id = 1",
                String.class
        );
        assertThat(passwordEncoder.matches(newEmergencyCode, storedEmergencyHash)).isTrue();
        assertThat(passwordEncoder.matches(emergencyCode, storedEmergencyHash)).isFalse();
        assertThat(storedPublicKey).isEqualTo(Base64.getEncoder().encodeToString(ownerKey.getPublic().getEncoded()));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_TOKEN_INVALID"));
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(
                        "/api/v1/auth/super-admin-recovery/challenges/{challengeId}/proof",
                        challengeId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emergencyCode":"%s","signatureBase64Url":"%s"}
                                """.formatted(emergencyCode, signature)))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN_RECOVERY_EXPIRED"));

        String consumedAt = jdbcTemplate.queryForObject(
                "select consumed_at::text from super_admin_recovery_challenge where id = ?",
                String.class,
                challengeId
        );
        assertThat(consumedAt).isNotBlank();
    }

    @Test
    void invalidProofAttemptsArePersistedAndTheThirdAttemptConsumesTheChallenge() throws Exception {
        KeyPair ownerKey = ed25519KeyPair();
        String accessToken = json(login(ownerEmail, OLD_PASSWORD)).path("accessToken").asText();
        JsonNode enrollment = enroll(ownerKey, accessToken, OLD_PASSWORD);
        String emergencyCode = enrollment.path("emergencyCodeShownOnce").asText();
        String newEmail = "owner.invalid." + UUID.randomUUID().toString().substring(0, 8) + "@rutafija.test";
        JsonNode challenge = json(mockMvc.perform(post("/api/v1/auth/super-admin-recovery/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountEmail":"%s","newEmail":"%s"}
                                """.formatted(ownerEmail, newEmail)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID challengeId = UUID.fromString(challenge.path("challengeId").asText());
        String invalidSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64]);

        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post(
                                    "/api/v1/auth/super-admin-recovery/challenges/{challengeId}/proof",
                                    challengeId
                            )
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"emergencyCode":"%s","signatureBase64Url":"%s"}
                                    """.formatted(emergencyCode, invalidSignature)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("SUPER_ADMIN_RECOVERY_INVALID"));
        }

        Short attempts = jdbcTemplate.queryForObject(
                "select failed_proof_attempts from super_admin_recovery_challenge where id = ?",
                Short.class,
                challengeId
        );
        assertThat(attempts).isEqualTo((short) 3);
        Integer emailsSent = jdbcTemplate.queryForObject(
                "select count(*) from email_otp_challenge where user_id = ? and purpose = 'SUPER_ADMIN_RECOVERY'",
                Integer.class,
                owner.getId()
        );
        assertThat(emailsSent).isZero();

        mockMvc.perform(post(
                        "/api/v1/auth/super-admin-recovery/challenges/{challengeId}/proof",
                        challengeId
                )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emergencyCode":"%s","signatureBase64Url":"%s"}
                                """.formatted(emergencyCode, invalidSignature)))
                .andExpect(status().isGone());
    }

    @Test
    void invalidEmergencyCodeIsRejectedEvenWithAValidChallengeSignature() throws Exception {
        KeyPair ownerKey = ed25519KeyPair();
        String accessToken = json(login(ownerEmail, OLD_PASSWORD)).path("accessToken").asText();
        enroll(ownerKey, accessToken, OLD_PASSWORD);
        String newEmail = "owner.wrong-code." + UUID.randomUUID().toString().substring(0, 8) + "@rutafija.test";
        JsonNode challenge = json(mockMvc.perform(post("/api/v1/auth/super-admin-recovery/challenges")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountEmail":"%s","newEmail":"%s"}
                                """.formatted(ownerEmail, newEmail)))
                .andExpect(status().isAccepted())
                .andReturn());
        UUID challengeId = UUID.fromString(challenge.path("challengeId").asText());
        String validSignature = sign(ownerKey, challenge.path("challengeToSignUtf8").asText());
        String wrongEmergencyCode = "A".repeat(43);

        mockMvc.perform(post(
                                "/api/v1/auth/super-admin-recovery/challenges/{challengeId}/proof",
                                challengeId
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emergencyCode":"%s","signatureBase64Url":"%s"}
                                """.formatted(wrongEmergencyCode, validSignature)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN_RECOVERY_INVALID"));

        Short attempts = jdbcTemplate.queryForObject(
                "select failed_proof_attempts from super_admin_recovery_challenge where id = ?",
                Short.class,
                challengeId
        );
        assertThat(attempts).isEqualTo((short) 1);
        Integer emailsSent = jdbcTemplate.queryForObject(
                "select count(*) from email_otp_challenge where user_id = ? and purpose = 'SUPER_ADMIN_RECOVERY'",
                Integer.class,
                owner.getId()
        );
        assertThat(emailsSent).isZero();
    }

    @Test
    void adminCannotEnrollTheRecoveryKeyAndAnEnrollmentCannotBeReplaced() throws Exception {
        KeyPair ownerKey = ed25519KeyPair();
        String ownerToken = json(login(ownerEmail, OLD_PASSWORD)).path("accessToken").asText();
        enroll(ownerKey, ownerToken, OLD_PASSWORD);

        Organization organization = organizationRepository.save(Organization.active(
                "Recovery Test Organization " + UUID.randomUUID(),
                "Recovery Test",
                "America/Lima"
        ));
        String adminEmail = "admin." + UUID.randomUUID().toString().substring(0, 8) + "@rutafija.test";
        AppUser admin = AppUser.organizationUser(
                organization,
                adminEmail,
                passwordEncoder.encode(OLD_PASSWORD),
                "Tenant Admin",
                UserRole.ADMIN
        );
        admin.verifyEmail(Instant.now());
        userRepository.save(admin);
        String adminToken = json(login(adminEmail, OLD_PASSWORD)).path("accessToken").asText();

        mockMvc.perform(post("/api/v1/auth/super-admin-recovery/enrollment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollmentBody(ownerKey, OLD_PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/auth/super-admin-recovery/enrollment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollmentBody(ownerKey, OLD_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPER_ADMIN_RECOVERY_ALREADY_ENROLLED"));
    }

    private JsonNode enroll(KeyPair keyPair, String accessToken, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/super-admin-recovery/enrollment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(enrollmentBody(keyPair, password)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        return json(result);
    }

    private String enrollmentBody(KeyPair keyPair, String password) throws Exception {
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        return """
                {"publicKeyX509Base64":"%s","currentPassword":"%s"}
                """.formatted(publicKey, password);
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String capturedOtp(String recipient) {
        org.mockito.ArgumentCaptor<String> code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq(recipient), code.capture(), any(Instant.class));
        assertThat(code.getValue()).matches("[0-9]{6}");
        return code.getValue();
    }

    private KeyPair ed25519KeyPair() throws Exception {
        return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    }

    private String sign(KeyPair keyPair, String payload) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(keyPair.getPrivate());
        signer.update(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
