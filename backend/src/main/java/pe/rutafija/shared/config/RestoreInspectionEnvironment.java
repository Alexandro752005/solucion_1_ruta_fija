package pe.rutafija.shared.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Profiles;

import java.util.Set;

public final class RestoreInspectionEnvironment implements EnvironmentPostProcessor, Ordered {
    static final String PROFILE = "restore-inspection";

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.acceptsProfiles(Profiles.of(PROFILE))) {
            return;
        }
        if (environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("Restore inspection cannot be combined with dev or test");
        }
        require(environment, "spring.flyway.enabled", "false");
        require(environment, "app.seed.enabled", "false");
        require(environment, "spring.sql.init.mode", "never");
        require(environment, "spring.jpa.generate-ddl", "false");
        require(environment, "spring.jpa.hibernate.ddl-auto", "validate");
        require(environment, "spring.datasource.hikari.read-only", "true");
        require(environment, "spring.datasource.hikari.connection-init-sql",
                "SET SESSION CHARACTERISTICS AS TRANSACTION READ ONLY");
        for (String property : Set.of("spring.datasource.jndi-name", "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url", "spring.datasource.hikari.data-source-class-name")) {
            if (Binder.get(environment).bind(property, String.class).isBound()) {
                throw forbidden(property);
            }
        }
        var jpa = Binder.get(environment).bind("spring.jpa.properties", Bindable.mapOf(String.class, String.class));
        for (String property : Set.of("hibernate.hbm2ddl.auto",
                "jakarta.persistence.schema-generation.database.action",
                "javax.persistence.schema-generation.database.action")) {
            if (jpa.isBound() && jpa.get().keySet().stream().anyMatch(property::equalsIgnoreCase)) {
                throw forbidden("spring.jpa.properties." + property);
            }
        }
    }

    private static void require(ConfigurableEnvironment environment, String property, String expected) {
        if (!expected.equalsIgnoreCase(Binder.get(environment).bind(property, String.class).orElse(null))) {
            throw forbidden(property);
        }
    }

    private static IllegalStateException forbidden(String property) {
        return new IllegalStateException("Unsafe restore-inspection setting: " + property);
    }
}
