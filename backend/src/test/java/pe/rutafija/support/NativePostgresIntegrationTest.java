package pe.rutafija.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base para integraciones que se ejecutan contra PostgreSQL 16 nativo.
 *
 * <p>La clase impide que Failsafe use desarrollo o recuperación y mantiene los
 * datos de prueba fuera de Git. Las migraciones siguen siendo responsabilidad
 * de Flyway con {@code rf_migrator}; el contexto de la aplicación usa
 * {@code rf_test}.</p>
 */
public abstract class NativePostgresIntegrationTest {

    private static final NativePostgresTestDatabase DATABASE = NativePostgresTestDatabase.load();

    @DynamicPropertySource
    static void nativePostgresProperties(DynamicPropertyRegistry registry) {
        DATABASE.registerSpringProperties(registry);
    }

    @BeforeEach
    void cleanNativeDatabaseBeforeEachTest() {
        DATABASE.cleanBusinessData();
        DATABASE.assertTestRolePermissions();
    }

    @AfterEach
    void cleanNativeDatabaseAfterEachTest() {
        DATABASE.cleanBusinessData();
    }
}
