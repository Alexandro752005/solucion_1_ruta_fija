package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.io.Console;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.*;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static pe.rutafija.support.ManualL01HumanTrial.*;

/** Local test launcher, outside production artifacts. Never accepts terms or generates recovery keys. */
final class ManualL01HumanTrialServer implements AutoCloseable {
    static final int FRONTEND_PORT = 4311, BACKEND_PORT = 8091;
    static final String BASE = "http://127.0.0.1:" + FRONTEND_PORT;
    static final Path STATE = ROOT.resolve("backend/.local/l01-human-trial-state.json");
    static final Path SECRET = ROOT.resolve("backend/.local/l01-human-trial-jwt.private");
    static final Path ACTIVE = ROOT.resolve("backend/.local/l01-human-trial.active");
    final Map<String, Object> evidence = new ConcurrentHashMap<>();
    final Map<String, Mail> mailbox = new ConcurrentHashMap<>();
    final List<String> ownerAccessSessions = new CopyOnWriteArrayList<>();
    final List<String> ownerRefreshSessions = new CopyOnWriteArrayList<>();
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    final Console console;
    final Map<String, Object> properties;
    Connection scopeLock;
    ConfigurableApplicationContext context;
    HttpServer frontend;
    JdbcTemplate jdbc;
    volatile String activationToken, ownerAccessToken, ownerRefreshCookie, oldEmergencyCode, newEmergencyCode, enrolledKey;
    boolean adminConfirmed, recoveryConfirmed, copyConfirmed;
    boolean smoke;
    UUID resumeRunId;
    UUID recoveryObservationSegment;
    int cleanupLockPid;

    private ManualL01HumanTrialServer(boolean smoke) {
        this.smoke = smoke;
        this.console = System.console();
        this.properties = configuration();
    }

    static void run(boolean smoke) throws Exception {
        run(smoke, null);
    }

    static void run(boolean smoke, UUID resumeRunId) throws Exception {
        if (!smoke && System.console() == null) throw new IllegalStateException("INTERACTIVE_CONSOLE_REQUIRED");
        try (var trial = new ManualL01HumanTrialServer(smoke)) {
            trial.resumeRunId = resumeRunId;
            trial.open();
            if (smoke) trial.checkSmoke();
            else trial.interact();
        }
    }

    void open() throws Exception {
        scopeLock = connect(properties);
        JdbcTemplate lockJdbc = new JdbcTemplate(new SingleConnectionDataSource(scopeLock, true));
        assertDatabase(lockJdbc);
        Boolean locked = lockJdbc.queryForObject("select pg_try_advisory_lock(?)", Boolean.class, CLEANUP_LOCK);
        if (!Boolean.TRUE.equals(locked)) throw new IllegalStateException("DATABASE_CLEANUP_SCOPE_BUSY");
        cleanupLockPid = Objects.requireNonNull(lockJdbc.queryForObject("select pg_backend_pid()", Integer.class));
        Map<String, Object> before = snapshot(lockJdbc);
        if (!Boolean.TRUE.equals(before.get("publishedFileHashMatches"))) throw new IllegalStateException("TERMS_FILE_CHANGED");
        var terms = (Map<?, ?>) before.get("currentTerms");
        if (!"1.0.0".equals(terms.get("version")) || !APPROVED_SHA.equals(terms.get("content_sha256"))
                || !Boolean.FALSE.equals(terms.get("synthetic"))) throw new IllegalStateException("APPROVED_CURRENT_TERMS_REQUIRED");
        if (!Files.isRegularFile(ROOT.resolve("frontend/dist/ruta-fija-crm/browser/index.html")))
            throw new IllegalStateException("FRONTEND_BUILD_REQUIRED");
        frontend = HttpServer.create(new InetSocketAddress("127.0.0.1", FRONTEND_PORT), 0);
        try (var portCheck = new java.net.ServerSocket()) { portCheck.bind(new InetSocketAddress("127.0.0.1", BACKEND_PORT)); }
        String signingKey;
        if (smoke) {
            signingKey = randomSigningKey();
            evidence.put("runId", UUID.randomUUID().toString());
            evidence.put("before", before);
        }
        else if (resumeRunId != null && !(Files.exists(STATE) && Files.exists(SECRET))) {
            String marker = Files.isRegularFile(ACTIVE) ? Files.readString(ACTIVE).strip() : "";
            Map<String, Object> saved = Files.isRegularFile(STATE) ? JSON.readValue(Files.readAllBytes(STATE), Map.class) : Map.of();
            var inventory = bootstrapInventory(lockJdbc, resumeRunId);
            String refusal = bootstrapResumeRefusal(resumeRunId, marker, saved, Files.exists(SECRET), inventory);
            if (refusal != null) throw new IllegalStateException(refusal);
            evidence.putAll(saved);
            evidence.put("runId", resumeRunId.toString());
            evidence.put("database", "ruta_fija_test");
            if (!evidence.containsKey("startedAtUtc")) {
                evidence.put("startedAtUtc", Files.getLastModifiedTime(ACTIVE).toInstant().toString());
                evidence.put("startedAtSource", "ORIGINAL_PROTECTION_MARKER_LAST_MODIFIED");
            }
            if (!evidence.containsKey("before")) { evidence.put("before", before); evidence.put("beforeRecordedDuringRecovery", true); }
            evidence.put("bootstrapStage", "PENDING_PASSWORD");
            recordBootstrapEvent("SAME_CASE_BOOTSTRAP_RESUMED", saved.isEmpty() ? "LEGACY_MARKER_ONLY_FIRST_FAILURE_NOT_RECORDED" : "PENDING_PASSWORD_NO_PREPARED_ACCOUNT");
            // No persistent identity/key exists: generate only the uncommitted runtime signing secret.
            signingKey = randomSigningKey();
        }
        else if (Files.exists(STATE) && Files.exists(SECRET)) {
            if (resumeRunId == null) throw new IllegalStateException("PREPARED_CASE_REQUIRES_EXPLICIT_RESUME");
            evidence.putAll(JSON.readValue(Files.readAllBytes(STATE), Map.class));
            if (resumeRunId != null && !resumeRunId.toString().equals(text("runId"))) throw new IllegalStateException("RESUME_CASE_ID_MISMATCH");
            if (!evidence.containsKey("ownerId") || !evidence.containsKey("organizationId"))
                throw new IllegalStateException("PRECOMMIT_RUNTIME_KEY_REQUIRES_REVIEW_NO_PASSWORD_OVERWRITE");
            signingKey = Files.readString(SECRET, StandardCharsets.US_ASCII).strip();
            if (!"ruta_fija_test".equals(evidence.get("database"))) throw new IllegalStateException("INVALID_CASE");
            if (count(lockJdbc, "select count(*) from app_user where id=? and role='SUPER_ADMIN' and email in (?,?) and full_name='SYNTHETIC HUMAN TRIAL OWNER'",
                    UUID.fromString(text("ownerId")), ownerEmail(), recoveredEmail()) != 1) throw new IllegalStateException("CASE_REMOVED_OR_NOT_OWNED");
        } else {
            if (Files.exists(ACTIVE)) throw new IllegalStateException("PENDING_CASE_REQUIRES_EXPLICIT_RESUME");
            if (resumeRunId != null) throw new IllegalStateException("RESUME_CASE_NOT_FOUND");
            // New case only when no prior owner/identity installation could be displaced.
            if (!smoke && (Files.exists(STATE) || Files.exists(SECRET)
                    || ((Number) before.get("superAdminAccounts")).longValue() != 0
                    || ((Number) before.get("personIdentityBindings")).longValue() != 0
                    || Boolean.TRUE.equals(before.get("personIdentityKeyRecorded"))
                    || Boolean.TRUE.equals(before.get("recoveryCredentialPresent")))) throw new IllegalStateException("EXISTING_INSTALLATION_REQUIRES_SELECTIVE_REVIEW");
            Map<?, ?> slots = (Map<?, ?>) before.get("remainingSlots");
            if (((Number) slots.get("SUPER_ADMIN")).longValue() < 1 || ((Number) slots.get("ADMIN")).longValue() < 1)
                throw new IllegalStateException("HISTORICAL_QUOTA_EXHAUSTED");
            signingKey = randomSigningKey();
            evidence.put("runId", UUID.randomUUID().toString());
            evidence.put("database", "ruta_fija_test");
            evidence.put("startedAtUtc", Instant.now().toString());
            evidence.put("before", before);
        }
        properties.putAll(runtimeProperties(signingKey));
        if (!smoke) {
            if (Files.exists(ACTIVE) && !text("runId").equals(Files.readString(ACTIVE).strip()))
                throw new IllegalStateException("ANOTHER_HUMAN_CASE_PENDING");
            if (!Files.exists(ACTIVE)) Files.writeString(ACTIVE, text("runId"), StandardOpenOption.CREATE_NEW);
            // Preserve a resumable public case before requesting any secret input.
            if (!evidence.containsKey("ownerId")) {
                evidence.put("bootstrapStage", "PENDING_PASSWORD");
                persist();
            }
        }
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("validatedManualL01TestOnly", properties));
        SpringApplication app = new SpringApplication(RutaFijaApplication.class);
        app.setEnvironment(environment);
        app.setAddCommandLineProperties(false);
        app.setRegisterShutdownHook(false);
        OtpEmailSender doubleMail = (recipient, code, expiry) -> {
            if (!Set.of(adminEmail(), ownerEmail(), recoveredEmail()).contains(recipient)) throw new IllegalArgumentException("SYNTHETIC_CASE_RECIPIENT_ONLY");
            mailbox.put(recipient, new Mail(code, expiry));
        };
        app.addInitializers(application -> application.addBeanFactoryPostProcessor(factory -> {
            var registry = (BeanDefinitionRegistry) factory;
            if (registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
                registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
            if (!registry.containsBeanDefinition("smtpOtpEmailSender")) throw new IllegalStateException("EXPECTED_MAIL_ADAPTER_MISSING");
            registry.removeBeanDefinition("smtpOtpEmailSender");
            factory.registerSingleton("smtpOtpEmailSender", doubleMail);
        }));
        context = app.run();
        if (context.getBean(OtpEmailSender.class) != doubleMail) throw new IllegalStateException("REAL_MAIL_NOT_ALLOWED");
        jdbc = context.getBean(JdbcTemplate.class);
        assertDatabase(jdbc);
        if (!smoke && !evidence.containsKey("ownerId")) bootstrap();
        if (!smoke) restorePreparedRecoveryReferences();
        frontend.createContext("/", this::serve);
        frontend.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        frontend.start();
    }

