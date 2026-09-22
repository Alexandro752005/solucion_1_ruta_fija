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

    private NativePostgresTestDatabase(
            String testUrl,
            String testUsername,
            String testPassword,
            String migratorUrl,
            String migratorUsername,
            String migratorPassword
    ) {
        this.testUrl = testUrl;
        this.testUsername = testUsername;
        this.testPassword = testPassword;
        this.migratorUrl = migratorUrl;
        this.migratorUsername = migratorUsername;
        this.migratorPassword = migratorPassword;
    }

    static NativePostgresTestDatabase load() {
        Map<String, String> localValues = readLocalConfiguration();
        String testUrl = requiredValue("RF_TEST_DATASOURCE_URL", localValues);
        String testUsername = requiredValue("RF_TEST_DATASOURCE_USERNAME", localValues);
        String testPassword = requiredValue("RF_TEST_DATASOURCE_PASSWORD", localValues);
        String migratorUrl = optionalValue("RF_TEST_MIGRATOR_URL", localValues).orElse(testUrl);
        String migratorUsername = optionalValue("RF_TEST_MIGRATOR_USERNAME", localValues)
                .or(() -> optionalValue("SPRING_FLYWAY_USERNAME", localValues))
                .orElseThrow(() -> missing("RF_TEST_MIGRATOR_USERNAME o SPRING_FLYWAY_USERNAME"));
        String migratorPassword = optionalValue("RF_TEST_MIGRATOR_PASSWORD", localValues)
                .or(() -> optionalValue("SPRING_FLYWAY_PASSWORD", localValues))
                .orElseThrow(() -> missing("RF_TEST_MIGRATOR_PASSWORD o SPRING_FLYWAY_PASSWORD"));

        DatabaseTarget testTarget = validateTarget(testUrl, "RF_TEST_DATASOURCE_URL");
        DatabaseTarget migratorTarget = validateTarget(migratorUrl, "RF_TEST_MIGRATOR_URL");
        if (!testTarget.equals(migratorTarget)) {
            throw new IllegalStateException(
                    "F1.4 rechaza un migrador que no apunte exactamente a ruta_fija_test."
            );
        }
        if (!EXPECTED_TEST_USER.equals(testUsername)) {
            throw new IllegalStateException("F1.4 exige RF_TEST_DATASOURCE_USERNAME=rf_test.");
        }
        if (!EXPECTED_MIGRATOR_USER.equals(migratorUsername)) {
            throw new IllegalStateException("F1.4 exige el migrador local rf_migrator.");
        }

        return new NativePostgresTestDatabase(
                testUrl,
                testUsername,
                testPassword,
                migratorUrl,
                migratorUsername,
                migratorPassword
        );
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
    }

    void cleanBusinessData() {
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

    void assertTestRolePermissions() {
        try (Connection connection = DriverManager.getConnection(
                testUrl,
                testUsername,
                testPassword
        );
             PreparedStatement statement = connection.prepareStatement("""
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
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "F1.4 no pudo validar los privilegios de rf_test.",
                    exception
            );
        }
    }

    private void assertCurrentDatabase(Connection connection) throws SQLException {
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
