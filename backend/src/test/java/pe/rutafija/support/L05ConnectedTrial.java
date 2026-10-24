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
 * Approved L05-CON-01: one case, at most two newly provisioned CONDUCTOR; no global cleanup or quota reset. */
public final class L05ConnectedTrial {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static final Path STATE=ROOT.resolve("backend/.local/l05-connected-state.json");
    private static final Path PRIVATE=ROOT.resolve("backend/.local/l05-connected.private");
    private static final Path MARKER=ROOT.resolve("backend/.local/l05-connected.active");
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
        var runner=new L05ConnectedTrial();boolean failed=false;
        try{runner.run();}catch(Exception failure){
            try{runner.state.put("result","PENDING_KEEP_CASE");runner.state.put("failedPhase",runner.phase);runner.persist();}catch(Exception ignored){}
            String code=Objects.toString(failure.getMessage(),"");
            if(!code.matches("[A-Z][A-Z0-9_]{0,100}"))code="DETAILS_REDACTED";
            System.out.println("CONNECTED_L05_RESULT=PENDING_KEEP_CASE; PHASE="+runner.phase+"; CAUSE="+failure.getClass().getSimpleName()+"; CHECK="+code);
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
            check(Set.of("42").contains(inspect.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",String.class)),"V42_REQUIRED");
            check(!Files.exists(ROOT.resolve("backend/.local/l01-human-trial.active"))&&!Files.exists(ROOT.resolve("backend/.local/l02-connected.active"))&&!Files.exists(ROOT.resolve("backend/.local/l03-connected.active"))&&!Files.exists(ROOT.resolve("backend/.local/l04-connected.active")),"OTHER_CASE_ACTIVE");
            check(Files.exists(ROOT.resolve("docs/operacion/L-05-caso-conectado-autorizacion.md")),"EXPLICIT_L05_TWO_CONDUCTOR_AUTHORIZATION_REQUIRED");
            check(Boolean.TRUE.equals(inspect.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)),"CLEANUP_LOCK_REQUIRED");
            initialize(inspect);
            L05ConnectedGuardProbe.main(new String[0]);
            state.put("rollbackFixtureGuard","PASS_REFUSED_BEFORE_FIXTURE");persist();
            frontend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+frontend.getAddress().getPort();control=UUID.randomUUID().toString();
            phase="BOOT_TEST_MAIL_DOUBLE";
            String key=Files.readString(ROOT.resolve("backend/.local/l01-human-trial-jwt.private"),StandardCharsets.US_ASCII).strip();
            config.putAll(ManualL01HumanTrialServer.runtimeProperties(key));config.put("server.port",0);config.put("spring.flyway.enabled",false);
            config.put("app.security.cors.allowed-origins",base);config.put("app.security.refresh-cookie.secure",false);
            config.put("app.qualification.maintenance.enabled",false);config.put("app.mobile.maintenance-enabled",false);
            var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("validatedL05ConnectedTest",config));
            var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
            OtpEmailSender simulated=(recipient,code,expiry)->{if(!allowedEmails().contains(recipient))throw new IllegalStateException("SYNTHETIC_MAIL_ONLY");mail.put(recipient,code);secrets.put("simulatedOtp:"+recipient,code);try{savePrivate();}catch(Exception failure){throw new IllegalStateException("PRIVATE_SIMULATED_MAIL_STATE_FAILED");}};
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
            if(!Boolean.TRUE.equals(state.get("cleanupStarted"))){for(int index=0;index<2;index++){selectParticipant(index);provision();phase="NORMAL_QUALIFICATION_HTTP_P"+index;qualify();state.put("qualifiedOnce",true);syncParticipant();savePrivate();persist();}
            selectParticipant(0);if(!state.containsKey("serviceTypeVersionId"))prepareCatalog();
            connectedWorkflow(config);}
            phase="SELECTIVE_FIXTURE_CLOSURE";cleanupAll();
            state.put("result","PASS_REAL_CRM_FLUTTER_HTTP_POSTGRES_SIMULATED_GPS_MAIL");state.put("httpEvents",httpEvents);state.put("externalSmtpAttempts",0);persist();
            Files.delete(PRIVATE);state.put("privateFixtureSecretsRemoved",true);persist();
            Files.delete(MARKER);state.put("TEST_CLEANUP_RELEASED",true);persist();
            System.out.println("CONNECTED_L05_RUN_ID="+text("runId"));System.out.println("CONNECTED_L05_RESULT="+text("result"));System.out.println("CONNECTED_L05_EVIDENCE="+ROOT.relativize(evidence));
            inspect.queryForObject("select pg_advisory_unlock(7518440514)",Boolean.class);
        }
    }
    private static final String RUN="844b3bf6-f2fa-4666-bd0e-b1f7a1188696";
    private static final List<String> PERSON_FIELDS=List.of("email","dni","groupId","vehicleId","requestId","driverId","accountId","periodId","invitationId","vehicleReviewed","companyReviewed","qualifiedOnce");
    private static final List<String> SECRET_FIELDS=List.of("password","mobileToken","invitationCode","verificationToken");
    private int participantIndex(){return ((Number)state.getOrDefault("participantIndex",0)).intValue();}
    private String fixtureKey(String suffix){return RUN+":p"+participantIndex()+":"+suffix;}
    @SuppressWarnings("unchecked") private List<Map<String,Object>> people(){return (List<Map<String,Object>>)state.get("participants");}
    private Set<String> allowedEmails(){return Set.of("conductor.l05.844b3bf6.p0@rutafija.test","conductor.l05.844b3bf6.p1@rutafija.test");}
    private void syncParticipant(){
        if(!state.containsKey("participants"))return;var p=people().get(participantIndex());
        for(String k:PERSON_FIELDS)if(state.containsKey(k))p.put(k,state.get(k));
        for(String k:SECRET_FIELDS)if(secrets.containsKey(k))secrets.put("p"+participantIndex()+":"+k,secrets.get(k));
    }
    private void selectParticipant(int index)throws Exception{
        check(index==0||index==1,"EXACT_TWO_PARTICIPANTS_ONLY");syncParticipant();
        for(String k:PERSON_FIELDS)state.remove(k);for(String k:SECRET_FIELDS)secrets.remove(k);
        state.put("participantIndex",index);state.putAll(people().get(index));
        for(String k:SECRET_FIELDS){Object value=secrets.get("p"+index+":"+k);if(value!=null)secrets.put(k,value);}
    }
    private void initialize(JdbcTemplate inspect)throws Exception{
        var decisions=JSON.readTree(Files.readAllBytes(ROOT.resolve("specs/decisiones-L05-2026-10-19.json"))).path("connectedCaseAuthorization");
        check(RUN.equals(decisions.path("runId").asText())&&decisions.path("maxNewConductorAccounts").asInt()==2&&"CONFIRMADA_USUARIO".equals(decisions.path("status").asText()),"EXACT_L05_CON01_AUTHORIZATION_REQUIRED");
        if(Files.exists(STATE)){
            state.putAll(JSON.readValue(Files.readAllBytes(STATE),FixtureJson.OBJECT_TYPE));check(RUN.equals(text("runId"))&&!Boolean.TRUE.equals(state.get("TEST_CLEANUP_RELEASED")),"SAME_OPEN_CASE_ONLY");
            check(Files.exists(MARKER)&&RUN.equals(Files.readString(MARKER).strip())&&Files.exists(PRIVATE),"SAME_CASE_PROTECTION_AND_PRIVATE_STATE_REQUIRED");
            secrets.putAll(JSON.readValue(Files.readAllBytes(PRIVATE),FixtureJson.OBJECT_TYPE));
            if(state.get("httpEvents") instanceof List<?> prior)for(Object item:prior)if(item instanceof Map<?,?> m)httpEvents.add(FixtureJson.object(m));
            if(state.containsKey("failedPhase")){var attempts=new ArrayList<Object>(state.get("attemptHistory") instanceof List<?> l?l:List.of());attempts.add(Map.of("phase",state.remove("failedPhase"),"at",Objects.toString(state.get("observedAt"),"")));state.put("attemptHistory",attempts);}
        }else{
            check(!Files.exists(MARKER)&&!Files.exists(PRIVATE),"INSPECT_EXISTING_PRIVATE_CASE");var before=ledger(inspect);check(before.equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",2)),"EXACT_PREAUTHORIZED_LEDGER_REQUIRED");
            state.put("runId",RUN);state.put("organizationId",ORG.toString());state.put("adminId",ADMIN.toString());state.put("ledgerBefore",before);state.put("termsAcceptancesBefore",inspect.queryForObject("select count(*) from admin_terms_acceptance",Integer.class));
            state.put("antecedentRowHashes",antecedentHashes(inspect,null));
            var participants=new ArrayList<Map<String,Object>>();for(int i=0;i<2;i++){String email="conductor.l05.844b3bf6.p"+i+"@rutafija.test",dni="6000000"+(5+i);check(inspect.queryForObject("select count(*) from app_user where email=?",Integer.class,email)==0&&inspect.queryForObject("select count(*) from conductor_onboarding_identity where email=?",Integer.class,email)==0&&inspect.queryForObject("select count(*) from driver where document_type='DNI' and document_number=?",Integer.class,dni)==0,"SYNTHETIC_IDENTITY_COLLISION");participants.add(new LinkedHashMap<>(Map.of("email",email,"dni",dni)));secrets.put("p"+i+":password","Synthetic-"+UUID.randomUUID());}
            state.put("participants",participants);state.put("participantIndex",0);state.putAll(participants.get(0));secrets.put("password",secrets.get("p0:password"));
            Files.writeString(MARKER,RUN,StandardOpenOption.CREATE_NEW);savePrivate();
        }
        evidence=ROOT.resolve("docs/evidencia/L-05/conectado-"+RUN+"/resultado.json");state.put("result","EN_CURSO_SAME_AUTHORIZED_CASE");persist();
    }

    private void provision()throws Exception{
        phase="NORMAL_ONBOARDING_HTTP";
        if(!state.containsKey("groupId"))state.put("groupId",call("/groups","POST",Map.of("name","SYNTHETIC L05 "+text("runId").substring(0,8)+" P"+participantIndex(),"description","Synthetic connected trial only"),adminToken,201).path("id").asText());persist();
        if(!state.containsKey("vehicleId"))state.put("vehicleId",call("/vehicles","POST",Map.of("plate",("SYN-L05-"+participantIndex()),"brand","SYNTHETIC","model","L05","year",2026),adminToken,201).path("id").asText());persist();
        var requests=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=?",String.class,email());
        if(!requests.isEmpty())state.put("requestId",requests.getFirst());
        if(!state.containsKey("requestId")){
            if(!secrets.containsKey("invitationCode")){var invite=call("/invitations","POST",Map.of("recipientEmail",email()),adminToken,201);secrets.put("invitationCode",invite.path("code").asText());state.put("invitationId",invite.path("id").asText());savePrivate();persist();}
            if(!secrets.containsKey("verificationToken")){
                var start=call("/onboarding/requests","POST",Map.of("email",email(),"password",secrets.get("password"),"fullName","SYNTHETIC L05 CONDUCTOR "+participantIndex(),"invitationCode",secrets.get("invitationCode")),null,202);
                secrets.put("verificationToken",start.path("verificationToken").asText());savePrivate();
            }
            check(secrets.containsKey("simulatedOtp:"+email()),"SIMULATED_OTP_REQUIRED");
            var verified=call("/onboarding/otp/verify","POST",Map.of("verificationToken",secrets.get("verificationToken"),"code",secrets.get("simulatedOtp:"+email())),null,202);
            state.put("requestId",verified.path("requestId").asText());persist();
        }
        String path="/onboarding/requests/"+text("requestId");var dossier=call(path+"/documentary-expediente","GET",null,adminToken,200);
        if(!dossier.path("personalApproved").asBoolean()){
            var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",dossier.path("version").asLong());body.put("operationKey",fixtureKey("personal"));body.put("modality","TAXI");body.put("documents",personalDocs());
            dossier=call(path+"/documentary-expediente","PUT",body,adminToken,200);
            dossier=call(path+"/approve","POST",Map.of("expectedVersion",dossier.path("version").asLong(),"operationKey",fixtureKey("approve"),"reason","Synthetic controlled review"),adminToken,200);
        }
        if(dossier.path("provisionedDriverId").isNull()||dossier.path("provisionedDriverId").isMissingNode())dossier=call(path+"/provision","POST",Map.of("expectedVersion",dossier.path("version").asLong(),"operationKey",fixtureKey("provision"),"reason","Synthetic controlled provision","groupId",text("groupId")),adminToken,200);
        state.put("driverId",dossier.path("provisionedDriverId").asText());state.put("accountId",dossier.path("provisionedUserId").asText());persist();
    }
    private List<Map<String,Object>> personalDocs(){
        List<Map<String,Object>> out=new ArrayList<>();
        var fields=List.of(Map.of("documentType","DNI","documentNumber",text("dni"),"fullName","SYNTHETIC L05 CONDUCTOR "+participantIndex(),"issuer","SYNTHETIC"),Map.of("licenseNumber","SYN-L05-LICENSE-"+participantIndex(),"licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"));
        for(int i=0;i<5;i++){var d=new LinkedHashMap<String,Object>();d.put("requirementId","CON-0"+(i+1));d.put("fields",i<2?fields.get(i):Map.of());d.put("expiryMode",i==1?"DATED":"NO_EXPIRY");if(i==1)d.put("validUntil",Instant.now().plus(Duration.ofDays(90)).toString());d.put("applicability","CONFIRMED");d.put("condition","Synthetic applicability only");d.put("sourceReference","Synthetic original review; not a real title");d.put("result",i<2?"CONFORME":"NO_APLICA_JUSTIFICADO");d.put("reviewReference","Synthetic controlled review");d.put("observation","Synthetic optional requirement not applicable");out.add(d);}return out;
    }

    private void qualify()throws Exception{
        String path="/drivers/"+text("driverId")+"/qualification";var q=call(path,"GET",null,adminToken,200);
        boolean prepared=Boolean.TRUE.equals(state.get("qualifiedOnce"));
        // A resumed own interruption intentionally has qualification blockers.
        // Never activate or redeclare availability merely to resume observation.
        if(!q.path("operationalEnabled").asBoolean()&&!prepared){
            if(!Boolean.TRUE.equals(state.get("vehicleReviewed"))){q=call(path+"/reviews","POST",reviews("VEHICLE",q.path("version").asLong()),adminToken,200);state.put("vehicleReviewed",true);persist();}
            if(!Boolean.TRUE.equals(state.get("companyReviewed"))){q=call(path+"/reviews","POST",reviews("COMPANY",q.path("version").asLong()),adminToken,200);state.put("companyReviewed",true);persist();}
            if(!state.containsKey("periodId")){var c=new LinkedHashMap<String,Object>(Map.of("expectedVersion",q.path("version").asLong(),"operationKey",fixtureKey("period"),"reason","SYNTHETIC-CONNECTED-PERIOD","vehicleId",text("vehicleId"),"kind","OWN","startAt",Instant.now().minusSeconds(60).toString()));q=call(path+"/periods","POST",c,adminToken,200);state.put("periodId",q.path("periods").get(q.path("periods").size()-1).path("id").asText());persist();}
            q=call(path+"/activate","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",fixtureKey("activate"),"reason","SYNTHETIC-EXPLICIT-ACTIVATION"),adminToken,200);
        }
        if(!prepared)check(q.path("operationalEnabled").asBoolean(),"QUALIFICATION_REQUIRED");
        else state.put("resumeQualificationObservedOnly",true);
        if(!textSecret("mobileToken").isBlank()){
            try{var prior=app.getBean(org.springframework.security.oauth2.jwt.JwtDecoder.class).decode(textSecret("mobileToken"));
                if("INCORPORATION".equals(prior.getClaimAsString("accessScope")))secrets.put("p"+participantIndex()+":restrictedMobileToken",textSecret("mobileToken"));
            }catch(org.springframework.security.oauth2.jwt.JwtException ignored){/* An expired old session is never revived. */}
        }
        var session=call("/mobile/auth/login","POST",Map.of("email",email(),"password",secrets.get("password")),null,200);
        secrets.put("mobileToken",session.path("accessToken").asText());savePrivate();
        var profile=call("/mobile/me","GET",null,textSecret("mobileToken"),200);if(!prepared&&Set.of("DISPONIBLE","DESCANSO","NO_DISPONIBLE").contains(profile.path("availabilityStatus").asText()))call("/mobile/availability","PUT",Map.of("status","DISPONIBLE"),textSecret("mobileToken"),200);
    }
    private String textSecret(String key){return Objects.toString(secrets.get(key),"");}
    private Map<String,Object> reviews(String scope,long version){
        var command=new LinkedHashMap<String,Object>();command.put("expectedVersion",version);command.put("operationKey",fixtureKey("review-")+scope);
        command.put("reason","SYNTHETIC-CONNECTED-REVIEW");command.put("scope",scope);if(scope.equals("VEHICLE"))command.put("vehicleId",text("vehicleId"));
        var docs=new ArrayList<Map<String,Object>>();
        for(var r:pe.rutafija.qualification.application.QualificationCatalog.REQUIREMENTS)if(r.scope().equals(scope)){
            var fields=new LinkedHashMap<String,String>();for(String key:r.fieldKeys())fields.put(key,switch(key){case "plate"->("SYN-L05-"+participantIndex());case "category"->"M1";case "bodyType"->"SEDAN";case "brand"->"SYNTHETIC";case "model"->"L05";case "year"->"2026";case "insuranceType"->"SOAT";case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"SYNTHETIC CHICLAYO";default->"SYNTHETIC reference";});
            var d=new LinkedHashMap<String,Object>();d.put("requirementId",r.id());d.put("fields",fields);d.put("expiryMode","DATED");d.put("validUntil",Instant.now().plus(Duration.ofDays(60)).toString());d.put("applicability","CONFIRMED");d.put("condition","SYNTHETIC explicit applicability");d.put("sourceReference","SYNTHETIC not a real title");d.put("result",r.id().equals("EMP-03")?"NO_APLICA_JUSTIFICADO":"CONFORME");d.put("reviewReference","SYNTHETIC visual review");docs.add(d);
        }command.put("documents",docs);return command;
    }
    private void prepareCatalog()throws Exception{
        var type=call("/service-types","POST",Map.of("name","SYNTHETIC L05 "+text("runId").replaceAll("[0-9]","x"),"beforeMinutes",0,"afterMinutes",0,"operationKey",fixtureKey("type"),"reason","SYNTHETIC-CONNECTED-ZERO-NOT-PILOT-DEFAULT"),adminToken,200);
        state.put("serviceTypeId",type.path("id").asText());state.put("serviceTypeVersionId",type.path("currentVersionId").asText());persist();
        var stops=List.of(Map.of("key","s","kind","START","name","SYNTHETIC origin"),Map.of("key","m","kind","INTERMEDIATE","name","SYNTHETIC middle"),Map.of("key","e","kind","END","name","SYNTHETIC destination","latitude",-6.77,"longitude",-79.84));
        var route=call("/routes","POST",Map.of("name","SYNTHETIC L05 "+text("runId").replaceAll("[0-9]","x"),"operationKey",fixtureKey("route"),"reason","SYNTHETIC-CONNECTED-ROUTE","stops",stops),adminToken,200);
        state.put("routeId",route.path("id").asText());state.put("routeVersionId",route.path("currentVersionId").asText());persist();
    }
    private void captureOffers()throws Exception{
        var rows=jdbc.queryForList("select id,service_id from assignment where driver_id=? order by created_at,id",UUID.fromString(text("driverId")));
        check(rows.size()>=1&&rows.size()<=8,"EXACT_CASE_OFFERS_REQUIRED");var last=rows.getLast();
        state.put("currentOfferId",last.get("id").toString());state.put("serviceId",last.get("service_id").toString());persist();
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
        if(path.equals("/onboarding/requests"))request.header("Idempotency-Key",fixtureKey("onboarding"));
        if(path.equals("/assignments")&&expected==201&&state.containsKey("creationKey"))request.header("Idempotency-Key",text("creationKey"));
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
        ProcessBuilder builder=crm?new ProcessBuilder("node",ROOT.resolve("frontend/e2e/l05-connected.mjs").toString()):new ProcessBuilder("cmd.exe","/d","/c","flutter.bat","test","--no-pub","--reporter","expanded","test/connected/l05_connected_test.dart");
        builder.directory(ROOT.resolve(crm?"frontend":"mobile").toFile());builder.redirectErrorStream(true);
        builder.environment().put("RF_L05_CONNECTED_CLIENTS","true");builder.environment().put("RF_L05_CONNECTED_BASE",base);
        builder.environment().put("RF_L05_CONNECTED_CONTROL",control);builder.environment().put("RF_L05_CONNECTED_RUN_ID",text("runId"));
        builder.environment().put("RF_L05_CONNECTED_STAGE",text("clientStage"));
        builder.environment().put("RF_L05_CONNECTED_OFFER_ID",text("currentOfferId"));
        builder.environment().put("RF_L05_CONNECTED_SERVICE_ID",text("serviceId"));
        builder.environment().put("RF_L05_CONNECTED_DRIVER_ID",text("driverId"));builder.environment().put("RF_L05_CONNECTED_TOKEN",Objects.toString(secrets.get("mobileToken"),""));
        builder.environment().put("RF_L05_CONNECTED_HANDOVER_ID",text("handoverId"));
        var process=builder.start();try(var reader=Executors.newSingleThreadExecutor()){
            var output=reader.submit(()->new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
            if(!process.waitFor(240,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("CLIENT_TIMEOUT");}
            String captured=output.get(10,TimeUnit.SECONDS);var safe=captured.lines().filter(l->(l.startsWith("CONNECTED_L05_")||l.startsWith("L05_CONNECTED_"))&&!l.contains("Bearer")&&secrets.values().stream().noneMatch(v->v.toString().length()>5&&l.contains(v.toString()))).toList();
            if(process.exitValue()!=0){
                String diagnostic=captured.contains("Timer is still pending")?"FLUTTER_PENDING_TIMER":captured.contains("RenderFlex overflowed")?"FLUTTER_LAYOUT_OVERFLOW":captured.contains("SYNTHETIC_WIDGET_WAIT")?"FLUTTER_WIDGET_WAIT":captured.contains("SYNTHETIC_WIDGET_FAILURE")?"FLUTTER_WIDGET_EXCEPTION":captured.contains("Expected:")?"CLIENT_ASSERTION_FAILED":captured.contains("TimeoutException")?"CLIENT_TIMEOUT":"CLIENT_FAILURE_DETAILS_REDACTED";
                state.put("lastClientDiagnostic",diagnostic);state.put("lastClientStage",text("clientStage"));persist();
            }
            Files.createDirectories(evidence.getParent());Files.write(evidence.resolveSibling((crm?"crm-":"flutter-")+System.currentTimeMillis()+".log"),safe,StandardCharsets.UTF_8);
            check(process.exitValue()==0&&safe.stream().anyMatch(line->line.contains("RESULT=PASS")),"CLIENT_FAILED_OR_NO_PASS_MARKER");var recorded=new ArrayList<Object>(state.get("clientResults") instanceof List<?> l?l:List.of());recorded.add(Map.of("stage",text("clientStage"),"channel",crm?"REAL_CRM_BROWSER_HTTP":"REAL_FLUTTER_RENDERER_REPOSITORY_HTTP","markers",safe,"at",Instant.now().toString()));state.put("clientResults",recorded);persist();
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
    private void cleanupAll()throws Exception{
        state.put("cleanupStarted",true);persist();
        for(int index=0;index<2;index++){
            selectParticipant(index);String suffix="cleanupParticipant"+index;if(Boolean.TRUE.equals(state.get(suffix)))continue;
            var current=call("/drivers/"+text("driverId")+"/qualification","GET",null,adminToken,200);
            for(var period:current.path("periods"))if("AUTHORIZED".equals(period.path("status").asText()))current=call("/drivers/"+text("driverId")+"/qualification/periods/"+period.path("id").asText()+"/revoke","POST",Map.of("expectedVersion",current.path("version").asLong(),"operationKey",fixtureKey("closure-period"),"reason","SYNTHETIC connected fixture closure"),adminToken,200);
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
                UUID account=UUID.fromString(text("accountId")),driver=UUID.fromString(text("driverId"));var user=app.getBean(AppUserRepository.class).findById(account).orElseThrow();check(user.getEmail().equals(email())&&user.getRole()==UserRole.CONDUCTOR&&ORG.equals(user.getOrganizationId()),"EXACT_FIXTURE_REQUIRED");
                check(jdbc.queryForObject("select count(*) from service_execution_segment where driver_id=? and ended_at is null",Integer.class,driver)==0,"NO_OPEN_SEGMENT_BEFORE_CLOSURE");
                app.getBean(DriverRepository.class).findById(driver).orElseThrow().deactivate();user.deactivate();app.getBean(VehicleRepository.class).findById(UUID.fromString(text("vehicleId"))).orElseThrow().deactivate();app.getBean(TransportGroupRepository.class).findById(UUID.fromString(text("groupId"))).orElseThrow().deactivate();
                app.getBean(RefreshTokenRepository.class).revokeActiveByUserId(account,Instant.now());app.getBean(pe.rutafija.audit.application.AuditService.class).recordSystem(user.getOrganization(),"L05_SYNTHETIC_FIXTURE_DISABLED","APP_USER",account,Map.of("caseRunId",RUN,"syntheticOnly",true));
            });
            check(Boolean.TRUE.equals(jdbc.queryForObject("select not active from app_user where id=?",Boolean.class,UUID.fromString(text("accountId")))),"FIXTURE_ACCOUNT_DISABLED_REQUIRED");
            check(!app.getBean(pe.rutafija.onboarding.infrastructure.ConductorOnboardingAccessRepository.class).findCredential(email()).orElseThrow().accountAllowed(),"LINKED_PRELIMINARY_ACCESS_DISABLED_REQUIRED");
            state.put(suffix,true);persist();
        }
        if(state.containsKey("browserRefreshFamilyId"))new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->app.getBean(RefreshTokenRepository.class).revokeFamily(UUID.fromString(text("browserRefreshFamilyId")),Instant.now()));
        var route=call("/routes/"+text("routeId"),"GET",null,adminToken,200);if(route.path("active").asBoolean())call("/routes/"+text("routeId")+"/status","POST",Map.of("expectedVersion",route.path("version").asLong(),"operationKey",RUN+":retire-route","reason","SYNTHETIC_CASE_CLOSED","active",false),adminToken,200);
        var type=call("/service-types/"+text("serviceTypeId"),"GET",null,adminToken,200);if(type.path("active").asBoolean())call("/service-types/"+text("serviceTypeId")+"/status","POST",Map.of("expectedVersion",type.path("version").asLong(),"operationKey",RUN+":retire-type","reason","SYNTHETIC_CASE_CLOSED","active",false),adminToken,200);
        check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",4)),"TWO_CREATIONS_LEDGER_PRESERVED_REQUIRED");check(jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class).equals(state.get("termsAcceptancesBefore")),"ACCEPTANCES_MUST_BE_PRESERVED");
        JsonNode antecedents=JSON.valueToTree(state.get("antecedentRowHashes"));
        check(JSON.valueToTree(antecedentHashes(jdbc,antecedents)).equals(antecedents),"ANTECEDENT_ROWS_MUST_BE_PRESERVED");
        state.put("antecedentPreservation","PASS_ORIGINAL_LEDGER_ACCEPTANCES_SERVICES_OFFERS_UNCHANGED");state.put("ledgerAfter",ledger(jdbc));state.put("selectiveCleanup","PASS_TWO_ACCOUNTS_DISABLED_SESSIONS_REVOKED_LEDGER_HISTORY_PRESERVED");state.put("globalCleanup",false);persist();
    }

    private void serve(HttpExchange exchange){try{
        String path=exchange.getRequestURI().getPath();
        if(path.startsWith("/__l05/")){
            if(!control.equals(exchange.getRequestHeaders().getFirst("X-L05-Control"))){reply(exchange,403,new byte[0],"text/plain");return;}
            reply(exchange,200,JSON.writeValueAsBytes(bridgeState()),"application/json");
        }else if(path.equals("/config/runtime-config.json"))reply(exchange,200,JSON.writeValueAsBytes(Map.of("apiBaseUrl","/api/v1","environment","test","appName","SYNTHETIC Ruta Fija L05")),"application/json");
        else if(path.startsWith("/api/")){
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+exchange.getRequestURI())).timeout(Duration.ofSeconds(30));
            exchange.getRequestHeaders().forEach((n,v)->{if(!Set.of("host","connection","content-length","upgrade").contains(n.toLowerCase(Locale.ROOT)))v.forEach(value->request.header(n,value));});
            var response=client.send(request.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes())).build(),HttpResponse.BodyHandlers.ofByteArray());
            if(path.equals("/api/v1/assignments")&&response.statusCode()==201&&state.containsKey("pendingUiCreateLabel")){var created=JSON.readTree(response.body());state.put(text("pendingUiCreateLabel")+"UiOfferId",created.path("id").asText());persist();}
            response.headers().map().forEach((n,v)->{if(!Set.of("connection","content-length","transfer-encoding").contains(n))exchange.getResponseHeaders().put(n,v);});
            httpEvents.add(Map.of("path",path,"method",exchange.getRequestMethod(),"status",response.statusCode(),"observedAt",Instant.now().toString(),"channel","client-real-http"));
            boolean lost=simulateLostResponse(path,exchange.getRequestMethod(),response.statusCode());
            if(lost)reply(exchange,504,JSON.writeValueAsBytes(Map.of("code","SYNTHETIC_LOST_RESPONSE","message","SYNTHETIC test transport")),"application/json");
            else reply(exchange,response.statusCode(),response.body(),response.headers().firstValue("content-type").orElse("application/json"));
        }else{var root=ROOT.resolve("frontend/dist/ruta-fija-crm/browser");var file=root.resolve(path.substring(1)).normalize();if(!file.startsWith(root)){reply(exchange,404,new byte[0],"text/plain");return;}if(!Files.isRegularFile(file))file=root.resolve("index.html");reply(exchange,200,Files.readAllBytes(file),file.toString().endsWith(".js")?"application/javascript":file.toString().endsWith(".css")?"text/css":"text/html");}
    }catch(Exception failure){try{reply(exchange,500,new byte[0],"text/plain");}catch(Exception ignored){}}finally{exchange.close();}}
    private static void reply(HttpExchange e,int status,byte[] bytes,String type)throws Exception{e.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");e.getResponseHeaders().set("Cache-Control","no-store");e.sendResponseHeaders(status,bytes.length==0?-1:bytes.length);if(bytes.length>0)e.getResponseBody().write(bytes);}
    private Map<String,Integer> ledger(JdbcTemplate db){var out=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))out.put(role,db.queryForObject("select count(*) from pilot_account_creation where role=?",Integer.class,role));return out;}
    private Map<String,Object> antecedentHashes(JdbcTemplate db,JsonNode baseline)throws Exception{
        var result=new LinkedHashMap<String,Object>();
        for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment")){
            String key=table.equals("pilot_account_creation")?"user_id":"id";var rows=new TreeMap<String,String>();
            for(var row:db.queryForList("select "+key+"::text row_id,to_jsonb(t)::text row_data from "+table+" t order by "+key)){
                String id=row.get("row_id").toString();if(baseline!=null&&!baseline.path(table).has(id))continue;
                rows.put(id,HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(row.get("row_data").toString().getBytes(StandardCharsets.UTF_8))));
            }result.put(table,rows);
        }return result;
    }
    private String text(String key){return Objects.toString(state.get(key),"");}private String email(){return text("email");}
    private synchronized void savePrivate()throws Exception{
        syncParticipant();
        Path temp=PRIVATE.resolveSibling("l05-connected-"+UUID.randomUUID()+".private");
        try{ManualL01HumanTrialServer.writeRestricted(temp,JSON.writeValueAsBytes(secrets));Files.move(temp,PRIVATE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private synchronized void persist()throws Exception{
        syncParticipant();state.put("phase",phase);state.put("observedAt",Instant.now().toString());state.put("httpEvents",httpEvents);Files.write(STATE,JSON.writeValueAsBytes(state));
        if(evidence!=null){Files.createDirectories(evidence.getParent());var safe=JSON.convertValue(state,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});safe.remove("dni");safe.remove("email");
            if(safe.get("participants") instanceof List<?> people)for(Object p:people)if(p instanceof Map<?,?> m){m.remove("dni");m.remove("email");}
            byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(safe);Files.write(evidence,bytes);Files.write(evidence.resolveSibling("observacion-"+System.currentTimeMillis()+"-"+Long.toUnsignedString(System.nanoTime())+".json"),bytes,StandardOpenOption.CREATE_NEW);
        }
    }
    @FunctionalInterface private interface Step {void run()throws Exception;}
    private void step(String id,Step action)throws Exception{phase=id;if(Boolean.TRUE.equals(state.get(id)))return;action.run();state.put(id,true);persist();}
    private JsonNode currentOffer()throws Exception{return call("/assignments/"+text("currentOfferId"),"GET",null,adminToken,200);}
    private JsonNode currentService()throws Exception{return call("/scheduled-services/"+text("serviceId"),"GET",null,adminToken,200);}
    private void bindOffer(JsonNode offer)throws Exception{state.put("currentOfferId",offer.path("id").asText());state.put("serviceId",offer.path("serviceId").asText());var services=new ArrayList<Object>(state.get("caseServices") instanceof List<?> l?l:List.of());if(!services.contains(text("serviceId")))services.add(text("serviceId"));check(services.size()<=4,"MAX_FOUR_CASE_SERVICES");state.put("caseServices",services);persist();}
    private void flutterStage(String stage)throws Exception{state.put("clientStage",stage);savePrivate();persist();runClient(false);}
    private void crmStage(String stage)throws Exception{state.put("clientStage",stage);prepareBrowserSession();persist();runClient(true);}
    private void createViaBrowser(String label)throws Exception{
        state.put("pendingUiCreateLabel",label);persist();
        if(state.containsKey(label+"UiOfferId")){state.put("currentOfferId",state.get(label+"UiOfferId"));bindOffer(currentOffer());crmStage("READ");}
        else {crmStage("CREATE");captureOffers();bindOffer(currentOffer());}
        state.remove("pendingUiCreateLabel");persist();
    }
    private void connectedWorkflow(Map<String,Object> config)throws Exception{
        step("CRM_INITIAL_OFFER",()->{selectParticipant(0);createViaBrowser("first");state.put("firstServiceId",text("serviceId"));state.put("firstOutgoingOfferId",text("currentOfferId"));});
        step("FLUTTER_FIRST_ACCEPT_START",()->{selectParticipant(0);state.put("currentOfferId",state.get("firstOutgoingOfferId"));state.put("serviceId",state.get("firstServiceId"));flutterStage("ACCEPT_START");check("EN_SERVICIO".equals(currentOffer().path("status").asText()),"FIRST_SERVICE_STARTED_REQUIRED");});
        step("CRM_HANDOVER_PREPARE_UNKNOWN",()->{selectParticipant(0);state.put("serviceId",state.get("firstServiceId"));state.put("currentOfferId",state.get("firstOutgoingOfferId"));var prior=call("/scheduled-services/"+text("serviceId")+"/handovers","GET",null,adminToken,200);if(prior.path("handovers").isEmpty()){state.put("losePrepareOnce",true);crmStage("PREPARE");}else{check(Boolean.TRUE.equals(state.get("prepareLostDelivered")),"INSPECT_PRIOR_PREPARATION_WITHOUT_REPEATING");crmStage("READ");}var view=call("/scheduled-services/"+text("serviceId")+"/handovers","GET",null,adminToken,200);check(view.path("handovers").size()==1,"ONE_PREPARED_HANDOVER");var h=view.path("handovers").get(0);state.put("handoverId",h.path("id").asText());state.put("firstIncomingOfferId",h.path("incomingAssignmentId").asText());check(Boolean.TRUE.equals(state.get("prepareLostDelivered")),"PREPARATION_TRANSPORT_FAULT_EXERCISED");});
        step("FLUTTER_INCOMING_ACCEPT_ONLY",()->{selectParticipant(1);state.put("serviceId",state.get("firstServiceId"));state.put("currentOfferId",state.get("firstIncomingOfferId"));flutterStage("ACCEPT_ONLY");check("SCHEDULED".equals(currentOffer().path("status").asText()),"ACCEPTANCE_MUST_NOT_START");check(currentService().path("service").path("currentOfferId").asText().equals(text("firstOutgoingOfferId")),"OUTGOING_EXECUTION_MUST_REMAIN");});
        step("FLUTTER_ASSUME_UNKNOWN",()->{selectParticipant(1);state.put("currentOfferId",state.get("firstIncomingOfferId"));state.put("serviceId",state.get("firstServiceId"));state.put("loseAssumeOnce",true);flutterStage("ASSUME");check(Boolean.TRUE.equals(state.get("assumeLostDelivered")),"ASSUMPTION_TRANSPORT_FAULT_EXERCISED");var header=currentService().path("service");check("IN_SERVICE".equals(header.path("status").asText())&&header.path("currentOfferId").asText().equals(text("firstIncomingOfferId")),"EFFECTIVE_HANDOVER_REQUIRED");check(jdbc.queryForObject("select count(*) from service_execution_segment where service_id=?",Integer.class,UUID.fromString(text("serviceId")))==2,"TWO_ORIGINAL_SEGMENTS_REQUIRED");});
        step("FLUTTER_POINT_COMPLETE",()->{selectParticipant(1);state.put("currentOfferId",state.get("firstIncomingOfferId"));state.put("serviceId",state.get("firstServiceId"));flutterStage("POINT_COMPLETE");check("COMPLETED".equals(currentService().path("service").path("status").asText()),"FIRST_COMPLETED_REQUIRED");state.put("firstExecution",executionFacts(text("serviceId")));});
        step("CRM_INTERRUPTION_OFFER",()->{selectParticipant(0);createViaBrowser("interruption");state.put("interruptionServiceId",text("serviceId"));state.put("interruptionOfferId",text("currentOfferId"));});
        step("FLUTTER_INTERRUPT",()->{selectParticipant(0);state.put("serviceId",state.get("interruptionServiceId"));state.put("currentOfferId",state.get("interruptionOfferId"));if(!"INTERRUPTED".equals(currentOffer().path("status").asText()))flutterStage("ACCEPT_START");flutterStage("INTERRUPT");check("INTERRUPTED".equals(currentOffer().path("status").asText()),"INTERRUPTION_REQUIRED");});
        step("CRM_SEPARATE_SAFETY_REVIEW",()->{selectParticipant(0);state.put("serviceId",state.get("interruptionServiceId"));state.put("currentOfferId",state.get("interruptionOfferId"));crmStage("RESOLVE");var rows=call("/scheduled-services/"+text("serviceId")+"/attention","GET",null,adminToken,200);check(rows.size()==1&&!rows.get(0).path("driverPending").asBoolean()&&!rows.get(0).path("vehiclePending").asBoolean(),"BOTH_EXPLICIT_RESOURCE_REVIEWS_REQUIRED");});
        step("CRM_CANCEL_INTERRUPTED_CONTINUITY",()->{selectParticipant(0);state.put("serviceId",state.get("interruptionServiceId"));state.put("currentOfferId",state.get("interruptionOfferId"));crmStage("CANCEL");check("CANCELLED".equals(currentService().path("service").path("status").asText()),"INTERRUPTED_CONTINUITY_CANCELLED_REQUIRED");});
        step("BUSINESS_RACE_PREPARE",()->{selectParticipant(0);createTechnicalService("race");proveRestrictedSessionRejection();flutterStage("ACCEPT_START");prepareTechnicalHandover();});
        step("BUSINESS_RACE_ASSUME_WITHDRAW",()->raceAssumeWithdraw());
        step("BUSINESS_RACE_SAFE_TERMINATION",()->{
            var header=currentService().path("service");String executing=header.path("currentOfferId").asText();selectParticipant(executing.equals(text("raceIncomingOfferId"))?1:0);state.put("currentOfferId",executing);flutterStage("INTERRUPT");resolveAllAttention();cancelCurrentService("SYNTHETIC_RACE_CASE_FINISHED");state.put("raceExecution",executionFacts(text("serviceId")));
        });
        step("RETURN_PREPARE_STARTED_SERVICE",()->{selectParticipant(0);createTechnicalService("return");flutterStage("ACCEPT_START");state.put("returnServiceId",text("serviceId"));state.put("returnOfferId",text("currentOfferId"));});
        step("RETURN_COMPATIBLE_DISABLED_OPERATIONS",()->returnExecutionCheck(config));
        step("CONNECTED_POSTCONDITIONS",()->{
            check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",4)),"EXACT_TWO_CREATIONS_REQUIRED");
            for(Object id:(List<?>)state.get("caseServices")){var s=call("/scheduled-services/"+id,"GET",null,adminToken,200).path("service");check(Set.of("COMPLETED","CANCELLED").contains(s.path("status").asText()),"ALL_CASE_SERVICES_TERMINAL_REQUIRED");check(jdbc.queryForObject("select count(*) from service_execution_segment where service_id=? and ended_at is null",Integer.class,UUID.fromString(id.toString()))==0,"NO_OPEN_CASE_SEGMENTS");}
            state.put("gpsSource","SYNTHETIC_SENSOR_AND_PERMISSION_DOUBLE_NOT_PHYSICAL");state.put("frontendBoundary","REAL_EDGE_CRM_UI_HTTP");state.put("mobileBoundary","PRODUCTION_FLUTTER_REPOSITORIES_AND_WIDGETS_REAL_HTTP_TEST_RENDERER");
        });
    }
    private List<Map<String,Object>> executionFacts(String service){return jdbc.queryForList("select id,assignment_id,driver_id,vehicle_id,ordinal,started_at,ended_at,end_kind,started_by,ended_by from service_execution_segment where service_id=? order by ordinal",UUID.fromString(service));}
    private void createTechnicalService(String label)throws Exception{
        String key=RUN+":"+label+":offer";var prior=jdbc.queryForList("select id from assignment where organization_id=? and idempotency_key=?",UUID.class,ORG,key);
        JsonNode offer;if(prior.size()==1)offer=call("/assignments/"+prior.getFirst(),"GET",null,adminToken,200);else{
            check(prior.isEmpty(),"EXACT_TECHNICAL_OFFER_KEY_REQUIRED");state.put("creationKey",key);persist();
            offer=call("/assignments","POST",Map.of("driverId",text("driverId"),"vehicleId",text("vehicleId"),"originText","SYNTHETIC origin","destinationText","SYNTHETIC destination","scheduledAt",Instant.now().plusSeconds(14400).toString(),"scheduledEndAt",Instant.now().plusSeconds(18000).toString(),"routeVersionId",text("routeVersionId"),"serviceTypeVersionId",text("serviceTypeVersionId")),adminToken,201);
        }bindOffer(offer);state.put(label+"ServiceId",text("serviceId"));state.put(label+"OutgoingOfferId",text("currentOfferId"));state.remove("creationKey");persist();
    }
    private void prepareTechnicalHandover()throws Exception{
        var next=people().get(1);var header=currentService().path("service");String op=state.containsKey("racePrepareKey")?text("racePrepareKey"):UUID.randomUUID().toString();state.put("racePrepareKey",op);persist();
        var view=call("/scheduled-services/"+text("serviceId")+"/handovers","GET",null,adminToken,200);JsonNode selected=null;for(var h:view.path("handovers"))if(op.equals(h.path("operationKey").asText()))selected=h;
        if(selected==null){view=call("/scheduled-services/"+text("serviceId")+"/handovers","POST",Map.of("operationKey",op,"expectedVersion",header.path("version").asLong(),"driverId",next.get("driverId"),"vehicleId",next.get("vehicleId"),"scheduledAt",Instant.now().plusSeconds(7200).toString(),"scheduledEndAt",Instant.now().plusSeconds(10800).toString(),"reason","SYNTHETIC concurrent handover","pointReference","SYNTHETIC_RACE_TRANSFER","safetyAndTransferConfirmed",true),adminToken,200);for(var h:view.path("handovers"))if(op.equals(h.path("operationKey").asText()))selected=h;}
        check(selected!=null,"RACE_HANDOVER_REQUIRED");state.put("raceHandoverId",selected.path("id").asText());state.put("raceIncomingOfferId",selected.path("incomingAssignmentId").asText());state.put("raceOutgoingOfferId",state.get("currentOfferId"));
        selectParticipant(1);state.put("currentOfferId",state.get("raceIncomingOfferId"));var i=currentOffer();if("PENDING_RESPONSE".equals(i.path("status").asText()))call("/mobile/assignments/"+text("currentOfferId")+"/accept","POST",command(i,false),textSecret("mobileToken"),200);
        call("/mobile/location/consent","PUT",Map.of("consent",true),textSecret("mobileToken"),200);persist();
    }
    private Map<String,Object> command(JsonNode offer,boolean gps){var c=new LinkedHashMap<String,Object>(Map.of("clientEventId",UUID.randomUUID(),"version",offer.path("version").asLong(),"occurredAt",Instant.now().toString()));if(gps)c.put("gps",Map.of("permissionGranted",true,"locationServiceEnabled",true,"foreground",true));return c;}
    private void proveRestrictedSessionRejection()throws Exception{
        if(Boolean.TRUE.equals(state.get("restrictedSessionRejectionProved")))return;
        String restricted=textSecret("p0:restrictedMobileToken");check(!restricted.isBlank(),"PRESERVED_RESTRICTED_SESSION_REQUIRED");
        var before=currentOffer();check("PENDING_RESPONSE".equals(before.path("status").asText()),"PENDING_OFFER_BEFORE_REJECTION_REQUIRED");
        var rejected=call("/mobile/assignments/"+text("currentOfferId")+"/accept","POST",command(before,false),restricted,403);
        check("DRIVER_INACTIVE".equals(rejected.path("code").asText()),"SPECIFIC_RESTRICTED_SCOPE_REJECTION_REQUIRED");
        var after=currentOffer();check(before.path("version").equals(after.path("version"))&&before.path("status").equals(after.path("status")),"REJECTION_MUST_NOT_CHANGE_OFFER");
        state.put("restrictedSessionRejectionProved",true);state.put("restrictedSessionRejection","PASS_HTTP403_DRIVER_INACTIVE_NO_OFFER_CHANGE_NORMAL_LOGIN_RENEWAL");persist();
    }
    private HttpRequest raceRequest(String path,Map<String,Object> body,String token)throws Exception{return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/api/v1"+path)).timeout(Duration.ofSeconds(45)).header("Origin",base).header("Content-Type","application/json").header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build();}
    private void raceAssumeWithdraw()throws Exception{
        state.put("serviceId",state.get("raceServiceId"));state.put("currentOfferId",state.get("raceIncomingOfferId"));selectParticipant(1);
        var prior=call("/scheduled-services/"+text("serviceId")+"/handovers","GET",null,adminToken,200);var h=prior.path("handovers").get(prior.path("handovers").size()-1);
        if(Set.of("ASSUMED","WITHDRAWN").contains(h.path("state").asText())){check(state.containsKey("raceHttpResults"),"DO_NOT_RECONSTRUCT_MISSING_RACE_RESPONSES");return;}
        var incoming=currentOffer();var assume=command(incoming,true);assume.put("readyConfirmed",true);var withdraw=Map.<String,Object>of("operationKey",UUID.randomUUID(),"expectedVersion",prior.path("service").path("version").asLong(),"reason","SYNTHETIC concurrent withdrawal");
        state.put("raceAssumeEventId",assume.get("clientEventId").toString());state.put("raceWithdrawKey",withdraw.get("operationKey").toString());persist();Instant started=Instant.now();
        var a=client.sendAsync(raceRequest("/mobile/assignments/"+text("raceIncomingOfferId")+"/assume-handover",assume,textSecret("mobileToken")),HttpResponse.BodyHandlers.ofByteArray());
        var b=client.sendAsync(raceRequest("/service-handovers/"+text("raceHandoverId")+"/withdraw",withdraw,adminToken),HttpResponse.BodyHandlers.ofByteArray());var ar=a.get(50,TimeUnit.SECONDS);var br=b.get(50,TimeUnit.SECONDS);
        var results=List.of(Map.of("action","ASSUME","status",ar.statusCode(),"finishedAt",Instant.now().toString()),Map.of("action","WITHDRAW","status",br.statusCode(),"finishedAt",Instant.now().toString()));state.put("raceStartedAt",started.toString());state.put("raceHttpResults",results);persist();
        check((ar.statusCode()==200&&br.statusCode()==409)||(br.statusCode()==200&&ar.statusCode()==409),"EXACT_ONE_WINNER_AND_STATE_CONFLICT_REQUIRED");var loser=JSON.readTree(ar.statusCode()==409?ar.body():br.body()).path("code").asText();check(Set.of("RESOURCE_CONFLICT","RESOURCE_VERSION_CONFLICT","ASSIGNMENT_INVALID_TRANSITION").contains(loser),"RACE_REJECTION_MUST_BE_BUSINESS_STATE");state.put("raceLoserCode",loser);
        var after=call("/scheduled-services/"+text("serviceId")+"/handovers","GET",null,adminToken,200);check(jdbc.queryForObject("select count(*) from service_handover_resolution where handover_id=?",Integer.class,UUID.fromString(text("raceHandoverId")))==1,"ONE_IMMUTABLE_RACE_RESOLUTION");state.put("raceResult","PASS_TWO_REAL_CONCURRENT_HTTP_REQUESTS_ONE_RESOLUTION");state.put("raceWinner",after.path("handovers").get(after.path("handovers").size()-1).path("state").asText());persist();
    }
    private void resolveAllAttention()throws Exception{
        for(var row:call("/scheduled-services/"+text("serviceId")+"/attention","GET",null,adminToken,200))if(row.path("driverPending").asBoolean()||row.path("vehiclePending").asBoolean())call("/service-attention/"+row.path("id").asText()+"/resolve","POST",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",row.path("version").asLong(),"releaseDriver",row.path("driverPending").asBoolean(),"vehicleDecision",row.path("vehiclePending").asBoolean()?"READY":"NO_CHANGE","safetyAndTransferConfirmed",true,"reason","SYNTHETIC technical safe resolution","verificationReference","SYNTHETIC_CASE_REVIEW"),adminToken,200);
    }
    private void cancelCurrentService(String reason)throws Exception{var s=currentService().path("service");if(!"CANCELLED".equals(s.path("status").asText()))call("/scheduled-services/"+text("serviceId")+"/cancel","POST",Map.of("operationKey",RUN+":cancel:"+text("serviceId"),"expectedVersion",s.path("version").asLong(),"reason",reason),adminToken,200);}
    private void returnExecutionCheck(Map<String,Object> config)throws Exception{
        state.put("serviceId",state.get("returnServiceId"));state.put("currentOfferId",state.get("returnOfferId"));selectParticipant(0);var before=currentService();check("IN_SERVICE".equals(before.path("service").path("status").asText()),"REAL_ONGOING_SERVICE_BEFORE_RETURN_REQUIRED");
        var properties=new LinkedHashMap<String,Object>(config);properties.put("app.qualification.operations-enabled",false);properties.put("server.port",0);var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("L05SameBinaryReturnTest",properties));var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
        OtpEmailSender refuse=(r,c,e)->{throw new IllegalStateException("NO_MAIL_IN_RETURN");};boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{var registry=(BeanDefinitionRegistry)factory;if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",refuse);}));
        try(var returned=boot.run()){int original=backendPort;backendPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)returned).getWebServer().getPort();try{
            var other=people().get(1);var q=call("/drivers/"+other.get("driverId")+"/qualification","GET",null,adminToken,200);check(!q.path("operationalEnabled").asBoolean()&&q.path("blockers").toString().contains("OPERATIONAL_FEATURE_DISABLED"),"EXPLICIT_DISABLED_FEATURE_BLOCKER_REQUIRED");
            var rejected=call("/assignments","POST",Map.of("driverId",other.get("driverId"),"vehicleId",other.get("vehicleId"),"routeVersionId",text("routeVersionId"),"serviceTypeVersionId",text("serviceTypeVersionId"),"originText","SYNTHETIC origin","destinationText","SYNTHETIC destination","scheduledAt",Instant.now().plusSeconds(20000).toString(),"scheduledEndAt",Instant.now().plusSeconds(24000).toString()),adminToken,409);check(rejected.path("message").asText().contains("OPERATIONAL_FEATURE_DISABLED"),"SPECIFIC_HTTP_FEATURE_REJECTION_REQUIRED");
            var own=call("/mobile/assignments/"+text("currentOfferId"),"GET",null,textSecret("mobileToken"),200);call("/mobile/assignments/"+text("currentOfferId")+"/complete","POST",command(own,false),textSecret("mobileToken"),200);check("COMPLETED".equals(currentService().path("service").path("status").asText()),"SAFE_OWN_COMPLETION_UNDER_RETURN_REQUIRED");
            state.put("returnResult","PASS_SAME_BINARY_DISABLED_NEW_OPERATIONS_OWN_CONSULTATION_AND_SAFE_COMPLETION");state.put("returnExecution",executionFacts(text("serviceId")));persist();
        }finally{backendPort=original;}}
    }
    private Map<String,Object> bridgeState(){var v=new LinkedHashMap<String,Object>();for(String k:List.of("runId","driverId","vehicleId","currentOfferId","serviceId","clientStage","serviceTypeVersionId","routeVersionId"))v.put(k,text(k));v.put("browserRefresh",secrets.get("browserRefresh"));v.put("replacementDriverId",people().get(1).get("driverId"));v.put("replacementVehicleId",people().get(1).get("vehicleId"));return v;}
    private synchronized boolean simulateLostResponse(String path,String method,int status){
        boolean prepare="POST".equals(method)&&path.matches("/api/v1/scheduled-services/[^/]+/handovers")&&Boolean.TRUE.equals(state.get("losePrepareOnce"));boolean assume="POST".equals(method)&&path.endsWith("/assume-handover")&&Boolean.TRUE.equals(state.get("loseAssumeOnce"));
        if(status==200&&(prepare||assume)){state.put(prepare?"losePrepareOnce":"loseAssumeOnce",false);state.put(prepare?"prepareLostDelivered":"assumeLostDelivered",true);httpEvents.add(Map.of("path",path,"method",method,"backendStatus",200,"clientStatus",504,"transportFault","SYNTHETIC_RESPONSE_LOSS_AFTER_SERVER_COMMIT","at",Instant.now().toString()));try{persist();}catch(Exception failure){throw new IllegalStateException("TRANSPORT_PROOF_PERSISTENCE_FAILED");}return true;}return false;
    }

    private static void check(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
}
