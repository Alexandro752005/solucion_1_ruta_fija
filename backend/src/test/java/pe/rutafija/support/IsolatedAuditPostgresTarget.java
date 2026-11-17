package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Opt-in boundary for one disposable audit cluster. It does not broaden the
 * historical port-5432 harness: a separate URL, current-run marker and live
 * data-directory observation are all required before Spring/Flyway can start.
 */
final class IsolatedAuditPostgresTarget {
    static final String MODE = "EXCLUSIVE_AUDIT_55441";
    static final String URL = "jdbc:postgresql://127.0.0.1:55441/ruta_fija_test";
    private static final String FAILURE = "EXCLUSIVE_AUDIT_POSTGRES_TARGET_NOT_PROVEN";
    private final Path dataDirectory;
    private final String runId;

    private IsolatedAuditPostgresTarget(Path dataDirectory, String runId) {
        this.dataDirectory = dataDirectory;
        this.runId = runId;
    }

    static Optional<IsolatedAuditPostgresTarget> fromEnvironment(Map<String, String> environment) {
        String mode = environment.get("RF_AUDIT_ISOLATED_MODE");
        String directory = environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY");
        String run = environment.get("RF_AUDIT_ISOLATED_RUN_ID");
        if (mode == null && directory == null && run == null) return Optional.empty();
        if (!MODE.equals(mode) || directory == null || run == null) throw rejected();
        try {
            if (!UUID.fromString(run).toString().equals(run)) throw rejected();
            Path supplied = Path.of(directory);
            if (!supplied.isAbsolute()) throw rejected();
            Path real = supplied.toRealPath();
            if (!real.equals(supplied.normalize()) || real.getNameCount() < 5
                    || !real.getFileName().toString().equals("data")
                    || !real.getParent().getFileName().toString().equals("postgresql")
                    || !real.getParent().getParent().getFileName().toString().equals("auditoria-integral-2026-11-13")
                    || !real.getParent().getParent().getParent().getFileName().toString().equals(".runtime")
                    || !Files.isRegularFile(real.getParent().getParent().getParent().getParent().resolve("backend/pom.xml"))) {
                throw rejected();
            }
            var target = new IsolatedAuditPostgresTarget(real, run);
            target.assertMarker();
            return Optional.of(target);
        } catch (IOException | IllegalArgumentException exception) {
            throw rejected();
        }
    }

    Path dataDirectory() { return dataDirectory; }

    void validateJdbcUrl(String jdbcUrl) {
        if (!URL.equals(jdbcUrl)) throw rejected();
    }

    void verify(Connection connection, String expectedRole) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(5);
            try (var row = statement.executeQuery("""
                    SELECT current_database(),current_user,inet_server_port(),
                           current_setting('data_directory'),current_setting('server_version_num')
                    """)) {
                if (!row.next()) throw rejected();
                assertObservation(row.getString(1), row.getString(2), row.getInt(3),
                        row.getString(4), row.getString(5), expectedRole);
                if (row.next()) throw rejected();
            }
        }
    }

    void assertObservation(String database, String role, int port, String observedDirectory,
                           String serverVersion, String expectedRole) {
        assertMarker();
        if (!"ruta_fija_test".equals(database) || port != 55441
                || !Set.of("rf_migrator", "rf_test", "rf_app").contains(expectedRole)
                || !expectedRole.equals(role) || serverVersion == null || !serverVersion.startsWith("16")) {
            throw rejected();
        }
        try {
            if (observedDirectory == null || !dataDirectory.equals(Path.of(observedDirectory).toRealPath())) {
                throw rejected();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw rejected();
        }
    }

    private void assertMarker() {
        try {
            if (!"16".equals(Files.readString(dataDirectory.resolve("PG_VERSION")).trim())) throw rejected();
            JsonNode marker = new ObjectMapper().readTree(dataDirectory.getParent().resolve("connection.json").toFile());
            if (marker.path("schemaVersion").asInt() != 1 || marker.path("port").asInt() != 55441
                    || !runId.equals(marker.path("runId").asText())
                    || !"ruta_fija_test".equals(marker.path("database").asText())
                    || !"rf_audit_cluster_admin".equals(marker.path("admin").asText())
                    || !"rf_migrator".equals(marker.path("migrator").asText())
                    || !"rf_test".equals(marker.path("testUser").asText())
                    || !"rf_app".equals(marker.path("appUser").asText())
                    || !dataDirectory.equals(Path.of(marker.path("dataDirectory").asText()).toRealPath())) {
                throw rejected();
            }
        } catch (IOException | IllegalArgumentException exception) {
            throw rejected();
        }
    }

    private static IllegalStateException rejected() {
        // Never echo credentials, JDBC user-info or private JSON content.
        return new IllegalStateException(FAILURE);
    }
}
