package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeGenerator;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.io.Console;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Function;
import java.util.function.Consumer;
import javax.sql.DataSource;

/**
 * EXPLICITLY MANUAL: never selected by Surefire/Failsafe and never invoked by a test.
 * A console, an exact confirmation and the current private SMTP configuration are
 * required for its single HTTP issuance. --check-only never connects to DB/SMTP.
 * No OTP, password, token, response body, credential or exception detail is printed.
 */
public final class ManualOtpDeliveryValidation {
    private static final String AUTHORIZED_SENDER = "rutafija.dev1@gmail.com";
    private static final String AUTHORIZED_RECIPIENT = "sandrito10xyz@gmail.com";
    private static final String CONFIRMATION = "ENVIAR_UN_OTP_L01";
    private static final SecureRandom RANDOM = new SecureRandom();

    private ManualOtpDeliveryValidation() { }

    public static void main(String[] args) {
        if (args.length == 2 && "--inspect-case".equals(args[0])) {
            try {
                ManualOtpCaseRecovery.inspectReadOnly(UUID.fromString(args[1]), AUTHORIZED_RECIPIENT);
                return;
            } catch (ManualOtpCaseRecovery.Refused failure) {
                System.err.println("MANUAL_OTP_CASE_INSPECTION=REFUSED_" + failure.code);
            } catch (Throwable failure) {
                System.err.println("MANUAL_OTP_CASE_INSPECTION=FAIL_DETAILS_REDACTED");
            }
            System.exit(1);
        }
        if (args.length == 1 && "--input-self-test".equals(args[0])) {
            inputSelfTest();
            return;
        }
        boolean checkOnly = args.length == 1 && "--check-only".equals(args[0]);
        UUID resumeCase = null;
        try {
            if (args.length == 2 && "--resume-case".equals(args[0])) resumeCase = UUID.fromString(args[1]);
            else if (args.length > 0 && !checkOnly) throw new IllegalArgumentException();
        } catch (IllegalArgumentException failure) {
            System.err.println("MANUAL_OTP_RESULT=REFUSED_ARGUMENTS");
            System.exit(2);
        }
        Console console = System.console();
        if (!checkOnly && console == null) {
            System.err.println("MANUAL_OTP_RESULT=INTERACTIVE_CONSOLE_REQUIRED");
            System.exit(2);
        }
        System.setProperty("jdk.httpclient.HttpClient.log", "");
        int exitCode = 1;
        ManualRun run = new ManualRun();
        Thread shutdown = new Thread(run::finish, "manual-otp-fixture-cleanup");
        try {
            Map<String, Object> configuration = configuration();
            if (checkOnly) {
                System.out.println("MANUAL_OTP_STATIC_GUARDS=PASS");
                System.out.println("MANUAL_OTP_DB_CONNECTED=false");
                System.out.println("MANUAL_OTP_SMTP_ATTEMPTS=0");
                return;
            }
            run.phase = "BOOTSTRAP";
            run.context = startBackend(configuration);
            run.jdbc = run.context.getBean(JdbcTemplate.class);
            run.transaction = new TransactionTemplate(run.context.getBean(PlatformTransactionManager.class));
            Runtime.getRuntime().addShutdownHook(shutdown);
            run.phase = "PREFLIGHT";
            run.lockDatabaseScope();
            if (resumeCase == null) preflight(run.jdbc, run.recipient);
            else {
                run.caseRunId = resumeCase;
                ManualOtpCaseRecovery.Snapshot prior = ManualOtpCaseRecovery.inspect(run.jdbc, resumeCase, run.recipient);
                prior.requireContinuable();
                prior.printSanitized();
            }
            console.printf("Validación manual L-01, solo ruta_fija_test y el destinatario autorizado.%n");
            console.printf("Se emitirá como máximo UN correo. No habrá reenvíos ni reintentos.%n");
            console.printf(resumeCase == null
                    ? "La preparación consume UNA creación ADMIN histórica de pruebas, incluso al desactivarla.%n"
                    : "Se reutilizan la identidad y el ADMIN sintéticos exactos; NO se consume otra creación ADMIN.%n");
            console.printf("La entrada del OTP es oculta: no aparecerán caracteres ni asteriscos.%n");
            console.printf("Una entrada vacía puede corregirse localmente (máximo tres lecturas); no envía ni verifica nada.%n");
            console.printf("No inicia L-02, no valida dispositivo físico ni acepta términos reales.%n");
            String confirmation = console.readLine("Para continuar escribe %s: ", CONFIRMATION);
            if (!CONFIRMATION.equals(confirmation)) throw failure("NOT_CONFIRMED");
            run.phase = "FIXTURE";
            run.fixture = resumeCase == null ? createFixture(run.context, run.transaction, run.jdbc, run.recipient) : continueFixture(run);
            System.out.println("MANUAL_OTP_RUN_ID=" + run.fixture.runId());
            if (run.caseRunId != null) System.out.println("MANUAL_OTP_CASE_RUN_ID=" + run.caseRunId);
            System.out.println("MANUAL_OTP_ADMIN_HISTORICAL_CREATIONS_CONSUMED=" + (run.caseRunId == null ? 1 : 0));
            run.phase = "HTTP_ISSUANCE";
            exerciseHttp(run, deadline -> {
                if (!"RECIBIDO".equals(console.readLine("Confirma la recepción del mensaje escribiendo RECIBIDO (sin el código): ")))
                    throw failure("RECEIPT_NOT_CONFIRMED");
                System.out.println("MANUAL_OTP_RECIPIENT_RECEIPT_CONFIRMED=true");
                return readOtp(() -> console.readPassword("OTP (no aparecerán caracteres ni asteriscos; nunca por chat): "),
                        deadline, Clock.systemUTC(), message -> console.printf("%s%n", message));
            });
            exitCode = 0;
        } catch (ValidationFailure failure) {
            System.err.println("MANUAL_OTP_RESULT=FAIL phase=" + run.phase + " reason=" + failure.code);
        } catch (ManualOtpCaseRecovery.Refused failure) {
            System.err.println("MANUAL_OTP_RESULT=FAIL phase=" + run.phase + " reason=" + failure.code);
        } catch (Throwable failure) {
            // Exception messages/causes can contain provider details or response bodies.
            System.err.println("MANUAL_OTP_RESULT=FAIL phase=" + run.phase + " reason=DETAILS_REDACTED");
        } finally {
            run.finish();
            try { Runtime.getRuntime().removeShutdownHook(shutdown); }
            catch (IllegalArgumentException | IllegalStateException ignored) { }
        }
        if (run.cleanupFailed) exitCode = 1;
        if (exitCode == 0) System.out.println("MANUAL_OTP_RESULT=PASS_PERSISTED_AND_CONSUMED_PENDING_REVIEW");
        System.exit(exitCode);
    }

