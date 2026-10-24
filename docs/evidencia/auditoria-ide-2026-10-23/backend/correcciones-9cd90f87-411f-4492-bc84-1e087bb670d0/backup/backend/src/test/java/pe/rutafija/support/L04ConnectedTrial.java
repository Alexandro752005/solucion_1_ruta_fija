package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.*;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in test-source runner: real clients/HTTP/PostgreSQL, mail double only.
 * One case, at most one newly provisioned CONDUCTOR; no global cleanup or quota reset. */
public final class L04ConnectedTrial {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static final Path STATE=ROOT.resolve("backend/.local/l04-connected-state.json");
    private static final Path PRIVATE=ROOT.resolve("backend/.local/l04-connected.private");
    private static final Path MARKER=ROOT.resolve("backend/.local/l04-connected.active");
    private static final UUID ADMIN=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979");
    private static final UUID ORG=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String,Object> state=new LinkedHashMap<>(), secrets=new ConcurrentHashMap<>();
    private final List<Map<String,Object>> httpEvents=new CopyOnWriteArrayList<>();
    private final Map<String,String> mail=new ConcurrentHashMap<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private ConfigurableApplicationContext app;private JdbcTemplate jdbc;private HttpServer frontend;
    private int backendPort;private String base,control,adminToken;private Path evidence;private String phase="INITIALIZE";
    public static void main(String[] args){
        if(args.length!=1||!"--run-connected".equals(args[0]))throw new IllegalArgumentException("EXPLICIT_TEST_MODE_REQUIRED");
        var runner=new L04ConnectedTrial();boolean failed=false;
        try{runner.run();}catch(Exception failure){
            try{runner.state.put("result","PENDING_KEEP_CASE");runner.state.put("failedPhase",runner.phase);runner.persist();}catch(Exception ignored){}
            System.out.println("CONNECTED_L04_RESULT=PENDING_KEEP_CASE; PHASE="+runner.phase+"; CAUSE="+failure.getClass().getSimpleName());
            failed=true;
        }finally{if(runner.frontend!=null)runner.frontend.stop(0);if(runner.app!=null)runner.app.close();runner.mail.clear();runner.secrets.clear();}
        if(failed)System.exit(1);
    }
    private void run()throws Exception{
        var config=ManualL01HumanTrial.configuration();
        phase="TEST_TARGET_PREFLIGHT";
        try(Connection guard=ManualL01HumanTrial.connect(config)){
            guard.setAutoCommit(true);
            JdbcTemplate inspect=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(guard,true));
            check("ruta_fija_test".equals(inspect.queryForObject("select current_database()",String.class)),"TEST_DATABASE_REQUIRED");
            check("rf_test".equals(inspect.queryForObject("select current_user",String.class)),"TEST_ROLE_REQUIRED");
            check(Set.of("35").contains(inspect.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",String.class)),"V35_REQUIRED");
            check(!Files.exists(ROOT.resolve("backend/.local/l01-human-trial.active"))&&!Files.exists(ROOT.resolve("backend/.local/l02-connected.active"))&&!Files.exists(ROOT.resolve("backend/.local/l03-connected.active")),"OTHER_CASE_ACTIVE");
            check(Files.exists(ROOT.resolve("docs/evidencia/L-04/autorizacion-conectado.json")),"EXPLICIT_L04_ONE_CONDUCTOR_AUTHORIZATION_REQUIRED");
            check(Boolean.TRUE.equals(inspect.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)),"CLEANUP_LOCK_REQUIRED");
            initialize(inspect);
            frontend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+frontend.getAddress().getPort();control=UUID.randomUUID().toString();
            phase="BOOT_TEST_MAIL_DOUBLE";
            String key=Files.readString(ROOT.resolve("backend/.local/l01-human-trial-jwt.private"),StandardCharsets.US_ASCII).strip();
            config.putAll(ManualL01HumanTrialServer.runtimeProperties(key));config.put("server.port",0);config.put("spring.flyway.enabled",false);
            config.put("app.security.cors.allowed-origins",base);config.put("app.security.refresh-cookie.secure",false);
            config.put("app.qualification.maintenance.enabled",false);
            var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("validatedL04ConnectedTest",config));
            var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
            OtpEmailSender simulated=(recipient,code,expiry)->{if(!email().equals(recipient))throw new IllegalStateException("SYNTHETIC_MAIL_ONLY");mail.put(recipient,code);};
            boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{
                var registry=(BeanDefinitionRegistry)factory;
                if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
                check(registry.containsBeanDefinition("smtpOtpEmailSender"),"SMTP_ADAPTER_NOT_FOUND");registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",simulated);
            }));
            app=boot.run();check(app.getBean(OtpEmailSender.class)==simulated,"MAIL_DOUBLE_REQUIRED");jdbc=app.getBean(JdbcTemplate.class);
            backendPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)app).getWebServer().getPort();
            check(app.getBean(InvitationCodeHasher.class).hashOrigin("person-identity:key-fingerprint:v1").equals(jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1",String.class)),"STABLE_IDENTITY_KEY_REQUIRED");
            adminToken=app.getBean(JwtService.class).issueAccessToken(app.getBean(AppUserRepository.class).findById(ADMIN).orElseThrow());
            frontend.createContext("/",this::serve);frontend.setExecutor(Executors.newVirtualThreadPerTaskExecutor());frontend.start();
            provision();
            phase="NORMAL_QUALIFICATION_HTTP";qualify();
            if(!secrets.containsKey("mobileToken")){var session=call("/mobile/auth/login","POST",Map.of("email",email(),"password",secrets.get("password")),null,200);
                check("OPERATIONS".equals(session.path("accessScope").asText()),"OPERATIONS_SCOPE_REQUIRED");secrets.put("mobileToken",session.path("accessToken").asText());savePrivate();}
            if(!state.containsKey("serviceTypeVersionId")||!state.containsKey("routeVersionId"))prepareCatalog();
            phase="CRM_INITIAL_OFFER";
            if(!Boolean.TRUE.equals(state.get("crmOfferPassed"))){state.put("clientStage","CREATE");prepareBrowserSession();runClient(true);captureOffers();state.put("crmOfferPassed",true);persist();}
            phase="FLUTTER_ACCEPT";
            if(!Boolean.TRUE.equals(state.get("flutterAcceptPassed"))){state.put("clientStage","ACCEPT");runClient(false);state.put("flutterAcceptPassed",true);persist();}
            phase="RETURN_AND_ROLLBACK_HTTP";
            if(!Boolean.TRUE.equals(state.get("returnPassed"))){returnCheck(config);state.put("returnPassed",true);persist();}
            phase="CRM_REOFFER";
            if(!Boolean.TRUE.equals(state.get("crmReofferPassed"))){state.put("clientStage","REOFFER");prepareBrowserSession();runClient(true);captureOffers();state.put("crmReofferPassed",true);persist();}
            phase="FLUTTER_REJECT";
            if(!Boolean.TRUE.equals(state.get("flutterRejectPassed"))){state.put("clientStage","REJECT");runClient(false);state.put("flutterRejectPassed",true);persist();}
            phase="CRM_CANCEL_SERVICE";
            if(!Boolean.TRUE.equals(state.get("crmCancelPassed"))){state.put("clientStage","CANCEL");prepareBrowserSession();runClient(true);state.put("crmCancelPassed",true);persist();}
            phase="SERVER_POSTCONDITIONS";
            var service=call("/scheduled-services/"+text("serviceId"),"GET",null,adminToken,200);
            check("CANCELLED".equals(service.path("service").path("status").asText()),"SERVICE_CANCELLED_REQUIRED");
            check(service.path("offers").size()==2,"EXACT_TWO_OFFERS_REQUIRED");
            check(jdbc.queryForObject("select count(*) from assignment where service_id=? and status='CANCELLED' and accepted_at is not null",Integer.class,UUID.fromString(text("serviceId")))==1,"ORIGINAL_ACCEPTANCE_RETAINED_REQUIRED");
            check(jdbc.queryForObject("select count(*) from assignment where service_id=? and status='REJECTED' and accepted_at is null",Integer.class,UUID.fromString(text("serviceId")))==1,"NEW_RECIPIENT_REJECTION_REQUIRED");
            state.put("connectedServiceConfirmed",true);state.put("httpEvents",httpEvents);state.put("externalSmtpAttempts",0);
            phase="SELECTIVE_FIXTURE_CLOSURE";cleanup();state.put("result","PASS_REAL_CRM_BROWSER_FLUTTER_HTTP_POSTGRES_SIMULATED_MAIL");persist();
            Files.delete(PRIVATE);state.put("privateFixtureSecretsRemoved",true);persist();
            Files.delete(MARKER);state.put("TEST_CLEANUP_RELEASED",true);persist();
            System.out.println("CONNECTED_L04_RUN_ID="+text("runId"));System.out.println("CONNECTED_L04_RESULT="+text("result"));System.out.println("CONNECTED_L04_EVIDENCE="+ROOT.relativize(evidence));
            inspect.queryForObject("select pg_advisory_unlock(7518440514)",Boolean.class);
        }
    }
    private void initialize(JdbcTemplate inspect)throws Exception{
        if(Files.exists(STATE)){
            state.putAll(JSON.readValue(Files.readAllBytes(STATE),Map.class));
            if(state.get("httpEvents") instanceof List<?> prior)for(Object item:prior)if(item instanceof Map<?,?> event)httpEvents.add((Map<String,Object>)event);
            check(!Boolean.TRUE.equals(state.get("TEST_CLEANUP_RELEASED")),"CASE_ALREADY_CLOSED");
            check(Files.exists(MARKER)&&Files.readString(MARKER).strip().equals(text("runId")),"SAME_CASE_MARKER_REQUIRED");
            secrets.putAll(JSON.readValue(Files.readAllBytes(PRIVATE),Map.class));
            if(state.containsKey("failedPhase")){
                List<Object> attempts=new ArrayList<>(state.get("attemptHistory") instanceof List<?> list?list:List.of());
                attempts.add(Map.of("result",Objects.toString(state.get("result"),"PENDING"),"failedPhase",state.remove("failedPhase"),"observedAt",Objects.toString(state.get("observedAt"),"")));state.put("attemptHistory",attempts);
            }
            state.put("result","EN_CURSO_SAME_CASE");
        }else{
            check(!Files.exists(MARKER)&&!Files.exists(PRIVATE),"EXISTING_CASE_MUST_BE_INSPECTED");
            check(inspect.queryForObject("select count(*) from pilot_account_creation where role='CONDUCTOR'",Integer.class)<20,"CONDUCTOR_QUOTA_EXHAUSTED");
            String run=UUID.randomUUID().toString();state.put("runId",run);state.put("email","conductor.l04."+run.substring(0,8)+"@rutafija.test");
            state.put("organizationId",ORG.toString());state.put("adminId",ADMIN.toString());state.put("dni","60000004");
            check(inspect.queryForObject("select count(*) from driver where document_type='DNI' and document_number=?",Integer.class,text("dni"))==0,"SYNTHETIC_IDENTITY_CONFLICT");
            state.put("ledgerBefore",ledger(inspect));state.put("termsAcceptancesBefore",inspect.queryForObject("select count(*) from admin_terms_acceptance",Integer.class));
            secrets.put("password","Synthetic-"+UUID.randomUUID());Files.writeString(MARKER,run,StandardOpenOption.CREATE_NEW);savePrivate();
        }
        evidence=ROOT.resolve("docs/evidencia/L-04/conectado-"+text("runId")+"/resultado.json");persist();
    }
    private void provision()throws Exception{
        phase="NORMAL_ONBOARDING_HTTP";
        if(!state.containsKey("groupId"))state.put("groupId",call("/groups","POST",Map.of("name","SYNTHETIC L04 "+text("runId").substring(0,8),"description","Synthetic connected trial only"),adminToken,201).path("id").asText());persist();
        if(!state.containsKey("vehicleId"))state.put("vehicleId",call("/vehicles","POST",Map.of("plate","SYN-L04","brand","SYNTHETIC","model","L04","year",2026),adminToken,201).path("id").asText());persist();
        var requests=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=?",String.class,email());
        if(!requests.isEmpty())state.put("requestId",requests.getFirst());
        if(!state.containsKey("requestId")){
            if(!secrets.containsKey("invitationCode")){var invite=call("/invitations","POST",Map.of("recipientEmail",email()),adminToken,201);secrets.put("invitationCode",invite.path("code").asText());state.put("invitationId",invite.path("id").asText());savePrivate();persist();}
            var start=call("/onboarding/requests","POST",Map.of("email",email(),"password",secrets.get("password"),"fullName","SYNTHETIC L04 CONDUCTOR","invitationCode",secrets.get("invitationCode")),null,202);
            check(mail.containsKey(email()),"SIMULATED_OTP_REQUIRED");
            var verified=call("/onboarding/otp/verify","POST",Map.of("verificationToken",start.path("verificationToken").asText(),"code",mail.remove(email())),null,202);
            state.put("requestId",verified.path("requestId").asText());persist();
        }
        String path="/onboarding/requests/"+text("requestId");var dossier=call(path+"/documentary-expediente","GET",null,adminToken,200);
        if(!dossier.path("personalApproved").asBoolean()){
            var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",dossier.path("version").asLong());body.put("operationKey",text("runId")+":personal");body.put("modality","TAXI");body.put("documents",personalDocs());
            dossier=call(path+"/documentary-expediente","PUT",body,adminToken,200);
            dossier=call(path+"/approve","POST",Map.of("expectedVersion",dossier.path("version").asLong(),"operationKey",text("runId")+":approve","reason","Synthetic controlled review"),adminToken,200);
        }
        if(dossier.path("provisionedDriverId").isNull()||dossier.path("provisionedDriverId").isMissingNode())dossier=call(path+"/provision","POST",Map.of("expectedVersion",dossier.path("version").asLong(),"operationKey",text("runId")+":provision","reason","Synthetic controlled provision","groupId",text("groupId")),adminToken,200);
        state.put("driverId",dossier.path("provisionedDriverId").asText());state.put("accountId",dossier.path("provisionedUserId").asText());persist();
    }
    private List<Map<String,Object>> personalDocs(){
        List<Map<String,Object>> out=new ArrayList<>();
        var fields=List.of(Map.of("documentType","DNI","documentNumber",text("dni"),"fullName","SYNTHETIC L04 CONDUCTOR","issuer","SYNTHETIC"),Map.of("licenseNumber","SYN-L04-LICENSE","licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"));
        for(int i=0;i<5;i++){var d=new LinkedHashMap<String,Object>();d.put("requirementId","CON-0"+(i+1));d.put("fields",i<2?fields.get(i):Map.of());d.put("expiryMode",i==1?"DATED":"NO_EXPIRY");if(i==1)d.put("validUntil",Instant.now().plus(Duration.ofDays(90)).toString());d.put("applicability","CONFIRMED");d.put("condition","Synthetic applicability only");d.put("sourceReference","Synthetic original review; not a real title");d.put("result",i<2?"CONFORME":"NO_APLICA_JUSTIFICADO");d.put("reviewReference","Synthetic controlled review");d.put("observation","Synthetic optional requirement not applicable");out.add(d);}return out;
    }

    private void qualify()throws Exception{
        String path="/drivers/"+text("driverId")+"/qualification";var q=call(path,"GET",null,adminToken,200);
        if(!q.path("operationalEnabled").asBoolean()){
            if(!Boolean.TRUE.equals(state.get("vehicleReviewed"))){q=call(path+"/reviews","POST",reviews("VEHICLE",q.path("version").asLong()),adminToken,200);state.put("vehicleReviewed",true);persist();}
            if(!Boolean.TRUE.equals(state.get("companyReviewed"))){q=call(path+"/reviews","POST",reviews("COMPANY",q.path("version").asLong()),adminToken,200);state.put("companyReviewed",true);persist();}
            if(!state.containsKey("periodId")){var c=new LinkedHashMap<String,Object>(Map.of("expectedVersion",q.path("version").asLong(),"operationKey",text("runId")+":period","reason","SYNTHETIC-CONNECTED-PERIOD","vehicleId",text("vehicleId"),"kind","OWN","startAt",Instant.now().minusSeconds(60).toString()));q=call(path+"/periods","POST",c,adminToken,200);state.put("periodId",q.path("periods").get(q.path("periods").size()-1).path("id").asText());persist();}
            q=call(path+"/activate","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",text("runId")+":activate","reason","SYNTHETIC-EXPLICIT-ACTIVATION"),adminToken,200);
        }
        check(q.path("operationalEnabled").asBoolean(),"QUALIFICATION_REQUIRED");
        var session=call("/mobile/auth/login","POST",Map.of("email",email(),"password",secrets.get("password")),null,200);
        secrets.put("mobileToken",session.path("accessToken").asText());savePrivate();
        call("/mobile/availability","PUT",Map.of("status","DISPONIBLE"),textSecret("mobileToken"),200);
    }
    private String textSecret(String key){return Objects.toString(secrets.get(key),"");}
    private Map<String,Object> reviews(String scope,long version){
        var command=new LinkedHashMap<String,Object>();command.put("expectedVersion",version);command.put("operationKey",text("runId")+":review-"+scope);
        command.put("reason","SYNTHETIC-CONNECTED-REVIEW");command.put("scope",scope);if(scope.equals("VEHICLE"))command.put("vehicleId",text("vehicleId"));
        var docs=new ArrayList<Map<String,Object>>();
        for(var r:pe.rutafija.qualification.application.QualificationCatalog.REQUIREMENTS)if(r.scope().equals(scope)){
            var fields=new LinkedHashMap<String,String>();for(String key:r.fieldKeys())fields.put(key,switch(key){case "plate"->"SYN-L04";case "category"->"M1";case "bodyType"->"SEDAN";case "brand"->"SYNTHETIC";case "model"->"L04";case "year"->"2026";case "insuranceType"->"SOAT";case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"SYNTHETIC CHICLAYO";default->"SYNTHETIC reference";});
            var d=new LinkedHashMap<String,Object>();d.put("requirementId",r.id());d.put("fields",fields);d.put("expiryMode","DATED");d.put("validUntil",Instant.now().plus(Duration.ofDays(60)).toString());d.put("applicability","CONFIRMED");d.put("condition","SYNTHETIC explicit applicability");d.put("sourceReference","SYNTHETIC not a real title");d.put("result",r.id().equals("EMP-03")?"NO_APLICA_JUSTIFICADO":"CONFORME");d.put("reviewReference","SYNTHETIC visual review");docs.add(d);
        }command.put("documents",docs);return command;
    }
    private void prepareCatalog()throws Exception{
        var type=call("/service-types","POST",Map.of("name","SYNTHETIC L04 "+text("runId").replaceAll("[0-9]","x"),"beforeMinutes",0,"afterMinutes",0,"operationKey",text("runId")+":type","reason","SYNTHETIC-CONNECTED-ZERO-NOT-PILOT-DEFAULT"),adminToken,200);
        state.put("serviceTypeId",type.path("id").asText());state.put("serviceTypeVersionId",type.path("currentVersionId").asText());persist();
        var stops=List.of(Map.of("key","s","kind","START","name","SYNTHETIC origin"),Map.of("key","m","kind","INTERMEDIATE","name","SYNTHETIC middle"),Map.of("key","e","kind","END","name","SYNTHETIC destination"));
        var route=call("/routes","POST",Map.of("name","SYNTHETIC L04 "+text("runId").replaceAll("[0-9]","x"),"operationKey",text("runId")+":route","reason","SYNTHETIC-CONNECTED-ROUTE","stops",stops),adminToken,200);
        state.put("routeId",route.path("id").asText());state.put("routeVersionId",route.path("currentVersionId").asText());persist();
    }
    private void captureOffers()throws Exception{
        var rows=jdbc.queryForList("select id,service_id from assignment where driver_id=? order by created_at,id",UUID.fromString(text("driverId")));
        check(rows.size()>=1&&rows.size()<=2,"EXACT_CASE_OFFERS_REQUIRED");var last=rows.getLast();
        state.put("currentOfferId",last.get("id").toString());state.put("serviceId",last.get("service_id").toString());persist();
    }
    private void returnCheck(Map<String,Object> config)throws Exception{
        var before=call("/scheduled-services/"+text("serviceId"),"GET",null,adminToken,200);var old=call("/assignments/"+text("currentOfferId"),"GET",null,adminToken,200);
        check("SCHEDULED".equals(old.path("status").asText())&&old.hasNonNull("acceptedAt"),"ACCEPTED_OFFER_REQUIRED");
        var properties=new LinkedHashMap<String,Object>(config);properties.put("app.qualification.operations-enabled",false);properties.put("server.port",0);
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("L04ReturnTest",properties));
        var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
        OtpEmailSender refuse=(r,c,e)->{throw new IllegalStateException("NO_MAIL_IN_RETURN");};
        boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{var registry=(BeanDefinitionRegistry)factory;if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",refuse);}));
        try(var returned=boot.run()){
            int originalPort=backendPort;backendPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)returned).getWebServer().getPort();
            try{
                var offer=new LinkedHashMap<String,Object>();for(String field:List.of("driverId","vehicleId","originText","destinationText","scheduledAt","scheduledEndAt","routeVersionId","serviceTypeVersionId"))offer.put(field,old.path(field).asText());
                call("/scheduled-services/"+text("serviceId")+"/offers","POST",Map.of("expectedVersion",before.path("service").path("version").asLong(),"operationKey",text("runId")+":return-reoffer","reason","SYNTHETIC-RETURN-ROLLBACK","offer",offer),adminToken,409);
                var after=call("/scheduled-services/"+text("serviceId"),"GET",null,adminToken,200);
                check(before.equals(after),"FAILED_REOFFER_MUST_ROLL_BACK_EXACTLY");state.put("returnResult","PASS_SAME_BINARY_DISABLED_NEW_OFFERS_WITH_EXACT_HTTP_ROLLBACK");
            }finally{backendPort=originalPort;}
        }
    }

    private void prepareBrowserSession()throws Exception{
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
            if(state.containsKey("browserRefreshFamilyId"))app.getBean(RefreshTokenRepository.class).revokeFamily(UUID.fromString(text("browserRefreshFamilyId")),Instant.now());
            var user=app.getBean(AppUserRepository.class).findById(ADMIN).orElseThrow();var tokens=app.getBean(OpaqueTokenService.class);String raw=tokens.generate();
            var refresh=app.getBean(RefreshTokenRepository.class).saveAndFlush(RefreshToken.firstInFamily(user,tokens.hash(raw),Instant.now().plus(Duration.ofHours(1))));
            secrets.put("browserRefresh",raw);state.put("browserRefreshFamilyId",refresh.getFamilyId().toString());
        });savePrivate();persist();
    }
    private JsonNode call(String path,String method,Object body,String token,int expected)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Origin",base).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(path.equals("/onboarding/requests"))request.header("Idempotency-Key",text("runId")+":onboarding");
        var started=Instant.now();var response=client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
        httpEvents.add(Map.of("path",path,"method",method,"status",response.statusCode(),"startedAt",started.toString(),"finishedAt",Instant.now().toString()));
        state.put("httpEvents",httpEvents);
        if(response.statusCode()!=expected){
            try{String code=JSON.readTree(response.body()).path("code").asText();if(code.matches("[A-Z][A-Z0-9_]{0,80}"))state.put("lastServerErrorCode",code);}catch(Exception ignored){}
            throw new IllegalStateException("HTTP_EXPECTATION_FAILED");
        }
        return response.body().length==0?JSON.createObjectNode():JSON.readTree(response.body());
    }
    private void runClient(boolean crm)throws Exception{
        ProcessBuilder builder=crm?new ProcessBuilder("node",ROOT.resolve("frontend/e2e/l04-connected.mjs").toString()):new ProcessBuilder("cmd.exe","/d","/c","flutter.bat","test","--no-pub","--reporter","expanded","test/connected/l04_connected_test.dart");
        builder.directory(ROOT.resolve(crm?"frontend":"mobile").toFile());builder.redirectErrorStream(true);
        builder.environment().put("RF_L04_CONNECTED_CLIENTS","true");builder.environment().put("RF_L04_CONNECTED_BASE",base);
        builder.environment().put("RF_L04_CONNECTED_CONTROL",control);builder.environment().put("RF_L04_CONNECTED_RUN_ID",text("runId"));
        builder.environment().put("RF_L04_CONNECTED_STAGE",text("clientStage"));
        builder.environment().put("RF_L04_CONNECTED_OFFER_ID",text("currentOfferId"));
        builder.environment().put("RF_L04_CONNECTED_SERVICE_ID",text("serviceId"));
        builder.environment().put("RF_L04_CONNECTED_DRIVER_ID",text("driverId"));builder.environment().put("RF_L04_CONNECTED_TOKEN",Objects.toString(secrets.get("mobileToken"),""));
        var process=builder.start();try(var reader=Executors.newSingleThreadExecutor()){
            var output=reader.submit(()->new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
            if(!process.waitFor(180,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("CLIENT_TIMEOUT");}
            String captured=output.get(10,TimeUnit.SECONDS);var safe=captured.lines().filter(l->(l.startsWith("CONNECTED_L04_")||l.startsWith("L04_CONNECTED_"))&&!l.contains("Bearer")&&secrets.values().stream().noneMatch(v->v.toString().length()>5&&l.contains(v.toString()))).toList();
            Files.createDirectories(evidence.getParent());Files.write(evidence.resolveSibling((crm?"crm-":"flutter-")+System.currentTimeMillis()+".log"),safe,StandardCharsets.UTF_8);
            check(process.exitValue()==0,"CLIENT_FAILED");
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
    private void cleanup()throws Exception{
        var current=call("/drivers/"+text("driverId")+"/qualification","GET",null,adminToken,200);
        for(var period:current.path("periods"))if("AUTHORIZED".equals(period.path("status").asText())){
            current=call("/drivers/"+text("driverId")+"/qualification/periods/"+period.path("id").asText()+"/revoke","POST",Map.of("expectedVersion",current.path("version").asLong(),"operationKey",text("runId")+":closure-period","reason","Synthetic connected fixture closure"),adminToken,200);
        }
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
            UUID account=UUID.fromString(text("accountId")),driver=UUID.fromString(text("driverId"));
            var user=app.getBean(AppUserRepository.class).findById(account).orElseThrow();check(user.getEmail().equals(email())&&user.getRole()==UserRole.CONDUCTOR&&ORG.equals(user.getOrganizationId()),"EXACT_FIXTURE_REQUIRED");
            app.getBean(DriverRepository.class).findById(driver).orElseThrow().deactivate();user.deactivate();
            app.getBean(VehicleRepository.class).findById(UUID.fromString(text("vehicleId"))).orElseThrow().deactivate();
            app.getBean(TransportGroupRepository.class).findById(UUID.fromString(text("groupId"))).orElseThrow().deactivate();
            app.getBean(RefreshTokenRepository.class).revokeActiveByUserId(account,Instant.now());
            if(state.containsKey("browserRefreshFamilyId"))app.getBean(RefreshTokenRepository.class).revokeFamily(UUID.fromString(text("browserRefreshFamilyId")),Instant.now());
        });
        var route=call("/routes/"+text("routeId"),"GET",null,adminToken,200);
        if(route.path("active").asBoolean())call("/routes/"+text("routeId")+"/status","POST",Map.of("expectedVersion",route.path("version").asLong(),"operationKey",text("runId")+":retire-route","reason","SYNTHETIC-CASE-CLOSED","active",false),adminToken,200);
        var type=call("/service-types/"+text("serviceTypeId"),"GET",null,adminToken,200);
        if(type.path("active").asBoolean())call("/service-types/"+text("serviceTypeId")+"/status","POST",Map.of("expectedVersion",type.path("version").asLong(),"operationKey",text("runId")+":retire-type","reason","SYNTHETIC-CASE-CLOSED","active",false),adminToken,200);
        var before=(Map<?,?>)state.get("ledgerBefore");var after=ledger(jdbc);
        check(Objects.equals(before.get("SUPER_ADMIN"),after.get("SUPER_ADMIN"))&&Objects.equals(before.get("ADMIN"),after.get("ADMIN"))&&((Number)after.get("CONDUCTOR")).intValue()==((Number)before.get("CONDUCTOR")).intValue()+1,"HISTORICAL_LEDGER_MISMATCH");
        check(jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class).equals(state.get("termsAcceptancesBefore")),"ACCEPTANCES_MUST_BE_PRESERVED");
        check(Boolean.TRUE.equals(jdbc.queryForObject("select not active from app_user where id=?",Boolean.class,UUID.fromString(text("accountId")))),"FIXTURE_MUST_BE_DISABLED");
        state.put("ledgerAfter",after);state.put("selectiveCleanup","PASS_DISABLED_AND_TOKENS_REVOKED_LEDGER_PRESERVED");state.put("globalCleanup",false);
    }
    private void serve(HttpExchange exchange){try{
        String path=exchange.getRequestURI().getPath();
        if(path.startsWith("/__l04/")){
            if(!control.equals(exchange.getRequestHeaders().getFirst("X-L04-Control"))){reply(exchange,403,new byte[0],"text/plain");return;}
            reply(exchange,200,JSON.writeValueAsBytes(Map.of("runId",text("runId"),"driverId",text("driverId"),"vehicleId",text("vehicleId"),"organizationId",ORG.toString(),"browserRefresh",secrets.get("browserRefresh"),"serviceTypeVersionId",text("serviceTypeVersionId"),"routeVersionId",text("routeVersionId"),"currentOfferId",text("currentOfferId"),"serviceId",text("serviceId"),"clientStage",text("clientStage"))),"application/json");
        }else if(path.equals("/config/runtime-config.json"))reply(exchange,200,JSON.writeValueAsBytes(Map.of("apiBaseUrl","/api/v1","environment","test","appName","SYNTHETIC Ruta Fija L04")),"application/json");
        else if(path.startsWith("/api/")){
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+exchange.getRequestURI())).timeout(Duration.ofSeconds(30));
            exchange.getRequestHeaders().forEach((n,v)->{if(!Set.of("host","connection","content-length","upgrade").contains(n.toLowerCase(Locale.ROOT)))v.forEach(value->request.header(n,value));});
            var response=client.send(request.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes())).build(),HttpResponse.BodyHandlers.ofByteArray());
            response.headers().map().forEach((n,v)->{if(!Set.of("connection","content-length","transfer-encoding").contains(n))exchange.getResponseHeaders().put(n,v);});
            httpEvents.add(Map.of("path",path,"method",exchange.getRequestMethod(),"status",response.statusCode(),"observedAt",Instant.now().toString(),"channel","client-real-http"));
            reply(exchange,response.statusCode(),response.body(),response.headers().firstValue("content-type").orElse("application/json"));
        }else{var root=ROOT.resolve("frontend/dist/ruta-fija-crm/browser");var file=root.resolve(path.substring(1)).normalize();if(!file.startsWith(root)){reply(exchange,404,new byte[0],"text/plain");return;}if(!Files.isRegularFile(file))file=root.resolve("index.html");reply(exchange,200,Files.readAllBytes(file),file.toString().endsWith(".js")?"application/javascript":file.toString().endsWith(".css")?"text/css":"text/html");}
    }catch(Exception failure){try{reply(exchange,500,new byte[0],"text/plain");}catch(Exception ignored){}}finally{exchange.close();}}
    private static void reply(HttpExchange e,int status,byte[] bytes,String type)throws Exception{e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);if(bytes.length>0)e.getResponseBody().write(bytes);}
    private Map<String,Integer> ledger(JdbcTemplate db){var out=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))out.put(role,db.queryForObject("select count(*) from pilot_account_creation where role=?",Integer.class,role));return out;}
    private String text(String key){return Objects.toString(state.get(key),"");}private String email(){return text("email");}
    private void savePrivate()throws Exception{
        Path temp=PRIVATE.resolveSibling("l04-connected-"+UUID.randomUUID()+".private");
        try{ManualL01HumanTrialServer.writeRestricted(temp,JSON.writeValueAsBytes(secrets));Files.move(temp,PRIVATE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private void persist()throws Exception{state.put("phase",phase);state.put("observedAt",Instant.now().toString());Files.write(STATE,JSON.writeValueAsBytes(state));if(evidence!=null){Files.createDirectories(evidence.getParent());var safe=new LinkedHashMap<>(state);safe.remove("dni");safe.remove("email");byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(safe);Files.write(evidence,bytes);
        Files.write(evidence.resolveSibling("observacion-"+System.currentTimeMillis()+"-"+Long.toUnsignedString(System.nanoTime())+".json"),bytes,StandardOpenOption.CREATE_NEW);}}
    private static void check(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
}
