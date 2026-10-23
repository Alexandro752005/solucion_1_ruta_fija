package pe.rutafija.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.terms.domain.AdminTermsContentHash;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doAnswer;

/** Opt-in real clients -> real HTTP -> PostgreSQL. Only mail and device storage are doubles. */
@EnabledIfEnvironmentVariable(named="RF_L01_CONNECTED_CLIENTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "server.address=127.0.0.1", "app.security.person-identity-gate.enabled=true",
        "app.security.admin-terms-gate.enabled=true", "app.mobile.offline-sync.enabled=false",
        "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF", "debug=false"})
@ActiveProfiles("test")
class ConnectedL01ClientsIT {
    private static final NativePostgresTestDatabase DATABASE=NativePostgresTestDatabase.load();
    private static HttpServer frontend;
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static final String CONTROL=UUID.randomUUID().toString();
    private final Map<String,Object> state=new ConcurrentHashMap<>();
    private final Map<String,String> otp=new ConcurrentHashMap<>();
    private final ObjectMapper json=new ObjectMapper();
    @LocalServerPort int backendPort;
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired TransportGroupRepository groups;
    @Autowired PasswordEncoder passwords;
    @MockitoBean OtpEmailSender mail;

    @DynamicPropertySource static void connectedOrigins(DynamicPropertyRegistry registry) throws Exception {
        DATABASE.registerSpringProperties(registry);
        frontend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        registry.add("app.security.cors.allowed-origins",()->"http://127.0.0.1:"+frontend.getAddress().getPort());
    }

    @BeforeEach void resetOnlyNativeTestData(){DATABASE.cleanBusinessData();DATABASE.assertTestRolePermissions();}
    @AfterEach void clearOnlyNativeTestData(){DATABASE.cleanBusinessData();}

    @Test void crmAndFlutterCompleteL01WithRealBackendAndSyntheticInputs() throws Exception {
        try {
            assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("ruta_fija_test");
            assertThat(Files.isRegularFile(ROOT.resolve("frontend/dist/ruta-fija-crm/browser/index.html"))).isTrue();
            seed();
            doAnswer(call->{
                String recipient=call.getArgument(0);
                if(!recipient.endsWith("@rutafija.test"))throw new IllegalArgumentException("Only synthetic recipients");
                otp.put(recipient,call.getArgument(1));return null;
            }).when(mail).sendVerificationCode(anyString(),anyString(),any(Instant.class));
            frontend.createContext("/",this::serve);
            frontend.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            frontend.start();
            runClient("crm","setup");
            runClient("flutter","register");
            assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_request where status='PENDING_REVIEW'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from app_user where role='CONDUCTOR'",Integer.class)).isZero();
            runClient("crm","return");
            runClient("flutter","correct");
            runClient("crm","approve");
            assertThat(jdbc.queryForObject("select count(*) from driver where active=true",Integer.class)).isZero();
            runClient("crm","recover");
            runClient("flutter","recover");
            runClient("flutter","register-rejection");
            runClient("crm","reject");
            runClient("crm","replace");
            assertThat(jdbc.queryForObject("select count(*) from app_user where role='CONDUCTOR'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from pilot_account_creation where role='CONDUCTOR'",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class)).isEqualTo(2);
            assertThat(jdbc.queryForObject("select count(*) from admin_terms_document where synthetic=false",Integer.class)).isZero();
            System.out.println("CONNECTED_L01_RESULT=PASS_CRM_BROWSER_AND_FLUTTER_UI_REAL_HTTP_TEST_DB_SIMULATED_MAIL");
        } finally { otp.clear();state.clear();if(frontend!=null)frontend.stop(0); }
    }

