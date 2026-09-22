import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;

/**
 * F3.1B: applies only the rehearsed V7/V8 pair to the protected local
 * development database. It never accepts a test or recovery JDBC target.
 */
public final class RunRutaFijaF31bFlyway {
    private static final String EXPECTED_URL =
            "jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1";
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE = List.of("1", "2", "3", "4", "5", "6");
    private static final List<String> AFTER = List.of("1", "2", "3", "4", "5", "6", "7", "8");

    private RunRutaFijaF31bFlyway() {
    }

    public static void main(String[] args) {
        String url = requireEnvironment("SPRING_FLYWAY_URL");
        String username = requireEnvironment("SPRING_FLYWAY_USERNAME");
        String password = requireEnvironment("SPRING_FLYWAY_PASSWORD");
        if (!EXPECTED_URL.equals(url)) {
            throw new IllegalArgumentException("F3.1B rechazo un destino distinto al desarrollo local autorizado.");
        }
        if (!EXPECTED_USER.equals(username)) {
            throw new IllegalArgumentException("F3.1B requiere la cuenta rf_migrator.");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
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
            throw new IllegalStateException("F3.1B dejo migraciones pendientes en desarrollo.");
        }

        System.out.println("F3_1B_FLYWAY=PASS migrations=V7,V8 target=development");
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
            throw new IllegalArgumentException("Falta la variable requerida: " + name);
        }
        return value;
    }
}
