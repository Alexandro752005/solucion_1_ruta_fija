package pe.rutafija.identity.recoverycli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.support.NativePostgresIntegrationTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Runs the local recovery CLI against the real test-profile HTTP application and test database. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "debug=false",
                "logging.level.root=INFO",
                "logging.level.org.springframework=INFO",
                "logging.level.org.hibernate.SQL=INFO"
        }
)
@ActiveProfiles("test")
class SuperAdminRecoveryCliBackendIT extends NativePostgresIntegrationTest {

    private static final String INITIAL_PASSWORD = "Temporary-Owner-Password-2026";
    private static final String RECOVERED_PASSWORD = "Temporary-Recovered-Password-2026";
    private static final String KEY_PASSWORD = "Temporary-Encrypted-Key-Password-2026";

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    @Autowired
    AppUserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Autowired JdbcTemplate jdbc;

    @MockitoBean
    OtpEmailSender emailSender;

    @TempDir
    Path tempDirectory;

    @Test
    void cliCompletesRecoveryOverHttpUsingOnlyTemporaryAccountKeyAndMockedEmail() throws Exception {
        Path normalizedTempDirectory = tempDirectory.toAbsolutePath().normalize();
        Path projectRoot = Path.of("").toAbsolutePath().normalize();
        assertThat(normalizedTempDirectory.startsWith(projectRoot)).isFalse();

        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String initialEmail = "temporary-owner-" + suffix + "@rutafija.test";
        String recoveredEmail = "temporary-recovered-" + suffix + "@rutafija.test";
        AppUser owner = AppUser.superAdmin(
                initialEmail,
                passwordEncoder.encode(INITIAL_PASSWORD),
                "Temporary Recovery Test Owner"
        );
        owner.verifyEmail(Instant.now());
        userRepository.save(owner);

        KeyPair temporaryKeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privateKeyPath = normalizedTempDirectory.resolve("temporary-recovery-key.p12");
        char[] keyPassword = KEY_PASSWORD.toCharArray();
        byte[] encodedPrivateKey = temporaryKeyPair.getPrivate().getEncoded();
        byte[] encryptedPrivateKey = SuperAdminRecoveryCli.encryptPrivateKey(encodedPrivateKey, keyPassword);
        SuperAdminRecoveryCli.writeRestrictedFile(privateKeyPath, encryptedPrivateKey);
        Arrays.fill(encodedPrivateKey, (byte) 0);
        Arrays.fill(encryptedPrivateKey, (byte) 0);
        Files.writeString(
                privateKeyPath.resolveSibling(privateKeyPath.getFileName() + ".pub"),
                Base64.getEncoder().encodeToString(temporaryKeyPair.getPublic().getEncoded()),
                StandardCharsets.US_ASCII
        );

        AtomicReference<String> capturedOtp = new AtomicReference<>();
        AtomicReference<String> capturedRecipient = new AtomicReference<>();
        doAnswer(invocation -> {
            capturedRecipient.set(invocation.getArgument(0));
            capturedOtp.set(invocation.getArgument(1));
            return null;
        }).when(emailSender).sendVerificationCode(anyString(), anyString(), any(Instant.class));

        ScriptedTerminal terminal = null;
        try {
            String apiBase = "http://127.0.0.1:" + port;
            ResponseEntity<JsonNode> loginResponse = postJson(apiBase + "/api/v1/auth/login", Map.of(
                    "email", initialEmail,
                    "password", INITIAL_PASSWORD
            ), null);
            JsonNode loginBody = loginResponse.getBody();
            assertThat(loginBody).isNotNull();
            String accessToken = loginBody.path("accessToken").asText();
            assertThat(!accessToken.isBlank()).isTrue();

            terminal = new ScriptedTerminal(
                    initialEmail,
                    recoveredEmail,
                    keyPassword,
                    accessToken,
                    capturedOtp,
                    INITIAL_PASSWORD,
                    RECOVERED_PASSWORD
            );
            if (loginBody instanceof ObjectNode objectNode) {
                objectNode.put("accessToken", "");
            }
            SuperAdminRecoveryCli.enroll(apiBase, privateKeyPath, terminal);
            assertThat(terminal.hasCapturedEmergencyCode()).isTrue();
            String enrolledPublicKey = jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1", String.class);
            // Exercise an encrypted backup COPY of a temporary key. This does
            // not create or certify any actual owner's custody media.
            Path copyDirectory = Files.createDirectory(normalizedTempDirectory.resolve("temporary-encrypted-copy"));
            Path copiedKey = copyDirectory.resolve("temporary-copy.p12");
            byte[] encryptedCopy = Files.readAllBytes(privateKeyPath);
            try { SuperAdminRecoveryCli.writeRestrictedFile(copiedKey, encryptedCopy); }
            finally { Arrays.fill(encryptedCopy, (byte) 0); }
            Files.copy(privateKeyPath.resolveSibling(privateKeyPath.getFileName()+".pub"), copiedKey.resolveSibling(copiedKey.getFileName()+".pub"));
            SuperAdminRecoveryCli.recover(apiBase, copiedKey, terminal);

            assertThat(terminal.shownCodes).isEqualTo(2);
            assertThat(Arrays.equals(terminal.capturedEmergencyCode,terminal.rotatedEmergencyCode)).isFalse();
            String storedHash=jdbc.queryForObject("select emergency_code_hash from super_admin_recovery_credential where id=1",String.class);
            assertThat(passwordEncoder.matches(new String(terminal.capturedEmergencyCode),storedHash)).isFalse();
            assertThat(passwordEncoder.matches(new String(terminal.rotatedEmergencyCode),storedHash)).isTrue();
            assertThat(jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1",String.class)).isEqualTo(enrolledPublicKey);
            HttpHeaders staleHeaders=new HttpHeaders();staleHeaders.setBearerAuth(accessToken);
            assertThat(http.exchange(apiBase+"/api/v1/auth/me",HttpMethod.GET,new HttpEntity<>(staleHeaders),JsonNode.class).getStatusCode().value()).isEqualTo(401);
            String refreshCookie=loginResponse.getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE).stream()
                    .filter(value->value.startsWith("rf_refresh=")).findFirst().orElseThrow().split(";",2)[0];
            HttpHeaders cookieHeaders=new HttpHeaders();cookieHeaders.setContentType(MediaType.APPLICATION_JSON);cookieHeaders.set(HttpHeaders.COOKIE,refreshCookie);
            assertThat(http.exchange(apiBase+"/api/v1/auth/refresh",HttpMethod.POST,new HttpEntity<>(Map.of(),cookieHeaders),JsonNode.class).getStatusCode().value()).isEqualTo(401);
            UUID consumedChallenge=jdbc.queryForObject("select id from super_admin_recovery_challenge where consumed_at is not null",UUID.class);
            assertThat(postJson(apiBase+"/api/v1/auth/super-admin-recovery/challenges/"+consumedChallenge+"/proof",Map.of(
                    "emergencyCode",new String(terminal.capturedEmergencyCode),
                    "signatureBase64Url",Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[64])),null).getStatusCode().value()).isEqualTo(410);

            assertThat(capturedRecipient.get()).isEqualTo(recoveredEmail);
            assertThat(capturedOtp.get() != null && capturedOtp.get().matches("[0-9]{6}")).isTrue();
            verify(emailSender, times(1)).sendVerificationCode(anyString(), anyString(), any(Instant.class));

            JsonNode recoveredLogin = postJson(apiBase + "/api/v1/auth/login", Map.of(
                    "email", recoveredEmail,
                    "password", RECOVERED_PASSWORD
            ), null).getBody();
            assertThat(recoveredLogin).isNotNull();
            assertThat(!recoveredLogin.path("accessToken").asText().isBlank()).isTrue();
            System.out.println("TEMPORARY_SA_CLI_ENCRYPTED_COPY_RECOVERY=PASS_NEW_EMAIL_PASSWORD_OLD_SESSIONS_REVOKED_KEY_UNCHANGED_TWO_ONE_TIME_CODES");
        } finally {
            Arrays.fill(keyPassword, '\0');
            if (terminal != null) {
                terminal.clearSecrets();
            }
            capturedOtp.set(null);
            capturedRecipient.set(null);
            clearInvocations(emailSender);
        }
    }

    private ResponseEntity<JsonNode> postJson(String url, Map<String, String> body, String bearerToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        headers.setCacheControl("no-store");
        if (bearerToken != null) {
            headers.setBearerAuth(bearerToken);
        }
        return http.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                JsonNode.class
        );
    }

    private static final class ScriptedTerminal implements SuperAdminRecoveryCli.Terminal {

        private final String initialEmail;
        private final String recoveredEmail;
        private final char[] keyPassword;
        private final char[] accessToken;
        private final AtomicReference<String> capturedOtp;
        private final char[] initialPassword;
        private final char[] recoveredPassword;
        private char[] capturedEmergencyCode;
        private char[] rotatedEmergencyCode;
        private int shownCodes;
        private int lineReadCount;
        private int secretReadCount;

        private ScriptedTerminal(
                String initialEmail,
                String recoveredEmail,
                char[] keyPassword,
                String accessToken,
                AtomicReference<String> capturedOtp,
                String initialPassword,
                String recoveredPassword
        ) {
            this.initialEmail = initialEmail;
            this.recoveredEmail = recoveredEmail;
            this.keyPassword = keyPassword.clone();
            this.accessToken = accessToken.toCharArray();
            this.capturedOtp = capturedOtp;
            this.initialPassword = initialPassword.toCharArray();
            this.recoveredPassword = recoveredPassword.toCharArray();
        }

        @Override
        public String readLine(String prompt) {
            return switch (lineReadCount++) {
                case 0 -> initialEmail;
                case 1 -> recoveredEmail;
                case 2 -> "S";
                case 3 -> "N";
                default -> throw new IllegalStateException("Unexpected recovery CLI line prompt");
            };
        }

        @Override
        public char[] readSecret(String prompt) {
            return switch (secretReadCount++) {
                case 0 -> keyPassword.clone();
                case 1 -> accessToken.clone();
                case 2 -> initialPassword.clone();
                case 3 -> keyPassword.clone();
                case 4 -> {
                    if (capturedEmergencyCode == null) {
                        throw new IllegalStateException("CLI enrollment did not retain its one-time code");
                    }
                    yield capturedEmergencyCode.clone();
                }
                case 5 -> {
                    String code = capturedOtp.get();
                    if (code == null) {
                        throw new IllegalStateException("Mock email provider did not capture the OTP");
                    }
                    yield code.toCharArray();
                }
                case 6, 7 -> recoveredPassword.clone();
                default -> throw new IllegalStateException("Unexpected recovery CLI secret prompt");
            };
        }

        @Override
        public void println(String value) {
            if (value.matches("[A-Za-z0-9_-]{43}")) {
                shownCodes++;
                if (capturedEmergencyCode == null) capturedEmergencyCode = value.toCharArray();
                else if (rotatedEmergencyCode == null) rotatedEmergencyCode = value.toCharArray();
                else throw new IllegalStateException("Unexpected third emergency-code presentation");
            }
            // Discard CLI output so no one-time code is written to the test log.
        }

        @Override
        public void printf(String format, Object... values) {
            // Discard CLI output so account and recovery data are not retained in logs.
        }

        private boolean hasCapturedEmergencyCode() {
            if (capturedEmergencyCode == null || capturedEmergencyCode.length != 43) {
                return false;
            }
            for (char character : capturedEmergencyCode) {
                if (!((character >= 'A' && character <= 'Z')
                        || (character >= 'a' && character <= 'z')
                        || (character >= '0' && character <= '9')
                        || character == '_' || character == '-')) {
                    return false;
                }
            }
            return true;
        }

        private void clearSecrets() {
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(accessToken, '\0');
            Arrays.fill(initialPassword, '\0');
            Arrays.fill(recoveredPassword, '\0');
            if (capturedEmergencyCode != null) {
                Arrays.fill(capturedEmergencyCode, '\0');
                capturedEmergencyCode = null;
            }
            if (rotatedEmergencyCode != null) { Arrays.fill(rotatedEmergencyCode,'\0');rotatedEmergencyCode=null; }
        }
    }
}
