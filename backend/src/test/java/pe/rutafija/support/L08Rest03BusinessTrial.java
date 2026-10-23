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
import java.util.Set;
import java.util.HashSet;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.rutafija.identity.application.JwtService;
import pe.rutafija.identity.infrastructure.AppUserRepository;

/** Normal HTTP business operation on the exact authorized restored copy, no account creation. */
public final class L08Rest03BusinessTrial {
    private static final String URL="jdbc:postgresql://127.0.0.1:55440/ruta_fija_test";
    private static final String JAR_SHA="eded50c4bf3706f0f92ca1cb1700e07946db5eca8405189a08ec6d437303aa4f";
    private static final int PORT=4317;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static final String CASE="8ba8e8bc-ea46-40bd-9848-11a11a79b9fd",NAME="SYNTHETIC REST03 "+CASE;
    public static void main(String[] args){String phase="PREFLIGHT";Path evidence=null;var result=new LinkedHashMap<String,Object>();try{
        require(args.length==0&&"RESTORED_V50_55440_BUSINESS".equals(env("RF_L08_REST03_BUSINESS_AUTHORIZATION")),"AUTHORIZATION");
        Path root=Path.of(env("RF_L08_REST03_BUSINESS_ROOT")).toRealPath();
        Path privateRoot=root.resolve("backend/.local/l08-restore-v50-"+CASE);var proof=JSON.readTree(Files.readAllBytes(privateRoot.resolve("equivalence-result.json")));
        require(CASE.equals(proof.path("caseId").asText())&&"PASS_FULL_V50_SNAPSHOT_EQUIVALENCE_READ_ONLY".equals(proof.path("result").asText()),"FULL_INTEGRITY_PROOF_REQUIRED");
        var output=root.resolve("docs/evidencia/L-08/incremento-3-escritorio/rest03-"+CASE);evidence=output.resolve("business-"+UUID.randomUUID()+".json");
        var jar=root.resolve("artifacts/L-08-post-cap25-2026-10-22/ruta-fija-l08-local.jar");require(JAR_SHA.equals(sha(jar)),"EXACT_BINARY");
        var applicationClasses=Path.of(env("RF_L08_REST03_BUSINESS_APPLICATION_CLASSES")).toRealPath();
        require(Path.of(RutaFijaApplication.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(applicationClasses),"PACKAGED_APP_ORIGIN");
        assertPortFree();var before=captureIntegrity();result.put("beforeIntegrity",before);result.put("caseId",CASE);result.put("startedAt",Instant.now().toString());
        var properties=properties();var environment=new StandardEnvironment();environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);environment.getPropertySources().addFirst(new MapPropertySource("L08Rest03Business",properties));
        var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(environment);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
        var mailAttempts=new AtomicInteger();OtpEmailSender noMail=(r,c,e)->{mailAttempts.incrementAndGet();throw new IllegalStateException("NO_MAIL_ALLOWED");};
        var groupId=new java.util.concurrent.atomic.AtomicReference<String>();
        boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{var registry=(BeanDefinitionRegistry)factory;
            if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
            require(registry.containsBeanDefinition("smtpOtpEmailSender"),"EXACT_MAIL_BOUNDARY");registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",noMail);
            Filter bounded=(request,response,chain)->{var req=(jakarta.servlet.http.HttpServletRequest)request;var res=(jakarta.servlet.http.HttpServletResponse)response;String path=req.getRequestURI();
                boolean read="GET".equals(req.getMethod())&&(path.startsWith("/api/v1/")||path.startsWith("/actuator/health/"));
                boolean write="POST".equals(req.getMethod())&&path.equals("/api/v1/groups")||"PATCH".equals(req.getMethod())&&groupId.get()!=null&&path.equals("/api/v1/groups/"+groupId.get());
                if(!read&&!write){res.setStatus(405);return;}chain.doFilter(request,response);};
            var f=new FilterRegistrationBean<Filter>(bounded);f.setOrder(Ordered.HIGHEST_PRECEDENCE+1);factory.registerSingleton("l08Rest03BoundedBusiness",f);
        }));
        phase="BOOT_EXACT_PACKAGED_V50";
        try(var app=boot.run()){
            require(app.getBean(OtpEmailSender.class)==noMail,"MAIL_DOUBLE");
            var tx=new TransactionTemplate(app.getBean(PlatformTransactionManager.class));tx.setReadOnly(true);
            var tokens=tx.execute(s->{var v=new LinkedHashMap<String,String>();for(var item:Map.of("ADMIN","92ebc07c-1319-4850-a734-f792cb299979","SA","088518c1-821c-4316-9f87-090f51ac44e0").entrySet()){
                var user=app.getBean(AppUserRepository.class).findById(UUID.fromString(item.getValue())).orElseThrow();require(user.isActive()&&user.isEmailVerified()&&user.getEmail().endsWith("@rutafija.test"),"EXISTING_SYNTHETIC_PRINCIPAL");v.put(item.getKey(),app.getBean(JwtService.class).issueAccessToken(user));}return v;});
            var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();var events=new ArrayList<Map<String,Object>>();result.put("events",events);
            phase="NORMAL_SERVER_GATES_AND_READS";
            request(http,"GET","/actuator/health/readiness",null,null,200,events);
            request(http,"GET","/api/v1/groups",null,null,401,events);
            request(http,"GET","/api/v1/groups",null,tokens.get("SA"),403,events);
            var terms=request(http,"GET","/api/v1/admin/terms/current",null,tokens.get("ADMIN"),200,events);require(terms.path("accepted").asBoolean(),"EXISTING_APPROVED_TERMS_REQUIRED");
            request(http,"GET","/api/v1/dashboard/admin",null,tokens.get("ADMIN"),200,events);
            var found=request(http,"GET","/api/v1/groups?search=SYNTHETIC%20REST03&size=100",null,tokens.get("ADMIN"),200,events);
            var matches=new ArrayList<JsonNode>();for(var item:found.path("items"))if(NAME.equals(item.path("name").asText()))matches.add(item);require(matches.size()<=1,"NO_DUPLICATE_CASE_GROUP");
            result.put("sameCommittedGroupRecoveredAfterHarnessFailure",!matches.isEmpty());
            phase="CREATE_OR_RECOVER_SAME_GROUP_BY_NORMAL_API";
            JsonNode group;if(matches.isEmpty()){
                var intent=output.resolve("group-create-intent.json");require(!Files.exists(intent),"UNCERTAIN_PRIOR_CREATE_REQUIRES_RECONCILIATION");Files.writeString(intent,"{\"caseId\":\""+CASE+"\",\"normalApiOnly\":true}",StandardOpenOption.CREATE_NEW);
                group=request(http,"POST","/api/v1/groups",Map.of("name",NAME,"description","Synthetic isolated recovery verification only"),tokens.get("ADMIN"),201,events);
            }else group=matches.getFirst();
            groupId.set(group.path("id").asText());require(!groupId.get().isBlank(),"GROUP_REFERENCE_REQUIRED");
            var persisted=request(http,"GET","/api/v1/groups?search=SYNTHETIC%20REST03&size=100",null,tokens.get("ADMIN"),200,events);
            require(persisted.path("items").findValuesAsText("id").contains(groupId.get()),"CREATED_GROUP_DURABLY_RECONSULTED");
            phase="SELECTIVE_GROUP_RETIREMENT_BY_NORMAL_API";
            if(group.path("active").asBoolean())group=request(http,"PATCH","/api/v1/groups/"+groupId.get(),Map.of("name",NAME,"description","Synthetic isolated recovery verification only","active",false,"expectedVersion",group.path("version").asLong()),tokens.get("ADMIN"),200,events);
            require(!group.path("active").asBoolean(),"CASE_GROUP_RETIRED");
            request(http,"GET","/actuator/health/readiness",null,null,200,events);
            result.put("businessVerifiedAt",Instant.now().toString());result.put("events",events);result.put("groupId",groupId.get());result.put("groupRetired",true);result.put("realLoginAccredited",false);tokens.clear();require(mailAttempts.get()==0,"NO_MAIL_ATTEMPT");
        }
        phase="PERSISTED_GROUP_AUDIT_AND_UNCHANGED_ANTECEDENTS";assertPortFree();var after=captureIntegrity();var changed=new ArrayList<String>();for(var key:before.keySet())if(!before.get(key).equals(after.get(key)))changed.add(key);
        require(new HashSet<>(changed).equals(Set.of("transport_group","audit_event")),"ONLY_NORMAL_GROUP_AND_AUDIT_MAY_CHANGE");
        result.put("afterIntegrity",after);result.put("changedCategories",changed);result.put("historicalLedgersAcceptancesAndOtherTablesPreserved",true);result.put("source5432Accessed",false);result.put("accountsCreated",0);result.put("migrationsExecuted",false);result.put("pilotEnabled",false);result.put("binarySha256",JAR_SHA);result.put("applicationClosedAndPortReleased",true);result.put("result","PASS_RESTORED_V50_NORMAL_HTTP_BUSINESS_ONLY_ISOLATED_DRILL");
        Files.write(evidence,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);System.out.println("L08_REST03_BUSINESS="+result.get("result"));
    }catch(Exception e){result.put("result","STOPPED_KEEP_CASE");result.put("phase",phase);result.put("type",e.getClass().getSimpleName());if(e instanceof IllegalStateException&&e.getMessage()!=null&&e.getMessage().matches("[A-Z][A-Z0-9_]{2,99}"))result.put("check",e.getMessage());try{if(evidence!=null)Files.write(evidence,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(result),StandardOpenOption.CREATE_NEW);}catch(Exception ignored){}System.out.println("L08_REST03_BUSINESS=STOPPED_KEEP_CASE");System.out.println("PHASE="+phase);System.exit(1);}}
    private static JsonNode request(HttpClient http,String method,String path,Object body,String token,int expected,List<Map<String,Object>> events)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:4317"+path)).timeout(Duration.ofSeconds(15)).header("Origin","http://127.0.0.1:4317").header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);
        var r=http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());events.add(Map.of("at",Instant.now().toString(),"method",method,"path",path.split("\\?")[0],"status",r.statusCode()));require(r.statusCode()==expected,"HTTP_STATUS_REQUIRED");return r.body().length==0?JSON.createObjectNode():JSON.readTree(r.body());
    }
    private static LinkedHashMap<String, Object> properties() {
        var p = new LinkedHashMap<String, Object>();
        p.put("spring.profiles.active", "l08-rest03-business");
        p.put("spring.config.location", "classpath:/application.yml"); p.put("spring.config.import", "");
        p.put("spring.datasource.url", URL); p.put("spring.datasource.username", "rf_test");
        p.put("spring.datasource.password", env("RF_L08_REST03_BUSINESS_TEST_PASSWORD"));
        p.put("spring.flyway.enabled", false); p.put("spring.jpa.hibernate.ddl-auto", "validate"); p.put("spring.jpa.open-in-view", false);
        p.put("spring.datasource.hikari.maximum-pool-size", 3); p.put("spring.datasource.hikari.minimum-idle", 0);
        p.put("spring.datasource.hikari.read-only", false);
        p.put("spring.datasource.hikari.connection-init-sql", "SET default_transaction_read_only = off");
        p.put("spring.datasource.hikari.connection-timeout", 5000); p.put("spring.datasource.hikari.validation-timeout", 3000);
        p.put("spring.datasource.hikari.data-source-properties.connectTimeout", "5");
        p.put("spring.datasource.hikari.data-source-properties.socketTimeout", "20");
        p.put("spring.datasource.hikari.data-source-properties.options", "-c statement_timeout=20000 -c lock_timeout=3000");
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
        p.put("app.security.jwt.secret-base64", env("RF_L08_REST03_BUSINESS_EPHEMERAL_JWT"));
        p.put("app.security.cors.allowed-origins", "http://127.0.0.1:4317");
        p.put("spring.main.banner-mode", "off"); p.put("debug", false); p.put("trace", false);
        p.put("logging.level.root", "OFF"); p.put("logging.level.org.springframework", "OFF");
        p.put("logging.level.org.hibernate", "OFF"); p.put("logging.level.pe.rutafija", "OFF");
        return p;
    }

    private static Map<String, Object> captureIntegrity() throws Exception {
        var properties = new Properties(); properties.setProperty("user", "rf_migrator");
        properties.setProperty("password", env("RF_L08_REST03_BUSINESS_MIGRATOR_PASSWORD"));
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
            require(row.getInt(3) == 55440, "EXACT_DATABASE_PORT");
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
