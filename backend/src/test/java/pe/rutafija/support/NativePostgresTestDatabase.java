package pe.rutafija.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuración y limpieza protegidas para las integraciones nativas.
 *
 * <p>Solo admite el destino local {@code ruta_fija_test}. Nunca toma la URL de
 * Flyway de desarrollo como fallback: la URL del migrador se deriva de la URL
 * de pruebas o se declara explícitamente con {@code RF_TEST_MIGRATOR_URL}.</p>
 */
final class NativePostgresTestDatabase {

    private static final String EXPECTED_DATABASE = "ruta_fija_test";
    private static final String EXPECTED_TEST_USER = "rf_test";
    private static final String EXPECTED_MIGRATOR_USER = "rf_migrator";
    private static final String TEST_JWT_SECRET = createTestJwtSecret();
    private static final long CLEANUP_LOCK_KEY = 7_518_440_514L;

    private final String testUrl;
    private final String testUsername;
    private final String testPassword;
    private final String migratorUrl;
    private final String migratorUsername;
    private final String migratorPassword;
    private final IsolatedAuditPostgresTarget isolatedAuditTarget;

    private NativePostgresTestDatabase(
            String testUrl,
            String testUsername,
            String testPassword,
            String migratorUrl,
            String migratorUsername,
            String migratorPassword,
            IsolatedAuditPostgresTarget isolatedAuditTarget
    ) {
        this.testUrl = testUrl;
        this.testUsername = testUsername;
        this.testPassword = testPassword;
        this.migratorUrl = migratorUrl;
        this.migratorUsername = migratorUsername;
        this.migratorPassword = migratorPassword;
        this.isolatedAuditTarget = isolatedAuditTarget;
    }

    static NativePostgresTestDatabase load() {
        var isolated = IsolatedAuditPostgresTarget.fromEnvironment(System.getenv());
        // An explicit disposable run never falls back to shared private credentials.
        Map<String, String> localValues = isolated.isPresent() ? Map.of() : readLocalConfiguration();
        String testUrl = requiredValue("RF_TEST_DATASOURCE_URL", localValues);
        String testUsername = requiredValue("RF_TEST_DATASOURCE_USERNAME", localValues);
        String testPassword = requiredValue("RF_TEST_DATASOURCE_PASSWORD", localValues);
        String migratorUrl = optionalValue("RF_TEST_MIGRATOR_URL", localValues).orElse(testUrl);
        String migratorUsername = isolated.isPresent() ? requiredValue("RF_TEST_MIGRATOR_USERNAME", localValues)
                : optionalValue("RF_TEST_MIGRATOR_USERNAME", localValues)
                .or(() -> optionalValue("SPRING_FLYWAY_USERNAME", localValues))
                .orElseThrow(() -> missing("RF_TEST_MIGRATOR_USERNAME o SPRING_FLYWAY_USERNAME"));
        String migratorPassword = isolated.isPresent() ? requiredValue("RF_TEST_MIGRATOR_PASSWORD", localValues)
                : optionalValue("RF_TEST_MIGRATOR_PASSWORD", localValues)
                .or(() -> optionalValue("SPRING_FLYWAY_PASSWORD", localValues))
                .orElseThrow(() -> missing("RF_TEST_MIGRATOR_PASSWORD o SPRING_FLYWAY_PASSWORD"));

        if (isolated.isPresent()) {
            isolated.orElseThrow().validateJdbcUrl(testUrl);
            isolated.orElseThrow().validateJdbcUrl(migratorUrl);
        } else {
            DatabaseTarget testTarget = validateTarget(testUrl, "RF_TEST_DATASOURCE_URL");
            DatabaseTarget migratorTarget = validateTarget(migratorUrl, "RF_TEST_MIGRATOR_URL");
            if (!testTarget.equals(migratorTarget)) {
                throw new IllegalStateException(
                        "F1.4 rechaza un migrador que no apunte exactamente a ruta_fija_test."
                );
            }
        }
        if (!EXPECTED_TEST_USER.equals(testUsername)) {
            throw new IllegalStateException("F1.4 exige RF_TEST_DATASOURCE_USERNAME=rf_test.");
        }
        if (!EXPECTED_MIGRATOR_USER.equals(migratorUsername)) {
            throw new IllegalStateException("F1.4 exige el migrador local rf_migrator.");
        }

        var database = new NativePostgresTestDatabase(
                testUrl,
                testUsername,
                testPassword,
                migratorUrl,
                migratorUsername,
                migratorPassword,
                isolated.orElse(null)
        );
        database.assertIsolatedIdentityBeforeSpring();
        return database;
    }

