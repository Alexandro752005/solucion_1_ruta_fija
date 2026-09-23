import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;

/**
 * Bootstrap actual: ejecuta Flyway sin iniciar Spring Boot, HTTP, semillas ni el CRM.
 * Las credenciales se leen solo del entorno de proceso preparado por PowerShell.
 */
public final class RunRutaFijaFlyway {
    private static final String EXPECTED_URL =
            "jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1";
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> EXPECTED_VERSIONS = List.of(
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
    );

    private RunRutaFijaFlyway() {
    }

    public static void main(String[] args) {
        String url = requireEnvironment("SPRING_FLYWAY_URL");
        String username = requireEnvironment("SPRING_FLYWAY_USERNAME");
        String password = requireEnvironment("SPRING_FLYWAY_PASSWORD");

        if (!EXPECTED_URL.equals(url)) {
            throw new IllegalArgumentException("Flyway rechazo un destino distinto al desarrollo local autorizado.");
        }
        if (!EXPECTED_USER.equals(username)) {
            throw new IllegalArgumentException("Flyway requiere la cuenta rf_migrator en F1.3.");
        }

        Flyway flyway = Flyway.configure()
                .dataSource(url, username, password)
                .locations("classpath:db/migration")
                .validateMigrationNaming(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();

        MigrationInfo[] appliedBefore = flyway.info().applied();
        MigrationInfo[] pendingBefore = flyway.info().pending();
        if (appliedBefore.length != 0 || pendingBefore.length != EXPECTED_VERSIONS.size()) {
            throw new IllegalStateException("El bootstrap exige una base vacia con exactamente V1-V10 pendientes.");
        }

        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != EXPECTED_VERSIONS.size()) {
            throw new IllegalStateException("Flyway no aplico exactamente las diez migraciones esperadas.");
        }

        List<String> appliedVersions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .filter(version -> version != null)
                .map(Object::toString)
                .toList();
        if (!EXPECTED_VERSIONS.equals(appliedVersions)) {
            throw new IllegalStateException("El historial Flyway final no corresponde exactamente a V1-V10.");
        }

        System.out.println("F3_3_FLYWAY_BOOTSTRAP=PASS migrations=10");
        System.out.println("El bootstrap no inicio Spring Boot, API, CRM ni Docker.");
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable de entorno requerida: " + name);
        }
        return value;
    }
}
