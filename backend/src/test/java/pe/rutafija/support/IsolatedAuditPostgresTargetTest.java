package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/** Configuration-boundary verification only; no JDBC connection or real database. */
class IsolatedAuditPostgresTargetTest {
    @TempDir Path temporary;

    @Test void absentOptInKeepsTheHistoricalHarnessBranch() {
        assertThat(IsolatedAuditPostgresTarget.fromEnvironment(Map.of())).isEmpty();
    }

    @Test void partialOptInCannotFallBackToTheSharedTarget() {
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(
                Map.of("RF_AUDIT_ISOLATED_DATA_DIRECTORY", temporary.toString())));
    }

    @Test void exactExplicitTargetAndCurrentRunAreAccepted() throws Exception {
        var environment = fixture();
        var target = IsolatedAuditPostgresTarget.fromEnvironment(environment).orElseThrow();
        target.validateJdbcUrl("jdbc:postgresql://127.0.0.1:55441/ruta_fija_test");
        target.assertObservation("ruta_fija_test", "rf_migrator", 55441,
                environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY"), "160015", "rf_migrator");
        target.assertObservation("ruta_fija_test", "rf_test", 55441,
                environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY"), "160015", "rf_test");
        target.assertObservation("ruta_fija_test", "rf_app", 55441,
                environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY"), "160015", "rf_app");
        assertThat(target.dataDirectory()).isEqualTo(Path.of(environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY")));
    }

    @Test void sharedAndAmbiguousJdbcTargetsRemainRejectedEvenWithOptIn() throws Exception {
        var target = IsolatedAuditPostgresTarget.fromEnvironment(fixture()).orElseThrow();
        for (String url : new String[]{
                "jdbc:postgresql://127.0.0.1:5432/ruta_fija_test",
                "jdbc:postgresql://localhost:55441/ruta_fija_test",
                "jdbc:postgresql://127.0.0.1:55441/solucion_ruta_fija_1",
                "jdbc:postgresql://127.0.0.1:55440/ruta_fija_test",
                "jdbc:postgresql://127.0.0.1:55441/ruta_fija_test?user=rf_test",
                "jdbc:postgresql://127.0.0.1:55441/ruta_fija_test#fragment",
                "jdbc:postgresql://user:password@127.0.0.1:55441/ruta_fija_test",
                "jdbc:postgresql://127.0.0.1:55441/ruta_fija_test/"}) {
            assertThatIllegalStateException().as(url).isThrownBy(() -> target.validateJdbcUrl(url));
        }
    }

    @Test void differentModeAndRunAreRejectedBeforeJdbc() throws Exception {
        var environment = fixture();
        var wrongMode = new HashMap<>(environment); wrongMode.put("RF_AUDIT_ISOLATED_MODE", "true");
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(wrongMode));
        var wrongRun = new HashMap<>(environment); wrongRun.put("RF_AUDIT_ISOLATED_RUN_ID", UUID.randomUUID().toString());
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(wrongRun));
    }

    @Test void unownedDirectoryAndMissingMarkerAreRejectedBeforeJdbc() throws Exception {
        var environment = fixture();
        var outside = new HashMap<>(environment); outside.put("RF_AUDIT_ISOLATED_DATA_DIRECTORY", temporary.toString());
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(outside));
        Files.delete(Path.of(environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY")).getParent().resolve("connection.json"));
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(environment));
    }

    @Test void wrongPostgresqlMajorAndRelativeDirectoryAreRejectedBeforeJdbc() throws Exception {
        var environment = fixture();
        var data = Path.of(environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY"));
        Files.writeString(data.resolve("PG_VERSION"), "15\n");
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(environment));
        var relative = new HashMap<>(environment); relative.put("RF_AUDIT_ISOLATED_DATA_DIRECTORY", ".runtime/auditoria-integral-2026-11-13/postgresql/data");
        assertThatIllegalStateException().isThrownBy(() -> IsolatedAuditPostgresTarget.fromEnvironment(relative));
    }

    @Test void livePortDatabaseRoleDirectoryAndMajorMustAllMatch() throws Exception {
        var environment = fixture();
        var target = IsolatedAuditPostgresTarget.fromEnvironment(environment).orElseThrow();
        String data = environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY");
        assertThatIllegalStateException().isThrownBy(() -> target.assertObservation("ruta_fija_test", "rf_migrator", 5432, data, "160015", "rf_migrator"));
        assertThatIllegalStateException().isThrownBy(() -> target.assertObservation("solucion_ruta_fija_1", "rf_migrator", 55441, data, "160015", "rf_migrator"));
        assertThatIllegalStateException().isThrownBy(() -> target.assertObservation("ruta_fija_test", "rf_app", 55441, data, "160015", "rf_migrator"));
        assertThatIllegalStateException().isThrownBy(() -> target.assertObservation("ruta_fija_test", "rf_migrator", 55441, temporary.toString(), "160015", "rf_migrator"));
        assertThatIllegalStateException().isThrownBy(() -> target.assertObservation("ruta_fija_test", "rf_migrator", 55441, data, "150000", "rf_migrator"));
    }

    @Test void markerIsRevalidatedBeforeEveryLiveObservation() throws Exception {
        var environment = fixture();
        var target = IsolatedAuditPostgresTarget.fromEnvironment(environment).orElseThrow();
        var data = Path.of(environment.get("RF_AUDIT_ISOLATED_DATA_DIRECTORY"));
        Files.writeString(data.getParent().resolve("connection.json"), "{}");
        assertThatIllegalStateException().isThrownBy(() -> target.assertObservation("ruta_fija_test", "rf_test", 55441, data.toString(), "160015", "rf_test"));
    }

    private Map<String, String> fixture() throws Exception {
        Path project = Files.createDirectories(temporary.resolve(UUID.randomUUID().toString()));
        Files.createDirectories(project.resolve("backend")); Files.writeString(project.resolve("backend/pom.xml"), "<project/>");
        Path data = Files.createDirectories(project.resolve(".runtime/auditoria-integral-2026-11-13/postgresql/data")).toRealPath();
        Files.writeString(data.resolve("PG_VERSION"), "16\n");
        String run = UUID.randomUUID().toString();
        new ObjectMapper().writeValue(data.getParent().resolve("connection.json").toFile(), Map.of(
                "schemaVersion", 1, "runId", run, "port", 55441, "database", "ruta_fija_test",
                "admin", "rf_audit_cluster_admin", "migrator", "rf_migrator", "testUser", "rf_test",
                "appUser", "rf_app", "dataDirectory", data.toString()));
        return Map.of("RF_AUDIT_ISOLATED_MODE", "EXCLUSIVE_AUDIT_55441",
                "RF_AUDIT_ISOLATED_DATA_DIRECTORY", data.toString(), "RF_AUDIT_ISOLATED_RUN_ID", run);
    }
}
