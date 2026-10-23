package pe.rutafija.operation.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import pe.rutafija.operation.application.MobileOfflineSyncService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MobileOfflineRegistrationTest {

    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withBean(MobileOfflineSyncService.class, () -> mock(MobileOfflineSyncService.class))
            .withUserConfiguration(MobileOfflineSyncController.class);

    @Test
    void ordinaryRuntimeCannotRegisterOfflineEvenWhenPropertyIsAccidentallyEnabled() {
        context.withPropertyValues("spring.profiles.active=local", "app.mobile.offline-sync.enabled=true")
                .run(application -> assertThat(application).doesNotHaveBean(MobileOfflineSyncController.class));
    }

    @Test
    void testProfileDoesNotRegisterOfflineByDefault() {
        context.withPropertyValues("spring.profiles.active=test")
                .run(application -> assertThat(application).doesNotHaveBean(MobileOfflineSyncController.class));
    }

    @Test
    void onlyExplicitLegacyRegressionConfigurationRegistersIt() {
        context.withPropertyValues("spring.profiles.active=test", "app.mobile.offline-sync.enabled=true")
                .run(application -> assertThat(application).hasSingleBean(MobileOfflineSyncController.class));
    }
}
