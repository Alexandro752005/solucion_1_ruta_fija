package pe.rutafija.shared.exception;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import static org.assertj.core.api.Assertions.assertThat;

class SafeLoggingDefaultsTest {
    @Test void defaultAndDevelopmentConfigurationDoNotEnableCredentialOrJdbcTracing()throws Exception{
        var source=new YamlPropertySourceLoader().load("default",new ClassPathResource("application.yml")).getFirst();
        for(String category:java.util.List.of("org.springframework.web","org.springframework.security","org.springframework.jdbc.core"))
            assertThat(source.getProperty("logging.level."+category)).isEqualTo("WARN");
        for(String category:java.util.List.of("org.hibernate.SQL","org.hibernate.orm.jdbc.bind","org.hibernate.engine.jdbc.spi.SqlExceptionHelper"))
            assertThat(source.getProperty("logging.level."+category)).isEqualTo("OFF");
        var dev=new YamlPropertySourceLoader().load("dev",new ClassPathResource("application-dev.yml")).getFirst();
        assertThat(dev.getProperty("logging.level.org.springframework.security")).isEqualTo("WARN");
    }
}
