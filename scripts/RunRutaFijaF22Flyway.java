import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;

/**
 * F2.2: applies the already rehearsed V6 only to the protected local
 * development database. It never accepts a test or recovery JDBC target.
 */
public final class RunRutaFijaF22Flyway {
    private static final String EXPECTED_URL =
            "jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1";
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE_VERSIONS = List.of("1", "2", "3", "4", "5");
    private static final List<String> AFTER_VERSIONS = List.of("1", "2", "3", "4", "5", "6");

    private RunRutaFijaF22Flyway() {
    }

    public static void main(String[] args) {
        String url = requiredEnvironment("SPRING_FLYWAY_URL");
        String username = requiredEnvironment("SPRING_FLYWAY_USERNAME");
        String password = requiredEnvironment("SPRING_FLYWAY_PASSWORD");
        if (!EXPECTED_URL.equals(url)) {
            throw new IllegalArgumentException("F2.2 rechazo un destino distinto a desarrollo local autorizado.");
        }
        if (!EXPECTED_USER.equals(username)) {
            throw new IllegalArgumentException("F2.2 requiere la cuenta rf_migrator.");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        assertVersions("aplicado previo", flyway.info().applied(), BEFORE_VERSIONS);
        assertVersions("pendiente previo", flyway.info().pending(), List.of("6"));

        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != 1) {
            throw new IllegalStateException("F2.2 esperaba aplicar exactamente V6.");
        }
        assertVersions("aplicado final", flyway.info().applied(), AFTER_VERSIONS);
        if (flyway.info().pending().length != 0) {
            throw new IllegalStateException("F2.2 dejó migraciones pendientes.");
        }

        System.out.println("F2_2_FLYWAY=PASS migration=V6 target=development");
    }

    private static void assertVersions(String state, MigrationInfo[] migrations, List<String> expected) {
        List<String> actual = Arrays.stream(migrations)
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .map(Object::toString)
                .toList();
        if (!expected.equals(actual)) {
            throw new IllegalStateException("F2.2 no reconoce el estado " + state + ": " + actual);
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable requerida: " + name);
        }
        return value;
    }
}