    private static void inputSelfTest() {
        Console console = System.console();
        if (console == null) {
            System.err.println("MANUAL_OTP_INPUT_SELF_TEST=INTERACTIVE_CONSOLE_REQUIRED; DB_CONNECTED=false; SMTP_ATTEMPTS=0");
            System.exit(2);
        }
        console.printf("Comprobación de entrada con DATOS FICTICIOS, sin DB, HTTP ni SMTP.%n");
        console.printf("No aparecerán caracteres ni asteriscos. Prueba primero Enter vacío y después seis dígitos ficticios.%n");
        char[] value = null;
        try {
            value = readOtp(() -> console.readPassword("Entrada ficticia oculta: "), Instant.now().plusSeconds(120),
                    Clock.systemUTC(), message -> console.printf("%s%n", message));
            System.out.println("MANUAL_OTP_INPUT_SELF_TEST=PASS; DB_CONNECTED=false; SMTP_ATTEMPTS=0; HTTP_VERIFICATIONS=0");
        } catch (ValidationFailure failure) {
            System.err.println("MANUAL_OTP_INPUT_SELF_TEST=FAIL_" + failure.code + "; DB_CONNECTED=false; SMTP_ATTEMPTS=0; HTTP_VERIFICATIONS=0");
            System.exit(1);
        } finally { if (value != null) Arrays.fill(value, '\0'); }
    }

