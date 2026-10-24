package pe.rutafija.identity.recoverycli;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SuperAdminRecoveryCliTest {

    @TempDir
    Path tempDirectory;

    @Test
    void encryptsAndDecryptsEd25519PrivateKeyAsEncryptedPkcs8() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        byte[] privateKey = pair.getPrivate().getEncoded();
        char[] password = "local-test-password-123".toCharArray();

        byte[] encrypted = SuperAdminRecoveryCli.encryptPrivateKey(privateKey, password);

        assertThat(new String(encrypted, StandardCharsets.ISO_8859_1)).doesNotContain("Ed25519");
        assertThat(SuperAdminRecoveryCli.decryptPrivateKey(encrypted, password).getEncoded())
                .containsExactly(privateKey);
        assertThatThrownBy(() -> SuperAdminRecoveryCli.decryptPrivateKey(encrypted, "wrong-password".toCharArray()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void signsExactUtf8ChallengeBytesWithEnrolledPublicKey() throws Exception {
        var pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String challenge = String.join("\n", "ruta-fija-super-admin-recovery-v1", "instalación", "correo@example.test");
        byte[] exactBytes = challenge.getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(exactBytes);
        byte[] signatureBytes = signer.sign();

        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pair.getPublic());
        verifier.update(exactBytes);
        assertThat(verifier.verify(signatureBytes)).isTrue();

        verifier.initVerify(pair.getPublic());
        verifier.update((challenge + " ").getBytes(StandardCharsets.UTF_8));
        assertThat(verifier.verify(signatureBytes)).isFalse();
    }

    @Test
    void checksTemporaryEncryptedCopyOfflineWithoutShowingSecretsAndRejectsWrongPasswordOrPublicKey() throws Exception {
        KeyPair pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path copy=tempDirectory.resolve("synthetic-encrypted-copy.p12");
        String password="Synthetic-Copy-Password-2026";
        byte[] encrypted=SuperAdminRecoveryCli.encryptPrivateKey(pair.getPrivate().getEncoded(),password.toCharArray());
        try {
            SuperAdminRecoveryCli.writeRestrictedFile(copy,encrypted);
            Path publicFile=copy.resolveSibling(copy.getFileName()+".pub");
            Files.writeString(publicFile,Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),StandardCharsets.US_ASCII);
            ScriptedTerminal terminal=new ScriptedTerminal(new String[0],new String[]{password});
            SuperAdminRecoveryCli.checkEncryptedKey(copy,terminal);
            String fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(pair.getPublic().getEncoded()));
            assertThat(terminal.output()).contains("LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE","PUBLIC_KEY_SHA256="+fingerprint)
                    .doesNotContain(password,Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            ScriptedTerminal wrongPassword=new ScriptedTerminal(new String[0],new String[]{"Wrong-Synthetic-Password"});
            assertThatThrownBy(()->SuperAdminRecoveryCli.checkEncryptedKey(copy,wrongPassword)).isInstanceOf(Exception.class);
            assertThat(wrongPassword.output()).doesNotContain("LOCAL_ENCRYPTED_KEY_CHECK=PASS");
            KeyPair different=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            Files.writeString(publicFile,Base64.getEncoder().encodeToString(different.getPublic().getEncoded()),StandardCharsets.US_ASCII);
            ScriptedTerminal mismatched=new ScriptedTerminal(new String[0],new String[]{password});
            assertThatThrownBy(()->SuperAdminRecoveryCli.checkEncryptedKey(copy,mismatched)).isInstanceOf(Exception.class);
            assertThat(mismatched.output()).doesNotContain("LOCAL_ENCRYPTED_KEY_CHECK=PASS");
        } finally { Arrays.fill(encrypted,(byte)0); }
    }

    @Test
    void roundTripsEscapedCanonicalChallengeJsonWithoutChangingItsBytes() {
        String challenge = "linea\nclave=\"valor\"\\\u00f1";
        Map<String, String> value = new LinkedHashMap<>();
        value.put("challengeToSignUtf8", challenge);
        value.put("nonce", "YWJj");

        String json = SuperAdminRecoveryCli.encodeJsonObject(value);

        assertThat(SuperAdminRecoveryCli.parseFlatJsonObject(json)).containsEntry("challengeToSignUtf8", challenge);
    }

    @Test
    void rejectsDuplicateAndNestedJsonFields() {
        assertThatThrownBy(() -> SuperAdminRecoveryCli.parseFlatJsonObject("{\"id\":\"1\",\"id\":\"2\"}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SuperAdminRecoveryCli.parseFlatJsonObject("{\"nested\":{\"id\":\"1\"}}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void allowsPlainHttpOnlyForLoopbackApi() throws Exception {
        assertThat(SuperAdminRecoveryCli.validateApiBase("http://127.0.0.1:8080").getHost()).isEqualTo("127.0.0.1");
        assertThatThrownBy(() -> SuperAdminRecoveryCli.validateApiBase("http://example.com:8080"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void completesRecoveryAgainstLocalFakeApiWithoutEchoingSubmittedSecrets() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = tempDirectory.resolve("test-recovery.p12");
        char[] keyPassword = "local-test-password-123".toCharArray();
        byte[] encodedPrivateKey = pair.getPrivate().getEncoded();
        byte[] encryptedPrivateKey = SuperAdminRecoveryCli.encryptPrivateKey(encodedPrivateKey, keyPassword);
        SuperAdminRecoveryCli.writeRestrictedFile(privatePath, encryptedPrivateKey);
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        Files.writeString(privatePath.resolveSibling(privatePath.getFileName() + ".pub"), publicKey,
                StandardCharsets.US_ASCII);

        UUID challengeId = UUID.randomUUID();
        UUID installationId = UUID.randomUUID();
        UUID accountReference = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(240);
        String nonce = "AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA";
        String requestedEmail = "new-owner@example.test";
        String challengeText = String.join("\n", "ruta-fija-super-admin-recovery-v1",
                installationId.toString(), accountReference.toString(), requestedEmail,
                challengeId.toString(), nonce, Long.toString(expiresAt.toEpochMilli()));
        AtomicReference<Map<String, String>> proofRequest = new AtomicReference<>();
        AtomicReference<Map<String, String>> resendRequest = new AtomicReference<>();
        AtomicReference<Map<String, String>> completionRequest = new AtomicReference<>();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/super-admin-recovery/challenges", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if (path.endsWith("/challenges")) {
                    Map<String, String> body = new LinkedHashMap<>();
                    body.put("challengeId", challengeId.toString());
                    body.put("installationId", installationId.toString());
                    body.put("accountReference", accountReference.toString());
                    body.put("nonce", nonce);
                    body.put("expiresAt", expiresAt.toString());
                    body.put("challengeToSignUtf8", challengeText);
                    respond(exchange, 202, SuperAdminRecoveryCli.encodeJsonObject(body));
                } else if (path.endsWith("/proof")) {
                    Map<String, String> body = SuperAdminRecoveryCli.parseFlatJsonObject(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                    proofRequest.set(body);
                    Signature verifier = Signature.getInstance("Ed25519");
                    verifier.initVerify(pair.getPublic());
                    verifier.update(challengeText.getBytes(StandardCharsets.UTF_8));
                    boolean valid = verifier.verify(Base64.getUrlDecoder().decode(body.get("signatureBase64Url")));
                    if (!valid || !"E".repeat(43).equals(body.get("emergencyCode"))) {
                        throw new AssertionError("El cliente no envió la prueba esperada");
                    }
                    Map<String, String> response = new LinkedHashMap<>();
                    response.put("verificationToken", "verification-token-test-1234567890");
                    response.put("otpExpiresAt", Instant.now().plusSeconds(300).toString());
                    response.put("challengeExpiresAt", expiresAt.toString());
                    response.put("emailSent", "true");
                    respond(exchange, 202, SuperAdminRecoveryCli.encodeJsonObject(response));
                } else if (path.endsWith("/email/resend")) {
                    resendRequest.set(SuperAdminRecoveryCli.parseFlatJsonObject(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    Map<String, String> response = new LinkedHashMap<>();
                    response.put("verificationToken", "verification-token-resend-1234567890");
                    response.put("otpExpiresAt", Instant.now().plusSeconds(300).toString());
                    response.put("challengeExpiresAt", expiresAt.toString());
                    response.put("emailSent", "true");
                    respond(exchange, 200, SuperAdminRecoveryCli.encodeJsonObject(response));
                } else if (path.endsWith("/complete")) {
                    completionRequest.set(SuperAdminRecoveryCli.parseFlatJsonObject(
                            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                    Map<String, String> response = new LinkedHashMap<>();
                    response.put("recoveredEmail", requestedEmail);
                    response.put("recoveredAt", Instant.now().toString());
                    response.put("newEmergencyCodeShownOnce", "N".repeat(43));
                    respond(exchange, 200, SuperAdminRecoveryCli.encodeJsonObject(response));
                } else {
                    respond(exchange, 404, "{}");
                }
            } catch (Throwable failure) {
                serverFailure.set(failure);
                respond(exchange, 500, "{}");
            }
        });
        server.start();
        try {
            ScriptedTerminal terminal = new ScriptedTerminal(
                    new String[]{"owner@example.test", requestedEmail, "S", "S"},
                    new String[]{new String(keyPassword), "E".repeat(43), "012345", "NewPassword-123", "NewPassword-123"}
            );
            SuperAdminRecoveryCli.recover("http://127.0.0.1:" + server.getAddress().getPort(), privatePath, terminal);

            assertThat(serverFailure.get()).isNull();
            assertThat(proofRequest.get()).containsEntry("emergencyCode", "E".repeat(43));
            assertThat(resendRequest.get()).containsEntry("verificationToken", "verification-token-test-1234567890");
            assertThat(completionRequest.get())
                    .containsEntry("code", "012345")
                    .containsEntry("newPassword", "NewPassword-123")
                    .containsEntry("verificationToken", "verification-token-resend-1234567890");
            assertThat(terminal.output())
                    .contains("Recuperación completada")
                    .contains("N".repeat(43))
                    .doesNotContain("E".repeat(43), "012345", "NewPassword-123", new String(keyPassword));
        } finally {
            server.stop(0);
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(encodedPrivateKey, (byte) 0);
            Arrays.fill(encryptedPrivateKey, (byte) 0);
        }
    }

    @Test
    void enrollsOnlyThePublicKeyAndShowsTheEmergencyCodeOnce() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privatePath = tempDirectory.resolve("enrollment-test.p12");
        char[] keyPassword = "local-test-password-123".toCharArray();
        byte[] encrypted = SuperAdminRecoveryCli.encryptPrivateKey(pair.getPrivate().getEncoded(), keyPassword);
        SuperAdminRecoveryCli.writeRestrictedFile(privatePath, encrypted);
        String publicKey = Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
        Files.writeString(privatePath.resolveSibling(privatePath.getFileName() + ".pub"), publicKey,
                StandardCharsets.US_ASCII);
        String emergencyCode = "R".repeat(43);
        AtomicReference<Map<String, String>> enrollmentRequest = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/auth/super-admin-recovery/enrollment", exchange -> {
            try {
                authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
                enrollmentRequest.set(SuperAdminRecoveryCli.parseFlatJsonObject(
                        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                Map<String, String> response = new LinkedHashMap<>();
                response.put("installationId", UUID.randomUUID().toString());
                response.put("publicKeyFingerprintSha256", "a".repeat(64));
                response.put("emergencyCodeShownOnce", emergencyCode);
                response.put("enrolledAt", Instant.now().toString());
                respond(exchange, 201, SuperAdminRecoveryCli.encodeJsonObject(response));
            } catch (Throwable failure) {
                serverFailure.set(failure);
                respond(exchange, 500, "{}");
            }
        });
        server.start();
        try {
            ScriptedTerminal terminal = new ScriptedTerminal(new String[0],
                    new String[]{"local-test-password-123", "session-token-test", "CurrentPassword-123"});
            SuperAdminRecoveryCli.enroll("http://127.0.0.1:" + server.getAddress().getPort(), privatePath, terminal);

            assertThat(serverFailure.get()).isNull();
            assertThat(authorization.get()).isEqualTo("Bearer session-token-test");
            assertThat(enrollmentRequest.get())
                    .containsEntry("publicKeyX509Base64", publicKey)
                    .containsEntry("currentPassword", "CurrentPassword-123")
                    .doesNotContainValue(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            assertThat(terminal.output())
                    .contains(emergencyCode)
                    .doesNotContain("session-token-test", "CurrentPassword-123");
        } finally {
            server.stop(0);
            Arrays.fill(keyPassword, '\0');
            Arrays.fill(encrypted, (byte) 0);
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
        Arrays.fill(bytes, (byte) 0);
    }

    private static final class ScriptedTerminal implements SuperAdminRecoveryCli.Terminal {
        private final Queue<String> lines;
        private final Queue<char[]> secrets;
        private final StringBuilder output = new StringBuilder();

        private ScriptedTerminal(String[] lines, String[] secrets) {
            this.lines = new ArrayDeque<>(java.util.List.of(lines));
            this.secrets = new ArrayDeque<>();
            for (String secret : secrets) {
                this.secrets.add(secret.toCharArray());
            }
        }

        @Override
        public String readLine(String prompt) {
            output.append(prompt);
            return lines.remove();
        }

        @Override
        public char[] readSecret(String prompt) {
            output.append(prompt);
            return secrets.remove();
        }

        @Override
        public void println(String value) {
            output.append(value).append('\n');
        }

        @Override
        public void printf(String format, Object... values) {
            output.append(format.formatted(values));
        }

        private String output() {
            return output.toString();
        }
    }
}