    static Map<String, Object> runtimeProperties(String signingKey) {
        Map<String, Object> p = new HashMap<>();
        p.put("spring.profiles.active", "test");
        p.put("spring.config.location", "classpath:/application.yml,classpath:/application-test.yml");
        p.put("spring.config.import", "");
        p.put("spring.flyway.enabled", false);
        p.put("spring.jpa.hibernate.ddl-auto", "validate");
        p.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
        p.put("spring.mail.host", ""); p.put("spring.mail.username", ""); p.put("spring.mail.password", "");
        p.put("server.address", "127.0.0.1"); p.put("server.port", BACKEND_PORT);
        p.put("server.forward-headers-strategy", "none");
        p.put("app.seed.enabled", false); p.put("app.mobile.offline-sync.enabled", false);
        p.put("app.security.admin-terms-gate.enabled", true);
        p.put("app.security.person-identity-gate.enabled", true);
        p.put("app.security.cors.allowed-origins", BASE);
        p.put("app.security.jwt.secret-base64", signingKey);
        p.put("debug", false); p.put("trace", false);
        p.put("spring.main.banner-mode", "off"); p.put("logging.level.root", "ERROR");
        p.put("logging.level.org.springframework.boot.SpringApplication", "OFF");
        for (String logger : List.of("org.hibernate.SQL", "org.hibernate.orm.jdbc.bind", "org.springframework.jdbc.core",
                "org.springframework.web", "pe.rutafija.shared.exception.GlobalExceptionHandler")) p.put("logging.level." + logger, "OFF");
        return p;
    }

