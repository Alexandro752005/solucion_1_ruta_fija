package pe.rutafija.identity.recoverycli;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.Console;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interactive local client for the existing SUPER_ADMIN recovery API.
 * It never sends a private key to the server and never accepts secrets as arguments.
 */
public final class SuperAdminRecoveryCli {

    private static final String KEYSTORE_ALIAS = "super-admin-recovery-ed25519-pkcs8";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<AclEntryPermission> PRIVATE_FILE_ACL = EnumSet.allOf(AclEntryPermission.class);

    private SuperAdminRecoveryCli() {
    }

    public static void main(String[] args) {
        Console console = System.console();
        if (console == null) {
            System.err.println("Ejecuta esta utilidad desde una terminal interactiva; no admite entrada redirigida.");
            System.exit(2);
            return;
        }
        Terminal terminal = new ConsoleTerminal(console);
        try {
            if (args.length == 2 && "keygen".equals(args[0])) {
                generateKeyPair(Path.of(args[1]), terminal);
            } else if (args.length == 2 && "check-key".equals(args[0])) {
                checkEncryptedKey(Path.of(args[1]), terminal);
            } else if (args.length == 3 && "enroll".equals(args[0])) {
                enroll(args[1], Path.of(args[2]), terminal);
            } else if (args.length == 3 && "recover".equals(args[0])) {
                recover(args[1], Path.of(args[2]), terminal);
            } else {
                printUsage();
                System.exit(2);
            }
        } catch (HttpFailure failure) {
            System.err.printf("La API rechazó la operación (HTTP %d). No se imprimió su cuerpo ni ningún secreto.%n",
                    failure.statusCode());
            System.exit(1);
        } catch (Exception failure) {
            // Do not print provider, request, cryptographic, or filesystem exception text.
            System.err.println("La operación no se completó. Revisa el estado local sin copiar secretos a registros.");
            System.exit(1);
        }
    }

