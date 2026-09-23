import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;

/** Applies the approved V9/V10 pair only to the protected local development database. */
public final class RunRutaFijaF33Flyway {
    private static final String EXPECTED_URL = "jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1";
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE = List.of("1", "2", "3", "4", "5", "6", "7", "8");
    private static final List<String> AFTER = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

    private RunRutaFijaF33Flyway() {
    }

    public static void main(String[] args) {
        Flyway flyway = configured(require("SPRING_FLYWAY_URL"), require("SPRING_FLYWAY_USERNAME"), require("SPRING_FLYWAY_PASSWORD"));
        assertEquals("url", EXPECTED_URL, require("SPRING_FLYWAY_URL"));
        assertEquals("user", EXPECTED_USER, require("SPRING_FLYWAY_USERNAME"));
        assertVersions("aplicadas antes", versions(flyway.info().applied()), BEFORE);
        assertVersions("pendientes antes", versions(flyway.info().pending()), List.of("9", "10"));
        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != 2) {
            throw new IllegalStateException("F3.3 esperaba aplicar exactamente V9 y V10.");
        }
        assertVersions("aplicadas despues", versions(flyway.info().applied()), AFTER);
        if (flyway.info().pending().length != 0) {
            throw new IllegalStateException("F3.3 dejo migraciones pendientes en desarrollo.");
        }
        System.out.println("F3_3_FLYWAY=PASS migrations=V9,V10 target=development");
    }

    static Flyway configured(String url, String username, String password) {
        return Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
    }

    static List<String> versions(MigrationInfo[] migrations) {
        return Arrays.stream(migrations)
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .map(Object::toString)
                .toList();
    }

    static void assertVersions(String operation, List<String> actual, List<String> expected) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("F3.3 no obtuvo las versiones " + expected + " para " + operation + ": " + actual);
        }
    }

    static void assertEquals(String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("F3.3 rechazo " + name + " fuera del destino autorizado.");
        }
    }

    static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable requerida: " + name);
        }
        return value;
    }
}
