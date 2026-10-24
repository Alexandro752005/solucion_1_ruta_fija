import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;

/** Applies only V11 to the protected native development database. */
public final class RunRutaFijaF44Flyway {
    private static final String EXPECTED_URL = "jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1";
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE = List.of(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
    );
    private static final List<String> AFTER = List.of(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"
    );

    private RunRutaFijaF44Flyway() {
    }

    public static void main(String[] args) {
        String url = require("SPRING_FLYWAY_URL");
        String username = require("SPRING_FLYWAY_USERNAME");
        String password = require("SPRING_FLYWAY_PASSWORD");
        assertEquals("url", EXPECTED_URL, url);
        assertEquals("user", EXPECTED_USER, username);

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        List<String> appliedBefore = versions(flyway.info().applied());
        if (AFTER.equals(appliedBefore) && flyway.info().pending().length == 0) {
            System.out.println("F4_4_FLYWAY=PASS state=already_applied migrations=V11 target=development");
            return;
        }
        assertVersions("aplicadas antes", appliedBefore, BEFORE);
        assertVersions("pendientes antes", versions(flyway.info().pending()), List.of("11"));

        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != 1) {
            throw new IllegalStateException("F4.4 esperaba aplicar exactamente V11.");
        }
        assertVersions("aplicadas despues", versions(flyway.info().applied()), AFTER);
        if (flyway.info().pending().length != 0) {
            throw new IllegalStateException("F4.4 dejo migraciones pendientes en desarrollo.");
        }
        System.out.println("F4_4_FLYWAY=PASS state=applied migrations=V11 target=development");
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
            throw new IllegalStateException("F4.4 no obtuvo las versiones " + expected + " para " + operation + ": " + actual);
        }
    }

    private static void assertEquals(String name, String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("F4.4 rechazo " + name + " fuera del destino autorizado.");
        }
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable requerida: " + name);
        }
        return value;
    }
}