    void bootstrap() throws Exception {
        String refusal = bootstrapResumeRefusal(UUID.fromString(text("runId")), Files.readString(ACTIVE).strip(), evidence,
                Files.exists(SECRET), bootstrapInventory(jdbc, UUID.fromString(text("runId"))));
        if (refusal != null) throw new IllegalStateException(refusal);
        console.printf("L01_HUMAN_RUN_ID=%s%nCONFIRMADO: ruta_fija_test/rf_test V26. Alta inicial SOLO de SUPER_ADMIN sintético. SMTP simulado.%n", text("runId"));
        char[] password = null, confirmation = null;
        try {
            recordBootstrapEvent("PASSWORD_CAPTURE_STARTED", "NO_SECRET_INPUT_PERSISTED"); persist();
            password = console.readPassword("Contraseña SUPER_ADMIN DE PRUEBA (12–72 ASCII recomendados; máximo 72 bytes UTF-8; sin caracteres ni asteriscos): ");
            confirmation = console.readPassword("Repite la contraseña de prueba: ");
            String inputRefusal = passwordCaptureRefusal(password, confirmation);
            if (inputRefusal != null) {
                recordBootstrapEvent("PASSWORD_CAPTURE_REJECTED", inputRefusal); persist();
                throw new IllegalStateException(inputRefusal);
            }
            recordBootstrapEvent("PASSWORD_CAPTURE_VALIDATED", "NO_INPUT_VALUES_OR_LENGTHS_RECORDED"); persist();
            String hash = context.getBean(PasswordEncoder.class).encode(new String(password));
            writeRestricted(SECRET, ((String) properties.get("app.security.jwt.secret-base64")).getBytes(StandardCharsets.US_ASCII));
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx -> {
                AppUser owner = AppUser.superAdmin(ownerEmail(), hash, "SYNTHETIC HUMAN TRIAL OWNER");
                owner.verifyEmail(Instant.now());
                owner = context.getBean(AppUserRepository.class).saveAndFlush(owner);
                Organization org = context.getBean(OrganizationRepository.class).saveAndFlush(
                        Organization.active("SYNTHETIC HUMAN TRIAL " + suffix(), "SYNTHETIC ONLY", "America/Lima"));
                evidence.put("ownerId", owner.getId().toString());
                evidence.put("organizationId", org.getId().toString());
            });
            evidence.put("bootstrapStage", "PREPARED");
            recordBootstrapEvent("BOOTSTRAP_ACCOUNT_PREPARED", "PASSWORD_HASH_STORED_BY_NORMAL_ENCODER_LEDGER_PRESERVED");
            persist();
        } catch (java.io.IOError inputFailure) {
            recordBootstrapEvent("PASSWORD_CAPTURE_INTERRUPTED", "CONSOLE_INPUT_UNAVAILABLE"); persist();
            throw new IllegalStateException("CONSOLE_INPUT_UNAVAILABLE");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }

    static Map<String, Object> bootstrapInventory(JdbcTemplate jdbc, UUID runId) {
        String suffix = runId.toString().substring(0, 8);
        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("superAdminAccounts", count(jdbc, "select count(*) from app_user where role='SUPER_ADMIN'"));
        inventory.put("superAdminHistoricalCreations", count(jdbc, "select count(*) from pilot_account_creation where role='SUPER_ADMIN'"));
        inventory.put("adminHistoricalCreations", count(jdbc, "select count(*) from pilot_account_creation where role='ADMIN'"));
        inventory.put("caseAccounts", count(jdbc, "select count(*) from app_user where email in (?,?,?)",
                "sa.human." + suffix + "@rutafija.test", "sa.recovered." + suffix + "@rutafija.test", "admin.human." + suffix + "@rutafija.test"));
        inventory.put("caseOrganizations", count(jdbc, "select count(*) from organization where legal_name=?", "SYNTHETIC HUMAN TRIAL " + suffix));
        inventory.put("identityBindings", count(jdbc, "select count(*) from person_identity"));
        inventory.put("identityKeyRecords", count(jdbc, "select count(*) from person_identity_hash_key"));
        inventory.put("recoveryCredentials", count(jdbc, "select count(*) from super_admin_recovery_credential"));
        return inventory;
    }

    static String bootstrapResumeRefusal(UUID requested, String marker, Map<String, Object> saved,
                                         boolean runtimeKeyPresent, Map<String, Object> inventory) {
        if (!requested.toString().equals(marker)) return "RESUME_MARKER_ID_MISMATCH";
        if (!saved.isEmpty() && (!requested.toString().equals(saved.get("runId")) || !"ruta_fija_test".equals(saved.get("database"))))
            return "RESUME_CASE_STATE_MISMATCH";
        if (saved.containsKey("ownerId") || saved.containsKey("organizationId") || runtimeKeyPresent)
            return "PREPARED_CREDENTIALS_REQUIRE_REVIEW_NO_PASSWORD_OVERWRITE";
        for (String key : List.of("superAdminAccounts", "superAdminHistoricalCreations", "caseAccounts", "caseOrganizations",
                "identityBindings", "identityKeyRecords", "recoveryCredentials"))
            if (!(inventory.get(key) instanceof Number n) || n.longValue() != 0) return "BOOTSTRAP_PERSISTENCE_NOT_EMPTY_NO_PASSWORD_RECAPTURE";
        if (!(inventory.get("adminHistoricalCreations") instanceof Number adminCreations)
                || adminCreations.longValue() < 0 || adminCreations.longValue() >= 4) return "HISTORICAL_QUOTA_EXHAUSTED";
        return null;
    }

    static String passwordCaptureRefusal(char[] password, char[] confirmation) {
        if (password == null || confirmation == null) return "PASSWORD_INPUT_CANCELLED";
        if (password.length < 12 || password.length > 72) return "PASSWORD_LENGTH_INVALID";
        byte[] bytes = new String(password).getBytes(StandardCharsets.UTF_8);
        try { if (bytes.length > 72) return "PASSWORD_UTF8_LIMIT_EXCEEDED"; }
        finally { Arrays.fill(bytes, (byte) 0); }
        return Arrays.equals(password, confirmation) ? null : "PASSWORD_CONFIRMATION_MISMATCH";
    }

    void recordBootstrapEvent(String event, String reason) {
        List<Map<String, String>> history = new ArrayList<>();
        if (evidence.get("bootstrapHistory") instanceof List<?> existing)
            for (Object entry : existing) if (entry instanceof Map<?, ?> item)
                history.add(Map.of("event", Objects.toString(item.get("event"), ""), "reason", Objects.toString(item.get("reason"), ""),
                        "occurredAtUtc", Objects.toString(item.get("occurredAtUtc"), "")));
        history.add(Map.of("event", event, "reason", reason, "occurredAtUtc", Instant.now().toString()));
        evidence.put("bootstrapHistory", history);
    }

