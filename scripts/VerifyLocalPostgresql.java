import java.io.Console;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Properties;

/** F0.2: prueba JDBC del perfil local sin arrancar Spring, Flyway ni las semillas. */
public class VerifyLocalPostgresql {
    private static final String EXPECTED_URL =
            "jdbc:postgresql://127.0.0.1:5432/solucion_ruta_fija_1";

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Indique la ruta de application-local.properties.");
        }
        Properties profile = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]), StandardCharsets.UTF_8)) {
            profile.load(reader);
        }
        String url = resolve(profile.getProperty("spring.datasource.url"));
        if (!EXPECTED_URL.equals(url)) {
            throw new IllegalArgumentException("Destino distinto a la base local aprobada; verificacion cancelada.");
        }
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", resolve(profile.getProperty("spring.datasource.username")));
        connectionProperties.setProperty("password", password());
        connectionProperties.setProperty("connectTimeout", "5");
        connectionProperties.setProperty("socketTimeout", "15");
        connectionProperties.setProperty("ApplicationName", "RutaFija-F02-readonly");
        connectionProperties.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=10000");
        try (Connection connection = DriverManager.getConnection(url, connectionProperties)) {
            connectionProperties.remove("password");
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                String initSql = profile.getProperty("spring.datasource.hikari.connection-init-sql");
                if (!"SET TIME ZONE 'UTC'".equals(initSql)) {
                    throw new IllegalArgumentException("La inicializacion de zona horaria requiere revision.");
                }
                statement.execute(initSql);
                try (ResultSet result = statement.executeQuery("""
                        SELECT current_database() AS database, current_user AS connected_role,
                          current_setting('server_version') AS server_version,
                          current_setting('server_encoding') AS server_encoding,
                          current_setting('client_encoding') AS client_encoding,
                          current_setting('TimeZone') AS session_timezone,
                          current_setting('transaction_read_only') AS read_only,
                          (SELECT count(*) FROM pg_tables WHERE schemaname NOT IN
                            ('pg_catalog', 'information_schema')) AS business_tables,
                          has_database_privilege(current_user, current_database(), 'CREATE') AS can_create,
                          to_regclass('public.flyway_schema_history') AS flyway_history
                        """)) {
                    if (!result.next() || !"solucion_ruta_fija_1".equals(result.getString("database"))
                            || !"UTF8".equals(result.getString("server_encoding"))
                            || !"UTF8".equals(result.getString("client_encoding"))
                            || !result.getString("server_version").startsWith("16.")
                            || !"UTC".equals(result.getString("session_timezone"))
                            || !"on".equals(result.getString("read_only"))) {
                        throw new IllegalStateException("El catalogo no cumple el preflight esperado.");
                    }
                    for (int column = 1; column <= result.getMetaData().getColumnCount(); column++) {
                        System.out.println(result.getMetaData().getColumnLabel(column) + "=" + result.getString(column));
                    }
                }
            } finally {
                connection.rollback();
            }
            System.out.println("JDBC_PREFLIGHT=PASS (conexion de solo lectura)");
        } catch (SQLException error) {
            System.err.println("JDBC_PREFLIGHT=FAIL; SQLSTATE=" + error.getSQLState());
            System.exit(1);
        } finally {
            connectionProperties.remove("password");
        }
    }

    private static String resolve(String value) {
        if (value == null || !value.startsWith("${") || !value.endsWith("}")) {
            throw new IllegalArgumentException("Propiedad local no compatible con la verificacion.");
        }
        String[] parts = value.substring(2, value.length() - 1).split(":", 2);
        String configured = System.getenv(parts[0]);
        return configured == null ? (parts.length == 2 ? parts[1] : "") : configured;
    }

    private static String password() {
        String configured = System.getenv("SPRING_DATASOURCE_PASSWORD");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("Use una consola interactiva o la variable SPRING_DATASOURCE_PASSWORD.");
        }
        char[] secret = console.readPassword("Clave PostgreSQL (entrada oculta): ");
        if (secret == null || secret.length == 0) {
            throw new IllegalStateException("No se recibio una clave.");
        }
        try {
            return new String(secret);
        } finally {
            Arrays.fill(secret, '\0');
        }
    }
}
