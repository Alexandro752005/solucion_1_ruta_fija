package pe.rutafija;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RutaFijaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RutaFijaApplication.class, args);
    }

    @Configuration(proxyBeanMethods = false)
    @Profile("!restore-inspection")
    @EnableScheduling
    static class OperationalScheduling {
    }
}