    void interact() throws Exception {
        console.printf("%nL01_HUMAN_RUN_ID=%s%nURL local: %s/login%n", text("runId"), BASE);
        console.printf("Correo SUPER_ADMIN sintético: %s%nCorreo ADMIN sintético: %s%nCorreo nuevo recuperación: %s%n", ownerEmail(), adminEmail(), recoveredEmail());
        console.printf("Organización sintética: %s%n", text("organizationId"));
        console.printf("Limpiezas suspendidas mediante lock. No hay entrega SMTP. No grabes ni redirijas esta consola.%n");
        console.printf("Comandos: correo | gate | sesion-cli | reparar-codigo | admin-ok | copia-ok | recuperacion-ok | verificar | salir%n");
        while (true) {
            String command = console.readLine("Ensayo L01> ");
            if (command == null || "salir".equals(command.strip())) {
                archive();
                if ("PASS_MANUAL_SYNTHETIC_ONLY".equals(evidence.get("manualSyntheticAdminResult"))
                        && "PASS_LOCAL_TEMPORARY_COPY_ONLY".equals(evidence.get("manualTemporaryRecoveryResult"))) {
                    if (text("runId").equals(Files.readString(ACTIVE).strip())) Files.delete(ACTIVE);
                    console.printf("HUMAN_TRIAL_EVIDENCE_ARCHIVED=true; TEST_CLEANUP_RELEASED=true%n");
                } else console.printf("TEST_CLEANUP_RELEASED=false; caso incompleto protegido por marcador. Conservar para continuación selectiva.%n");
                return;
            }
            try {
                switch (command.strip()) {
                    case "correo" -> showMailbox();
                    case "gate" -> checkBeforeAcceptance();
                    case "sesion-cli" -> showOwnerSession();
                    case "reparar-codigo" -> repairRecoveryFixture();
                    case "admin-ok" -> { adminConfirmed = true; evidence.put("humanAdminConfirmation", true); persist(); }
                    case "copia-ok" -> { copyConfirmed = true; evidence.put("humanTemporaryCopyReadAndUsedReported", true); persist(); }
                    case "recuperacion-ok" -> { recoveryConfirmed = true; evidence.put("humanRecoveryConfirmation", true); persist(); }
                    case "verificar" -> archive();
                    default -> console.printf("Comando no reconocido.%n");
                }
            } catch (Exception refused) {
                console.printf("PASO_NO_COMPLETADO. Sin detalles sensibles; revisar estado y no reintentar correo automáticamente.%n");
                if ("reparar-codigo".equals(command.strip()) && refused instanceof IllegalStateException
                        && refused.getMessage() != null && refused.getMessage().matches("REPAIR_[A-Z0-9_]{1,90}"))
                    console.printf("L01_FIXTURE_REPAIR_REASON=%s%n", refused.getMessage());
            }
        }
    }

    void showMailbox() {
        // Synthetic OTP is displayed only in the operator's non-redirected console, never archived.
        for (String address : List.of(adminEmail(), recoveredEmail(), ownerEmail())) {
            Mail message = mailbox.remove(address);
            if (message != null && Instant.now().isBefore(message.expiresAt()))
                console.printf("CORREO SIMULADO para %s · vence %s · OTP: %s%n", address, message.expiresAt(), message.code());
        }
    }

    void checkBeforeAcceptance() throws Exception {
        UUID admin = findAdmin();
        if (activationToken == null || admin == null || count(jdbc, "select count(*) from admin_terms_acceptance where admin_user_id=?", admin) != 0)
            throw new IllegalStateException("EXPECTED_VERIFIED_INACTIVE_ADMIN_NO_ACCEPTANCE");
        if (count(jdbc, "select count(*) from admin_activation a join app_user u on u.id=a.admin_user_id where a.admin_user_id=? and not u.active and a.email_verified_at is not null and a.password_set_at is not null", admin) != 1)
            throw new IllegalStateException("EMAIL_NOT_YET_VERIFIED");
        HttpResponse<byte[]> completed = request("POST", "/api/v1/admin-activation/complete", new byte[0], Map.of("X-Admin-Activation-Token", activationToken));
        HttpResponse<byte[]> operation = request("GET", "/api/v1/groups", new byte[0], Map.of("X-Admin-Activation-Token", activationToken));
        JsonNode body = JSON.readTree(completed.body());
        boolean passed = completed.statusCode() == 409 && operation.statusCode() == 401
                && body.toString().contains("Falta la aceptacion expresa")
                && count(jdbc, "select count(*) from admin_terms_acceptance where admin_user_id=?", admin) == 0;
        evidence.put("preAcceptanceGate", Map.of("passed", passed, "activationHttp", completed.statusCode(), "operationalHttp", operation.statusCode(), "checkedAtUtc", Instant.now().toString()));
        persist();
        console.printf("PRE_ACCEPTANCE_GATE=%s (activación HTTP %d; operación sin sesión HTTP %d)%n", passed ? "PASS" : "FAIL", completed.statusCode(), operation.statusCode());
    }

    void showOwnerSession() throws Exception {
        char[] password = console.readPassword("Contraseña actual del SUPER_ADMIN sintético (entrada invisible): ");
        try {
            if (password == null) throw new IllegalStateException("NO_INPUT");
            HttpResponse<byte[]> response = request("POST", "/api/v1/auth/login", JSON.writeValueAsBytes(Map.of("email", ownerEmail(), "password", new String(password))), Map.of());
            observe("/api/v1/auth/login", "POST", JSON.writeValueAsBytes(Map.of("email", ownerEmail())), response);
            if (response.statusCode() != 200 || ownerAccessToken == null) throw new IllegalStateException("LOGIN_FAILED");
            console.printf("Token de prueba para pegar SOLO en la entrada oculta de enroll; no lo registres ni compartas:%n%s%n", ownerAccessToken);
        } finally { if (password != null) Arrays.fill(password, '\0'); }
    }

