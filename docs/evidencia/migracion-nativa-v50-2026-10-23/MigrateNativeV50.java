import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

public class MigrateNativeV50 {
  public static void main(String[] args) {
    String db = System.getenv("RF_V50_DATABASE");
    if (!("solucion_ruta_fija_1".equals(db) || "rf_v50_rehearsal_20261023_1714".equals(db)))
      throw new IllegalArgumentException("EXACT_DATABASE_REQUIRED");
    String password = System.getenv("SPRING_FLYWAY_PASSWORD");
    if (password == null || password.isBlank()) throw new IllegalStateException("MISSING_PRIVATE_PASSWORD");
    Flyway flyway = Flyway.configure()
        .dataSource("jdbc:postgresql://127.0.0.1:5432/" + db, "rf_migrator", password)
        .locations("filesystem:" + Path.of("backend/src/main/resources/db/migration").toAbsolutePath())
        .target("50").cleanDisabled(true).baselineOnMigrate(false).outOfOrder(false)
        .validateOnMigrate(true).validateMigrationNaming(true).group(true)
        .ignoreMigrationPatterns("*:pending").load();
    flyway.validate();
    MigrationInfo[] applied = flyway.info().applied();
    String[] expected = IntStream.rangeClosed(1, 12).mapToObj(Integer::toString).toArray(String[]::new);
    String[] actual = Arrays.stream(applied).map(m -> m.getVersion() == null ? "REPEATABLE" : m.getVersion().toString()).toArray(String[]::new);
    if (!Arrays.equals(expected, actual)) throw new IllegalStateException("EXACT_V12_PREFLIGHT_REQUIRED");
    MigrationInfo[] pending = flyway.info().pending();
    String[] expectedPending = IntStream.rangeClosed(13, 50).mapToObj(Integer::toString).toArray(String[]::new);
    if (!Arrays.equals(expectedPending, Arrays.stream(pending).map(m -> m.getVersion().toString()).toArray(String[]::new)))
      throw new IllegalStateException("EXACT_38_PENDING_MIGRATIONS_REQUIRED");
    var result = flyway.migrate();
    if (result.migrationsExecuted != 38) throw new IllegalStateException("EXPECTED_38_EXECUTED");
    Flyway.configure().configuration(flyway.getConfiguration())
        .ignoreMigrationPatterns(new String[0]).load().validate();
    if (flyway.info().applied().length != 50 || flyway.info().pending().length != 0)
      throw new IllegalStateException("FINAL_HISTORY_MISMATCH");
    System.out.println("MIGRATION_V50=PASS database=" + db + " applied=38 total=50 group=true");
  }
}
