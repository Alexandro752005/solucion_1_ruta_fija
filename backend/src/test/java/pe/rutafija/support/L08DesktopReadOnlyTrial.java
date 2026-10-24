package pe.rutafija.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.backup.DatabaseIntegrity;
import pe.rutafija.identity.application.OtpEmailSender;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;
import java.util.UUID;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.rutafija.identity.application.JwtService;
import pe.rutafija.identity.infrastructure.AppUserRepository;

/** Read-only browser inspection with existing synthetic principals, never login/refresh evidence. */
public final class L08DesktopReadOnlyTrial {
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/ruta_fija_test";
    private static final String JAR_SHA = "eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f";
    private static final int PORT = 4316;
    private static final ObjectMapper JSON = new ObjectMapper();
    public static void main(String[] args) {
        String phase="PREFLIGHT"; Path output=null; var evidence=new LinkedHashMap<String,Object>();
        int code=1;
        try {
            require(args.length==0 && "EXISTING_SYNTHETIC_READ_ONLY".equals(env("RF_L08_DESKTOP_AUTHORIZATION")),"AUTHORIZATION");
            require(URL.equals(env("RF_L08_DESKTOP_URL")),"TARGET");
            Path root=Path.of(env("RF_L08_DESKTOP_ROOT")).toRealPath();
            Path jar=root.resolve("artifacts/L-08-post-cap25-2026-10-22/ruta-fija-l08-local.jar");
            require(JAR_SHA.equals(sha(jar)),"PRESERVED_JAR_HASH");
            output=Path.of(env("RF_L08_DESKTOP_OUTPUT")).toAbsolutePath().normalize();
            require(output.startsWith(root.resolve("docs/evidencia/L-08/incremento-3-escritorio"))&&!Files.exists(output),"NEW_BOUNDED_OUTPUT");
            Files.createDirectories(output);
            Path applicationClasses=Path.of(env("RF_L08_DESKTOP_APPLICATION_CLASSES")).toRealPath();
            require(Path.of(RutaFijaApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(applicationClasses),"PACKAGED_APP_ORIGIN");
            require(Collections.list(RutaFijaApplication.class.getClassLoader().getResources("pe/rutafija/RutaFijaApplication.class")).size()==1,"UNIQUE_APP_CLASS");
            assertPortFree(); var before=captureIntegrity();
            evidence.put("startedAt",Instant.now().toString());evidence.put("binarySha256",JAR_SHA);
            evidence.put("database","ruta_fija_test@127.0.0.1:5432");evidence.put("runtimeRole","rf_test");
            evidence.put("beforeIntegrity",before);evidence.put("authenticationOrigin","SYNTHETIC_TEST_JWT_ISSUED_BY_EXISTING_JWT_SERVICE_NOT_LOGIN");
            var properties=properties();var environment=new StandardEnvironment();
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            environment.getPropertySources().addFirst(new MapPropertySource("L08DesktopReadOnly",properties));
            var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(environment);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
            var mailAttempts=new AtomicInteger();OtpEmailSender noMail=(r,c,e)->{mailAttempts.incrementAndGet();throw new IllegalStateException("L08_DESKTOP_REFUSES_MAIL");};
            boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{
                var registry=(BeanDefinitionRegistry)factory;
                if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
                require(registry.containsBeanDefinition("smtpOtpEmailSender"),"EXACT_MAIL_BOUNDARY");
                registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",noMail);
                Filter readOnly=(request,response,chain)->{
                    var req=(jakarta.servlet.http.HttpServletRequest)request;var res=(jakarta.servlet.http.HttpServletResponse)response;
                    boolean read="GET".equals(req.getMethod())&&(req.getRequestURI().startsWith("/api/v1/")||req.getRequestURI().equals("/ws/operations"));
                    boolean transientTicket="POST".equals(req.getMethod())&&req.getRequestURI().equals("/api/v1/operations/stream-ticket");
                    if(!read&&!transientTicket){res.setStatus(405);res.setHeader("Cache-Control","no-store");return;}
                    chain.doFilter(request,response);
                };
                var filter=new FilterRegistrationBean<Filter>(readOnly);filter.setOrder(Ordered.HIGHEST_PRECEDENCE+1);factory.registerSingleton("l08DesktopReadOnlyBoundary",filter);
            }));
            phase="START_READ_ONLY_PACKAGED_APPLICATION";
            boolean browserPassed=false;
            try(var app=boot.run();var lock=DriverManager.getConnection(URL,dbProperties("rf_test",env("RF_L08_DESKTOP_TEST_PASSWORD")))){
                lock.setReadOnly(true);verifyTarget(lock,"rf_test");
                try(var q=lock.createStatement();var row=q.executeQuery("select pg_try_advisory_lock(7518440514)")){require(row.next()&&row.getBoolean(1),"SHARED_TEST_NOT_PROTECTED");}
                var ds=app.getBean(javax.sql.DataSource.class);require(ds instanceof HikariDataSource&&((HikariDataSource)ds).isReadOnly(),"READ_ONLY_POOL");
                require(app.getBean(OtpEmailSender.class)==noMail&&!app.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME),"NO_MAIL_OR_SCHEDULERS");
                var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));tx.setReadOnly(true);
                var repo=app.getBean(AppUserRepository.class);var jwt=app.getBean(JwtService.class);
                var tokens=tx.execute(s->{var result=new LinkedHashMap<String,String>();
                    for(var item:Map.of("ADMIN","92ebc07c-1319-4850-a734-f792cb299979","SA","088518c1-821c-4316-9f87-090f51ac44e0").entrySet()){
                        var user=repo.findById(UUID.fromString(item.getValue())).orElseThrow();
                        require(user.isActive()&&user.isEmailVerified()&&user.getEmail().endsWith("@rutafija.test"),"ELIGIBLE_EXISTING_SYNTHETIC_PRINCIPAL");
                        require((item.getKey().equals("SA")?"SUPER_ADMIN":"ADMIN").equals(user.getRole().name()),"EXACT_ROLE");
                        if(item.getKey().equals("ADMIN"))require(user.getOrganizationId().toString().equals("1a759298-7831-4aaf-9299-c3920a9cc28a"),"EXACT_ORGANIZATION");
                        result.put(item.getKey(),jwt.issueAccessToken(user));
                    }return result;});
                phase="READ_ONLY_BROWSER_OBSERVATION";
                var pb=new ProcessBuilder("node",root.resolve("frontend/e2e/l08-operational-accessibility.mjs").toString(),"--execute-read-only");
                pb.directory(root.toFile());pb.environment().clear();
                for(String key:List.of("SystemRoot","WINDIR","TEMP","TMP","PATH","PATHEXT"))if(System.getenv(key)!=null)pb.environment().put(key,System.getenv(key));
                pb.environment().put("RF_L08_DESKTOP_MODE","EXISTING_SYNTHETIC_READ_ONLY");pb.environment().put("RF_L08_DESKTOP_API","http://127.0.0.1:4316");pb.environment().put("RF_L08_DESKTOP_OUTPUT",output.toString());
                pb.environment().put("RF_L08_DESKTOP_ADMIN_TOKEN",tokens.get("ADMIN"));pb.environment().put("RF_L08_DESKTOP_SA_TOKEN",tokens.get("SA"));
                pb.redirectErrorStream(true);pb.redirectOutput(output.resolve("browser-execution.log").toFile());
                var child=pb.start();pb.environment().clear();tokens.clear();
                if(!child.waitFor(300,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();child.waitFor();throw new IllegalStateException("BROWSER_DEADLINE_KEEP_EVIDENCE");}
                browserPassed=child.exitValue()==0;evidence.put("browserExitCode",child.exitValue());
                require(mailAttempts.get()==0,"NO_MAIL_ATTEMPT");evidence.put("mailAttempts",mailAttempts.get());
            }
            phase="CLOSED_CONTEXT_AND_UNCHANGED_DATABASE";assertPortFree();var after=captureIntegrity();evidence.put("afterIntegrity",after);
            require(before.equals(after),"ALL_TABLES_AND_SCHEMA_UNCHANGED");
            evidence.put("allTableAndSchemaHashesPreserved",true);evidence.put("accountsCreated",0);evidence.put("migrationsExecuted",false);evidence.put("normalIdentityTermsAndTenantGatesPreserved",true);
            evidence.put("applicationClosedAndPortReleased",true);evidence.put("pilotEnabled",false);evidence.put("humanScreenReaderAccredited",false);
            evidence.put("browserEvidenceSha256",sha(output.resolve("navegadores.json")));evidence.put("finishedAt",Instant.now().toString());
            evidence.put("result",browserPassed?"PASS_OPERATIONAL_READ_ONLY_NATIVE_BROWSER_ZOOM":"FAIL_BROWSER_OBSERVATION_DATABASE_PRESERVED");
            Files.write(output.resolve("resultado.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence),StandardOpenOption.CREATE_NEW);
            System.out.println("L08_DESKTOP="+evidence.get("result"));System.out.println("EVIDENCE_FILE="+root.relativize(output.resolve("resultado.json")).toString().replace('\\','/'));
            code=browserPassed?0:1;
        } catch(Exception failure){
            evidence.put("result","STOPPED_KEEP_EVIDENCE");evidence.put("phase",phase);evidence.put("type",failure.getClass().getSimpleName());
            if(failure instanceof IllegalStateException&&failure.getMessage()!=null&&failure.getMessage().matches("[A-Z][A-Z0-9_]{2,79}"))evidence.put("check",failure.getMessage());
            try{if(output!=null&&Files.isDirectory(output))Files.write(output.resolve("fallo.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(evidence),StandardOpenOption.CREATE_NEW);}catch(Exception ignored){}
            System.out.println("L08_DESKTOP=STOPPED_KEEP_EVIDENCE");System.out.println("L08_DESKTOP_PHASE="+phase);System.out.println("L08_DESKTOP_TYPE="+failure.getClass().getSimpleName());
        }
        System.exit(code);
    }
    private static Properties dbProperties(String user,String password){var p=new Properties();p.setProperty("user",user);p.setProperty("password",password);p.setProperty("options","-c default_transaction_read_only=on -c statement_timeout=20000 -c lock_timeout=3000");return p;}
    private static LinkedHashMap<String, Object> properties() {
        var p = new LinkedHashMap<String, Object>();
        p.put("spring.profiles.active", "l08-desktop-read-only");
        p.put("spring.config.location", "classpath:/application.yml"); p.put("spring.config.import", "");
        p.put("spring.datasource.url", URL); p.put("spring.datasource.username", "rf_test");
        p.put("spring.datasource.password", env("RF_L08_DESKTOP_TEST_PASSWORD"));
        p.put("spring.flyway.enabled", false); p.put("spring.jpa.hibernate.ddl-auto", "validate"); p.put("spring.jpa.open-in-view", false);
        p.put("spring.datasource.hikari.maximum-pool-size", 3); p.put("spring.datasource.hikari.minimum-idle", 0);
        p.put("spring.datasource.hikari.read-only", true);
        p.put("spring.datasource.hikari.connection-init-sql", "SET default_transaction_read_only = on");
        p.put("spring.datasource.hikari.connection-timeout", 5000); p.put("spring.datasource.hikari.validation-timeout", 3000);
        p.put("spring.datasource.hikari.data-source-properties.connectTimeout", "5");
        p.put("spring.datasource.hikari.data-source-properties.socketTimeout", "20");
        p.put("spring.datasource.hikari.data-source-properties.options", "-c default_transaction_read_only=on -c statement_timeout=20000 -c lock_timeout=3000");
        p.put("spring.autoconfigure.exclude", "org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");
        p.put("spring.mail.host", ""); p.put("spring.mail.username", ""); p.put("spring.mail.password", "");
        p.put("management.health.mail.enabled", false);
        p.put("management.endpoint.health.show-details", "always"); p.put("management.endpoint.health.show-components", "always");
        p.put("management.endpoint.health.probes.enabled", true);
        p.put("management.endpoint.health.group.readiness.include", "readinessState,db");
        p.put("management.endpoint.health.group.liveness.include", "livenessState");
        p.put("server.address", "127.0.0.1"); p.put("server.port", PORT); p.put("server.forward-headers-strategy", "none");
        p.put("server.shutdown", "graceful"); p.put("spring.lifecycle.timeout-per-shutdown-phase", "10s");
        p.put("app.seed.enabled", false); p.put("app.mobile.offline-sync.enabled", false);
        p.put("app.qualification.maintenance.enabled", false); p.put("app.mobile.maintenance-enabled", false);
        p.put("app.retention.technical-enabled", false);
        p.put("app.security.person-identity-gate.enabled", true); p.put("app.security.admin-terms-gate.enabled", true);
        p.put("app.security.jwt.secret-base64", env("RF_L08_DESKTOP_EPHEMERAL_JWT"));
        p.put("app.security.cors.allowed-origins", "http://127.0.0.1:4316");
        p.put("spring.main.banner-mode", "off"); p.put("debug", false); p.put("trace", false);
        p.put("logging.level.root", "OFF"); p.put("logging.level.org.springframework", "OFF");
        p.put("logging.level.org.hibernate", "OFF"); p.put("logging.level.pe.rutafija", "OFF");
        return p;
    }

    private static Map<String, Object> captureIntegrity() throws Exception {
        var properties = new Properties(); properties.setProperty("user", "rf_migrator");
        properties.setProperty("password", env("RF_L08_DESKTOP_MIGRATOR_PASSWORD"));
        properties.setProperty("connectTimeout", "5"); properties.setProperty("socketTimeout", "20");
        properties.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=20000 -c lock_timeout=3000");
        try (var connection = DriverManager.getConnection(URL, properties)) {
            connection.setReadOnly(true); verifyTarget(connection, "rf_migrator");
            return DatabaseIntegrity.capture(connection);
        } finally { properties.clear(); }
    }

    private static void verifyTarget(Connection connection, String role) throws Exception {
        require(connection.isReadOnly(), "JDBC_READ_ONLY");
        try (var q = connection.createStatement(); var row = q.executeQuery("select current_database(),current_user,inet_server_port(),datcollate,datctype,current_setting('default_transaction_read_only'),current_setting('transaction_read_only'),(select rolsuper from pg_roles where rolname=current_user),host(inet_server_addr()) from pg_database where datname=current_database()")) {
            require(row.next(), "TARGET_ROW_REQUIRED");
            require("ruta_fija_test".equals(row.getString(1)), "EXACT_DATABASE_NAME");
            require(role.equals(row.getString(2)), "EXACT_DATABASE_ROLE");
            require(row.getInt(3) == 5432, "EXACT_DATABASE_PORT");
            require("Spanish_Peru.1252".equals(row.getString(4)) && "Spanish_Peru.1252".equals(row.getString(5)), "EXACT_DATABASE_REGION");
            require("on".equals(row.getString(6)) && "on".equals(row.getString(7)), "READ_ONLY_SESSION_REQUIRED");
            require(!row.getBoolean(8), "NON_SUPERUSER_ROLE_REQUIRED");
            require("127.0.0.1".equals(row.getString(9)), "EXACT_LOOPBACK_ADDRESS_REQUIRED");
        }
        try (var q = connection.createStatement(); var row = q.executeQuery("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1")) {
            require(row.next() && "50".equals(row.getString(1)) && !row.next(), "EXACT_SCHEMA_50");
        }
        try (var q = connection.createStatement(); var row = q.executeQuery("select bool_and(success),count(*) filter(where version is not null) from flyway_schema_history")) {
            require(row.next() && row.getBoolean(1) && row.getInt(2) == 50, "50_SUCCESSFUL_VERSIONED_MIGRATIONS_ONLY");
        }
    }

    private static Map<String, Object> health(HttpClient client, String group, List<String> components) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + PORT + "/actuator/health/" + group))
            .timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        byte[] bytes;
        try (InputStream body = response.body()) { bytes = body.readNBytes(4097); }
        require(response.statusCode() == 200 && bytes.length <= 4096, "BOUNDED_HTTP_HEALTH_200");
        JsonNode json = JSON.readTree(bytes); require("UP".equals(json.path("status").asText()), "HEALTH_UP");
        var actual = new ArrayList<String>(); json.path("components").fieldNames().forEachRemaining(actual::add);
        require(actual.size() == components.size() && actual.containsAll(components), "EXACT_HEALTH_COMPOSITION");
        for (String component : components) require("UP".equals(json.path("components").path(component).path("status").asText()), "HEALTH_COMPONENT_UP");
        return Map.of("httpStatus", response.statusCode(), "status", "UP", "components", components, "observedAt", Instant.now().toString());
    }

    private static void assertPortFree() throws Exception {
        try (var socket = new ServerSocket()) { socket.setReuseAddress(false); socket.bind(new InetSocketAddress("127.0.0.1", PORT)); }
    }
    private static String env(String name) { String value = System.getenv(name); require(value != null && !value.isBlank(), "REQUIRED_PRIVATE_INPUT"); return value; }
    private static String sha(Path path) throws Exception {
        var hash = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) { byte[] chunk = new byte[65536]; int count; while ((count = input.read(chunk)) >= 0) hash.update(chunk, 0, count); }
        return HexFormat.of().formatHex(hash.digest());
    }
    private static void require(boolean condition, String code) { if (!condition) throw new IllegalStateException(code); }
}