    static byte[] encryptPrivateKey(byte[] pkcs8, char[] password) throws GeneralSecurityException {
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, password);
            // The standard PKCS#12 secret-key container encrypts this opaque PKCS#8 value at rest.
            SecretKey entryValue = new SecretKeySpec(pkcs8, "Ed25519");
            keyStore.setEntry(KEYSTORE_ALIAS,
                    new KeyStore.SecretKeyEntry(entryValue),
                    new KeyStore.PasswordProtection(password));
            ByteArrayOutputStream encoded = new ByteArrayOutputStream();
            keyStore.store(encoded, password);
            return encoded.toByteArray();
        } catch (IOException exception) {
            throw new GeneralSecurityException("No se pudo codificar el contenedor PKCS#12", exception);
        }
    }

    static PrivateKey decryptPrivateKey(byte[] encryptedPkcs8, char[] password)
            throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(new ByteArrayInputStream(encryptedPkcs8), password);
        KeyStore.Entry entry = keyStore.getEntry(KEYSTORE_ALIAS, new KeyStore.PasswordProtection(password));
        if (!(entry instanceof KeyStore.SecretKeyEntry secretKeyEntry)
                || !"Ed25519".equalsIgnoreCase(secretKeyEntry.getSecretKey().getAlgorithm())) {
            throw new GeneralSecurityException("El contenedor no tiene una entrada Ed25519 admitida");
        }
        byte[] encoded = secretKeyEntry.getSecretKey().getEncoded();
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        } finally {
            wipe(encoded);
        }
    }

    static Map<String, String> parseFlatJsonObject(String json) {
        return new FlatJsonObjectParser(json).parse();
    }

    static String encodeJsonObject(Map<String, String> values) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            appendJsonString(json, entry.getKey());
            json.append(':');
            if (entry.getValue() == null) {
                json.append("null");
            } else {
                appendJsonString(json, entry.getValue());
            }
        }
        return json.append('}').toString();
    }

    static URI validateApiBase(String rawBase) throws Exception {
        URI uri = URI.create(rawBase).normalize();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!"http".equals(scheme) && !"https".equals(scheme))
                || uri.getHost() == null
                || (uri.getPath() != null && !uri.getPath().isBlank() && !"/".equals(uri.getPath()))
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null) {
            throw new IllegalArgumentException("La URL de API no es válida");
        }
        if ("http".equals(scheme)) {
            String host = uri.getHost().replace("[", "").replace("]", "").toLowerCase(Locale.ROOT);
            if (!("localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host))) {
                InetAddress address = InetAddress.getByName(host);
                if (!address.isLoopbackAddress()) {
                    throw new IllegalArgumentException("HTTP sin TLS solo se permite en loopback");
                }
            }
        }
        return uri;
    }

    static void generateKeyPair(Path requestedPrivatePath, Terminal terminal) throws Exception {
        Path privatePath = validatePrivateKeyPath(requestedPrivatePath);
        Path publicPath = publicKeyPath(privatePath);
        if (Files.exists(privatePath) || Files.exists(publicPath)) {
            throw new IOException("Los archivos de clave ya existen");
        }
        char[] password = readSecret(terminal, "Contraseña de cifrado local (mínimo 12 caracteres): ");
        char[] confirmation = readSecret(terminal, "Repite la contraseña de cifrado local: ");
        byte[] privateBytes = null;
        byte[] encryptedBytes = null;
        try {
            if (password.length < 12 || !constantTimeEquals(password, confirmation)) {
                throw new IllegalArgumentException("La contraseña local no cumple la confirmación requerida");
            }
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            KeyPair pair = generator.generateKeyPair();
            privateBytes = pair.getPrivate().getEncoded();
            encryptedBytes = encryptPrivateKey(privateBytes, password);
            writeRestrictedFile(privatePath, encryptedBytes);
            try {
                Files.writeString(publicPath, Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()),
                        StandardCharsets.US_ASCII);
            } catch (IOException exception) {
                Files.deleteIfExists(privatePath);
                throw exception;
            }
            terminal.printf("Par Ed25519 creado. Clave privada cifrada: %s%n", privatePath);
            terminal.printf("Clave pública X.509 Base64: %s%n", publicPath);
            terminal.println("No inscribas una clave real hasta confirmar la custodia del archivo y su copia separada.");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
            wipe(privateBytes);
            wipe(encryptedBytes);
        }
    }

    static void checkEncryptedKey(Path requestedPrivatePath, Terminal terminal) throws Exception {
        Path privatePath=validatePrivateKeyPath(requestedPrivatePath);
        PublicKey publicKey=decodePublicKey(Files.readString(publicKeyPath(privatePath),StandardCharsets.US_ASCII).strip());
        char[] password=readSecret(terminal,"Contraseña del archivo cifrado que se comprobará localmente: ");
        byte[] encrypted=null;
        try {
            encrypted=Files.readAllBytes(privatePath);
            verifyKeyPair(decryptPrivateKey(encrypted,password),publicKey);
            String fingerprint=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded()));
            terminal.println("LOCAL_ENCRYPTED_KEY_CHECK=PASS_NO_API_NO_MAIL_NO_EMERGENCY_CODE");
            terminal.println("PUBLIC_KEY_SHA256="+fingerprint);
        } finally { Arrays.fill(password,'\0');wipe(encrypted); }
    }

    static void enroll(String rawBase, Path requestedPrivatePath, Terminal terminal) throws Exception {
        URI apiBase = validateApiBase(rawBase);
        Path privatePath = validatePrivateKeyPath(requestedPrivatePath);
        Path publicPath = publicKeyPath(privatePath);
        String publicKey = Files.readString(publicPath, StandardCharsets.US_ASCII).strip();
        PublicKey decodedPublic = decodePublicKey(publicKey);
        char[] keyPassword = readSecret(terminal, "Contraseña del archivo de clave privada: ");
        byte[] encryptedKey = Files.readAllBytes(privatePath);
        PrivateKey privateKey;
        try {
            privateKey = decryptPrivateKey(encryptedKey, keyPassword);
            verifyKeyPair(privateKey, decodedPublic);
        } finally {
            Arrays.fill(keyPassword, '\0');
            wipe(encryptedKey);
        }
        char[] tokenChars = readSecret(terminal, "Token de sesión SUPER_ADMIN (entrada oculta): ");
        char[] password = null;
        try {
            password = readSecret(terminal, "Contraseña actual de SUPER_ADMIN: ");
            String token = new String(tokenChars);
            Map<String, String> request = Map.of(
                    "publicKeyX509Base64", publicKey,
                    "currentPassword", new String(password)
            );
            Map<String, String> response = post(apiBase,
                    "/api/v1/auth/super-admin-recovery/enrollment", request, token);
            String fingerprint = required(response, "publicKeyFingerprintSha256");
            String emergencyCode = required(response, "emergencyCodeShownOnce");
            if (!emergencyCode.matches("[A-Za-z0-9_-]{43}")) {
                throw new IllegalStateException("La API devolvió un código de emergencia con formato inesperado");
            }
            terminal.printf("Clave pública inscrita. Instalación: %s%n", required(response, "installationId"));
            terminal.printf("Huella SHA-256: %s%n", fingerprint);
            terminal.println("Código de emergencia (mostrar una sola vez; guardar aparte de la clave privada):");
            terminal.println(emergencyCode);
            terminal.println("La clave pública utilizada procede del archivo local: " + publicPath);
        } finally {
            Arrays.fill(tokenChars, '\0');
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    static void recover(String rawBase, Path requestedPrivatePath, Terminal terminal) throws Exception {
        URI apiBase = validateApiBase(rawBase);
        Path privatePath = validatePrivateKeyPath(requestedPrivatePath);
        Path publicPath = publicKeyPath(privatePath);
        String publicKeyBase64 = Files.readString(publicPath, StandardCharsets.US_ASCII).strip();
        PublicKey publicKey = decodePublicKey(publicKeyBase64);

        char[] accountEmailChars = terminal.readLine("Correo de la cuenta SUPER_ADMIN: ").toCharArray();
        char[] newEmailChars = terminal.readLine("Correo nuevo: ").toCharArray();
        char[] keyPassword = readSecret(terminal, "Contraseña del archivo de clave: ");
        byte[] encryptedKeyBytes = Files.readAllBytes(privatePath);
        PrivateKey privateKey;
        try {
            privateKey = decryptPrivateKey(encryptedKeyBytes, keyPassword);
        } finally {
            Arrays.fill(keyPassword, '\0');
            wipe(encryptedKeyBytes);
        }

        String accountEmail = new String(accountEmailChars).strip();
        String newEmail = new String(newEmailChars).strip();
        Arrays.fill(accountEmailChars, '\0');
        Arrays.fill(newEmailChars, '\0');
        Map<String, String> startRequest = Map.of("accountEmail", accountEmail, "newEmail", newEmail);
        Map<String, String> challenge = post(apiBase,
                "/api/v1/auth/super-admin-recovery/challenges", startRequest, null);

        UUID challengeId = UUID.fromString(required(challenge, "challengeId"));
        Instant expiresAt = Instant.parse(required(challenge, "expiresAt"));
        String challengeText = required(challenge, "challengeToSignUtf8");
        Instant now = Instant.now();
        if (!expiresAt.isAfter(now) || Duration.between(now, expiresAt).compareTo(Duration.ofMinutes(5).plusSeconds(10)) > 0) {
            throw new IllegalStateException("El reto venció o no tiene una vigencia admitida; inicia una solicitud nueva");
        }

        terminal.println("Revisa los datos exactos del reto antes de firmarlo:");
        terminal.printf("Instalación: %s%nCuenta de recuperación: %s%nCorreo nuevo: %s%nVence: %s%n",
                required(challenge, "installationId"), required(challenge, "accountReference"), newEmail, expiresAt);
        terminal.println("Bytes UTF-8 representados por el reto (se firmarán sin reconstruirlos):");
        terminal.println(escapeForTerminal(challengeText));
        if (!"S".equalsIgnoreCase(terminal.readLine("¿Firmar y continuar? Escribe S para confirmar: ").strip())) {
            throw new IllegalStateException("La firma fue cancelada por el titular");
        }

        byte[] challengeBytes = challengeText.getBytes(StandardCharsets.UTF_8);
        byte[] signatureBytes;
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(challengeBytes);
        signatureBytes = signer.sign();
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(publicKey);
        verifier.update(challengeBytes);
        if (!verifier.verify(signatureBytes)) {
            wipe(challengeBytes);
            wipe(signatureBytes);
            throw new IllegalStateException("La clave privada no corresponde a la clave pública local");
        }
        String signatureBase64Url = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
        wipe(challengeBytes);
        wipe(signatureBytes);

        char[] emergencyCodeChars = readSecret(terminal, "Código de emergencia de 43 caracteres: ");
        String emergencyCode = new String(emergencyCodeChars);
        Arrays.fill(emergencyCodeChars, '\0');
        if (!emergencyCode.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("El código de emergencia no tiene el formato admitido");
        }
        Map<String, String> proofRequest = Map.of(
                "emergencyCode", emergencyCode,
                "signatureBase64Url", signatureBase64Url
        );
        Map<String, String> proof = post(apiBase,
                "/api/v1/auth/super-admin-recovery/challenges/" + challengeId + "/proof",
                proofRequest, null);
        if (!"true".equals(proof.get("emailSent"))) {
            throw new IllegalStateException("La API no confirmó el envío del OTP; no se solicitará el código al titular");
        }
        String verificationToken = required(proof, "verificationToken");
        Instant otpExpiresAt = Instant.parse(required(proof, "otpExpiresAt"));
        terminal.printf("La API confirmó el envío. El OTP vence: %s%n", otpExpiresAt);
        if ("S".equalsIgnoreCase(terminal.readLine(
                "¿Solicitar un único reenvío si el primer mensaje no llegó? Escribe S para reenviar: ").strip())) {
            Map<String, String> resend = post(apiBase,
                    "/api/v1/auth/super-admin-recovery/challenges/" + challengeId + "/email/resend",
                    Map.of("verificationToken", verificationToken), null);
            if (!"true".equals(resend.get("emailSent"))) {
                throw new IllegalStateException("La API no confirmó el reenvío del OTP");
            }
            verificationToken = required(resend, "verificationToken");
            otpExpiresAt = Instant.parse(required(resend, "otpExpiresAt"));
            terminal.printf("La API confirmó el reenvío. El OTP vence: %s%n", otpExpiresAt);
        }
        char[] otpChars = readSecret(terminal, "OTP recibido por el nuevo correo: ");
        char[] newPasswordChars = readSecret(terminal, "Nueva contraseña (mínimo 12 caracteres): ");
        char[] passwordConfirmation = readSecret(terminal, "Repite la nueva contraseña: ");
        try {
            String code = new String(otpChars);
            if (!code.matches("[0-9]{6}") || newPasswordChars.length < 12 || newPasswordChars.length > 128
                    || !constantTimeEquals(newPasswordChars, passwordConfirmation)) {
                throw new IllegalArgumentException("El OTP o la confirmación de contraseña no cumple el formato");
            }
            Map<String, String> completionRequest = Map.of(
                    "verificationToken", verificationToken,
                    "code", code,
                    "newPassword", new String(newPasswordChars)
            );
            Map<String, String> completion = post(apiBase,
                    "/api/v1/auth/super-admin-recovery/challenges/" + challengeId + "/complete",
                    completionRequest, null);
            if (!newEmail.equalsIgnoreCase(required(completion, "recoveredEmail"))) {
                throw new IllegalStateException("La API confirmó un correo distinto al solicitado; guarda la respuesta para soporte");
            }
            String newEmergencyCode = required(completion, "newEmergencyCodeShownOnce");
            if (!newEmergencyCode.matches("[A-Za-z0-9_-]{43}")) {
                throw new IllegalStateException("La API devolvió un código rotado con formato inesperado");
            }
            terminal.println("Recuperación completada. Código de emergencia nuevo (mostrar una sola vez):");
            terminal.println(newEmergencyCode);
            terminal.println("La clave pública local no se modificó. Guarda el código en una ubicación distinta.");
        } finally {
            Arrays.fill(otpChars, '\0');
            Arrays.fill(newPasswordChars, '\0');
            Arrays.fill(passwordConfirmation, '\0');
        }
    }

    private static Map<String, String> post(URI base, String path, Map<String, String> payload, String bearerToken)
            throws Exception {
        URI endpoint = URI.create(stripTrailingSlash(base.toString()) + path);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        byte[] requestBody = encodeJsonObject(payload).getBytes(StandardCharsets.UTF_8);
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Cache-Control", "no-store")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody));
        if (bearerToken != null) {
            request.header("Authorization", "Bearer " + bearerToken);
        }
        try {
            HttpResponse<byte[]> response = client.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            try {
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new HttpFailure(response.statusCode());
                }
                return parseFlatJsonObject(new String(response.body(), StandardCharsets.UTF_8));
            } finally {
                wipe(response.body());
            }
        } finally {
            wipe(requestBody);
        }
    }

    private static Path validatePrivateKeyPath(Path requested) throws IOException {
        Path path = requested.toAbsolutePath().normalize();
        if (path.getParent() == null || path.getFileName() == null || Files.isDirectory(path)) {
            throw new IOException("La ruta de clave debe ser un archivo absoluto");
        }
        Path projectRoot = findProjectRoot();
        if (path.startsWith(projectRoot)) {
            throw new IOException("La clave privada debe guardarse fuera del repositorio");
        }
        return path;
    }

    private static Path findProjectRoot() {
        List<Path> starts = new java.util.ArrayList<>();
        starts.add(Path.of("").toAbsolutePath().normalize());
        try {
            Path codeLocation = Path.of(SuperAdminRecoveryCli.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI()).toAbsolutePath().normalize();
            starts.add(Files.isDirectory(codeLocation) ? codeLocation : codeLocation.getParent());
        } catch (Exception ignored) {
            // The current directory remains the fallback for unusual class loaders.
        }
        for (Path start : starts) {
            Path candidate = start;
            while (candidate != null) {
                if (Files.exists(candidate.resolve("backend/pom.xml"))
                        && Files.exists(candidate.resolve("specs/registro.md"))) {
                    return candidate;
                }
                candidate = candidate.getParent();
            }
        }
        return Path.of("").toAbsolutePath().normalize();
    }

    private static Path publicKeyPath(Path privatePath) {
        return privatePath.resolveSibling(privatePath.getFileName() + ".pub");
    }

    static void writeRestrictedFile(Path path, byte[] content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".ruta-fija-recovery-", ".tmp");
        try {
            Files.write(temporary, content);
            restrictFile(temporary);
            Files.move(temporary, path);
            restrictFile(path);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void restrictFile(Path path) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView != null) {
            var owner = Files.getOwner(path);
            AclEntry ownerEntry = AclEntry.newBuilder()
                    .setType(AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(PRIVATE_FILE_ACL)
                    .build();
            aclView.setAcl(List.of(ownerEntry));
            return;
        }
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
            return;
        }
        throw new IOException("El sistema de archivos no permite restringir los permisos de la clave");
    }

    private static PublicKey decodePublicKey(String encoded) throws GeneralSecurityException {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException exception) {
            throw new GeneralSecurityException("La clave pública local no es Base64", exception);
        }
        if (bytes.length != 44) {
            throw new GeneralSecurityException("La clave pública no es X.509 Ed25519");
        }
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(bytes));
    }

    private static void verifyKeyPair(PrivateKey privateKey, PublicKey publicKey) throws GeneralSecurityException {
        byte[] proof = "ruta-fija-super-admin-recovery-local-key-check-v1".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(proof);
        byte[] signed = signer.sign();
        try {
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(proof);
            if (!verifier.verify(signed)) {
                throw new GeneralSecurityException("La clave pública y privada locales no corresponden");
            }
        } finally {
            wipe(proof);
            wipe(signed);
        }
    }

    private static char[] readSecret(Terminal terminal, String prompt) {
        char[] value = terminal.readSecret(prompt);
        if (value == null) {
            throw new IllegalStateException("No se recibió el valor solicitado");
        }
        return value;
    }

    private static boolean constantTimeEquals(char[] first, char[] second) {
        int difference = first.length ^ second.length;
        int max = Math.max(first.length, second.length);
        for (int i = 0; i < max; i++) {
            char left = i < first.length ? first[i] : 0;
            char right = i < second.length ? second[i] : 0;
            difference |= left ^ right;
        }
        return difference == 0;
    }

    private static String required(Map<String, String> body, String field) {
        String value = body.get(field);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("La respuesta no contiene el campo requerido " + field);
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }

    private static void appendJsonString(StringBuilder output, String value) {
        output.append('"');
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        output.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        output.append(ch);
                    }
                }
            }
        }
        output.append('"');
    }

    private static String escapeForTerminal(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '\n' -> safe.append("\\n\n");
                case '\r' -> safe.append("\\r");
                case '\t' -> safe.append("\\t");
                case '\\' -> safe.append("\\\\");
                default -> {
                    if (Character.isISOControl(ch)) {
                        safe.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
                    } else {
                        safe.append(ch);
                    }
                }
            }
        }
        return safe.toString();
    }

    private static void wipe(byte[] value) {
        if (value != null) {
            Arrays.fill(value, (byte) 0);
        }
    }

    private static void printUsage() {
        System.out.println("Uso (terminal interactiva; los secretos se solicitan ocultos):");
        System.out.println("  java -cp backend/target/classes pe.rutafija.identity.recoverycli.SuperAdminRecoveryCli keygen <ruta-clave-p8e-fuera-del-repo>");
        System.out.println("  java -cp backend/target/classes pe.rutafija.identity.recoverycli.SuperAdminRecoveryCli check-key <ruta-clave-cifrada-o-copia-fuera-del-repo>");
        System.out.println("  java -cp backend/target/classes pe.rutafija.identity.recoverycli.SuperAdminRecoveryCli enroll <api-base> <ruta-clave-p8e>");
        System.out.println("  java -cp backend/target/classes pe.rutafija.identity.recoverycli.SuperAdminRecoveryCli recover <api-base> <ruta-clave-p8e>");
        System.out.println("HTTP sin TLS solo admite loopback. La clave privada cifrada nunca se envía a la API.");
    }

    interface Terminal {
        String readLine(String prompt);

        char[] readSecret(String prompt);

        void println(String value);

        void printf(String format, Object... values);
    }

    private static final class ConsoleTerminal implements Terminal {
        private final Console console;

        private ConsoleTerminal(Console console) {
            this.console = console;
        }

        @Override
        public String readLine(String prompt) {
            String value = console.readLine("%s", prompt);
            if (value == null) {
                throw new IllegalStateException("No se recibió la entrada solicitada");
            }
            return value;
        }

        @Override
        public char[] readSecret(String prompt) {
            return console.readPassword("%s", prompt);
        }

        @Override
        public void println(String value) {
            console.writer().println(value);
            console.writer().flush();
        }

        @Override
        public void printf(String format, Object... values) {
            console.printf(format, values);
            console.flush();
        }
    }

    private static final class HttpFailure extends Exception {
        @java.io.Serial
        private static final long serialVersionUID = -3629730560888174975L;
        private final int statusCode;

        private HttpFailure(int statusCode) {
            this.statusCode = statusCode;
        }

        int statusCode() {
            return statusCode;
        }
    }

    private static final class FlatJsonObjectParser {
        private final String json;
        private int index;

        private FlatJsonObjectParser(String json) {
            this.json = json;
        }

        Map<String, String> parse() {
            skipWhitespace();
            expect('{');
            Map<String, String> values = new LinkedHashMap<>();
            skipWhitespace();
            if (take('}')) {
                ensureEnd();
                return Map.copyOf(values);
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                String value = parseValue();
                if (values.containsKey(key)) {
                    throw malformed();
                }
                values.put(key, value);
                skipWhitespace();
                if (take('}')) {
                    ensureEnd();
                    return java.util.Collections.unmodifiableMap(values);
                }
                expect(',');
            }
        }

        private String parseValue() {
            if (index >= json.length()) {
                throw malformed();
            }
            if (json.charAt(index) == '"') {
                return parseString();
            }
            if (json.startsWith("null", index)) {
                index += 4;
                return null;
            }
            if (json.startsWith("true", index)) {
                index += 4;
                return "true";
            }
            if (json.startsWith("false", index)) {
                index += 5;
                return "false";
            }
            int start = index;
            while (index < json.length() && ",}".indexOf(json.charAt(index)) < 0
                    && !Character.isWhitespace(json.charAt(index))) {
                index++;
            }
            if (start == index) {
                throw malformed();
            }
            String number = json.substring(start, index);
            if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) {
                throw malformed();
            }
            return number;
        }

        private String parseString() {
            expect('"');
            StringBuilder value = new StringBuilder();
            while (index < json.length()) {
                char ch = json.charAt(index++);
                if (ch == '"') {
                    return value.toString();
                }
                if (ch < 0x20) {
                    throw malformed();
                }
                if (ch != '\\') {
                    value.append(ch);
                    continue;
                }
                if (index >= json.length()) {
                    throw malformed();
                }
                char escaped = json.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> value.append(escaped);
                    case 'b' -> value.append('\b');
                    case 'f' -> value.append('\f');
                    case 'n' -> value.append('\n');
                    case 'r' -> value.append('\r');
                    case 't' -> value.append('\t');
                    case 'u' -> value.append(parseUnicodeEscape());
                    default -> throw malformed();
                }
            }
            throw malformed();
        }

        private char parseUnicodeEscape() {
            if (index + 4 > json.length()) {
                throw malformed();
            }
            int value = 0;
            for (int i = 0; i < 4; i++) {
                int digit = Character.digit(json.charAt(index++), 16);
                if (digit < 0) {
                    throw malformed();
                }
                value = (value << 4) | digit;
            }
            return (char) value;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private boolean take(char expected) {
            if (index < json.length() && json.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                throw malformed();
            }
        }

        private void ensureEnd() {
            skipWhitespace();
            if (index != json.length()) {
                throw malformed();
            }
        }

        private IllegalArgumentException malformed() {
            return new IllegalArgumentException("La respuesta JSON no cumple el formato de objeto plano esperado");
        }
    }
}