    private void seed(){
        String suffix=UUID.randomUUID().toString().substring(0,8);
        String password="Synthetic-Connected-"+UUID.randomUUID();
        AppUser owner=AppUser.superAdmin("owner."+suffix+"@rutafija.test",passwords.encode(password),"SYNTHETIC OWNER");
        owner.verifyEmail(Instant.now());users.saveAndFlush(owner);
        Organization org=organizations.saveAndFlush(Organization.active("SYNTHETIC CONNECTED "+suffix,"SYNTHETIC","America/Lima"));
        Organization other=organizations.saveAndFlush(Organization.active("SYNTHETIC OTHER "+suffix,"SYNTHETIC OTHER","America/Lima"));
        TransportGroup group=groups.saveAndFlush(TransportGroup.active(org,"SYNTHETIC GROUP","L01 connected test only"));
        TransportGroup otherGroup=groups.saveAndFlush(TransportGroup.active(other,"SYNTHETIC OTHER GROUP","L01 tenant boundary only"));
        String content="SYNTHETIC TEST DOCUMENT ONLY. Connected client acceptance fixture. Not approved pilot terms.";
        jdbc.update("insert into admin_terms_document(id,document_key,version,content,content_sha256,synthetic,created_at) values(?,?,?,?,?,true,current_timestamp)",
                UUID.randomUUID(),"ADMIN_TERMS","SYNTHETIC-TEST-ONLY-CONNECTED-L01",content,AdminTermsContentHash.sha256(content));
        state.putAll(Map.of("ownerEmail",owner.getEmail(),"password",password,"organizationId",org.getId().toString(),
                "otherOrganizationId",other.getId().toString(),"groupId",group.getId().toString(),
                "adminEmail","admin."+suffix+"@rutafija.test","replacementEmail","replacement."+suffix+"@rutafija.test",
                "applicantEmail","applicant."+suffix+"@rutafija.test","recoveredEmail","recovered."+suffix+"@rutafija.test"));
        state.put("rejectionEmail","rejection."+suffix+"@rutafija.test");
        state.put("otherGroupId",otherGroup.getId().toString());
    }

