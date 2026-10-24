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
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

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
class EmailOtpFlowIT extends NativePostgresIntegrationTest {

    private static final String PASSWORD = "Email-Otp-Password-2026";

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

    @MockitoBean
    OtpEmailSender emailSender;

    private AppUser user;
    private String email;

    @BeforeEach
    void prepareUnverifiedUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "otp." + suffix + "@rutafija.test";
        Organization organization = organizationRepository.save(Organization.active(
                "Organización OTP " + suffix,
                "Org OTP",
                "America/Lima"
        ));
        user = userRepository.save(AppUser.organizationUser(
                organization,
                email,
                passwordEncoder.encode(PASSWORD),
                "Usuario OTP de integración",
                UserRole.ADMIN
        ));
    }

    @Test
    void initialLoginStoresOnlyHashesAndVerificationCompletesOneSessionOnce() throws Exception {
        MvcResult pending = beginOtp();
        JsonNode pendingBody = json(pending);
        String verificationToken = pendingBody.at("/verification/verificationToken").asText();
        String code = capturedCode();

        assertThat(verificationToken).isNotBlank();
        assertThat(pendingBody.path("emailVerificationRequired").asBoolean()).isTrue();
        assertThat(pendingBody.path("accessToken").isMissingNode()
                || pendingBody.path("accessToken").isNull()).isTrue();
        assertThat(pending.getResponse().getHeader(HttpHeaders.SET_COOKIE))
                .contains("rf_refresh=")
                .contains("Max-Age=0");
        assertThat(pending.getResponse().getHeader(HttpHeaders.CACHE_CONTROL)).contains("no-store");
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();

        String otpHash = jdbcTemplate.queryForObject(
                "select otp_hash from email_otp_challenge where user_id = ?",
                String.class,
                user.getId()
        );
        String tokenHash = jdbcTemplate.queryForObject(
                "select verification_token_hash from email_otp_challenge where user_id = ?",
                String.class,
                user.getId()
        );
        assertThat(otpHash).matches("[0-9a-f]{64}").isNotEqualTo(code);
        assertThat(tokenHash).matches("[0-9a-f]{64}").isNotEqualTo(verificationToken);

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(verificationToken, code)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().exists(HttpHeaders.SET_COOKIE))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isTrue();
        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(verificationToken, code)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
    }

    @Test
    void resendInvalidatesThePreviousOtpAndVerificationToken() throws Exception {
        JsonNode pending = json(beginOtp());
        String oldToken = pending.at("/verification/verificationToken").asText();
        String oldCode = capturedCode();

        JsonNode resent = json(resend(oldToken));
        String currentToken = resent.path("verificationToken").asText();
        assertThat(currentToken).isNotBlank().isNotEqualTo(oldToken);

        org.mockito.ArgumentCaptor<String> codes = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender, times(2)).sendVerificationCode(eq(email), codes.capture(), any(Instant.class));
        String currentCode = codes.getAllValues().get(1);
        assertThat(currentCode).matches("[0-9]{6}");

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(oldToken, oldCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(currentToken, currentCode)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void twoInvalidCodesPersistTheLockAndBlockFurtherValidation() throws Exception {
        MvcResult pending = beginOtp();
        String verificationToken = json(pending).at("/verification/verificationToken").asText();
        String actualCode = capturedCode();
        String invalidCode = "000000".equals(actualCode) ? "000001" : "000000";

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(verificationToken, invalidCode)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(verificationToken, invalidCode)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));

        mockMvc.perform(post("/api/v1/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(verifyBody(verificationToken, actualCode)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_LOCKED"));

        Short failures = jdbcTemplate.queryForObject(
                "select failed_attempts from email_otp_challenge where user_id = ?",
                Short.class,
                user.getId()
        );
        assertThat(failures).isEqualTo((short) 2);
        assertThat(userRepository.findById(user.getId()).orElseThrow().isEmailVerified()).isFalse();
    }

    @Test
    void initialSendAndTwoResendsExhaustTheIndependentRollingWindow() throws Exception {
        JsonNode pending = json(beginOtp());
        String verificationToken = pending.at("/verification/verificationToken").asText();

        JsonNode firstResend = json(resend(verificationToken));
        JsonNode secondResend = json(resend(firstResend.path("verificationToken").asText()));
        String currentToken = secondResend.path("verificationToken").asText();

        mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"%s\"}".formatted(currentToken)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("EMAIL_OTP_RATE_LIMITED"));

        verify(emailSender, times(3)).sendVerificationCode(eq(email), any(String.class), any(Instant.class));
        Integer sends = jdbcTemplate.queryForObject(
                "select count(*) from (select send_at_1 from email_otp_challenge where user_id = ? "
                        + "union all select send_at_2 from email_otp_challenge where user_id = ? "
                        + "union all select send_at_3 from email_otp_challenge where user_id = ?) s "
                        + "where send_at_1 is not null",
                Integer.class,
                user.getId(),
                user.getId(),
                user.getId()
        );
        assertThat(sends).isEqualTo(3);
    }

    private MvcResult beginOtp() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")
                ))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andReturn();
    }

    private MvcResult resend(String verificationToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/otp/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"verificationToken\":\"%s\"}".formatted(verificationToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
    }

    private String capturedCode() {
        org.mockito.ArgumentCaptor<String> code = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(emailSender).sendVerificationCode(eq(email), code.capture(), any(Instant.class));
        assertThat(code.getValue()).matches("[0-9]{6}");
        return code.getValue();
    }

    private String verifyBody(String verificationToken, String code) {
        return """
                {"verificationToken":"%s","code":"%s"}
                """.formatted(verificationToken, code);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
