package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.backup.DatabaseIntegrity;
import pe.rutafija.identity.application.OtpEmailSender;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

/** One-shot health-only check of the preserved V48 binary on the approved restored database. */
public final class L08RestoredApiReadOnlyProbe {
    private static final String CASE = "c748e5ab-c20a-43d6-9c17-af5d0a703dab";
    private static final String URL = "jdbc:postgresql://127.0.0.1:55438/ruta_fija_test_l08_locale";
    private static final String JAR_SHA = "23a949ace14161e7e3d7110be31836cc03e26c1df59549fa78e303738d5abaa1";
    private static final int PORT = 4313;
    private static final ObjectMapper JSON = new ObjectMapper();

    private L08RestoredApiReadOnlyProbe() { }

    public static void main(String[] args) {
        String phase = "EXACT_AUTHORIZATION_AND_PATHS";
        Path output = null;
        var evidence = new LinkedHashMap<String, Object>();
        try {
            require(args.length == 0, "NO_ARGUMENTS_ALLOWED");
            require("READ_ONLY_V48_API_EXISTING_LOCALE_TARGET".equals(env("RF_L08_RESTORED_API_AUTHORIZATION")), "AUTHORIZATION");
            require(URL.equals(env("RF_L08_RESTORED_API_URL")), "TARGET");
            Path root = Path.of(env("RF_L08_RESTORED_API_ROOT")).toRealPath();
            Path jar = root.resolve("artifacts/L-07-cierre-2026-10-21/ruta-fija-backend.jar");
            Path applicationClasses = Path.of(env("RF_L08_RESTORED_API_APPLICATION_CLASSES")).toRealPath();
            Path probeClasses = Path.of(env("RF_L08_RESTORED_API_PROBE_CLASSES")).toRealPath();
            require(applicationClasses.startsWith(root.resolve("backend/target")) && probeClasses.startsWith(root.resolve("backend/target")), "CLASSPATH_LOCATION");
            require(!applicationClasses.equals(root.resolve("backend/target/classes")), "NO_CURRENT_APP_CLASSES");
            require(JAR_SHA.equals(sha(jar)), "PRESERVED_JAR_HASH");
            output = root.resolve("docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/arranque-v48-solo-lectura.json");
            require(!Files.exists(output) && Files.isDirectory(output.getParent()), "CREATE_NEW_EVIDENCE_ONLY");
            var manifest = JSON.readTree(Files.readAllBytes(root.resolve("backend/.local/l08-restore-" + CASE + "/l08-locale-isolated-restore.json")));
            require(CASE.equals(manifest.path("caseId").asText()) && "ruta_fija_test_l08_locale".equals(manifest.path("database").asText()), "APPROVED_RESTORE_CASE");
            require(Files.isRegularFile(root.resolve("docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/reconciliacion-snapshot-collation.json")), "PRIOR_SQL_PROOF_REQUIRED");
            var proof = JSON.readTree(Files.readAllBytes(root.resolve("docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/reconciliacion-snapshot-collation.json")));
            require("PASS_READ_ONLY_RECONCILED_SQL_SNAPSHOT_INTEGRITY_NOT_SERVICE_RTO".equals(proof.path("result").asText())
                && CASE.equals(proof.path("caseId").asText()), "PRIOR_SQL_PROOF_RESULT");
            phase = "PRESERVED_BINARY_CLASS_PROVENANCE";
            require(Path.of(RutaFijaApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(applicationClasses), "APP_CLASS_ORIGIN");
            require(Path.of(DatabaseIntegrity.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(probeClasses), "INTEGRITY_HELPER_ORIGIN");
            require(Collections.list(RutaFijaApplication.class.getClassLoader().getResources("pe/rutafija/RutaFijaApplication.class")).size() == 1, "UNIQUE_APP_CLASS");
            int migrationCount = 0;
            try (var zip = new ZipFile(jar.toFile())) {
                for (var entry : Collections.list(zip.entries())) {
                    String name = entry.getName();
                    if (name.matches("BOOT-INF/classes/db/migration/V[0-9]+__.+\\.sql")) {
                        int version = Integer.parseInt(name.substring(name.lastIndexOf('/') + 2, name.indexOf("__")));
                        require(version >= 1 && version <= 48, "MIGRATION_BOUNDARY"); migrationCount++;
                    }
                }
                require(migrationCount == 48, "48_PRESERVED_MIGRATIONS");
                byte[] original = zip.getInputStream(zip.getEntry("BOOT-INF/classes/pe/rutafija/RutaFijaApplication.class")).readAllBytes();
                require(MessageDigest.isEqual(original, Files.readAllBytes(applicationClasses.resolve("pe/rutafija/RutaFijaApplication.class"))), "APP_BYTES_FROM_PRESERVED_JAR");
            }
            assertPortFree();
            phase = "BEFORE_ALL_TABLE_AND_SCHEMA_HASHES";
            Map<String, Object> before = captureIntegrity();
            evidence.put("caseId", CASE); evidence.put("binarySha256", JAR_SHA); evidence.put("schemaVersion", "48");
            evidence.put("database", "ruta_fija_test_l08_locale"); evidence.put("databasePort", 55438);
            evidence.put("locale", "Spanish_Peru.1252"); evidence.put("startedAt", Instant.now().toString());
            evidence.put("binaryMigrationCount", migrationCount);
            evidence.put("sqlSnapshotProofSha256", sha(root.resolve("docs/evidencia/L-08/incremento-2-mision-y-controles/restauracion/reconciliacion-snapshot-collation.json")));
            evidence.put("probeSourceSha256", sha(root.resolve("backend/src/test/java/pe/rutafija/support/L08RestoredApiReadOnlyProbe.java")));
            evidence.put("integrityHelperSourceSha256", sha(root.resolve("backend/src/main/java/pe/rutafija/backup/DatabaseIntegrity.java")));
            var p = properties();
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(new MapPropertySource("L08ExactRestoredV48ReadOnly", p));
            var boot = new SpringApplication(RutaFijaApplication.class);
            boot.setEnvironment(environment); boot.setAddCommandLineProperties(false); boot.setRegisterShutdownHook(false);
            var mailAttempts = new AtomicInteger();
            OtpEmailSender noMail = (recipient, code, expiry) -> { mailAttempts.incrementAndGet(); throw new IllegalStateException("L08_RESTORED_API_REFUSES_ALL_MAIL"); };
            boot.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
                var registry = (BeanDefinitionRegistry) factory;
                if (registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
                    registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
                require(registry.containsBeanDefinition("smtpOtpEmailSender"), "EXACT_MAIL_BOUNDARY");
                registry.removeBeanDefinition("smtpOtpEmailSender"); factory.registerSingleton("smtpOtpEmailSender", noMail);
                Filter onlyHealth = (request, response, chain) -> {
                    var req = (jakarta.servlet.http.HttpServletRequest) request;
                    var res = (jakarta.servlet.http.HttpServletResponse) response;
                    if (!"GET".equals(req.getMethod()) || req.getQueryString() != null
                            || !List.of("/actuator/health/readiness", "/actuator/health/liveness").contains(req.getRequestURI())) {
                        res.setStatus(404); res.setHeader("Cache-Control", "no-store"); return;
                    }
                    chain.doFilter(request, response);
                };
                var filter = new FilterRegistrationBean<Filter>(onlyHealth);
                filter.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
                factory.registerSingleton("l08RestoredApiHealthOnlyFilter", filter);
            }));
            phase = "START_PRESERVED_V48_APPLICATION";
            try (var context = boot.run()) {
                require(((ServletWebServerApplicationContext) context).getWebServer().getPort() == PORT, "EXACT_API_PORT");
                require(context.getBean(OtpEmailSender.class) == noMail, "MAIL_DOUBLE_INSTALLED");
                require(!context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME), "NO_SCHEDULED_PROCESSOR");
                require(!context.containsBean("devDataSeeder") && !context.containsBean("qualificationMaintenanceService")
                        && !context.containsBean("mobileOperationMaintenanceService"), "NO_MUTATING_BACKGROUND_BEANS");
                require(Boolean.TRUE.equals(environment.getProperty("app.security.person-identity-gate.enabled", Boolean.class))
                        && Boolean.TRUE.equals(environment.getProperty("app.security.admin-terms-gate.enabled", Boolean.class)), "GATES_ENABLED");
                var dataSource = context.getBean(javax.sql.DataSource.class);
                require(dataSource instanceof HikariDataSource && ((HikariDataSource) dataSource).isReadOnly(), "READ_ONLY_POOL");
                try (var c = dataSource.getConnection()) { verifyTarget(c, "rf_test"); }
                phase = "REAL_LOCAL_HTTP_HEALTH_AND_DB_COMPONENT";
                var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
                var readiness = health(client, "readiness", List.of("readinessState", "db"));
                var liveness = health(client, "liveness", List.of("livenessState"));
                var blocked = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + "/api/v1/auth/login"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
                require(blocked.statusCode() == 404, "BUSINESS_ROUTES_BLOCKED");
                evidence.put("readiness", readiness); evidence.put("liveness", liveness);
                evidence.put("businessRoutesBlockedByTestFilter", true);
                evidence.put("readOnlyPoolAndSessionVerified", true); evidence.put("identityAndTermsGatesEnabled", true);
                evidence.put("schedulersAndSeederAbsent", true); evidence.put("smtpAdapterReplacedByRefusingDouble", true);
                evidence.put("mailAttempts", mailAttempts.get()); require(mailAttempts.get() == 0, "NO_MAIL_ATTEMPT");
            }
            phase = "CONTEXT_CLOSED_AND_DATABASE_UNCHANGED";
            assertPortFree();
            Map<String, Object> after = captureIntegrity();
            require(before.equals(after), "ALL_TABLE_AND_SCHEMA_HASHES_PRESERVED");
            require(JAR_SHA.equals(sha(jar)), "PRESERVED_BINARY_UNCHANGED");
            evidence.put("beforeIntegrity", before); evidence.put("afterIntegrity", after);
            evidence.put("allTableAndSchemaCountsAndHashesUnchanged", true); evidence.put("contextClosedAndPortReleased", true);
            evidence.put("applicationClassOrigin", root.relativize(applicationClasses).toString().replace('\\', '/'));
            evidence.put("noCurrentApplicationClassesOnClasspath", true); evidence.put("source5432Accessed", false);
            evidence.put("flywayMigrationsExecuted", false); evidence.put("applicationDatabaseWrites", 0);
            evidence.put("businessRecoveryAccredited", false); evidence.put("serviceRtoAccredited", false);
            evidence.put("rpoAccredited", false); evidence.put("independentBackupAccredited", false);
            evidence.put("finishedAt", Instant.now().toString());
            evidence.put("result", "PASS_PRESERVED_V48_API_READ_ONLY_ON_RESTORED_SNAPSHOT_NOT_BUSINESS_RTO");
            Files.write(output, JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence), StandardOpenOption.CREATE_NEW);
            System.out.println("L08_RESTORED_API=" + evidence.get("result"));
            System.out.println("EVIDENCE_FILE=" + root.relativize(output).toString().replace('\\', '/'));
            System.out.println("EVIDENCE_SHA256=" + sha(output));
        } catch (Exception failure) {
            System.out.println("L08_RESTORED_API=STOPPED_KEEP_EXISTING_RESTORE");
            System.out.println("L08_RESTORED_API_PHASE=" + phase);
            System.out.println("L08_RESTORED_API_TYPE=" + failure.getClass().getSimpleName());
            if (failure instanceof IllegalStateException && failure.getMessage() != null && failure.getMessage().matches("[A-Z][A-Z0-9_]{2,79}"))
                System.out.println("L08_RESTORED_API_CHECK=" + failure.getMessage());
            System.exit(1);
        }
    }

    private static LinkedHashMap<String, Object> properties() {
        var p = new LinkedHashMap<String, Object>();
        p.put("spring.profiles.active", "l08-restored-read-only");
        p.put("spring.config.location", "classpath:/application.yml"); p.put("spring.config.import", "");
        p.put("spring.datasource.url", URL); p.put("spring.datasource.username", "rf_test");
        p.put("spring.datasource.password", env("RF_L08_RESTORED_API_TEST_PASSWORD"));
        p.put("spring.flyway.enabled", false); p.put("spring.jpa.hibernate.ddl-auto", "validate"); p.put("spring.jpa.open-in-view", false);
        p.put("spring.datasource.hikari.maximum-pool-size", 3); p.put("spring.datasource.hikari.minimum-idle", 0);
        p.put("spring.datasource.hikari.read-only", true);
        p.put("spring.datasource.hikari.connection-init-sql", "SET default_transaction_read_only = on");
        p.put("spring.datasource.hikari.connection-timeout", 5000); p.put("spring.datasource.hikari.validation-timeout", 3000);
        p.put("spring.datasource.hikari.data-source-properties.connectTimeout", "5");
        p.put("spring.datasource.hikari.data-source-properties.socketTimeout", "20");
        p.put("spring.datasource.hikari.data-source-properties.options", "-c default_transaction_read_only=on -c statement_timeout=20000 -c lock_timeout=3000");
        p.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
        p.put("spring.mail.host", ""); p.put("spring.mail.username", ""); p.put("spring.mail.password", "");
        p.put("management.health.mail.enabled", false);
        p.put("management.endpoint.health.show-details", "always"); p.put("management.endpoint.health.show-components", "always");
        p.put("management.endpoint.health.probes.enabled", true);
        p.put("management.endpoint.health.group.readiness.include", "readinessState,db");
        p.put("management.endpoint.health.group.liveness.include", "livenessState");
        p.put("server.address", "127.0.0.1"); p.put("server.port", PORT); p.put("server.forward-headers-strategy", "none");
        p.put("server.shutdown", "graceful"); p.put("spring.lifecycle.timeout-per-shutdown-phase", "10s");
        p.put("app.seed.enabled", false); p.put("app.mobile.offline-sync.enabled", false);
        p.put("app.qualification.maintenance.enabled", false); p.put("app.mobile.maintenance-enabled", false);
        p.put("app.retention.technical-enabled", false);
        p.put("app.security.person-identity-gate.enabled", true); p.put("app.security.admin-terms-gate.enabled", true);
        p.put("app.security.jwt.secret-base64", env("RF_L08_RESTORED_API_EPHEMERAL_JWT"));
        p.put("app.security.cors.allowed-origins", "http://127.0.0.1:4313");
        p.put("spring.main.banner-mode", "off"); p.put("debug", false); p.put("trace", false);
        p.put("logging.level.root", "OFF"); p.put("logging.level.org.springframework", "OFF");
        p.put("logging.level.org.hibernate", "OFF"); p.put("logging.level.pe.rutafija", "OFF");
        return p;
    }

    private static Map<String, Object> captureIntegrity() throws Exception {
        var properties = new Properties(); properties.setProperty("user", "rf_migrator");
        properties.setProperty("password", env("RF_L08_RESTORED_API_MIGRATOR_PASSWORD"));
        properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "20");
        properties.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=20000 -c lock_timeout=3000");
        try (var connection = DriverManager.getConnection(URL, properties)) {
            connection.setReadOnly(true); verifyTarget(connection, "rf_migrator");
            return DatabaseIntegrity.capture(connection);
        } finally { properties.clear(); }
    }

    private static void verifyTarget(Connection connection, String role) throws Exception {
        require(connection.isReadOnly(), "JDBC_READ_ONLY");
        try (var q = connection.createStatement(); var row = q.executeQuery("select current_database(),current_user,inet_server_port(),datcollate,datctype,current_setting('default_transaction_read_only'),current_setting('transaction_read_only'),(select rolsuper from pg_roles where rolname=current_user),host(inet_server_addr()) from pg_database where datname=current_database()")) {
            require(row.next(), "TARGET_ROW_REQUIRED");
            require("ruta_fija_test_l08_locale".equals(row.getString(1)), "EXACT_DATABASE_NAME");
            require(role.equals(row.getString(2)), "EXACT_DATABASE_ROLE");
            require(row.getInt(3) == 55438, "EXACT_DATABASE_PORT");
            require("Spanish_Peru.1252".equals(row.getString(4)) && "Spanish_Peru.1252".equals(row.getString(5)), "EXACT_DATABASE_REGION");
            require("on".equals(row.getString(6)) && "on".equals(row.getString(7)), "READ_ONLY_SESSION_REQUIRED");
            require(!row.getBoolean(8), "NON_SUPERUSER_ROLE_REQUIRED");
            require("127.0.0.1".equals(row.getString(9)), "EXACT_LOOPBACK_ADDRESS_REQUIRED");
        }
        try (var q = connection.createStatement(); var row = q.executeQuery("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1")) {
            require(row.next() && "48".equals(row.getString(1)) && !row.next(), "EXACT_SCHEMA_48");
        }
        try (var q = connection.createStatement(); var row = q.executeQuery("select bool_and(success),count(*) filter(where version is not null) from flyway_schema_history")) {
            require(row.next() && row.getBoolean(1) && row.getInt(2) == 48, "48_SUCCESSFUL_VERSIONED_MIGRATIONS_ONLY");
        }
    }

    private static Map<String, Object> health(HttpClient client, String group, List<String> components) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + "/actuator/health/" + group))
            .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (InputStream body = response.body()) { bytes = body.readNBytes(4097); }
        require(response.statusCode() == 200 && bytes.length <= 4096, "BOUNDED_HTTP_HEALTH_200");
        JsonNode json = JSON.readTree(bytes); require("UP".equals(json.path("status").asText()), "HEALTH_UP");
        var actual = new ArrayList<String>(); json.path("components").fieldNames().forEachRemaining(actual::add);
        require(actual.size() == components.size() && actual.containsAll(components), "EXACT_HEALTH_COMPOSITION");
        for (String component : components) require("UP".equals(json.path("components").path(component).path("status").asText()), "HEALTH_COMPONENT_UP");
        return Map.of("httpStatus", response.statusCode(), "status", "UP", "components", components, "observedAt", Instant.now().toString());
    }

    private static void assertPortFree() throws Exception {
        try (var socket = new ServerSocket()) { socket.setReuseAddress(false); socket.bind(new InetSocketAddress("127.0.0.1", PORT)); }
    }
    private static String env(String name) { String value = System.getenv(name); require(value != null && !value.isBlank(), "REQUIRED_PRIVATE_INPUT"); return value; }
    private static String sha(Path path) throws Exception {
        var hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) { byte[] chunk = new byte[65536]; int count; while ((count = input.read(chunk)) >= 0) hash.update(chunk, 0, count); }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }
}