    private void runClient(String client,String stage) throws Exception {
        ProcessBuilder builder;
        if(client.equals("crm"))builder=new ProcessBuilder("node",ROOT.resolve("frontend/e2e/l01-connected.mjs").toString(),stage);
        else builder=new ProcessBuilder("cmd.exe","/d","/c","flutter.bat","test","--no-pub","--reporter","expanded","test/connected/l01_connected_test.dart");
        builder.directory(ROOT.resolve(client.equals("crm")?"frontend":"mobile").toFile());
        builder.redirectErrorStream(true);
        builder.environment().put("RF_L01_CONNECTED_BASE","http://127.0.0.1:"+frontend.getAddress().getPort());
        builder.environment().put("RF_L01_CONNECTED_CONTROL",CONTROL);
        builder.environment().put("RF_L01_CONNECTED_STAGE",stage);
        Process process=builder.start();
        ExecutorService reader=Executors.newSingleThreadExecutor();
        Future<String> output=reader.submit(()->new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
        try {
            boolean completed=process.waitFor(180,TimeUnit.SECONDS);
            if(!completed){process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();}
            String captured=output.get(10,TimeUnit.SECONDS);
            // Child diagnostics are synthetic, but never retain bodies, inputs or credentials.
            captured.lines().filter(line->line.startsWith("CONNECTED_")).forEach(System.out::println);
            if(!completed||process.exitValue()!=0){
                // Store only bounded test diagnostics, excluding lines with any runtime input.
                List<String> safe=captured.lines().filter(line->!containsSecret(line)).limit(140).toList();
                Files.write(ROOT.resolve("backend/target/l01-connected-"+client+"-"+stage+"-diagnostic.log"),safe,StandardCharsets.UTF_8);
                throw new AssertionError("Connected client failed: "+client+"/"+stage+"; sanitized diagnostic in target");
            }
        } finally { if(process.isAlive())process.destroyForcibly();reader.shutdownNow(); }
    }
    private boolean containsSecret(String line){
        return state.values().stream().map(Object::toString).anyMatch(value->value.length()>5&&line.contains(value))
                ||otp.values().stream().anyMatch(line::contains)||line.contains("ac1.")||line.contains("oa1.")||line.contains("Bearer ");
    }

    private void serve(HttpExchange exchange){
        try {
            String path=exchange.getRequestURI().getPath();
            if(path.startsWith("/__l01/")){
                if(!CONTROL.equals(exchange.getRequestHeaders().getFirst("X-Synthetic-Control"))){reply(exchange,403,new byte[0],"text/plain");return;}
                if(path.equals("/__l01/otp")){
                    String role=exchange.getRequestURI().getRawQuery();
                    Object address=state.get(role+"Email");
                    reply(exchange,200,json.writeValueAsBytes(Map.of("code",otp.getOrDefault(address,""))),"application/json");
                } else if(path.equals("/__l01/state")&&exchange.getRequestMethod().equals("POST")){
                    Map<?,?> update=json.readValue(exchange.getRequestBody().readNBytes(8193),Map.class);
                    for(String key:List.of("invitationCode","rejectionInvitationCode","recoveryId","recoveryToken"))if(update.containsKey(key))state.put(key,update.get(key));
                    reply(exchange,200,"{}".getBytes(StandardCharsets.UTF_8),"application/json");
                } else {
                    Map<String,Object> snapshot=new HashMap<>(state);
                    var requests=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=?",String.class,state.get("applicantEmail"));
                    if(!requests.isEmpty())snapshot.put("requestId",requests.getFirst());
                    var rejected=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=?",String.class,state.get("rejectionEmail"));
                    if(!rejected.isEmpty())snapshot.put("rejectionRequestId",rejected.getFirst());
                    var drivers=jdbc.queryForList("select d.id::text from driver d join app_user u on u.id=d.user_id where u.email in (?,?)",String.class,state.get("applicantEmail"),state.get("recoveredEmail"));
                    if(!drivers.isEmpty())snapshot.put("driverId",drivers.getFirst());
                    reply(exchange,200,json.writeValueAsBytes(snapshot),"application/json");
                }
            } else if(path.equals("/config/runtime-config.json")){
                // Only this isolated client run may display synthetic terms.
                // The repository's ordinary local-native config stays unchanged.
                reply(exchange,200,json.writeValueAsBytes(Map.of("apiBaseUrl","/api/v1","environment","test","appName","SYNTHETIC Ruta Fija L01")),"application/json");
            } else if(path.startsWith("/api/")){
                HttpRequest.Builder request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+exchange.getRequestURI())).timeout(Duration.ofSeconds(25));
                exchange.getRequestHeaders().forEach((name,values)->{
                    if(!Set.of("host","connection","content-length","upgrade").contains(name.toLowerCase(Locale.ROOT)))values.forEach(value->request.header(name,value));
                });
                request.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes()));
                HttpResponse<byte[]> response=HttpClient.newHttpClient().send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
                response.headers().map().forEach((name,values)->{
                    if(!Set.of("connection","content-length","transfer-encoding").contains(name))exchange.getResponseHeaders().put(name,values);
                });
                reply(exchange,response.statusCode(),response.body(),response.headers().firstValue("content-type").orElse("application/json"));
            } else {
                Path base=ROOT.resolve("frontend/dist/ruta-fija-crm/browser");
                Path file=base.resolve(path.substring(1)).normalize();
                if(!file.startsWith(base)){reply(exchange,404,new byte[0],"text/plain");return;}
                if(!Files.isRegularFile(file))file=base.resolve("index.html");
                String type=file.toString().endsWith(".js")?"application/javascript":file.toString().endsWith(".css")?"text/css":file.toString().endsWith(".html")?"text/html":"application/octet-stream";
                reply(exchange,200,Files.readAllBytes(file),type);
            }
        } catch(Exception error){try{reply(exchange,500,new byte[0],"text/plain");}catch(Exception ignored){}}
        finally {exchange.close();}
    }
    private static void reply(HttpExchange exchange,int status,byte[] bytes,String type) throws Exception {
        exchange.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control","no-store");
        exchange.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);
        if(bytes.length>0)exchange.getResponseBody().write(bytes);
    }
}
