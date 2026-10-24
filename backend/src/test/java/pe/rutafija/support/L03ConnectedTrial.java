package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in real browser/client HTTP smoke, existing synthetic ADMIN, no account creations.
 * Only technical vehicle/catalog data; failures preserve same state/marker for diagnosis. */
public final class L03ConnectedTrial {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize(), STATE=ROOT.resolve("backend/.local/l03-connected-state.json"), MARKER=ROOT.resolve("backend/.local/l03-connected.active");
    private static final UUID ADMIN=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String,Object> state=new LinkedHashMap<>();private final List<Map<String,Object>> events=new CopyOnWriteArrayList<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private ConfigurableApplicationContext app;private HttpServer frontend;private JdbcTemplate jdbc;private int port;private String base,control,token,refresh;private UUID family;private Path evidence;private String phase="PREFLIGHT";
    public static void main(String[] args){if(args.length!=1||!args[0].equals("--run-connected"))throw new IllegalArgumentException("EXPLICIT_TEST_ONLY");var r=new L03ConnectedTrial();boolean failed=false;
        try{r.run();}catch(Exception ex){failed=true;r.state.put("result","PENDING_KEEP_CASE");r.state.put("failedPhase",r.phase);try{r.persist();}catch(Exception ignored){}System.out.println("CONNECTED_L03_RESULT=PENDING_KEEP_CASE; PHASE="+r.phase+"; DETAILS_REDACTED");}
        finally{if(r.frontend!=null)r.frontend.stop(0);if(r.app!=null){if(r.family!=null)try{new TransactionTemplate(r.app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->r.app.getBean(RefreshTokenRepository.class).revokeFamily(r.family,Instant.now()));}catch(Exception ignored){}r.app.close();}}
        if(failed)System.exit(1);
    }
    private void run()throws Exception {
        var config=ManualL01HumanTrial.configuration();
        try(Connection guard=ManualL01HumanTrial.connect(config)){
            guard.setAutoCommit(true);var inspect=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(guard,true));
            check("ruta_fija_test/rf_test".equals(inspect.queryForObject("select current_database()||'/'||current_user",String.class)),"TEST_ONLY");
            check("32".equals(inspect.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",String.class)),"V32_REQUIRED");
            check(!Files.exists(ROOT.resolve("backend/.local/l01-human-trial.active"))&&!Files.exists(ROOT.resolve("backend/.local/l02-connected.active")),"OTHER_CASE_ACTIVE");
            check(Boolean.TRUE.equals(inspect.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)),"LOCK_REQUIRED");
            if(Files.exists(STATE)){state.putAll(JSON.readValue(Files.readAllBytes(STATE),FixtureJson.OBJECT_TYPE));check(!Boolean.TRUE.equals(state.get("TEST_CLEANUP_RELEASED")),"CLOSED_CASE_NO_REPEAT");check(Files.readString(MARKER).strip().equals(text("runId")),"SAME_MARKER_REQUIRED");}
            else{check(!Files.exists(MARKER),"ORPHAN_MARKER");state.put("runId",UUID.randomUUID().toString());state.put("ledgerBefore",ledger(inspect));state.put("acceptancesBefore",inspect.queryForObject("select count(*) from admin_terms_acceptance",Integer.class));Files.writeString(MARKER,text("runId"),StandardOpenOption.CREATE_NEW);}
            evidence=ROOT.resolve("docs/evidencia/L-03/conectado-"+text("runId")+"/resultado.json");persist();
            frontend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+frontend.getAddress().getPort();control=UUID.randomUUID().toString();
            phase="BOOT_TEST_MAIL_DOUBLE";String signingKey=Files.readString(ROOT.resolve("backend/.local/l01-human-trial-jwt.private"),StandardCharsets.US_ASCII).strip();
            config.putAll(ManualL01HumanTrialServer.runtimeProperties(signingKey));config.put("server.port",0);config.put("server.address","127.0.0.1");config.put("spring.flyway.enabled",false);config.put("app.security.cors.allowed-origins",base);config.put("app.security.refresh-cookie.secure",false);config.put("app.qualification.maintenance.enabled",false);
            var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("explicitL03Test",config));var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
            OtpEmailSender mail=(a,b,c)->{throw new IllegalStateException("MAIL_NOT_NEEDED_IN_L03_SMOKE");};
            boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{var registry=(BeanDefinitionRegistry)factory;if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);check(registry.containsBeanDefinition("smtpOtpEmailSender"),"MAIL_ADAPTER_REQUIRED");registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",mail);}));
            app=boot.run();check(app.getBean(OtpEmailSender.class)==mail,"MAIL_DOUBLE_REQUIRED");jdbc=app.getBean(JdbcTemplate.class);port=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)app).getWebServer().getPort();
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{var user=app.getBean(AppUserRepository.class).findById(ADMIN).orElseThrow();check(user.isActive()&&user.getRole()==UserRole.ADMIN,"EXISTING_SYNTHETIC_ADMIN_REQUIRED");token=app.getBean(JwtService.class).issueAccessToken(user);var tokens=app.getBean(OpaqueTokenService.class);refresh=tokens.generate();var session=app.getBean(RefreshTokenRepository.class).saveAndFlush(RefreshToken.firstInFamily(user,tokens.hash(refresh),Instant.now().plus(Duration.ofHours(1))));family=session.getFamilyId();});
            frontend.createContext("/",this::serve);frontend.setExecutor(Executors.newVirtualThreadPerTaskExecutor());frontend.start();
            phase="TECHNICAL_UNIT";
            if(!state.containsKey("vehicleId")){state.put("vehicleId",call("/vehicles","POST",Map.of("plate","T-"+text("runId").substring(0,10),"brand","SYNTHETIC","model","L03","year",2026),201).path("id").asText());persist();}
            phase="CRM_BROWSER";if(!Boolean.TRUE.equals(state.get("crmPassed"))){runClient(true);state.put("crmPassed",true);persist();}
            phase="FLUTTER_AUTHORIZATION";if(!Boolean.TRUE.equals(state.get("flutterPassed"))){runClient(false);state.put("flutterPassed",true);persist();}
            phase="SELECTIVE_CLOSE";
            var m=call("/vehicles/"+text("vehicleId")+"/maintenance","GET",null,200);check(m.path("records").size()==1&&m.path("records").get(0).path("status").asText().equals("CANCELLED"),"EXACT_CANCELLED_PLAN_REQUIRED");
            check(!m.path("mechanicallyBlockedNow").asBoolean(),"CANCELLED_PLAN_MUST_NOT_BLOCK");
            call("/vehicles/"+text("vehicleId")+"/status","POST",Map.of("status","INACTIVO"),200);
            String routeName="SYNTHETIC L03 "+text("runId").replaceAll("[0-9]","x");
            UUID routeId=jdbc.queryForObject("select id from route_definition where name=? and organization_id=(select organization_id from app_user where id=?)",UUID.class,routeName,ADMIN);state.put("routeId",routeId.toString());
            var route=call("/routes/"+routeId,"GET",null,200);check(route.path("versions").size()==1,"ROUTE_HISTORY_REQUIRED");
            if(route.path("active").asBoolean())call("/routes/"+route.path("id").asText()+"/status","POST",Map.of("expectedVersion",route.path("version").asLong(),"operationKey",text("runId")+"-retire","reason","SYNTHETIC-CASE-CLOSED","active",false),200);
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->app.getBean(RefreshTokenRepository.class).revokeFamily(family,Instant.now()));
            check(ledger(jdbc).equals(state.get("ledgerBefore")),"LEDGER_CHANGED");check(jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class).equals(state.get("acceptancesBefore")),"ACCEPTANCES_CHANGED");
            state.put("ledgerAfter",ledger(jdbc));state.put("result","PASS_REAL_BROWSER_HTTP_PG_AND_FLUTTER_ROLE_REJECTION");state.put("accountCreations",0);state.put("externalMailAttempts",0);state.put("cleanup","TECHNICAL_UNIT_INACTIVE_ROUTE_RETIRED_PLAN_CANCELLED_REFRESH_FAMILY_REVOKED_HISTORY_RETAINED");state.put("mobileHappyPathTcpTested",false);state.put("globalCleanup",false);state.put("httpEvents",events);persist();
            Files.delete(MARKER);inspect.queryForObject("select pg_advisory_unlock(7518440514)",Boolean.class);state.put("TEST_CLEANUP_RELEASED",true);persist();System.out.println("CONNECTED_L03_RUN_ID="+text("runId"));System.out.println("CONNECTED_L03_RESULT="+text("result"));
        }
    }
    private void runClient(boolean crm)throws Exception {
        var p=crm?new ProcessBuilder("node",ROOT.resolve("frontend/e2e/l03-connected.mjs").toString()):new ProcessBuilder("cmd.exe","/d","/c","flutter.bat","test","--no-pub","--reporter","expanded","test/connected/l03_connected_test.dart");p.directory(ROOT.resolve(crm?"frontend":"mobile").toFile());p.redirectErrorStream(true);p.environment().put("RF_L03_CONNECTED_BASE",base);p.environment().put("RF_L03_CONNECTED_CONTROL",control);p.environment().put("RF_L03_CONNECTED_CLIENTS","true");
        Process process=p.start();try(ExecutorService read=Executors.newSingleThreadExecutor()){var output=read.submit(()->new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));if(!process.waitFor(180,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("CLIENT_TIMEOUT");}var safe=output.get(10,TimeUnit.SECONDS).lines().filter(s->s.startsWith("CONNECTED_L03_")&&!s.contains(refresh)&&!s.contains(token)&&!s.contains(control)).toList();Files.createDirectories(evidence.getParent());Files.write(evidence.resolveSibling((crm?"crm-":"flutter-")+System.currentTimeMillis()+".log"),safe,StandardCharsets.UTF_8);check(process.exitValue()==0,"CLIENT_FAILED");}finally{if(process.isAlive())process.destroyForcibly();}
    }
    private JsonNode call(String path,String method,Object body,int expected)throws Exception {var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Origin",base).header("Authorization","Bearer "+token).header("Content-Type","application/json");var r=client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());check(r.statusCode()==expected,"HTTP_STATUS_"+r.statusCode());return JSON.readTree(r.body());}
    private void serve(HttpExchange e){try{String path=e.getRequestURI().getPath();
        if(path.equals("/__l03/state")){if(!control.equals(e.getRequestHeaders().getFirst("X-L03-Control"))){reply(e,403,new byte[0],"text/plain");return;}reply(e,200,JSON.writeValueAsBytes(Map.of("runId",text("runId"),"vehicleId",text("vehicleId"),"browserRefresh",refresh,"adminToken",token)),"application/json");}
        else if(path.equals("/config/runtime-config.json"))reply(e,200,JSON.writeValueAsBytes(Map.of("apiBaseUrl","/api/v1","environment","test","appName","SYNTHETIC L03")),"application/json");
        else if(path.startsWith("/api/")){var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+e.getRequestURI())).timeout(Duration.ofSeconds(30));e.getRequestHeaders().forEach((n,v)->{if(!Set.of("host","connection","content-length","upgrade").contains(n.toLowerCase(Locale.ROOT)))v.forEach(x->r.header(n,x));});var response=client.send(r.method(e.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(e.getRequestBody().readAllBytes())).build(),HttpResponse.BodyHandlers.ofByteArray());response.headers().map().forEach((n,v)->{if(!Set.of("connection","content-length","transfer-encoding").contains(n))e.getResponseHeaders().put(n,v);});events.add(Map.of("path",path,"method",e.getRequestMethod(),"status",response.statusCode(),"observedAt",Instant.now().toString()));reply(e,response.statusCode(),response.body(),response.headers().firstValue("content-type").orElse("application/json"));}
        else{var root=ROOT.resolve("frontend/dist/ruta-fija-crm/browser");var file=root.resolve(path.substring(1)).normalize();if(!file.startsWith(root)){reply(e,404,new byte[0],"text/plain");return;}if(!Files.isRegularFile(file))file=root.resolve("index.html");reply(e,200,Files.readAllBytes(file),file.toString().endsWith(".js")?"application/javascript":file.toString().endsWith(".css")?"text/css":"text/html");}
    }catch(Exception ex){try{reply(e,500,new byte[0],"text/plain");}catch(Exception ignored){}}finally{e.close();}}
    private static void reply(HttpExchange e,int status,byte[] data,String type)throws Exception{e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(status,data.length==0?-1:data.length);if(data.length>0)e.getResponseBody().write(data);}
    private Map<String,Integer> ledger(JdbcTemplate db){var r=new LinkedHashMap<String,Integer>();for(var role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))r.put(role,db.queryForObject("select count(*) from pilot_account_creation where role=?",Integer.class,role));return r;}
    private void persist()throws Exception{state.put("phase",phase);state.put("observedAt",Instant.now().toString());Files.write(STATE,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(state));if(evidence!=null){Files.createDirectories(evidence.getParent());Files.write(evidence,JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(state));}}
    private String text(String key){return Objects.toString(state.get(key),"");}private static void check(boolean ok,String code){if(!ok)throw new IllegalStateException(code);}
}