    /** Empty local input is not an HTTP verification and never extends the original expiry. */
    static char[] readOtp(Supplier<char[]> input, Instant deadline, Clock clock, Consumer<String> notices) {
        for (int read = 0; read < 3; read++) {
            if (!clock.instant().isBefore(deadline)) throw failure("OTP_ORIGINAL_VALIDITY_EXPIRED");
            char[] value = input.get();
            if (value == null) throw failure("OTP_INPUT_CANCELLED");
            if (!clock.instant().isBefore(deadline)) {
                Arrays.fill(value, '\0');
                throw failure("OTP_ORIGINAL_VALIDITY_EXPIRED");
            }
            if (value.length == 0) {
                Arrays.fill(value, '\0');
                if (read == 2) throw failure("OTP_EMPTY_LOCAL_READ_LIMIT");
                notices.accept("Entrada vacía: no se envió ninguna verificación HTTP. Continúa dentro de la vigencia original, sin otro correo.");
                continue;
            }
            boolean valid = value.length == 6;
            for (char digit : value) valid &= digit >= '0' && digit <= '9';
            if (!valid) { Arrays.fill(value, '\0'); throw failure("OTP_INPUT_FORMAT_REJECTED"); }
            return value;
        }
        throw failure("OTP_EMPTY_LOCAL_READ_LIMIT");
    }

    private static Map<String, Object> configuration() throws Exception {
        Path backend = Path.of("").toAbsolutePath().normalize();
        if (!Files.isRegularFile(backend.resolve("pom.xml"))) throw failure("RUN_FROM_BACKEND");
        Path privateFile = backend.resolve(".local/ruta-fija-native.env");
        Map<String, String> mail = new HashMap<>();
        for (String line : Files.readAllLines(privateFile, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.stripLeading().startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator <= 0) throw failure("PRIVATE_CONFIGURATION_FORMAT");
            String key = line.substring(0, separator).strip();
            if (key.equals("SPRING_MAIL_USERNAME") || key.equals("SPRING_MAIL_PASSWORD")) {
                if (mail.putIfAbsent(key, line.substring(separator + 1)) != null) throw failure("DUPLICATED_PRIVATE_MAIL_VARIABLE");
            }
        }
        Properties local = new Properties();
        try (var reader = Files.newBufferedReader(backend.resolve("src/main/resources/application-local.properties"), StandardCharsets.UTF_8)) {
            local.load(reader);
        }
        if (!AUTHORIZED_SENDER.equals(mail.get("SPRING_MAIL_USERNAME"))) throw failure("PRIVATE_SMTP_USERNAME_MISMATCH");
        if (!AUTHORIZED_SENDER.equals(local.getProperty("app.otp.email.allowed-sender"))) throw failure("ALLOWED_SENDER_MISMATCH");
        if (!AUTHORIZED_RECIPIENT.equals(local.getProperty("app.otp.email.allowed-recipients"))) throw failure("ALLOWED_RECIPIENT_MISMATCH");
        if (!mail.containsKey("SPRING_MAIL_PASSWORD") || mail.get("SPRING_MAIL_PASSWORD").isBlank()) throw failure("PRIVATE_SMTP_PASSWORD_MISSING");

        Map<String, Object> values = new HashMap<>();
        NativePostgresTestDatabase.load().registerSpringProperties((name, supplier) -> values.put(name, supplier.get()));
        values.put("spring.profiles.active", "test");
        values.put("spring.config.location", "classpath:/application.yml,classpath:/application-test.yml");
        values.put("spring.config.import", "");
        values.put("spring.flyway.enabled", false);
        values.put("server.address", "127.0.0.1");
        values.put("server.port", 0);
        values.put("server.forward-headers-strategy", "none");
        values.put("app.seed.enabled", false);
        values.put("app.mobile.offline-sync.enabled", false);
        values.put("app.security.admin-terms-gate.enabled", true);
        values.put("app.security.person-identity-gate.enabled", true);
        values.put("spring.mail.host", "smtp.gmail.com");
        values.put("spring.mail.port", 587);
        values.put("spring.mail.username", mail.get("SPRING_MAIL_USERNAME"));
        values.put("spring.mail.password", mail.get("SPRING_MAIL_PASSWORD"));
        values.put("spring.mail.test-connection", false);
        values.put("app.otp.email.allowed-sender", AUTHORIZED_SENDER);
        values.put("app.otp.email.allowed-recipients", AUTHORIZED_RECIPIENT);
        for (String option : new String[]{"auth", "starttls.enable", "starttls.required"}) values.put("spring.mail.properties.mail.smtp." + option, true);
        for (String option : new String[]{"connectiontimeout", "timeout", "writetimeout"}) values.put("spring.mail.properties.mail.smtp." + option, 5000);
        values.put("spring.mail.properties.mail.debug", false);
        values.put("spring.mail.properties.mail.debug.auth", false);
        values.put("debug", false);
        values.put("trace", false);
        values.put("spring.http.log-request-details", false);
        values.put("spring.mvc.log-request-details", false);
        values.put("logging.level.root", "INFO");
        values.put("logging.level.org.springframework.web", "WARN");
        values.put("logging.level.org.springframework.boot.SpringApplication", "OFF");
        values.put("logging.level.org.hibernate.SQL", "OFF");
        values.put("logging.level.org.hibernate.orm.jdbc.bind", "OFF");
        values.put("logging.level.org.springframework.jdbc.core", "OFF");
        values.put("logging.level.pe.rutafija.shared.exception.GlobalExceptionHandler", "OFF");
        return values;
    }