    void registerSpringProperties(DynamicPropertyRegistry registry) {
        registry.add("RF_TEST_DATASOURCE_URL", () -> testUrl);
        registry.add("RF_TEST_DATASOURCE_USERNAME", () -> testUsername);
        registry.add("RF_TEST_DATASOURCE_PASSWORD", () -> testPassword);
        registry.add("RF_TEST_MIGRATOR_URL", () -> migratorUrl);
        registry.add("RF_TEST_MIGRATOR_USERNAME", () -> migratorUsername);
        registry.add("RF_TEST_MIGRATOR_PASSWORD", () -> migratorPassword);
        registry.add("spring.datasource.url", () -> testUrl);
        registry.add("spring.datasource.username", () -> testUsername);
        registry.add("spring.datasource.password", () -> testPassword);
        registry.add("spring.flyway.url", () -> migratorUrl);
        registry.add("spring.flyway.user", () -> migratorUsername);
        registry.add("spring.flyway.password", () -> migratorPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.seed.enabled", () -> false);
        registry.add("app.security.jwt.secret-base64", () -> TEST_JWT_SECRET);
        registry.add("app.security.cors.allowed-origins", () -> "http://localhost:4200");
        if (isolatedAuditTarget != null) {
            registry.add("app.mobile.maintenance-enabled", () -> false);
            registry.add("app.qualification.maintenance.enabled", () -> false);
            registry.add("spring.test.mockmvc.print", () -> "NONE");
            registry.add("spring.test.mockmvc.print-only-on-failure", () -> false);
            for (String logger : List.of("org.springframework.web", "org.springframework.security",
                    "org.springframework.jdbc.core", "org.hibernate.SQL", "org.hibernate.orm.jdbc.bind")) {
                registry.add("logging.level." + logger, () -> "OFF");
            }
        }
    }

    private void assertIsolatedIdentityBeforeSpring() {
        if (isolatedAuditTarget == null) return;
        var settings = new java.util.Properties();
        settings.setProperty("user", migratorUsername);
        settings.setProperty("password", migratorPassword);
        settings.setProperty("connectTimeout", "5");
        settings.setProperty("socketTimeout", "10");
        try (var connection = DriverManager.getConnection(migratorUrl, settings)) {
            isolatedAuditTarget.verify(connection, migratorUsername);
        } catch (SQLException exception) {
            throw new IllegalStateException("EXCLUSIVE_AUDIT_IDENTITY_REQUIRED_BEFORE_SPRING");
        }
    }

    void cleanBusinessData() {
        assertNoPendingHumanTrial();
        if ("REBUILD_OWNED_DISPOSABLE_SCHEMA_55441".equals(System.getenv("RF_AUDIT_NATIVE_SCHEMA_RESET"))) {
            rebuildExclusiveDisposableSchema();
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                migratorUrl,
                migratorUsername,
                migratorPassword
        )) {
            connection.setAutoCommit(false);
            assertCurrentDatabase(connection);
            try (PreparedStatement lock = connection.prepareStatement(
                    "select pg_advisory_xact_lock(?)"
            )) {
                lock.setLong(1, CLEANUP_LOCK_KEY);
                lock.execute();
            }

            // Recheck after acquiring the same lock: a human case may have started while waiting.
            assertNoPendingHumanTrial();

            String tables = businessTables(connection);
            if (tables != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("truncate table " + tables + " restart identity cascade");
                }
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "F1.4 no pudo limpiar de forma controlada ruta_fija_test.",
                    exception
            );
        }
    }

    private void rebuildExclusiveDisposableSchema() {
        if (isolatedAuditTarget == null)
            throw new IllegalStateException("SCHEMA_REBUILD_REQUIRES_EXACT_EXCLUSIVE_AUDIT_IDENTITY");
        // V51 deliberately rejects blanket TRUNCATE. A disposable test database
        // can instead begin a fresh schema lifecycle. Never disable a history
        // trigger or apply this reset to the historical shared 5432 branch.
        try (Connection connection=DriverManager.getConnection(migratorUrl,migratorUsername,migratorPassword)) {
            isolatedAuditTarget.verify(connection,migratorUsername);
            try (Statement statement=connection.createStatement()) {
                statement.execute("select pg_advisory_lock("+CLEANUP_LOCK_KEY+")");
                try {
                    assertNoPendingHumanTrial();
                    isolatedAuditTarget.verify(connection,migratorUsername);
                    awaitExclusiveQuiescence(statement);
                    statement.execute("DROP SCHEMA public CASCADE");
                    statement.execute("CREATE SCHEMA public AUTHORIZATION rf_migrator");
                    statement.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
                    statement.execute("GRANT USAGE ON SCHEMA public TO rf_app,rf_test");
                    statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE rf_migrator IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO rf_app");
                    statement.execute("ALTER DEFAULT PRIVILEGES FOR ROLE rf_migrator IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO rf_app");
                    // Apply the unchanged migrations, including their guards,
                    // to the new empty schema. No business history is edited.
                    var flyway=org.flywaydb.core.Flyway.configure()
                            .dataSource(migratorUrl,migratorUsername,migratorPassword)
                            .locations("classpath:db/migration","classpath:db/test-migration")
                            .target("51").group(true).cleanDisabled(true).load();
                    flyway.migrate();
                    if(!flyway.validateWithResult().validationSuccessful)
                        throw new IllegalStateException("EXCLUSIVE_REBUILT_SCHEMA_VALIDATION_REQUIRED");
                } finally {
                    statement.execute("select pg_advisory_unlock("+CLEANUP_LOCK_KEY+")");
                }
            }
        } catch(SQLException exception) {
            throw new IllegalStateException("EXCLUSIVE_DISPOSABLE_SCHEMA_REBUILD_FAILED",exception);
        }
    }

    void finishBusinessDataCase() {
        if ("REBUILD_OWNED_DISPOSABLE_SCHEMA_55441".equals(System.getenv("RF_AUDIT_NATIVE_SCHEMA_RESET"))) {
            if(isolatedAuditTarget==null)
                throw new IllegalStateException("SCHEMA_REBUILD_REQUIRES_EXACT_EXCLUSIVE_AUDIT_IDENTITY");
            // The next @BeforeEach recreates the entire disposable schema.
            // A second recreation here adds no isolation and can race with
            // normal connection-pool housekeeping immediately after a request.
            assertNoPendingHumanTrial();
            assertIsolatedIdentityBeforeSpring();
            return;
        }
        cleanBusinessData();
    }

    private static void awaitExclusiveQuiescence(Statement statement) throws SQLException {
        int stableIdleObservations=0;
        for(int attempt=0;attempt<100;attempt++) {
            try(ResultSet rows=statement.executeQuery("""
                    select count(*) from pg_stat_activity where datname=current_database()
                      and pid<>pg_backend_pid() and state is distinct from 'idle'
                    """)) {
                if(rows.next()&&rows.getInt(1)==0)stableIdleObservations++;
                else stableIdleObservations=0;
                if(stableIdleObservations>=3)return;
            }
            try { Thread.sleep(50); }
            catch(InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("EXCLUSIVE_SCHEMA_REBUILD_INTERRUPTED",interrupted);
            }
        }
        throw new IllegalStateException("EXCLUSIVE_SCHEMA_REBUILD_REFUSES_OTHER_ACTIVE_CONNECTIONS");
    }

    private static void assertNoPendingHumanTrial() {
        for (Path config : localConfigurationCandidates()) {
            assertNoL08ConnectedFixture(config.resolveSibling("l08-connected.active"));
            if(Files.exists(config.resolveSibling("l07-connected.active")))throw new IllegalStateException("L07 connected case active: no global cleanup authorized.");
            assertNoHumanTrialMarker(config.resolveSibling("l01-human-trial.active"));
            if(Files.exists(config.resolveSibling("l06-connected.active")))throw new IllegalStateException("L06 connected case active: no global cleanup authorized.");
            if(Files.exists(config.resolveSibling("l03-connected.active")))throw new IllegalStateException("L03 connected case active: no global cleanup authorized.");
            if(Files.exists(config.resolveSibling("l02-connected.active")))throw new IllegalStateException(
                    "L-02 connected fixture pending archival/selective closure: global cleanup refused.");
            assertNoHistoricalL02Fixture(config.resolveSibling("l02-connected-state.json"));
        }
    }

    static void assertNoL08ConnectedFixture(Path marker) {
        if (Files.exists(marker)) throw new IllegalStateException(
                "L08 connected case protected: archive and retire its exact fixture before database suites or cleanup.");
    }

    static void assertNoHistoricalL02Fixture(Path state) {
        if(Files.exists(state))throw new IllegalStateException(
                "Historical L-02 case and creation counters must be preserved. Use rollback tests or exact selective cleanup; global truncation is not authorized.");
    }

    static void assertNoHumanTrialMarker(Path marker) {
        if (Files.exists(marker)) throw new IllegalStateException(
                "Ensayo humano L-01 pendiente de archivo/liberación: limpieza de ruta_fija_test suspendida. No borrar el marcador para forzar una prueba.");
    }

    void assertTestRolePermissions() {
        try (Connection connection = DriverManager.getConnection(
                testUrl,
                testUsername,
                testPassword
        )) {
            assertCurrentDatabase(connection, testUsername);
            try (PreparedStatement statement = connection.prepareStatement("""
                     select
                         has_schema_privilege(current_user, 'public', 'CREATE') as can_create,
                         has_table_privilege(current_user, 'public.organization', 'SELECT') as can_select,
                         has_table_privilege(current_user, 'public.organization', 'INSERT') as can_insert,
                         has_table_privilege(current_user, 'public.organization', 'UPDATE') as can_update,
                         has_table_privilege(current_user, 'public.organization', 'DELETE') as can_delete
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()
                    || resultSet.getBoolean("can_create")
                    || !resultSet.getBoolean("can_select")
                    || !resultSet.getBoolean("can_insert")
                    || !resultSet.getBoolean("can_update")
                    || !resultSet.getBoolean("can_delete")) {
                throw new IllegalStateException(
                        "F1.4 requiere rf_test con DML y sin CREATE sobre el esquema public."
                );
            }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "F1.4 no pudo validar los privilegios de rf_test.",
                    exception
            );
        }
    }

    private void assertCurrentDatabase(Connection connection) throws SQLException {
        assertCurrentDatabase(connection, migratorUsername);
    }

    private void assertCurrentDatabase(Connection connection, String expectedRole) throws SQLException {
        if (isolatedAuditTarget != null) {
            isolatedAuditTarget.verify(connection, expectedRole);
            return;
        }
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("select current_database()")) {
            if (!resultSet.next() || !EXPECTED_DATABASE.equals(resultSet.getString(1))) {
                throw new IllegalStateException(
                        "F1.4 rechazó una limpieza fuera de ruta_fija_test."
                );
            }
        }
    }

    private String businessTables(Connection connection) throws SQLException {
        String sql = """
                select string_agg(format('%I.%I', schemaname, tablename), ', ' order by tablename)
                from pg_tables
                where schemaname = 'public'
                  and tablename <> 'flyway_schema_history'
                """;
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static DatabaseTarget validateTarget(String jdbcUrl, String variableName) {
        if (!jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(variableName + " debe usar PostgreSQL local por JDBC.");
        }
        URI uri;
        try {
            uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(variableName + " no contiene una URL JDBC válida.", exception);
        }
        String host = uri.getHost();
        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String path = uri.getPath();
        String database = path == null ? "" : path.replaceFirst("^/", "");

        if (!("127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host))
                || port != 5432
                || !EXPECTED_DATABASE.equals(database)
                || uri.getUserInfo() != null) {
            throw new IllegalStateException(
                    variableName + " debe apuntar solo a PostgreSQL local ruta_fija_test:5432."
            );
        }
        return new DatabaseTarget(host.toLowerCase(), port, database);
    }

    private static String requiredValue(String key, Map<String, String> localValues) {
        return optionalValue(key, localValues).orElseThrow(() -> missing(key));
    }

    private static Optional<String> optionalValue(String key, Map<String, String> localValues) {
        String environmentValue = System.getenv(key);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return Optional.of(environmentValue);
        }
        return Optional.ofNullable(localValues.get(key)).filter(value -> !value.isBlank());
    }

    private static IllegalStateException missing(String key) {
        return new IllegalStateException(
                "F1.4 requiere " + key
                        + " en el entorno o en backend/.local/ruta-fija-native.env."
        );
    }

    private static Map<String, String> readLocalConfiguration() {
        for (Path candidate : localConfigurationCandidates()) {
            if (Files.isRegularFile(candidate)) {
                return parseLocalConfiguration(candidate);
            }
        }
        return Map.of();
    }

    private static String createTestJwtSecret() {
        if (IsolatedAuditPostgresTarget.MODE.equals(System.getenv("RF_AUDIT_ISOLATED_MODE"))) {
            String supplied = System.getenv("RF_AUDIT_JWT_SECRET_BASE64");
            try {
                if (supplied == null || Base64.getDecoder().decode(supplied).length < 32) {
                    throw new IllegalStateException("EXCLUSIVE_AUDIT_STABLE_TEST_KEY_REQUIRED");
                }
                return supplied;
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("EXCLUSIVE_AUDIT_STABLE_TEST_KEY_REQUIRED");
            }
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static List<Path> localConfigurationCandidates() {
        Path userDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return List.of(
                userDirectory.resolve(".local/ruta-fija-native.env"),
                userDirectory.resolve("backend/.local/ruta-fija-native.env"),
                userDirectory.resolve("../backend/.local/ruta-fija-native.env").normalize()
        );
    }

    private static Map<String, String> parseLocalConfiguration(Path path) {
        Map<String, String> values = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    throw new IllegalStateException("La configuración local F1.4 tiene una línea inválida.");
                }
                String key = line.substring(0, separator).trim();
                String value = line.substring(separator + 1);
                if (values.putIfAbsent(key, value) != null) {
                    throw new IllegalStateException("La configuración local F1.4 repite una variable.");
                }
            }
            return values;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo leer la configuración local F1.4.", exception);
        }
    }

    private record DatabaseTarget(String host, int port, String database) {
        private DatabaseTarget {
            Objects.requireNonNull(host);
            Objects.requireNonNull(database);
        }
    }
}
