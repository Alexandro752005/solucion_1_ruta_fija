import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * F3.1B: rehearses V7/V8 from an isolated filesystem location. The active
 * classpath deliberately contains only V1-V6 during this command.
 */
public final class RunRutaFijaF31bRehearsalFlyway {
    private static final Pattern RECOVERY_URL = Pattern.compile(
            "^jdbc:postgresql://127\\.0\\.0\\.1:5432/ruta_fija_recovery_\\d{8}_f31b(?:_r[1-9]\\d*)?$"
    );
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE = List.of("1", "2", "3", "4", "5", "6");
    private static final List<String> AFTER = List.of("1", "2", "3", "4", "5", "6", "7", "8");
    private static final List<String> CANDIDATES = List.of(
            "V7__mobile_assignment_workflow.sql",
            "V8__mobile_current_location.sql"
    );

    private RunRutaFijaF31bRehearsalFlyway() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("F3.1B requiere la carpeta aislada de V7 y V8.");
        }

        String url = requireEnvironment("RF_F31B_FLYWAY_URL");
        String username = requireEnvironment("RF_F31B_FLYWAY_USERNAME");
        String password = requireEnvironment("RF_F31B_FLYWAY_PASSWORD");
        if (!RECOVERY_URL.matcher(url).matches()) {
            throw new IllegalArgumentException("F3.1B rechaza un destino distinto a la recuperacion aislada local.");
        }
        if (!EXPECTED_USER.equals(username)) {
            throw new IllegalArgumentException("F3.1B requiere rf_migrator para ensayar V7 y V8.");
        }

        Path candidateDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        assertCandidateDirectory(candidateDirectory);
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
        assertVersions("pendientes antes", versions(flyway.info().pending()), List.of("7", "8"));

        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != 2) {
            throw new IllegalStateException("F3.1B esperaba aplicar exactamente V7 y V8.");
        }

        assertVersions("aplicadas despues", versions(flyway.info().applied()), AFTER);
        if (flyway.info().pending().length != 0) {
            throw new IllegalStateException("F3.1B dejo migraciones pendientes en la recuperacion aislada.");
        }
        System.out.println("F3_1B_REHEARSAL_FLYWAY=PASS migrations=V7,V8 target=isolated-recovery");
    }

    private static void assertCandidateDirectory(Path candidateDirectory) throws Exception {
        if (!Files.isDirectory(candidateDirectory)) {
            throw new IllegalArgumentException("No existe la carpeta candidata F3.1B.");
        }
        List<String> actual;
        try (Stream<Path> files = Files.list(candidateDirectory)) {
            actual = files
                    .filter(Files::isRegularFile)
                    .map(path -> path.getFileName().toString())
                    .sorted()
                    .toList();
        }
        if (!CANDIDATES.equals(actual)) {
            throw new IllegalArgumentException("La carpeta candidata F3.1B debe contener exactamente V7 y V8.");
        }
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
                    "F3.1B no obtuvo las versiones " + expected + " para " + operation + ": " + actual
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
