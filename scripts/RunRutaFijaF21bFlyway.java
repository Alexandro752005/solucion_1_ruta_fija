import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * F2.1B: ensaya una candidata V6 contra una restauración V5 sin iniciar Spring
 * Boot ni incluir la migración candidata en el classpath operativo.
 */
public final class RunRutaFijaF21bFlyway {
    private static final Pattern FIXTURE_URL = Pattern.compile(
            "^jdbc:postgresql://127\\.0\\.0\\.1:5432/ruta_fija_recovery_\\d{8}_f21b(?:_r[1-9]\\d*)?$"
    );
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE = List.of("1", "2", "3", "4", "5");
    private static final List<String> AFTER = List.of("1", "2", "3", "4", "5", "6");
    private static final String V6_FILE = "V6__unify_administrative_roles_to_admin.sql";

    private RunRutaFijaF21bFlyway() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("F2.1B requiere la carpeta aislada de la candidata V6.");
        }

        String url = requireEnvironment("RF_F21B_FLYWAY_URL");
        String username = requireEnvironment("RF_F21B_FLYWAY_USERNAME");
        String password = requireEnvironment("RF_F21B_FLYWAY_PASSWORD");
        if (!FIXTURE_URL.matcher(url).matches()) {
            throw new IllegalArgumentException("F2.1B rechaza un destino que no sea una recuperación temporal local.");
        }
        if (!EXPECTED_USER.equals(username)) {
            throw new IllegalArgumentException("F2.1B requiere rf_migrator para ensayar V6.");
        }

        Path candidateDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(candidateDirectory) || !Files.isRegularFile(candidateDirectory.resolve(V6_FILE))) {
            throw new IllegalArgumentException("No existe la candidata V6 esperada en la carpeta aislada.");
        }

        String filesystemLocation = "filesystem:" + candidateDirectory.toString().replace('\\', '/');
        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration", filesystemLocation)
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        assertVersions("aplicadas antes", versions(flyway.info().applied()), BEFORE);
        assertVersions("pendientes antes", versions(flyway.info().pending()), List.of("6"));

        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != 1) {
            throw new IllegalStateException("F2.1B esperaba ejecutar exactamente V6.");
        }
        assertVersions("aplicadas después", versions(flyway.info().applied()), AFTER);

        System.out.println("F2_1B_FLYWAY=PASS migration=V6 target=isolated-recovery");
    }

    private static List<String> versions(MigrationInfo[] migrations) {
        return Arrays.stream(migrations)
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .map(Object::toString)
                .toList();
    }

    private static void assertVersions(String operation, List<String> actual, List<String> expected) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "F2.1B no obtuvo las versiones " + expected + " para " + operation + ": " + actual
            );
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable de entorno requerida: " + name);
        }
        return value;
    }
}
