import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Applies V9/V10 only to a restored, isolated local recovery copy. */
public final class RunRutaFijaF33RehearsalFlyway {
    private static final Pattern RECOVERY_URL = Pattern.compile(
            "^jdbc:postgresql://127\\.0\\.0\\.1:5432/ruta_fija_recovery_\\d{8}_f33(?:_r[1-9]\\d*)?$"
    );
    private static final String EXPECTED_USER = "rf_migrator";
    private static final List<String> BEFORE = List.of("1", "2", "3", "4", "5", "6", "7", "8");
    private static final List<String> AFTER = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10");

    private RunRutaFijaF33RehearsalFlyway() {
    }

    public static void main(String[] args) {
        String url = require("RF_F33_FLYWAY_URL");
        String username = require("RF_F33_FLYWAY_USERNAME");
        String password = require("RF_F33_FLYWAY_PASSWORD");
        if (!RECOVERY_URL.matcher(url).matches() || !EXPECTED_USER.equals(username)) {
            throw new IllegalArgumentException("F3.3 rechazo un destino o usuario de ensayo no autorizado.");
        }
        Flyway flyway = RunRutaFijaF33Flyway.configured(url, username, password);
        RunRutaFijaF33Flyway.assertVersions("ensayo antes", RunRutaFijaF33Flyway.versions(flyway.info().applied()), BEFORE);
        RunRutaFijaF33Flyway.assertVersions("ensayo pendiente", RunRutaFijaF33Flyway.versions(flyway.info().pending()), List.of("9", "10"));
        MigrateResult result = flyway.migrate();
        if (result.migrationsExecuted != 2) {
            throw new IllegalStateException("F3.3 esperaba aplicar V9/V10 en la recuperacion aislada.");
        }
        RunRutaFijaF33Flyway.assertVersions("ensayo despues", RunRutaFijaF33Flyway.versions(flyway.info().applied()), AFTER);
        System.out.println("F3_3_REHEARSAL_FLYWAY=PASS migrations=V9,V10 target=isolated-recovery");
    }

    private static String require(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Falta la variable requerida: " + name);
        }
        return value;
    }
}
