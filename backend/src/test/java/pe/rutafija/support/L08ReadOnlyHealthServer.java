package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.identity.application.OtpEmailSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Test-only actual Spring API. Health reads only; no accounts, schedulers, mail or schema changes. */
public final class L08ReadOnlyHealthServer {
    static final int PORT = 4312;
    private static final ObjectMapper JSON = new ObjectMapper();
    private L08ReadOnlyHealthServer() { }

    public static void main(String[] args) {
        Path ready = null;
        String phase = "ARGUMENTS";
        try {
            if (args.length != 2 || !"--owned-read-only-health-server".equals(args[0])) throw new IllegalStateException();
            if (!L08AvailabilityRehearsal.RUN_ID.equals(System.getenv("RF_L08_AVAILABILITY_REHEARSAL_RUN_ID"))
                    || !"READ_ONLY_HEALTH_REAL_POSTGRES_TEST".equals(System.getenv("RF_L08_HEALTH_SERVER_AUTHORIZATION")))
                throw new IllegalStateException();
            Path run = L08AvailabilityRehearsal.runDirectory();
            ready = Path.of(args[1]).toAbsolutePath().normalize();
            if (!ready.getParent().equals(run) || !ready.getFileName().toString().matches("api-ready-[12]\\.json")
                    || !Files.isRegularFile(run.resolve("authorization.json")) || Files.exists(ready)) throw new IllegalStateException();
            phase = "PORT_FREE";
            try (var socket = new ServerSocket()) { socket.setReuseAddress(false); socket.bind(new InetSocketAddress("127.0.0.1", PORT)); }
            phase = "VALIDATED_TEST_CONFIGURATION";
            NativePostgresTestDatabase database = NativePostgresTestDatabase.load();
            database.assertTestRolePermissions();
            var properties = new LinkedHashMap<String, Object>();
            database.registerSpringProperties((key, value) -> properties.put(key, value.get()));
            properties.put("spring.profiles.active", "test");
            properties.put("spring.config.location", "classpath:/application.yml,classpath:/application-test.yml");
            properties.put("spring.config.import", "");
            properties.put("spring.flyway.enabled", false);
            properties.put("spring.jpa.hibernate.ddl-auto", "validate");
            properties.put("spring.jpa.open-in-view", false);
            properties.put("spring.datasource.hikari.maximum-pool-size", 3);
            properties.put("spring.datasource.hikari.minimum-idle", 0);
            properties.put("spring.datasource.hikari.read-only", true);
            properties.put("spring.datasource.hikari.connection-init-sql", "SET default_transaction_read_only = on");
            properties.put("spring.datasource.hikari.connection-timeout", 5000);
            properties.put("spring.datasource.hikari.validation-timeout", 3000);
            properties.put("spring.datasource.hikari.data-source-properties.connectTimeout", "5");
            properties.put("spring.datasource.hikari.data-source-properties.socketTimeout", "5");
            properties.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
            properties.put("spring.mail.host", ""); properties.put("spring.mail.username", ""); properties.put("spring.mail.password", "");
            properties.put("management.health.mail.enabled", false);
            properties.put("management.endpoint.health.show-details", "never");
            properties.put("management.endpoint.health.show-components", "never");
            properties.put("management.endpoint.health.probes.enabled", true);
            properties.put("management.endpoint.health.group.readiness.include", "readinessState,db");
            properties.put("management.endpoint.health.group.liveness.include", "livenessState");
            properties.put("server.address", "127.0.0.1"); properties.put("server.port", PORT);
            properties.put("server.shutdown", "graceful"); properties.put("spring.lifecycle.timeout-per-shutdown-phase", "10s");
            properties.put("server.forward-headers-strategy", "none");
            properties.put("app.seed.enabled", false); properties.put("app.mobile.offline-sync.enabled", false);
            properties.put("app.qualification.maintenance.enabled", false); properties.put("app.mobile.maintenance-enabled", false);
            properties.put("app.security.person-identity-gate.enabled", true); properties.put("app.security.admin-terms-gate.enabled", true);
            properties.put("debug", false); properties.put("trace", false); properties.put("spring.main.banner-mode", "off");
            properties.put("logging.level.root", "OFF");
            for (String logger : List.of("org.springframework", "org.hibernate", "org.springframework.jdbc.core", "pe.rutafija"))
                properties.put("logging.level." + logger, "OFF");
            var environment = new StandardEnvironment();
            environment.getPropertySources().addFirst(new MapPropertySource("L08ReadOnlyHealthRehearsal", properties));
            var boot = new SpringApplication(RutaFijaApplication.class);
            boot.setEnvironment(environment); boot.setAddCommandLineProperties(false); boot.setRegisterShutdownHook(false);
            OtpEmailSender noMail = (recipient, code, expiry) -> { throw new IllegalStateException("L08_HEALTH_REHEARSAL_REFUSES_ALL_MAIL"); };
            boot.addInitializers(context -> context.addBeanFactoryPostProcessor(factory -> {
                var registry = (BeanDefinitionRegistry) factory;
                if (registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
                    registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
                if (!registry.containsBeanDefinition("smtpOtpEmailSender")) throw new IllegalStateException("EXPECTED_MAIL_BOUNDARY_REQUIRED");
                registry.removeBeanDefinition("smtpOtpEmailSender"); factory.registerSingleton("smtpOtpEmailSender", noMail);
                Filter onlyHealth = (request, response, chain) -> {
                    var httpRequest = (jakarta.servlet.http.HttpServletRequest) request;
                    var httpResponse = (jakarta.servlet.http.HttpServletResponse) response;
                    if (!"GET".equals(httpRequest.getMethod()) || httpRequest.getQueryString() != null
                            || !List.of("/actuator/health/readiness", "/actuator/health/liveness").contains(httpRequest.getRequestURI())) {
                        httpResponse.setStatus(404); httpResponse.setHeader("Cache-Control", "no-store"); return;
                    }
                    chain.doFilter(request, response);
                };
                var registration = new FilterRegistrationBean<Filter>(onlyHealth);
                registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
                factory.registerSingleton("l08ReadOnlyHealthOnlyFilter", registration);
            }));
            phase = "BOOTSTRAP_CONTEXT";
            try (var context = boot.run()) {
                if (context.getBean(OtpEmailSender.class) != noMail) throw new IllegalStateException("MAIL_DOUBLE_REQUIRED");
                if (((ServletWebServerApplicationContext) context).getWebServer().getPort() != PORT) throw new IllegalStateException();
                var jdbc = context.getBean(JdbcTemplate.class);
                if (!"ruta_fija_test".equals(jdbc.queryForObject("select current_database()", String.class))
                        || !"rf_test".equals(jdbc.queryForObject("select current_user", String.class))
                        || !"on".equals(jdbc.queryForObject("show default_transaction_read_only", String.class))
                        || !"48".equals(jdbc.queryForObject("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1", String.class))
                        || !Boolean.TRUE.equals(environment.getProperty("app.security.person-identity-gate.enabled", Boolean.class))
                        || !Boolean.TRUE.equals(environment.getProperty("app.security.admin-terms-gate.enabled", Boolean.class))) throw new IllegalStateException();
                var status = new LinkedHashMap<String, Object>();
                status.put("runId", L08AvailabilityRehearsal.RUN_ID); status.put("result", "READY_ACTUAL_SPRING_API_READ_ONLY_TEST");
                status.put("database", "ruta_fija_test"); status.put("role", "rf_test"); status.put("schemaVersion", "48");
                status.put("serverPort", PORT); status.put("startedAt", Instant.now().toString());
                status.put("readOnlyDatasource", true); status.put("mailRefused", true); status.put("schedulersRemoved", true);
                status.put("identityAndTermsGatesEnabled", true); status.put("businessRoutesBlockedByTestFilter", true);
                L08AvailabilityRehearsal.writeNewJson(ready, status);
                phase = "WAITING_FOR_OWNED_STOP";
                try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                    if (!"STOP_OWNED_API".equals(input.readLine())) throw new IllegalStateException();
                }
            }
        } catch (Exception failure) {
            if (ready != null) {
                try { L08AvailabilityRehearsal.writeNewJson(ready.resolveSibling(ready.getFileName() + ".failure.json"),
                    Map.of("runId", L08AvailabilityRehearsal.RUN_ID, "result", "STOPPED_DETAILS_REDACTED", "phase", phase,
                        "causeType", failure.getClass().getSimpleName(), "at", Instant.now().toString())); }
                catch (Exception ignored) { }
            }
            System.exit(1);
        }
    }
}