    private static ConfigurableApplicationContext startBackend(Map<String, Object> values) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("manualOtpValidatedConfiguration", values));
        SpringApplication application = new SpringApplication(RutaFijaApplication.class);
        application.setEnvironment(environment);
        application.setAddCommandLineProperties(false);
        application.setRegisterShutdownHook(false);
        // This isolated manual process must not expire or sweep unrelated test data.
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
            if (factory instanceof BeanDefinitionRegistry registry
                    && registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME)) {
                registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
            }
        }));
        return application.run();
    }

    private static void preflight(JdbcTemplate jdbc, String recipient) {
        if (!"ruta_fija_test".equals(jdbc.queryForObject("select current_database()", String.class))) throw failure("DATABASE_TARGET_REJECTED");
        if (count(jdbc, "select count(*) from pilot_account_creation where role='ADMIN'") >= 4) throw failure("ADMIN_HISTORICAL_CAP_EXHAUSTED");
        if (count(jdbc, "select count(*) from app_user where lower(email)=?", recipient) != 0) throw failure("RECIPIENT_ALREADY_APP_USER");
        if (count(jdbc, "select count(*) from conductor_onboarding_identity where lower(email)=?", recipient) != 0) throw failure("RECIPIENT_ALREADY_PRELIMINARY_IDENTITY");
        if (count(jdbc, "select count(*) from organization_invitation where lower(recipient_email)=? and revoked_at is null and used_at is null and expires_at>current_timestamp", recipient) != 0) throw failure("RECIPIENT_HAS_ACTIVE_INVITATION");
    }

    static Fixture createFixture(ConfigurableApplicationContext context, TransactionTemplate transaction, JdbcTemplate jdbc, String recipient) {
        return transaction.execute(status -> {
            // Recheck under the same transaction before creating this exact fixture.
            preflight(jdbc, recipient);
            UUID runId = UUID.randomUUID();
            Organization organization = context.getBean(OrganizationRepository.class).saveAndFlush(Organization.active(
                    "SYNTHETIC MANUAL OTP " + runId, "SYNTHETIC OTP ONLY", "America/Lima"));
            AppUser candidate = AppUser.organizationUser(organization, "manual-otp-admin." + runId + "@rutafija.test",
                    context.getBean(PasswordEncoder.class).encode(randomSecret()), "SYNTHETIC MANUAL ADMIN", UserRole.ADMIN);
            candidate.deactivate();
            AppUser admin = context.getBean(AppUserRepository.class).saveAndFlush(candidate);
            UUID invitationId = UUID.randomUUID();
            String invitationCode = context.getBean(InvitationCodeGenerator.class).generate();
            Instant issued = Instant.now();
            jdbc.update("""
                    insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at)
                    values (?,?,?,?,?,?,?)
                    """, invitationId, organization.getId(), admin.getId(), recipient,
                    context.getBean(InvitationCodeHasher.class).hashCode(invitationCode, recipient),
                    Timestamp.from(issued), Timestamp.from(issued.plus(Duration.ofHours(120))));
            return new Fixture(runId, organization.getId(), admin.getId(), invitationId, invitationCode);
        });
    }

    private static Fixture continueFixture(ManualRun run) {
        return run.transaction.execute(status -> {
            UUID attemptId = UUID.randomUUID();
            UUID invitationId = UUID.randomUUID();
            String code = run.context.getBean(InvitationCodeGenerator.class).generate();
            Instant now = Instant.now();
            ManualOtpCaseRecovery.Snapshot prior = ManualOtpCaseRecovery.prepareContinuation(run.jdbc, run.caseRunId, run.recipient,
                    invitationId, run.context.getBean(InvitationCodeHasher.class).hashCode(code, run.recipient), now);
            run.priorSendTimes = prior.challenge().sends();
            // No identity/challenge/ledger write here. The ordinary HTTP endpoint owns OTP issuance.
            return new Fixture(attemptId, prior.root().organizationId(), prior.root().adminId(), invitationId, code);
        });
    }

    private static void exerciseHttp(ManualRun run, Function<Instant, char[]> otpReader) throws Exception {
        ObjectMapper mapper = run.context.getBean(ObjectMapper.class);
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        URI base = URI.create("http://127.0.0.1:" + ((ServletWebServerApplicationContext) run.context).getWebServer().getPort());
        String operation = "manual-otp-" + run.fixture.runId();
        System.out.println("MANUAL_OTP_HTTP_ISSUANCE_ATTEMPTS=1");
        JsonNode issued = post(client, mapper, base.resolve("/api/v1/onboarding/requests"), Map.of(
                "email", run.recipient, "password", randomSecret(), "fullName", "SYNTHETIC MANUAL APPLICANT",
                "invitationCode", run.fixture.invitationCode()), operation);
        if (!issued.path("emailSent").asBoolean(false) || issued.path("verificationToken").asText().isBlank()) throw failure("ISSUANCE_NOT_CONFIRMED");
        System.out.println("MANUAL_OTP_HTTP_ISSUANCE_CONFIRMED=true");
        System.out.println("MANUAL_OTP_TRANSPORT_RETURNED_SUCCESS=true");
        System.out.println("MANUAL_OTP_ISSUED_AT_UTC=" + Instant.now());
        String verificationToken = issued.path("verificationToken").asText();
        run.phase = "PERSISTED_CHALLENGE";
        if (count(run.jdbc, """
                select count(*) from conductor_onboarding_email_challenge c
                join conductor_onboarding_identity i on i.id=c.identity_id
                where c.invitation_id=? and i.email=? and i.email_verified_at is null
                  and c.otp_hash ~ '^[0-9a-f]{64}$' and c.verification_token_hash ~ '^[0-9a-f]{64}$'
                  and c.otp_expires_at=c.otp_issued_at+interval '5 minutes'
                  and c.send_at_1 is not null
                  and c.failed_attempts=0 and c.consumed_at is null
                """, run.fixture.invitationId(), run.recipient) != 1) throw failure("PERSISTED_CHALLENGE_INVALID");
        verifySendContinuity(run);
        assertNoOperationalIdentity(run);
        System.out.println("MANUAL_OTP_PERSISTED_CHALLENGE=PASS_HASHED_TTL_5_MINUTES_ONE_NEW_SEND");
        run.phase = "CONSOLE_OTP_INPUT";
        Instant deadline = Instant.parse(issued.path("otpExpiresAt").asText());
        char[] otp = otpReader.apply(deadline);
        if (otp == null) throw failure("OTP_INPUT_CANCELLED");
        try {
            if (otp.length != 6) throw failure("OTP_INPUT_FORMAT_REJECTED");
            for (char value : otp) if (value < '0' || value > '9') throw failure("OTP_INPUT_FORMAT_REJECTED");
            run.phase = "HTTP_CONSUMPTION";
            System.out.println("MANUAL_OTP_HTTP_VERIFICATION_ATTEMPTS=1");
            JsonNode accepted = post(client, mapper, base.resolve("/api/v1/onboarding/otp/verify"),
                    Map.of("verificationToken", verificationToken, "code", otp), null);
            if (accepted.size() != 2 || !"PENDING_REVIEW".equals(accepted.path("status").asText())) throw failure("PENDING_REVIEW_NOT_CONFIRMED");
            UUID requestId = UUID.fromString(accepted.path("requestId").asText());
            run.phase = "PERSISTED_CONSUMPTION";
            if (count(run.jdbc, """
                    select count(*) from conductor_onboarding_request r
                    join conductor_onboarding_identity i on i.id=r.identity_id
                    join conductor_onboarding_email_challenge c on c.request_id=r.id and c.identity_id=i.id
                    join organization_invitation v on v.id=r.invitation_id
                    where r.id=? and r.organization_id=? and r.invitation_id=? and r.status='PENDING_REVIEW'
                      and r.linked_user_id is null and i.email_verified_at is not null
                      and c.consumed_at is not null and c.otp_hash is null and c.otp_expires_at is null and v.used_at is not null
                    """, requestId, run.fixture.organizationId(), run.fixture.invitationId()) != 1) throw failure("CONSUMPTION_NOT_PERSISTED");
            if (count(run.jdbc, "select count(*) from conductor_onboarding_request where invitation_id=?", run.fixture.invitationId()) != 1) throw failure("DUPLICATED_REQUEST");
            assertNoOperationalIdentity(run);
            System.out.println("MANUAL_OTP_HTTP_CONSUMPTION=PASS_PENDING_REVIEW_NO_JWT_NO_COOKIE");
            System.out.println("MANUAL_OTP_CHALLENGE_CONSUMED_AND_OTP_CLEARED=true");
            System.out.println("MANUAL_OTP_OPERATIONAL_APPUSER_OR_DRIVER_CREATED=false");
            System.out.println("MANUAL_OTP_RESEND_ATTEMPTS=0");
            System.out.println("MANUAL_OTP_CONSUMED_AT_UTC=" + Instant.now());
            if (run.simulatedNegativeChecks) {
                // Q-018 intentionally returns the same minimal receipt for the
                // consumed capability. This is not a second OTP verification.
                JsonNode replay = post(client, mapper, base.resolve("/api/v1/onboarding/otp/verify"),
                        Map.of("verificationToken", verificationToken, "code", "000000"), null);
                if (!accepted.equals(replay) || count(run.jdbc,
                        "select count(*) from conductor_onboarding_request where invitation_id=?", run.fixture.invitationId()) != 1)
                    throw failure("RECEIPT_REPLAY_CHANGED_RESULT");
                postExpected(client, mapper, base.resolve("/api/v1/onboarding/otp/resend"),
                        Map.of("verificationToken", verificationToken), null, 410);
                assertNoOperationalIdentity(run);
                System.out.println("SIMULATED_OTP_RECEIPT_REPLAY=PASS_SAME_REQUEST_NO_SECOND_VERIFICATION");
                System.out.println("SIMULATED_OTP_CONSUMED_CHALLENGE_RESEND=REJECTED_WITHOUT_DELIVERY");
            } else {
                System.out.println("MANUAL_OTP_IDEMPOTENT_RECEIPT_REPLAY_NOT_EXECUTED=true");
            }
        } finally { Arrays.fill(otp, '\0'); }
    }

    private static void verifySendContinuity(ManualRun run) {
        run.jdbc.queryForObject("""
                select otp_issued_at,send_at_1,send_at_2,send_at_3
                from conductor_onboarding_email_challenge where invitation_id=?
                """, (rs, row) -> {
            Instant issued = rs.getTimestamp("otp_issued_at").toInstant();
            java.util.ArrayList<Instant> expected = new java.util.ArrayList<>(run.priorSendTimes.stream()
                    .filter(t -> t.isAfter(issued.minus(Duration.ofMinutes(60)))).toList());
            expected.add(issued);
            expected.sort(Instant::compareTo);
            List<Instant> actual = java.util.stream.Stream.of(rs.getTimestamp("send_at_1"), rs.getTimestamp("send_at_2"), rs.getTimestamp("send_at_3"))
                    .filter(java.util.Objects::nonNull).map(Timestamp::toInstant).toList();
            if (!expected.equals(actual) || actual.size() > 3) throw failure("SEND_HISTORY_CONTINUITY_FAILED");
            return true;
        }, run.fixture.invitationId());
        System.out.println("MANUAL_OTP_SEND_WINDOW_CONTINUITY=PASS_PRESERVED_NO_RESET");
    }

    private static JsonNode post(HttpClient client, ObjectMapper mapper, URI uri, Map<String, Object> payload, String operation) throws Exception {
        return postExpected(client, mapper, uri, payload, operation, 202);
    }

    private static JsonNode postExpected(HttpClient client, ObjectMapper mapper, URI uri, Map<String, Object> payload, String operation, int expectedStatus) throws Exception {
        byte[] body = mapper.writeValueAsBytes(payload);
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
                    .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body));
            if (operation != null) builder.header("Idempotency-Key", operation);
            HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            try {
                if (response.statusCode() != expectedStatus) throw failure("HTTP_STATUS_" + response.statusCode());
                if (!response.headers().allValues("Set-Cookie").isEmpty()) throw failure("UNEXPECTED_SESSION_COOKIE");
                JsonNode result = mapper.readTree(response.body());
                for (String field : new String[]{"accessToken", "refreshToken", "jwt", "tokenType"}) if (result.has(field)) throw failure("UNEXPECTED_OPERATIONAL_TOKEN");
                return result;
            } finally { Arrays.fill(response.body(), (byte) 0); }
        } finally { Arrays.fill(body, (byte) 0); }
    }

    private static void assertNoOperationalIdentity(ManualRun run) {
        if (count(run.jdbc, "select count(*) from app_user where lower(email)=?", run.recipient) != 0
                || count(run.jdbc, "select count(*) from driver where organization_id=?", run.fixture.organizationId()) != 0) throw failure("UNEXPECTED_OPERATIONAL_IDENTITY");
    }

    private static int count(JdbcTemplate jdbc, String sql, Object... args) {
        Integer result = jdbc.queryForObject(sql, Integer.class, args);
        return result == null ? 0 : result;
    }
    private static String randomSecret() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }

    /** Test seam: caller supplies a real test backend with an OtpEmailSender double. */
    static void exerciseWithSimulatedMail(ConfigurableApplicationContext context, Supplier<char[]> otpReader) throws Exception {
        ManualRun run = new ManualRun();
        run.context = context;
        run.closeContext = false;
        run.simulatedNegativeChecks = true;
        run.recipient = "manual.otp." + UUID.randomUUID() + "@rutafija.test";
        run.jdbc = context.getBean(JdbcTemplate.class);
        run.transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        try {
            run.fixture = createFixture(context, run.transaction, run.jdbc, run.recipient);
            exerciseHttp(run, deadline -> otpReader.get());
        } finally {
            run.finish();
        }
        if (run.cleanupFailed) throw failure("SIMULATED_FIXTURE_CLEANUP_FAILED");
    }

    private static ValidationFailure failure(String code) { return new ValidationFailure(code); }
    private static final class ValidationFailure extends RuntimeException {
        private final String code;
        private ValidationFailure(String code) { super(null, null, false, false); this.code = code; }
    }
    record Fixture(UUID runId, UUID organizationId, UUID adminId, UUID invitationId, String invitationCode) {
        @Override public String toString() { return "Fixture[runId=" + runId + ",secrets=redacted]"; }
    }
    private static final class ManualRun {
        private ConfigurableApplicationContext context;
        private JdbcTemplate jdbc;
        private TransactionTemplate transaction;
        private volatile Fixture fixture;
        private String phase = "STATIC_CONFIGURATION";
        private boolean finished;
        private boolean cleanupFailed;
        private boolean closeContext = true;
        private boolean simulatedNegativeChecks;
        private String recipient = AUTHORIZED_RECIPIENT;
        private UUID caseRunId;
        private List<Instant> priorSendTimes = List.of();
        private Connection scopeLock;

        private void lockDatabaseScope() throws Exception {
            scopeLock = context.getBean(DataSource.class).getConnection();
            try (var statement = scopeLock.prepareStatement("select pg_try_advisory_lock(?)")) {
                // Same lock respected by NativePostgresTestDatabase cleanup. Never waits or cleans.
                statement.setLong(1, 7_518_440_514L);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || !result.getBoolean(1)) throw failure("TEST_DATABASE_BUSY_NO_SEND");
                }
            }
        }

        private synchronized void finish() {
            if (finished) return;
            finished = true;
            try {
                if (fixture != null && context != null && context.isActive()) {
                    cleanupExactFixture(context, jdbc, transaction, fixture);
                    System.out.println("MANUAL_OTP_EXACT_FIXTURE_CLEANUP=PASS_DISABLED_AND_TOKENS_REVOKED");
                    System.out.println("MANUAL_OTP_LEDGER_CREATION_PRESERVED=true");
                }
            } catch (Throwable failure) {
                cleanupFailed = true;
                System.err.println("MANUAL_OTP_EXACT_FIXTURE_CLEANUP=FAIL_MANUAL_REVIEW_REQUIRED");
                if (fixture != null) System.err.println("MANUAL_OTP_CLEANUP_RUN_ID=" + fixture.runId());
            } finally {
                if (scopeLock != null) {
                    try {
                        try (var statement = scopeLock.prepareStatement("select pg_advisory_unlock(?)")) {
                            statement.setLong(1, 7_518_440_514L);
                            statement.execute();
                        }
                        scopeLock.close();
                    } catch (Exception ignored) { cleanupFailed = true; }
                }
                if (context != null && closeContext) context.close();
            }
        }
    }

    static void cleanupExactFixture(ConfigurableApplicationContext context, JdbcTemplate jdbc,
                                    TransactionTemplate transaction, Fixture fixture) {
        transaction.executeWithoutResult(status -> {
            var limitsBefore = jdbc.queryForList("""
                    select send_at_1,send_at_2,send_at_3,failed_attempts,blocked_until
                    from conductor_onboarding_email_challenge where invitation_id=?
                    """, fixture.invitationId());
            jdbc.update("""
                    update conductor_onboarding_access_token set revoked_at=greatest(current_timestamp,created_at)
                    where identity_id in(select identity_id from conductor_onboarding_email_challenge where invitation_id=?) and revoked_at is null
                    """, fixture.invitationId());
            // Drop the receipt capability as well as the OTP. Preserve sends and attempts.
            jdbc.update("""
                    update conductor_onboarding_email_challenge set otp_hash=null,otp_expires_at=null,
                      verification_token_hash=encode(sha256(?::bytea),'hex'),
                      challenge_expires_at=greatest(current_timestamp,created_at),updated_at=current_timestamp
                    where invitation_id=?
                    """, randomSecret().getBytes(StandardCharsets.UTF_8), fixture.invitationId());
            jdbc.update("""
                    update conductor_onboarding_identity set password_hash=?,updated_at=current_timestamp
                    where id in(select identity_id from conductor_onboarding_email_challenge where invitation_id=?)
                    """, context.getBean(PasswordEncoder.class).encode(randomSecret()), fixture.invitationId());
            // Never give a used invitation the incompatible revoked state.
            jdbc.update("update organization_invitation set revoked_at=coalesce(revoked_at,current_timestamp) where id=? and organization_id=? and used_at is null",
                    fixture.invitationId(), fixture.organizationId());
            jdbc.update("update app_user set active=false,session_version=session_version+1 where id=? and organization_id=?",
                    fixture.adminId(), fixture.organizationId());
            jdbc.update("update organization set status='INACTIVE',updated_at=current_timestamp where id=?", fixture.organizationId());
            var limitsAfter = jdbc.queryForList("""
                    select send_at_1,send_at_2,send_at_3,failed_attempts,blocked_until
                    from conductor_onboarding_email_challenge where invitation_id=?
                    """, fixture.invitationId());
            if (!limitsBefore.equals(limitsAfter)
                    || count(jdbc, "select count(*) from pilot_account_creation where user_id=? and role='ADMIN'", fixture.adminId()) != 1
                    || count(jdbc, "select count(*) from app_user where id=? and organization_id=? and active=false", fixture.adminId(), fixture.organizationId()) != 1
                    || count(jdbc, "select count(*) from organization where id=? and status='INACTIVE'", fixture.organizationId()) != 1
                    || count(jdbc, "select count(*) from conductor_onboarding_email_challenge where invitation_id=? and (otp_hash is not null or otp_expires_at is not null or challenge_expires_at>clock_timestamp())", fixture.invitationId()) != 0
                    || count(jdbc, "select count(*) from conductor_onboarding_access_token where identity_id in(select identity_id from conductor_onboarding_email_challenge where invitation_id=?) and revoked_at is null and expires_at>clock_timestamp()", fixture.invitationId()) != 0
                    || count(jdbc, "select count(*) from organization_invitation where id=? and used_at is null and revoked_at is null", fixture.invitationId()) != 0)
                throw failure("EXACT_FIXTURE_CLEANUP_ASSERTION_FAILED");
        });
    }
}
