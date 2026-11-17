package pe.rutafija.support;

import org.springframework.test.context.DynamicPropertyRegistry;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Explicit adapter for reviewed specialized tests; never a production bean. */
public final class IsolatedAuditSpecializedPostgres {
    private static final String MODE="EXCLUSIVE_APP_55441";
    private IsolatedAuditSpecializedPostgres() { }

    public static boolean enabled() {
        String mode=System.getenv("RF_AUDIT_SPECIALIZED_INTEGRATION");
        if(mode==null)return false;
        if(!MODE.equals(mode))throw refused();
        target();
        return true;
    }

    public static String databaseName(String historical) {
        return enabled()?"ruta_fija_test":historical;
    }

    public static int port(int historical) {return enabled()?55441:historical;}

    public static Path repositoryRoot(Path historical) {
        return enabled()?target().dataDirectory().getParent().getParent().getParent().getParent():historical;
    }

    public static void register(DynamicPropertyRegistry registry) {
        if(!enabled())throw refused();
        // Loads only the exact opt-in URL and proves live migrator identity
        // before any Spring context or migration can start.
        NativePostgresTestDatabase.load().registerSpringProperties(registry);
        String password=System.getenv("RF_AUDIT_APPLICATION_PASSWORD");
        if(password==null||password.isBlank())throw refused();
        var connectionProperties=new java.util.Properties();
        connectionProperties.setProperty("user","rf_app");
        connectionProperties.setProperty("password",password);
        connectionProperties.setProperty("connectTimeout","5");
        connectionProperties.setProperty("socketTimeout","10");
        try(var connection=DriverManager.getConnection(IsolatedAuditPostgresTarget.URL,connectionProperties)) {
            connection.setReadOnly(true);
            target().verify(connection,"rf_app");
            try(var query=connection.createStatement();var rows=query.executeQuery(
                    "select count(*),max(version::integer) from public.flyway_schema_history where success and version is not null")) {
                if(!rows.next()||rows.getInt(1)!=51||rows.getInt(2)!=51)throw refused();
            }
        } catch(SQLException failure) {throw refused();}
        registry.add("spring.datasource.url",()->IsolatedAuditPostgresTarget.URL);
        registry.add("spring.datasource.username",()->"rf_app");
        registry.add("spring.datasource.password",()->password);
        // The reviewed preflight owns migration/reset; these specialized
        // fixtures run against current V51, including its protective triggers.
        registry.add("spring.flyway.enabled",()->false);
        registry.add("spring.config.import",()->"");
    }

    private static IsolatedAuditPostgresTarget target() {
        return IsolatedAuditPostgresTarget.fromEnvironment(System.getenv()).orElseThrow(IsolatedAuditSpecializedPostgres::refused);
    }

    private static IllegalStateException refused() {
        return new IllegalStateException("EXACT_OWNED_SPECIALIZED_AUDIT_TARGET_REQUIRED");
    }
}