    void restorePreparedRecoveryReferences() throws Exception {
        recoveryObservationSegment = UUID.randomUUID();
        if (((Number) evidence.getOrDefault("enrollmentResponseCount", 0)).intValue() == 0) return;
        if (!ManualL01RecoveryFixtureRepair.RUN.toString().equals(text("runId")))
            throw new IllegalStateException("PREPARED_RECOVERY_CASE_REQUIRES_REVIEW");
        if (((Number) evidence.getOrDefault("recoveryCompletionResponseCount", 0)).intValue() != 0
                || count(jdbc, "select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_COMPLETED'",
                    ManualL01RecoveryFixtureRepair.OWNER) != 0)
            throw new IllegalStateException("RECOVERY_ALREADY_COMPLETED_REQUIRES_REVIEW");
        if (!ManualL01RecoveryFixtureRepair.authorizationValid()) throw new IllegalStateException("REPAIR_AUTHORIZATION_REQUIRED");
        beginRecoverySegment(evidence, recoveryObservationSegment, Instant.now());
        enrolledKey = jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1 and user_id=? and installation_id=?",
                String.class, ManualL01RecoveryFixtureRepair.OWNER, ManualL01RecoveryFixtureRepair.INSTALLATION);
        String fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Base64.getDecoder().decode(enrolledKey)));
        if (!ManualL01RecoveryFixtureRepair.PUBLIC_SHA.equals(fingerprint) || !fingerprint.equals(text("publicKeyFingerprintSha256")))
            throw new IllegalStateException("REPAIR_PUBLIC_REFERENCE_MISMATCH");
        evidence.put("sessionVersionBeforeRecovery", jdbc.queryForObject("select session_version from app_user where id=?", Long.class,
                ManualL01RecoveryFixtureRepair.OWNER));
        var repairs = jdbc.queryForList("select id::text,occurred_at from audit_event where user_id=? and action=? order by occurred_at",
                ManualL01RecoveryFixtureRepair.OWNER, ManualL01RecoveryFixtureRepair.AUDIT_ACTION);
        if (repairs.size() > 1) throw new IllegalStateException("REPAIR_MULTIPLE_AUDITS_REFUSED");
        if (repairs.size() == 1) {
            evidence.put("syntheticFixtureCodeRepairCount", 1);
            evidence.put("fixtureRepairAuditId", repairs.getFirst().get("id"));
            evidence.put("fixtureRepairHiddenConfirmationBeforeWrite", true);
            evidence.put("fixtureRepairProposalId", "REC-CI07-02");
            evidence.put("fixtureRepairProposalSha256", ManualL01RecoveryFixtureRepair.PROPOSAL_SHA);
            evidence.put("fixtureRepairReferenceSource", "PERSISTED_AUDIT_RELOADED_NO_SECRET");
        }
        persist();
    }

    static List<String> recoverySegmentFields() {
        return List.of("recoveryObservationSegmentId", "recoverySegmentStartedAtUtc", "humanRecoveryConfirmation",
                "humanTemporaryCopyReadAndUsedReported", "publicKeyPreserved", "oldAccessSessionRejected",
                "oldRefreshSessionRejected", "oldEmergencyCodeRejectedByStoredHash", "rotatedEmergencyCodeMatchesStoredHash",
                "recoveredOwnerLoginObserved", "sessionVersionIncremented", "oldAccessSessionsChecked", "oldRefreshSessionsChecked",
                "manualTemporaryRecoveryResult");
    }

    static void beginRecoverySegment(Map<String, Object> state, UUID segment, Instant now) {
        var previous = new LinkedHashMap<String, Object>();
        for (String key : recoverySegmentFields()) if (state.containsKey(key)) previous.put(key, state.get(key));
        previous.put("archivedOnResumeAtUtc", now.toString());
        List<Object> history = new ArrayList<>();
        if (state.get("recoverySegmentHistory") instanceof List<?> old) history.addAll(old);
        history.add(previous);
        state.put("recoverySegmentHistory", history);
        for (String key : recoverySegmentFields()) state.remove(key);
        state.put("recoveryObservationSegmentId", segment.toString());
        state.put("recoverySegmentStartedAtUtc", now.toString());
        state.put("recoverySegmentBaselineSource", "PERSISTED_PUBLIC_KEY_AND_NEWLY_OBSERVED_SESSIONS");
        state.put("previousVolatileSessionsReconstructed", false);
        state.put("originalEmergencyCodeDeliveryVerified", false);
        state.put("manualTemporaryRecoveryResult", "PENDING");
    }

    void authenticateOwnerForRepair(String prompt) throws Exception {
        char[] password = console.readPassword("%s", prompt);
        byte[] body = null;
        try {
            if (password == null) throw new IllegalStateException("REPAIR_AUTHENTICATION_CANCELLED");
            body = JSON.writeValueAsBytes(Map.of("email", ownerEmail(), "password", new String(password)));
            HttpResponse<byte[]> response = request("POST", "/api/v1/auth/login", body, Map.of());
            JsonNode result = JSON.readTree(response.body());
            if (response.statusCode() != 200 || !text("ownerId").equals(result.path("user").path("id").asText())
                    || !"SUPER_ADMIN".equals(result.path("user").path("role").asText()))
                throw new IllegalStateException("REPAIR_AUTHENTICATION_REFUSED");
            observe("/api/v1/auth/login", "POST", JSON.writeValueAsBytes(Map.of("email", ownerEmail())), response);
        } finally { if (password != null) Arrays.fill(password, '\0'); if (body != null) Arrays.fill(body, (byte) 0); }
    }

    void repairRecoveryFixture() throws Exception {
        if (smoke || console == null || recoveryObservationSegment == null)
            throw new IllegalStateException("REPAIR_INTERACTIVE_CASE_REQUIRED");
        var store = new ManualL01RecoveryFixtureRepair.JdbcStore(jdbc,
                context.getBean(PlatformTransactionManager.class), evidence, cleanupLockPid, recoveryObservationSegment);
        var result = ManualL01RecoveryFixtureRepair.execute(new ManualL01RecoveryFixtureRepair.ProtectedTerminal() {
            public void showOnce(char[] code) {
                console.printf("%nCODIGO SUSTITUTO DEL FIXTURE SINTETICO — mostrar una sola vez, guardar separado:%n%s%n", new String(code));
                console.flush();
            }
            public char[] readHiddenConfirmation() {
                return console.readPassword("Repite AHORA los 43 caracteres del sustituto (entrada invisible, sin asteriscos; Enter vacío cancela sin escribir): ");
            }
        }, new ManualL01RecoveryFixtureRepair.Authentication() {
            public void authenticateAndReauthenticate() throws Exception {
                authenticateOwnerForRepair("Autenticación: contraseña actual del SUPER_ADMIN sintético (oculta): ");
                authenticateOwnerForRepair("Reautenticación: repite su contraseña actual (oculta): ");
            }
            public void verifyCurrentSession() throws Exception {
                if (ownerAccessToken == null || ownerRefreshCookie == null) throw new IllegalStateException("REPAIR_SESSION_REQUIRED");
                var response = request("GET", "/api/v1/auth/me", new byte[0], Map.of("Authorization", "Bearer " + ownerAccessToken));
                JsonNode body = JSON.readTree(response.body());
                if (response.statusCode() != 200 || !text("ownerId").equals(body.path("id").asText())
                        || !"SUPER_ADMIN".equals(body.path("role").asText())) throw new IllegalStateException("REPAIR_SESSION_REFUSED");
            }
        }, store, context.getBean(PasswordEncoder.class), code -> oldEmergencyCode = new String(code));
        evidence.putAll(result);
        evidence.put("fixtureRepairReferenceSource", "CONFIRMED_HUMAN_DELIVERY_BEFORE_TRANSACTION");
        persist();
        console.printf("L01_FIXTURE_REPAIR=PASS_PRECONDITION_ONLY%nSYNTHETIC_FIXTURE_CODE_REPAIR_COUNT=1%nRECOVERY_SEGMENT_ID=%s%n", recoveryObservationSegment);
        console.printf("Conserva el sustituto solo localmente. Recuperación manual todavía PENDING; no reimprimir ni repetir la reparación.%n");
    }

    void serve(HttpExchange exchange) {
        try {
            String host = exchange.getRequestHeaders().getFirst("Host");
            String origin = exchange.getRequestHeaders().getFirst("Origin");
            if (!("127.0.0.1:" + FRONTEND_PORT).equals(host) || (origin != null && !BASE.equals(origin))) { reply(exchange, 403, new byte[0], "text/plain"); return; }
            String path = exchange.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                byte[] body = exchange.getRequestBody().readNBytes(1_048_577);
                if (body.length > 1_048_576) { reply(exchange, 413, new byte[0], "text/plain"); return; }
                Map<String, String> headers = new HashMap<>();
                exchange.getRequestHeaders().forEach((name, values) -> {
                    if (!Set.of("host", "connection", "content-length", "upgrade", "http2-settings").contains(name.toLowerCase(Locale.ROOT))) headers.put(name, String.join(",", values));
                });
                HttpResponse<byte[]> response = request(exchange.getRequestMethod(), exchange.getRequestURI().toString(), body, headers);
                // Evidence failures must never suppress the ordinary response, especially a code shown once.
                try { observe(path, exchange.getRequestMethod(), body, response); }
                catch (Exception observationFailure) {
                    evidence.put("observationIncomplete", true);
                    if (console != null) console.printf("OBSERVACION_INCOMPLETA: respuesta original conservada; no repetir la operación ni correo por ese aviso.%n");
                }
                response.headers().map().forEach((name, values) -> { if (!Set.of("connection", "content-length", "transfer-encoding").contains(name)) exchange.getResponseHeaders().put(name, values); });
                reply(exchange, response.statusCode(), response.body(), response.headers().firstValue("content-type").orElse("application/json"));
                Arrays.fill(body, (byte) 0);
            } else if (path.equals("/config/runtime-config.json")) {
                reply(exchange, 200, JSON.writeValueAsBytes(Map.of("apiBaseUrl", "/api/v1", "environment", "test", "appName", "SYNTHETIC HUMAN L01 — NO PILOTO")), "application/json");
            } else {
                Path base = ROOT.resolve("frontend/dist/ruta-fija-crm/browser");
                Path file = base.resolve(path.substring(1)).normalize();
                if (!file.startsWith(base)) { reply(exchange, 404, new byte[0], "text/plain"); return; }
                if (!Files.isRegularFile(file)) file = base.resolve("index.html");
                String type = file.toString().endsWith(".js") ? "application/javascript" : file.toString().endsWith(".css") ? "text/css" : "text/html";
                reply(exchange, 200, Files.readAllBytes(file), type);
            }
        } catch (Exception failure) { try { reply(exchange, 500, new byte[0], "text/plain"); } catch (Exception ignored) { } }
        finally { exchange.close(); }
    }

    synchronized void observe(String path, String method, byte[] requestBody, HttpResponse<byte[]> response) throws Exception {
        if (smoke || response.statusCode() < 200 || response.statusCode() >= 300) return;
        JsonNode body;
        try { body = JSON.readTree(response.body()); } catch (Exception ignored) { return; }
        if (body == null) return;
        if ("POST".equals(method) && path.equals("/api/v1/auth/login")) {
            JsonNode input = JSON.readTree(requestBody);
            if (ownerEmail().equals(input.path("email").asText())) {
                ownerAccessToken = body.path("accessToken").asText(null);
                ownerRefreshCookie = response.headers().allValues("set-cookie").stream().filter(v -> v.startsWith("rf_refresh=")).map(v -> v.split(";", 2)[0]).findFirst().orElse(null);
                if (ownerAccessToken != null) ownerAccessSessions.add(ownerAccessToken);
                if (ownerRefreshCookie != null) ownerRefreshSessions.add(ownerRefreshCookie);
            }
            if (recoveredEmail().equals(input.path("email").asText()) && text("ownerId").equals(body.path("user").path("id").asText()))
                evidence.put("recoveredOwnerLoginObserved", true);
            if (adminEmail().equals(input.path("email").asText()) && text("adminId").equals(body.path("user").path("id").asText()))
                evidence.put("activatedAdminLoginObserved", true);
        }
        if (path.equals("/api/v1/platform/admins") && method.equals("POST")) {
            UUID admin = findAdmin();
            if (admin != null && admin.toString().equals(body.path("adminUserId").asText())) {
                evidence.put("adminId", admin.toString());
                evidence.put("adminCreatedInactiveObserved", count(jdbc, "select count(*) from app_user where id=? and not active", admin) == 1);
            }
        }
        if (path.equals("/api/v1/admin-activation/claim")) activationToken = body.path("activationToken").asText(null);
        if (path.equals("/api/v1/admin-activation/terms") && method.equals("POST")) evidence.put("explicitAcceptanceHttpObserved", JSON.readTree(requestBody).path("accepted").asBoolean(false));
        if (path.equals("/api/v1/auth/super-admin-recovery/enrollment")) {
            oldEmergencyCode = body.path("emergencyCodeShownOnce").asText(null);
            enrolledKey = jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1 and user_id=?", String.class, UUID.fromString(text("ownerId")));
            evidence.put("enrollmentResponseCount", ((Number) evidence.getOrDefault("enrollmentResponseCount", 0)).intValue() + 1);
            evidence.put("publicKeyFingerprintSha256", body.path("publicKeyFingerprintSha256").asText());
            evidence.put("sessionVersionBeforeRecovery", jdbc.queryForObject("select session_version from app_user where id=?", Long.class, UUID.fromString(text("ownerId"))));
        }
        if (path.startsWith("/api/v1/auth/super-admin-recovery/challenges/") && path.endsWith("/proof")
                && ((Number) evidence.getOrDefault("syntheticFixtureCodeRepairCount", 0)).intValue() == 1) {
            String code = JSON.readTree(requestBody).path("emergencyCode").asText();
            if (!code.matches("[A-Za-z0-9_-]{43}")) throw new IllegalStateException("REPAIR_PROOF_FORMAT_REFUSED");
            oldEmergencyCode = code;
            evidence.put("oldCodeReferenceSource", "SUCCESSFUL_NORMAL_PROOF_CURRENT_SEGMENT_REPAIRED_FIXTURE");
        }
        if (path.startsWith("/api/v1/auth/super-admin-recovery/challenges/") && path.endsWith("/complete")) {
            newEmergencyCode = body.path("newEmergencyCodeShownOnce").asText(null);
            evidence.put("recoveryCompletionResponseCount", ((Number) evidence.getOrDefault("recoveryCompletionResponseCount", 0)).intValue() + 1);
            checkRecoveredSessionAndCodes();
        }
        persist();
    }

    void checkRecoveredSessionAndCodes() throws Exception {
        String stored = jdbc.queryForObject("select emergency_code_hash from super_admin_recovery_credential where id=1 and user_id=?", String.class, UUID.fromString(text("ownerId")));
        PasswordEncoder encoder = context.getBean(PasswordEncoder.class);
        evidence.put("oldEmergencyCodeRejectedByStoredHash", oldEmergencyCode != null && !encoder.matches(oldEmergencyCode, stored));
        evidence.put("rotatedEmergencyCodeMatchesStoredHash", newEmergencyCode != null && encoder.matches(newEmergencyCode, stored));
        String currentKey = jdbc.queryForObject("select public_key_x509 from super_admin_recovery_credential where id=1", String.class);
        evidence.put("publicKeyPreserved", enrolledKey != null && enrolledKey.equals(currentKey));
        boolean accessRejected = !ownerAccessSessions.isEmpty(), refreshRejected = !ownerRefreshSessions.isEmpty();
        for (String token : ownerAccessSessions) accessRejected &= request("GET", "/api/v1/auth/me", new byte[0], Map.of("Authorization", "Bearer " + token)).statusCode() == 401;
        for (String cookie : ownerRefreshSessions) refreshRejected &= request("POST", "/api/v1/auth/refresh", "{}".getBytes(StandardCharsets.UTF_8), Map.of("Cookie", cookie)).statusCode() == 401;
        evidence.put("oldAccessSessionRejected", accessRejected);
        evidence.put("oldRefreshSessionRejected", refreshRejected);
        evidence.put("oldAccessSessionsChecked", ownerAccessSessions.size());
        evidence.put("oldRefreshSessionsChecked", ownerRefreshSessions.size());
        long version = jdbc.queryForObject("select session_version from app_user where id=?", Long.class, UUID.fromString(text("ownerId")));
        evidence.put("sessionVersionIncremented", evidence.get("sessionVersionBeforeRecovery") instanceof Number n && version == n.longValue() + 1);
        oldEmergencyCode = null; newEmergencyCode = null;
    }

    UUID findAdmin() {
        var rows = jdbc.queryForList("select u.id from app_user u join admin_activation a on a.admin_user_id=u.id where u.email=? and u.organization_id=? and u.role='ADMIN' and a.created_by_super_admin_id=?", UUID.class,
                adminEmail(), UUID.fromString(text("organizationId")), UUID.fromString(text("ownerId")));
        return rows.size() == 1 ? rows.getFirst() : null;
    }

    synchronized void archive() throws Exception {
        if (smoke) return;
        UUID admin = findAdmin();
        if (admin != null) {
            evidence.put("adminId", admin.toString());
            var acceptance = jdbc.queryForList("""
                    select a.id::text acceptance_id,a.admin_user_id::text account_id,a.organization_id::text organization_id,
                      a.document_id::text document_id,a.document_version,a.content_sha256,a.accepted_at,
                      u.active,act.password_set_at,act.email_verified_at,act.completed_at,act.status,
                      (act.password_set_at<=act.email_verified_at and act.email_verified_at<=a.accepted_at
                        and a.accepted_at<=act.completed_at) ordering_valid,
                      exists(select 1 from person_identity p where p.user_id=u.id) identity_reviewed,
                      exists(select 1 from audit_event ev where ev.user_id=u.id and ev.entity_id=d.id
                        and ev.action='ADMIN_ACTIVATION_TERMS_ACCEPTED') acceptance_audited
                    from admin_terms_acceptance a join app_user u on u.id=a.admin_user_id
                    join admin_activation act on act.admin_user_id=u.id join admin_terms_document d on d.id=a.document_id
                    where a.admin_user_id=? and a.organization_id=? and d.document_key='ADMIN_TERMS' and d.version='1.0.0'
                      and a.document_version=d.version and a.content_sha256=d.content_sha256 and d.content_sha256=? and not d.synthetic
                    """, admin, UUID.fromString(text("organizationId")), APPROVED_SHA);
            evidence.put("persistedSyntheticAdminAcceptance", acceptance);
            evidence.put("manualSyntheticAdminResult", adminEvidenceComplete(evidence, acceptance) ? "PASS_MANUAL_SYNTHETIC_ONLY" : "PENDING");
        } else evidence.put("manualSyntheticAdminResult", "PENDING");
        UUID owner = UUID.fromString(text("ownerId"));
        var challenges = jdbc.queryForList("select id::text,proof_verified_at,consumed_at,failed_proof_attempts,expires_at from super_admin_recovery_challenge where user_id=? order by created_at", owner);
        evidence.put("recoveryChallenges", challenges);
        boolean consumed = challenges.stream().anyMatch(c -> c.get("consumed_at") != null && c.get("proof_verified_at") != null);
        boolean recovery = consumed && recoveryEvidenceComplete(evidence)
                && count(jdbc, "select count(*) from audit_event where user_id=? and action='SUPER_ADMIN_RECOVERY_COMPLETED'", owner) == 1;
        evidence.put("manualTemporaryRecoveryResult", recovery ? "PASS_LOCAL_TEMPORARY_COPY_ONLY" : "PENDING");
        evidence.put("independentCustodyVerified", false);
        evidence.put("legitimateAdminOperationalAcceptanceVerified", false);
        evidence.put("smtpAttempts", 0);
        evidence.put("L01Status", "EN_CURSO");
        evidence.put("after", snapshot(jdbc));
        evidence.put("observedAtUtc", Instant.now().toString());
        persist();
        Path folder = ROOT.resolve("docs/evidencia/L-01/ensayos-humanos-" + text("runId"));
        Files.createDirectories(folder);
        Path file = folder.resolve("observacion-" + Instant.now().toEpochMilli() + ".json");
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence);
        Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Files.writeString(Path.of(file + ".sha256"), hash + "  " + file.getFileName() + System.lineSeparator(), StandardOpenOption.CREATE_NEW);
        console.printf("MANUAL_ADMIN_RESULT=%s%nMANUAL_RECOVERY_RESULT=%s%nEVIDENCE_FILE=%s%nEVIDENCE_SHA256=%s%n", evidence.get("manualSyntheticAdminResult"), evidence.get("manualTemporaryRecoveryResult"), ROOT.relativize(file), hash);
        console.printf("CUSTODIA_INDEPENDIENTE=PENDIENTE; ADMIN_LEGITIMO=PENDIENTE; L01=EN_CURSO%n");
    }

    static boolean adminEvidenceComplete(Map<String, Object> state, List<Map<String, Object>> rows) {
        return !Boolean.TRUE.equals(state.get("observationIncomplete")) && rows.size() == 1
                && state.get("preAcceptanceGate") instanceof Map<?, ?> gate && Boolean.TRUE.equals(gate.get("passed"))
                && List.of("adminCreatedInactiveObserved", "explicitAcceptanceHttpObserved", "activatedAdminLoginObserved", "humanAdminConfirmation")
                    .stream().allMatch(key -> Boolean.TRUE.equals(state.get(key)))
                && "COMPLETED".equals(rows.getFirst().get("status"))
                && List.of("active", "ordering_valid", "identity_reviewed", "acceptance_audited").stream().allMatch(key -> Boolean.TRUE.equals(rows.getFirst().get(key)));
    }

    static boolean recoveryEvidenceComplete(Map<String, Object> state) {
        return !Boolean.TRUE.equals(state.get("observationIncomplete"))
                && (!state.containsKey("syntheticFixtureCodeRepairCount")
                    || (((Number) state.get("syntheticFixtureCodeRepairCount")).intValue() == 1
                        && Boolean.TRUE.equals(state.get("fixtureRepairHiddenConfirmationBeforeWrite"))
                        && "REC-CI07-02".equals(state.get("fixtureRepairProposalId"))
                        && ManualL01RecoveryFixtureRepair.PROPOSAL_SHA.equals(state.get("fixtureRepairProposalSha256"))
                        && state.get("recoveryObservationSegmentId") instanceof String
                        && ((Number) state.getOrDefault("oldAccessSessionsChecked", 0)).intValue() > 0
                        && ((Number) state.getOrDefault("oldRefreshSessionsChecked", 0)).intValue() > 0))
                && List.of("humanRecoveryConfirmation", "humanTemporaryCopyReadAndUsedReported", "publicKeyPreserved", "oldAccessSessionRejected",
                    "oldRefreshSessionRejected", "oldEmergencyCodeRejectedByStoredHash", "rotatedEmergencyCodeMatchesStoredHash", "recoveredOwnerLoginObserved", "sessionVersionIncremented")
                    .stream().allMatch(key -> Boolean.TRUE.equals(state.get(key)))
                && ((Number) state.getOrDefault("enrollmentResponseCount", 0)).intValue() == 1
                && ((Number) state.getOrDefault("recoveryCompletionResponseCount", 0)).intValue() == 1;
    }

    void checkSmoke() throws Exception {
        if (request("GET", "/api/v1/auth/me", new byte[0], Map.of()).statusCode() != 401) throw new IllegalStateException("AUTH_GUARD");
        if (request("GET", "/api/v1/groups", new byte[0], Map.of()).statusCode() != 401) throw new IllegalStateException("OPERATION_GUARD");
        HttpResponse<String> config = http.send(HttpRequest.newBuilder(URI.create(BASE + "/config/runtime-config.json")).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (config.statusCode() != 200 || !config.body().contains("test")) throw new IllegalStateException("TEST_FRONTEND");
        var after = snapshot(jdbc);
        var before = (Map<?, ?>) evidence.get("before");
        for (String key : List.of("historicalCreations", "termsAcceptanceCount", "personIdentityBindings", "currentTerms"))
            if (!Objects.equals(before.get(key), after.get(key))) throw new IllegalStateException("SMOKE_CHANGED_DATA");
        System.out.println("L01_HUMAN_SMOKE=PASS_REAL_BACKEND_PROXY_GUARDS_BOTH_GATES_ENABLED_MAIL_DOUBLE_NO_FIXTURE_WRITE_NO_MIGRATION");
    }

    HttpResponse<byte[]> request(String method, String path, byte[] body, Map<String, String> headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + BACKEND_PORT + path)).timeout(Duration.ofSeconds(25));
        headers.forEach(builder::header);
        if (headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Content-Type"))) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    static void reply(HttpExchange exchange, int status, byte[] body, String type) throws Exception {
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length != 0) exchange.getResponseBody().write(body);
    }

    static String randomSigningKey() { byte[] bytes = new byte[64]; new SecureRandom().nextBytes(bytes); return Base64.getEncoder().encodeToString(bytes); }
    String text(String key) { return Objects.toString(evidence.get(key), ""); }
    String suffix() { return text("runId").substring(0, 8); }
    String ownerEmail() { return "sa.human." + suffix() + "@rutafija.test"; }
    String adminEmail() { return "admin.human." + suffix() + "@rutafija.test"; }
    String recoveredEmail() { return "sa.recovered." + suffix() + "@rutafija.test"; }
    synchronized void persist() throws Exception { if (!smoke) writePublicStateAtomically(STATE, evidence); }

    static void writePublicStateAtomically(Path state, Map<String, Object> data) throws Exception {
        Path temp = Files.createTempFile(state.getParent(), "l01-human-state-", ".tmp");
        try {
            Files.write(temp, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(data));
            Files.move(temp, state, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }

    static void writeRestricted(Path path, byte[] bytes) throws Exception {
        if (Files.exists(path)) throw new IllegalStateException("PRIVATE_FILE_EXISTS");
        Files.createFile(path);
        var posix = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (posix != null) posix.setPermissions(PosixFilePermissions.fromString("rw-------"));
        else {
            var acl = Files.getFileAttributeView(path, AclFileAttributeView.class);
            if (acl == null) throw new IllegalStateException("PRIVATE_ACL_UNAVAILABLE");
            var entry = AclEntry.newBuilder().setType(AclEntryType.ALLOW).setPrincipal(Files.getOwner(path)).setPermissions(EnumSet.allOf(AclEntryPermission.class)).build();
            acl.setAcl(List.of(entry));
        }
        Files.write(path, bytes);
    }

    @Override public void close() throws Exception {
        mailbox.clear(); activationToken = null; ownerAccessToken = null; ownerRefreshCookie = null; oldEmergencyCode = null; newEmergencyCode = null;
        ownerAccessSessions.clear(); ownerRefreshSessions.clear();
        if (frontend != null) frontend.stop(0);
        if (context != null) context.close();
        if (scopeLock != null) scopeLock.close();
    }

    record Mail(String code, Instant expiresAt) { }
}
