package pe.rutafija.shared.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestoreInspectionEnvironmentTest {
    @Test
    void loadsTheReadonlyProfileBeforeCreatingAnyBeans() {
        try (var context = application().profiles("restore-inspection").run()) {
            assertThat(context.getEnvironment().getProperty("spring.flyway.enabled")).isEqualTo("false");
            assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.read-only")).isEqualTo("true");
            assertThat(context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "spring.flyway.enabled=true", "app.seed.enabled=true", "spring.sql.init.mode=always",
            "spring.jpa.hibernate.ddl-auto=update", "spring.jpa.generate-ddl=true",
            "spring.jpa.properties.hibernate.hbm2ddl.auto=update",
            "spring.jpa.properties.jakarta.persistence.schema-generation.database.action=create",
            "spring.datasource.hikari.read-only=false", "spring.datasource.type=alternate",
            "spring.datasource.hikari.jdbc-url=invalid", "spring.datasource.hikari.connection-init-sql=SELECT 1"
    })
    void rejectsAnUnsafeOverrideBeforeStartup(String setting) {
        assertThatThrownBy(() -> application().profiles("restore-inspection").run("--" + setting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unsafe restore-inspection setting: " + setting.substring(0, setting.indexOf('=')));
    }

    @Test
    void rejectsLoadingTheTestMigrationsAndKeepsNormalRuntimeSettings() {
        assertThatThrownBy(() -> application().profiles("restore-inspection", "test").run())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Restore inspection cannot be combined with dev or test");
        try (var context = application().run()) {
            assertThat(context.getEnvironment().getProperty("spring.flyway.enabled")).isEqualTo("true");
        }
    }

    private static SpringApplicationBuilder application() {
        return new SpringApplicationBuilder(EnvironmentOnly.class)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .registerShutdownHook(false);
    }

    @Configuration(proxyBeanMethods = false)
    static class EnvironmentOnly {
    }
}
