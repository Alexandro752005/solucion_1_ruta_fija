package pe.rutafija.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.seed")
public record DevSeedProperties(boolean enabled, String demoPassword) {
}
