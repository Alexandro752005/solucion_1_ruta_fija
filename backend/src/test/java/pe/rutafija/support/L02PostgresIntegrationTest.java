package pe.rutafija.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

/**
 * L-02 integration boundary: real rf_test, no migrations and no database cleanup.
 * MockMvc calls on the test thread join this rollback-only fixture transaction.
 * A worker thread does not inherit this transaction and requires its own design.
 */
@Transactional
@Rollback
public abstract class L02PostgresIntegrationTest {
    private static final NativePostgresTestDatabase DATABASE = NativePostgresTestDatabase.load();

    @Autowired protected JdbcTemplate jdbc;

    @DynamicPropertySource
    static void localTestDatabase(DynamicPropertyRegistry registry) {
        DATABASE.registerSpringProperties(registry);
        // V28 must have been applied explicitly by the guarded preflight first.
        registry.add("spring.flyway.enabled", () -> false);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.seed.enabled", () -> false);
        registry.add("app.qualification.maintenance.enabled", () -> false);
        // Synthetic clocks must not drive a background purge on another connection.
        registry.add("app.mobile.maintenance-enabled", () -> false);
        registry.add("app.security.admin-terms-gate.enabled", () -> true);
        registry.add("app.security.person-identity-gate.enabled", () -> true);
        // All inherited contexts share Logback. Child DEBUG settings must never
        // render authentication DTOs, bearer values or JDBC parameter bindings.
        for(String logger:java.util.List.of("org.springframework.web","org.springframework.security","org.springframework.jdbc.core","org.hibernate.SQL","org.hibernate.orm.jdbc.bind"))
            registry.add("logging.level."+logger,()->"OFF");
    }

    @BeforeEach
    void verifyRollbackBoundary() {
        NativePostgresTestDatabase.assertNoL08ConnectedFixture(java.nio.file.Path.of(".local/l08-connected.active"));
        NativePostgresTestDatabase.assertNoL08ConnectedFixture(java.nio.file.Path.of("backend/.local/l08-connected.active"));
        if(java.nio.file.Files.exists(java.nio.file.Path.of(".local/l07-connected.active"))||java.nio.file.Files.exists(java.nio.file.Path.of("backend/.local/l07-connected.active")))
            throw new IllegalStateException("L07 connected case protected: finish and archive it before backend fixture suites.");
        if(java.nio.file.Files.exists(java.nio.file.Path.of(".local/l06-connected.active"))||java.nio.file.Files.exists(java.nio.file.Path.of("backend/.local/l06-connected.active")))
            throw new IllegalStateException("L06 connected case protected: finish and archive it before backend fixture suites.");
        if(java.nio.file.Files.exists(java.nio.file.Path.of(".local/l05-connected.active"))||java.nio.file.Files.exists(java.nio.file.Path.of("backend/.local/l05-connected.active")))
            throw new IllegalStateException("L05 connected case protected: finish and archive it before backend fixture suites.");
        DATABASE.assertTestRolePermissions();
        var row = jdbc.queryForMap("select current_database() db, current_user principal");
        if (!"ruta_fija_test".equals(row.get("db")) || !"rf_test".equals(row.get("principal")))
            throw new IllegalStateException("L02 requires ruta_fija_test and rf_test.");
        String version = jdbc.queryForObject("""
                select version from flyway_schema_history
                 where success and version is not null order by installed_rank desc limit 1
                """, String.class);
        Integer failed = jdbc.queryForObject("select count(*) from flyway_schema_history where not success", Integer.class);
        if (!java.util.Set.of("28", "29", "30", "31", "32", "33", "34", "35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "46", "47", "48", "49", "50").contains(version) || failed == null || failed != 0)
            throw new IllegalStateException("Regression requires explicitly migrated successful V28 or additive authorized V29..V50.");
    }
}
