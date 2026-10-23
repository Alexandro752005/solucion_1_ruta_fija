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


/** L06-CON-01: opt-in synthetic connected case, one new CONDUCTOR maximum.
 * Reuses guarded onboarding, transport and selective retirement from L06 facilities.
 * Never reopens that case or uses a physical device/real mail. */
public final class L06ConnectedTrial {
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize();
    private static final Path STATE=ROOT.resolve("backend/.local/l06-connected-state.json"),PRIVATE=ROOT.resolve("backend/.local/l06-connected.private"),MARKER=ROOT.resolve("backend/.local/l06-connected.active");
    private static final UUID ADMIN=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979"),ORG=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
    private static final String RUN="51e69ee6-e4a0-4c3f-9a35-f19de0072422";
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String,Object> state=new LinkedHashMap<>(),secrets=new ConcurrentHashMap<>();
    private final List<Map<String,Object>> httpEvents=new CopyOnWriteArrayList<>();
    private final Map<String,String> mail=new ConcurrentHashMap<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private ConfigurableApplicationContext app;private JdbcTemplate jdbc;private HttpServer frontend;
    private int backendPort;private String base,control,adminToken;private Path evidence;private String phase="INITIALIZE";
    public static void main(String[] args){
        if(args.length!=1||!"--run-connected".equals(args[0]))throw new IllegalArgumentException("EXPLICIT_TEST_MODE_REQUIRED");
        var runner=new L06ConnectedTrial();boolean failed=false;
        try{runner.run();}catch(Exception failure){
            try{runner.state.put("result","PENDING_KEEP_CASE");runner.state.put("failedPhase",runner.phase);if(runner.evidence!=null)runner.persist();}catch(Exception ignored){}
            String code=Objects.toString(failure.getMessage(),"");if(!code.matches("[A-Z][A-Z0-9_]{0,100}"))code="DETAILS_REDACTED";
            System.out.println("CONNECTED_L06_RESULT=PENDING_KEEP_CASE; PHASE="+runner.phase+"; CAUSE="+failure.getClass().getSimpleName()+"; CHECK="+code);failed=true;
        }finally{if(runner.frontend!=null)runner.frontend.stop(0);if(runner.app!=null)runner.app.close();runner.mail.clear();runner.secrets.clear();}
        if(failed)System.exit(1);
    }
    private void run()throws Exception{
        var config=ManualL01HumanTrial.configuration();phase="TEST_TARGET_PREFLIGHT";
        try(Connection guard=ManualL01HumanTrial.connect(config)){
            guard.setAutoCommit(true);JdbcTemplate inspect=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(guard,true));
            check("ruta_fija_test".equals(inspect.queryForObject("select current_database()",String.class)),"TEST_DATABASE_REQUIRED");
            check("rf_test".equals(inspect.queryForObject("select current_user",String.class)),"TEST_ROLE_REQUIRED");
            check("44".equals(inspect.queryForObject("select version from flyway_schema_history where success order by installed_rank desc limit 1",String.class)),"V44_REQUIRED");
            check(inspect.queryForObject("select count(*) from flyway_schema_history where not success",Integer.class)==0,"SUCCESSFUL_SCHEMA_REQUIRED");
            // Own marker is mandatory on resume and is verified together with state/private below.
            for(String marker:List.of("l01-human-trial.active","l02-connected.active","l03-connected.active","l04-connected.active","l05-connected.active"))check(!Files.exists(ROOT.resolve("backend/.local/"+marker)),"OTHER_CASE_ACTIVE");
            var approval=JSON.readTree(Files.readAllBytes(ROOT.resolve("specs/decisiones-L06-2026-10-20.json"))).path("connectedCaseAuthorization");
            check(RUN.equals(approval.path("runId").asText())&&approval.path("maxNewConductorAccounts").asInt()==1&&"CONFIRMADA_USUARIO".equals(approval.path("status").asText()),"EXACT_L06_CON01_AUTHORIZATION_REQUIRED");
            check("docs/operacion/L-06-caso-conectado-propuesta.md".equals(approval.path("proposal").asText())&&approval.path("proposalSha256").asText().equals(HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(ROOT.resolve(approval.path("proposal").asText()))))),"EXACT_AUTHORIZED_PROPOSAL_HASH_REQUIRED");
            check(Files.exists(ROOT.resolve("docs/operacion/L-06-caso-conectado-autorizacion.md")),"EXPLICIT_L06_AUTHORIZATION_REQUIRED");
            check(Boolean.TRUE.equals(inspect.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)),"CLEANUP_LOCK_REQUIRED");
            initialize(inspect);L06ConnectedGuardProbe.main(new String[0]);state.put("rollbackFixtureGuard","PASS_REFUSED_BEFORE_FIXTURE");persist();
            frontend=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);base="http://127.0.0.1:"+frontend.getAddress().getPort();control=UUID.randomUUID().toString();
            phase="BOOT_TEST_MAIL_DOUBLE";
            String key=Files.readString(ROOT.resolve("backend/.local/l01-human-trial-jwt.private"),StandardCharsets.US_ASCII).strip();
            config.putAll(ManualL01HumanTrialServer.runtimeProperties(key));config.put("server.port",0);config.put("spring.flyway.enabled",false);
            config.put("app.security.cors.allowed-origins",base);config.put("app.security.refresh-cookie.secure",false);
            config.put("app.qualification.maintenance.enabled",false);config.put("app.mobile.maintenance-enabled",false);
            var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("validatedL06ConnectedTest",config));
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

            if(!Boolean.TRUE.equals(state.get("cleanupStarted"))){
                provision();qualify();state.put("qualifiedOnce",true);savePrivate();persist();
                if(!state.containsKey("secondaryGroupId"))state.put("secondaryGroupId",call("/groups","POST",Map.of("name","SYNTHETIC L06 secondary "+RUN,"description","Synthetic snapshot and withdrawal case"),adminToken,201).path("id").asText());persist();
                step("SECONDARY_MEMBERSHIP",()->membership(true));connectedWorkflow(config);
            }
            phase="SELECTIVE_FIXTURE_CLOSURE";cleanup();
            state.put("result","PASS_REAL_CRM_FLUTTER_HTTP_POSTGRES_SIMULATED_MAIL");state.put("externalSmtpAttempts",0);state.put("externalPushAttempts",0);persist();
            Files.delete(PRIVATE);state.put("privateFixtureSecretsRemoved",true);persist();Files.delete(MARKER);state.put("TEST_CLEANUP_RELEASED",true);persist();
            inspect.queryForObject("select pg_advisory_unlock(7518440514)",Boolean.class);
            System.out.println("CONNECTED_L06_RUN_ID="+RUN);System.out.println("CONNECTED_L06_RESULT="+text("result"));System.out.println("CONNECTED_L06_EVIDENCE="+ROOT.relativize(evidence));
        }
    }
    private void initialize(JdbcTemplate inspect)throws Exception{
        if(Files.exists(STATE)){
            state.putAll(JSON.readValue(Files.readAllBytes(STATE),Map.class));check(RUN.equals(text("runId"))&&!Boolean.TRUE.equals(state.get("TEST_CLEANUP_RELEASED")),"SAME_OPEN_CASE_ONLY");
            check(Files.exists(MARKER)&&RUN.equals(Files.readString(MARKER).strip())&&Files.exists(PRIVATE),"SAME_CASE_PROTECTION_REQUIRED");secrets.putAll(JSON.readValue(Files.readAllBytes(PRIVATE),Map.class));
            if(state.get("httpEvents") instanceof List<?> prior)for(Object item:prior)if(item instanceof Map<?,?> m)httpEvents.add((Map<String,Object>)m);
            if(state.containsKey("failedPhase")){var attempts=new ArrayList<Object>(state.get("attemptHistory") instanceof List<?> l?l:List.of());attempts.add(Map.of("phase",state.remove("failedPhase"),"at",Objects.toString(state.get("observedAt"),"")));state.put("attemptHistory",attempts);}
            var resumedLedger=ledger(inspect);check(resumedLedger.get("SUPER_ADMIN")==1&&resumedLedger.get("ADMIN")==3&&Set.of(4,5).contains(resumedLedger.get("CONDUCTOR")),"ORIGINAL_LEDGER_AND_ONE_CREATION_LIMIT");
        }else{
            check(!Files.exists(MARKER)&&!Files.exists(PRIVATE),"INSPECT_EXISTING_PRIVATE_CASE");check(ledger(inspect).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",4)),"EXACT_PREAUTHORIZED_LEDGER_REQUIRED");
            String email="conductor.l06.51e69ee6@rutafija.test",dni="60000007";
            check(inspect.queryForObject("select count(*) from app_user where email=?",Integer.class,email)==0&&inspect.queryForObject("select count(*) from conductor_onboarding_identity where email=?",Integer.class,email)==0&&inspect.queryForObject("select count(*) from driver where document_type='DNI' and document_number=?",Integer.class,dni)==0,"SYNTHETIC_IDENTITY_COLLISION");
            state.put("runId",RUN);state.put("organizationId",ORG.toString());state.put("adminId",ADMIN.toString());state.put("email",email);state.put("dni",dni);state.put("ledgerBefore",ledger(inspect));
            state.put("termsAcceptancesBefore",inspect.queryForObject("select count(*) from admin_terms_acceptance",Integer.class));state.put("antecedentRowHashes",antecedentHashes(inspect,null));
            secrets.put("password","Synthetic-"+UUID.randomUUID());Files.writeString(MARKER,RUN,StandardOpenOption.CREATE_NEW);savePrivate();
        }
        evidence=ROOT.resolve("docs/evidencia/L-06/conectado-"+RUN+"/resultado.json");state.put("result","EN_CURSO_SAME_AUTHORIZED_CASE");persist();
    }
    private int participantIndex(){return 0;}
    private String fixtureKey(String suffix){return RUN+":"+suffix;}
    private void syncParticipant(){}
    private Set<String> allowedEmails(){return Set.of("conductor.l06.51e69ee6@rutafija.test");}
    private void provision()throws Exception{
        phase="NORMAL_ONBOARDING_HTTP";
        if(!state.containsKey("groupId"))state.put("groupId",call("/groups","POST",Map.of("name","SYNTHETIC L06 "+text("runId").substring(0,8)+" P"+participantIndex(),"description","Synthetic connected trial only"),adminToken,201).path("id").asText());persist();
        if(!state.containsKey("vehicleId"))state.put("vehicleId",call("/vehicles","POST",Map.of("plate",("SYN-L06-"+participantIndex()),"brand","SYNTHETIC","model","L06","year",2026),adminToken,201).path("id").asText());persist();
        var requests=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=?",String.class,email());
        if(!requests.isEmpty())state.put("requestId",requests.getFirst());
        if(!state.containsKey("requestId")){
            if(!secrets.containsKey("invitationCode")){var invite=call("/invitations","POST",Map.of("recipientEmail",email()),adminToken,201);secrets.put("invitationCode",invite.path("code").asText());state.put("invitationId",invite.path("id").asText());savePrivate();persist();}
            if(!secrets.containsKey("verificationToken")){
                var start=call("/onboarding/requests","POST",Map.of("email",email(),"password",secrets.get("password"),"fullName","SYNTHETIC L06 CONDUCTOR "+participantIndex(),"invitationCode",secrets.get("invitationCode")),null,202);
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
        var fields=List.of(Map.of("documentType","DNI","documentNumber",text("dni"),"fullName","SYNTHETIC L06 CONDUCTOR "+participantIndex(),"issuer","SYNTHETIC"),Map.of("licenseNumber","SYN-L06-LICENSE-"+participantIndex(),"licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"));
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
            var fields=new LinkedHashMap<String,String>();for(String key:r.fieldKeys())fields.put(key,switch(key){case "plate"->("SYN-L06-"+participantIndex());case "category"->"M1";case "bodyType"->"SEDAN";case "brand"->"SYNTHETIC";case "model"->"L06";case "year"->"2026";case "insuranceType"->"SOAT";case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"SYNTHETIC CHICLAYO";default->"SYNTHETIC reference";});
            var d=new LinkedHashMap<String,Object>();d.put("requirementId",r.id());d.put("fields",fields);d.put("expiryMode","DATED");d.put("validUntil",Instant.now().plus(Duration.ofDays(60)).toString());d.put("applicability","CONFIRMED");d.put("condition","SYNTHETIC explicit applicability");d.put("sourceReference","SYNTHETIC not a real title");d.put("result",r.id().equals("EMP-03")?"NO_APLICA_JUSTIFICADO":"CONFORME");d.put("reviewReference","SYNTHETIC visual review");docs.add(d);
        }command.put("documents",docs);return command;
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
        ProcessBuilder builder=crm?new ProcessBuilder("node",ROOT.resolve("frontend/e2e/l06-connected.mjs").toString()):new ProcessBuilder("cmd.exe","/d","/c","flutter.bat","test","--no-pub","--reporter","expanded","test/connected/l06_connected_test.dart");
        builder.directory(ROOT.resolve(crm?"frontend":"mobile").toFile());builder.redirectErrorStream(true);
        builder.environment().put("RF_L06_CONNECTED_CLIENTS","true");builder.environment().put("RF_L06_CONNECTED_BASE",base);
        builder.environment().put("RF_L06_CONNECTED_CONTROL",control);builder.environment().put("RF_L06_CONNECTED_RUN_ID",text("runId"));
        builder.environment().put("RF_L06_CONNECTED_STAGE",text("clientStage"));
        builder.environment().put("RF_L06_CONNECTED_OFFER_ID",text("currentOfferId"));
        builder.environment().put("RF_L06_CONNECTED_SERVICE_ID",text("serviceId"));
        builder.environment().put("RF_L06_CONNECTED_DRIVER_ID",text("driverId"));builder.environment().put("RF_L06_CONNECTED_TOKEN",Objects.toString(secrets.get("mobileToken"),""));
        builder.environment().put("RF_L06_CONNECTED_HANDOVER_ID",text("handoverId"));
        var process=builder.start();try(var reader=Executors.newSingleThreadExecutor()){
            var output=reader.submit(()->new String(process.getInputStream().readAllBytes(),StandardCharsets.UTF_8));
            if(!process.waitFor(240,TimeUnit.SECONDS)){process.destroyForcibly();throw new IllegalStateException("CLIENT_TIMEOUT");}
            String captured=output.get(10,TimeUnit.SECONDS);var safe=captured.lines().filter(l->(l.startsWith("CONNECTED_L06_")||l.startsWith("L06_CONNECTED_"))&&!l.contains("Bearer")&&secrets.values().stream().noneMatch(v->v.toString().length()>5&&l.contains(v.toString()))).toList();
            if(process.exitValue()!=0){
                // Diagnostic material stays private; only allowlisted markers reach evidence.
                ManualL01HumanTrialServer.writeRestricted(ROOT.resolve("backend/.local/l06-client-failure-"+text("clientStage")+"-"+System.currentTimeMillis()+".private"),captured.getBytes(StandardCharsets.UTF_8));
                String diagnostic=captured.contains("Timer is still pending")?"FLUTTER_PENDING_TIMER":captured.contains("RenderFlex overflowed")?"FLUTTER_LAYOUT_OVERFLOW":captured.contains("SYNTHETIC_WIDGET_WAIT")?"FLUTTER_WIDGET_WAIT":captured.contains("SYNTHETIC_WIDGET_FAILURE")?"FLUTTER_WIDGET_EXCEPTION":captured.contains("Expected:")?"CLIENT_ASSERTION_FAILED":captured.contains("TimeoutException")?"CLIENT_TIMEOUT":"CLIENT_FAILURE_DETAILS_REDACTED";
                state.put("lastClientDiagnostic",diagnostic);state.put("lastClientStage",text("clientStage"));persist();
            }
            Files.createDirectories(evidence.getParent());Files.write(evidence.resolveSibling((crm?"crm-":"flutter-")+System.currentTimeMillis()+".log"),safe,StandardCharsets.UTF_8);
            check(process.exitValue()==0&&safe.stream().anyMatch(line->line.contains("RESULT=PASS")),"CLIENT_FAILED_OR_NO_PASS_MARKER");var recorded=new ArrayList<Object>(state.get("clientResults") instanceof List<?> l?l:List.of());recorded.add(Map.of("stage",text("clientStage"),"channel",crm?"REAL_CRM_BROWSER_HTTP":"REAL_FLUTTER_RENDERER_REPOSITORY_HTTP","markers",safe,"at",Instant.now().toString()));state.put("clientResults",recorded);persist();
        }finally{if(process.isAlive())process.destroyForcibly();}
    }
    private void serve(HttpExchange exchange){try{
        String path=exchange.getRequestURI().getPath();
        if(path.startsWith("/__l06/")){
            if(!control.equals(exchange.getRequestHeaders().getFirst("X-L06-Control"))){reply(exchange,403,new byte[0],"text/plain");return;}
            reply(exchange,200,JSON.writeValueAsBytes(bridgeState()),"application/json");
        }else if(path.equals("/config/runtime-config.json"))reply(exchange,200,JSON.writeValueAsBytes(Map.of("apiBaseUrl","/api/v1","environment","test","appName","SYNTHETIC Ruta Fija L06")),"application/json");
        else if(path.startsWith("/api/")){
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+exchange.getRequestURI())).timeout(Duration.ofSeconds(30));
            exchange.getRequestHeaders().forEach((n,v)->{if(!Set.of("host","connection","content-length","upgrade").contains(n.toLowerCase(Locale.ROOT)))v.forEach(value->request.header(n,value));});
            var response=client.send(request.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes())).build(),HttpResponse.BodyHandlers.ofByteArray());
            if(response.statusCode()==201&&(path.equals("/api/v1/incidents")||path.equals("/api/v1/mobile/incidents")||path.equals("/api/v1/announcements"))){var created=JSON.readTree(response.body());String field=path.endsWith("announcements")?"currentAnnouncementId":state.containsKey("currentIncidentId")?"relatedIncidentId":"currentIncidentId";state.put(field,created.path("id").asText());persist();}
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
        for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment","incident","announcement")){
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
        Path temp=PRIVATE.resolveSibling("l06-connected-"+UUID.randomUUID()+".private");
        try{ManualL01HumanTrialServer.writeRestricted(temp,JSON.writeValueAsBytes(secrets));Files.move(temp,PRIVATE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(temp);}
    }
    private synchronized void persist()throws Exception{
        syncParticipant();state.put("phase",phase);state.put("observedAt",Instant.now().toString());state.put("httpEvents",httpEvents);Files.write(STATE,JSON.writeValueAsBytes(state));
        if(evidence!=null){Files.createDirectories(evidence.getParent());var safe=JSON.convertValue(state,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});safe.remove("dni");safe.remove("email");
            if(safe.get("participants") instanceof List<?> people)for(Object p:people)if(p instanceof Map<?,?> m){m.remove("dni");m.remove("email");}
            byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(safe);Files.write(evidence,bytes);Files.write(evidence.resolveSibling("observacion-"+System.currentTimeMillis()+"-"+Long.toUnsignedString(System.nanoTime())+".json"),bytes,StandardOpenOption.CREATE_NEW);
        }
    }

    @FunctionalInterface private interface Step{void run()throws Exception;}
    private void step(String id,Step action)throws Exception{phase=id;if(Boolean.TRUE.equals(state.get(id)))return;action.run();state.put(id,true);persist();}
    private JsonNode incident(String field)throws Exception{return call("/incidents/"+text(field),"GET",null,adminToken,200);}
    private void stage(String stage,boolean crm)throws Exception{state.put("clientStage",stage);if(crm)prepareBrowserSession();savePrivate();persist();runClient(crm);}
    private void membership(boolean active)throws Exception{
        var r=call("/groups/"+text("secondaryGroupId")+"/resources","GET",null,adminToken,200);
        boolean exists=false;for(var m:r.path("memberships"))if(m.path("resourceId").asText().equals(text("driverId"))&&m.path("active").asBoolean())exists=true;
        if(exists==active)return;
        call("/groups/"+text("secondaryGroupId")+"/memberships","POST",Map.of("expectedVersion",r.path("version").asLong(),"operationKey",fixtureKey("group-"+active),"resourceKind","DRIVER","resourceId",text("driverId"),"active",active,"reason","SYNTHETIC snapshot membership review"),adminToken,200);
    }
    private void connectedWorkflow(Map<String,Object> config)throws Exception{
        step("FLUTTER_REPORT",()->{if(!state.containsKey("currentIncidentId"))state.put("loseReportOnce",true);stage(state.containsKey("currentIncidentId")?"REPORT_RECONCILE":"REPORT",false);check(state.containsKey("currentIncidentId")&&Boolean.TRUE.equals(state.get("reportLostDelivered")),"ONE_REPORT_WITH_REAL_RESPONSE_LOSS_REQUIRED");});
        step("CRM_RESPONSIBILITY_FOLLOWUP",()->stage("ATTEND",true));
        step("FLUTTER_INFORMATION",()->stage("INFORMATION",false));
        step("CRITICAL_NOTICE_WITHOUT_AUTOMATIC_INTERRUPTION",()->{
            var current=incident("currentIncidentId");
            if(!"CRITICAL".equals(current.path("severity").asText()))current=call("/incidents/"+text("currentIncidentId")+"/assessment","POST",Map.of("operationKey",UUID.nameUUIDFromBytes((RUN+":critical-review").getBytes(StandardCharsets.UTF_8)),"expectedVersion",current.path("version").asLong(),"severity","CRITICAL","reason","SYNTHETIC explicit critical review; no actual interruption"),adminToken,200);
            check(!"INTERRUPTION_RECORDED".equals(current.path("operationalEffect").asText()),"SEVERITY_MUST_NOT_INTERRUPT_SERVICE");
            var failures=call("/notices?failures=true&size=100","GET",null,adminToken,200);int matching=0;
            for(var notice:failures.path("items"))if(text("currentIncidentId").equals(notice.path("references").path("incidentId").asText())&&"NOT_CONFIGURED".equals(notice.path("externalStatus").asText()))matching++;
            check(matching>0,"DURABLE_EXTERNAL_NON_DELIVERY_REQUIRED");state.put("criticalNoticeResult","PASS_DURABLE_NOT_CONFIGURED_WITHOUT_AUTOMATIC_INTERRUPTION");persist();stage("NOTICES",true);
        });
        step("CRM_STRUCTURED_CLOSURE",()->{if(!"RESOLVED".equals(incident("currentIncidentId").path("status").asText()))state.put("loseClosureOnce",true);stage("CLOSE",true);check("RESOLVED".equals(incident("currentIncidentId").path("status").asText()),"CLOSED_ORIGINAL_REQUIRED");check(Boolean.TRUE.equals(state.get("closureLostDelivered")),"CLOSURE_RESPONSE_LOSS_REQUIRED");});
        step("CRM_RELATED_NEW_CASE",()->{stage("RELATED",true);check(incident("relatedIncidentId").path("previousIncidentId").asText().equals(text("currentIncidentId")),"ORIGINAL_CASE_LINK_REQUIRED");});
        step("CRM_IMPORTANT_PUBLICATION",()->{if(!state.containsKey("currentAnnouncementId"))state.put("losePublishOnce",true);stage("PUBLISH",true);check(Boolean.TRUE.equals(state.get("publishLostDelivered")),"PUBLICATION_RESPONSE_LOSS_REQUIRED");});
        step("FLUTTER_RECEIPT_NOT_ACK",()->stage("CONSULT_INBOX",false));
        step("LEAVE_GROUP",()->{membership(false);var newer=publish("groupAfterExit",text("secondaryGroupId"));state.put("newGroupAnnouncementId",newer.path("id").asText());persist();});
        step("FLUTTER_RETAIN_OLD_READ_ACK",()->stage("READ_ACK",false));
        step("CRM_REVIEW_WITHDRAWAL",()->{var r=call("/announcements/"+text("currentAnnouncementId")+"/attention","GET",null,adminToken,200);if(r.path("items").get(0).path("withdrawnAt").isMissingNode())state.put("loseWithdrawalOnce",true);stage("WITHDRAW",true);check(Boolean.TRUE.equals(state.get("withdrawalLostDelivered")),"WITHDRAWAL_RESPONSE_LOSS_REQUIRED");});
        step("FLUTTER_WITHDRAWN_SCOPE",()->stage("WITHDRAWN",false));
        step("INCIDENT_VERSION_RACE",this::incidentRace);
        step("ACK_WITHDRAWAL_RACE",this::announcementRace);
        step("SAME_BINARY_RETURN",()->returnCheck(config));
    }
    private JsonNode publish(String label,String group)throws Exception{
        UUID operation=UUID.nameUUIDFromBytes((RUN+":"+label).getBytes(StandardCharsets.UTF_8));
        var b=new LinkedHashMap<String,Object>(Map.of("operationKey",operation,"title","SYNTHETIC L06 "+label,"body","SYNTHETIC operational notice","audienceType",group==null?"ORGANIZATION":"GROUP","requireReadAck",true));if(group!=null)b.put("audienceId",group);
        return call("/announcements","POST",b,adminToken,201);
    }
    private HttpRequest raceRequest(String path,Object body,String token)throws Exception{return HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Origin",base).header("Content-Type","application/json").header("Authorization","Bearer "+token).POST(HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build();}
    private void incidentRace()throws Exception{
        var i=incident("relatedIncidentId");check("OPEN".equals(i.path("status").asText()),"OPEN_RELATED_CASE_REQUIRED_FOR_RACE");
        String path="/incidents/"+text("relatedIncidentId")+"/assessment";Instant start=Instant.now();
        var a=client.sendAsync(raceRequest(path,Map.of("operationKey",UUID.randomUUID(),"expectedVersion",i.path("version").asLong(),"severity","LOW","reason","SYNTHETIC first concurrent review"),adminToken),HttpResponse.BodyHandlers.ofByteArray());
        var b=client.sendAsync(raceRequest(path,Map.of("operationKey",UUID.randomUUID(),"expectedVersion",i.path("version").asLong(),"severity","MEDIUM","reason","SYNTHETIC second concurrent review"),adminToken),HttpResponse.BodyHandlers.ofByteArray());
        var ar=a.get(40,TimeUnit.SECONDS);var br=b.get(40,TimeUnit.SECONDS);state.put("incidentRace",Map.of("startedAt",start.toString(),"firstStatus",ar.statusCode(),"secondStatus",br.statusCode(),"finishedAt",Instant.now().toString()));persist();
        check((ar.statusCode()==200&&br.statusCode()==409)||(ar.statusCode()==409&&br.statusCode()==200),"ONE_INCIDENT_VERSION_WINNER_REQUIRED");
        var loser=JSON.readTree(ar.statusCode()==409?ar.body():br.body());check("RESOURCE_VERSION_CONFLICT".equals(loser.path("code").asText()),"SPECIFIC_VERSION_REJECTION_REQUIRED");
        var history=call("/incidents/"+text("relatedIncidentId")+"/history","GET",null,adminToken,200);int count=0;for(var action:history)if(action.path("kind").asText().equals("ASSESSMENT"))count++;check(count==1,"EXACT_ONE_ASSESSMENT_ROUND");
        var now=incident("relatedIncidentId");now=call("/incidents/"+text("relatedIncidentId")+"/responsibility","POST",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",now.path("version").asLong(),"adminId",ADMIN,"reason","SYNTHETIC final case responsibility"),adminToken,200);
        call("/incidents/"+text("relatedIncidentId")+"/closure","POST",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",now.path("version").asLong(),"diagnosis","SYNTHETIC recurrence reviewed","actions","SYNTHETIC checked independently","result","SYNTHETIC recurrence resolved","evidenceRequired",false,"evidenceMode","NOT_REQUIRED","evidenceExplanation","SYNTHETIC no real incident or original file"),adminToken,200);
    }
    private void announcementRace()throws Exception{
        var a=publish("raceReadWithdrawal",null);String id=a.path("id").asText();state.put("raceAnnouncementId",id);persist();
        var before=call("/announcements/"+id+"/attention","GET",null,adminToken,200);check(before.path("items").size()==1,"ONE_RACE_RECIPIENT");
        Instant start=Instant.now();var read=client.sendAsync(raceRequest("/mobile/announcements/"+id+"/read",Map.of(),textSecret("mobileToken")),HttpResponse.BodyHandlers.ofByteArray());
        var withdraw=client.sendAsync(raceRequest("/announcements/"+id+"/recipients/"+text("accountId")+"/withdraw",Map.of("operationKey",UUID.randomUUID(),"expectedVersion",0,"reason","SYNTHETIC concurrent security withdrawal"),adminToken),HttpResponse.BodyHandlers.ofByteArray());
        var rr=read.get(40,TimeUnit.SECONDS);var wr=withdraw.get(40,TimeUnit.SECONDS);state.put("announcementRace",Map.of("startedAt",start.toString(),"readStatus",rr.statusCode(),"withdrawStatus",wr.statusCode(),"finishedAt",Instant.now().toString()));persist();
        check(wr.statusCode()==200&&Set.of(200,404).contains(rr.statusCode()),"SERIALIZED_READ_WITHDRAWAL_REQUIRED");
        var after=call("/announcements/"+id+"/attention","GET",null,adminToken,200).path("items").get(0);check("WITHDRAWN".equals(after.path("accessStatus").asText()),"WITHDRAWAL_MUST_END_ACCESS");
        if(rr.statusCode()==200)check(after.path("readAt").asText().equals(JSON.readTree(rr.body()).path("readAt").asText()),"FIRST_READ_TIMESTAMP_PRESERVED");else check(after.path("readAt").isMissingNode()||after.path("readAt").isNull(),"NO_READ_AFTER_WITHDRAWAL");
        call("/mobile/announcements/"+id,"GET",null,textSecret("mobileToken"),404);
    }
    private void returnCheck(Map<String,Object> config)throws Exception{
        var properties=new LinkedHashMap<String,Object>(config);properties.put("app.qualification.operations-enabled",false);properties.put("server.port",0);
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("L06SameBinaryReturn",properties));var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
        OtpEmailSender refuse=(r,c,e)->{throw new IllegalStateException("NO_MAIL_IN_RETURN");};boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{var registry=(BeanDefinitionRegistry)factory;if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",refuse);}));
        try(var returned=boot.run()){int original=backendPort;backendPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)returned).getWebServer().getPort();try{
            var q=call("/mobile/qualification","GET",null,textSecret("mobileToken"),200);check(!q.path("operationalEnabled").asBoolean()&&q.path("blockers").size()==1&&q.path("blockers").toString().contains("OPERATIONAL_FEATURE_DISABLED"),"DISABLED_OPERATIONAL_FEATURE_REQUIRED");
            call("/mobile/incidents/"+text("currentIncidentId"),"GET",null,textSecret("mobileToken"),200);call("/mobile/notices","GET",null,textSecret("mobileToken"),200);
            var before=call("/mobile/me","GET",null,textSecret("mobileToken"),200);
            var rejected=call("/mobile/availability","PUT",Map.of("status","DISPONIBLE"),textSecret("mobileToken"),409);
            check("RESOURCE_CONFLICT".equals(rejected.path("code").asText())&&"El conductor requiere habilitación operativa vigente.".equals(rejected.path("message").asText()),"SPECIFIC_OPERATIONAL_ELIGIBILITY_REJECTION_REQUIRED");
            check(before.path("availabilityStatus").equals(call("/mobile/me","GET",null,textSecret("mobileToken"),200).path("availabilityStatus")),"REJECTED_AVAILABILITY_MUST_NOT_MUTATE_STATE");
            state.put("returnBlockers",q.path("blockers"));state.put("returnOperationStatus",409);state.put("returnOperationCode","RESOURCE_CONFLICT");
            state.put("returnResult","PASS_SAME_BINARY_DISABLED_OPERATIONS_OWN_CONSULTATION_PRESERVED");persist();
        }finally{backendPort=original;}}
    }
    private void cleanup()throws Exception{
        check(Boolean.TRUE.equals(state.get("SAME_BINARY_RETURN")),"ALL_CONNECTED_STAGES_REQUIRED_BEFORE_RETIREMENT");state.put("cleanupStarted",true);persist();
        var q=call("/drivers/"+text("driverId")+"/qualification","GET",null,adminToken,200);
        for(var p:q.path("periods"))if("AUTHORIZED".equals(p.path("status").asText()))q=call("/drivers/"+text("driverId")+"/qualification/periods/"+p.path("id").asText()+"/revoke","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",fixtureKey("retire-period"),"reason","SYNTHETIC connected fixture closed"),adminToken,200);
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
            UUID account=UUID.fromString(text("accountId"));var u=app.getBean(AppUserRepository.class).findById(account).orElseThrow();check(email().equals(u.getEmail())&&ORG.equals(u.getOrganizationId())&&u.getRole()==UserRole.CONDUCTOR,"EXACT_SYNTHETIC_FIXTURE_REQUIRED");
            check(jdbc.queryForObject("select count(*) from assignment where driver_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')",Integer.class,UUID.fromString(text("driverId")))==0,"NO_ACTIVE_COMMITMENTS_BEFORE_RETIREMENT");
            app.getBean(DriverRepository.class).findById(UUID.fromString(text("driverId"))).orElseThrow().deactivate();u.deactivate();app.getBean(VehicleRepository.class).findById(UUID.fromString(text("vehicleId"))).orElseThrow().deactivate();
            for(String k:List.of("groupId","secondaryGroupId"))app.getBean(TransportGroupRepository.class).findById(UUID.fromString(text(k))).orElseThrow().deactivate();
            app.getBean(RefreshTokenRepository.class).revokeActiveByUserId(account,Instant.now());if(state.containsKey("browserRefreshFamilyId"))app.getBean(RefreshTokenRepository.class).revokeFamily(UUID.fromString(text("browserRefreshFamilyId")),Instant.now());
            app.getBean(pe.rutafija.audit.application.AuditService.class).recordSystem(u.getOrganization(),"L06_SYNTHETIC_FIXTURE_DISABLED","APP_USER",account,Map.of("caseRunId",RUN,"syntheticOnly",true));
        });
        check(Boolean.TRUE.equals(jdbc.queryForObject("select not active from app_user where id=?",Boolean.class,UUID.fromString(text("accountId")))),"DISABLED_ACCOUNT_REQUIRED");
        check(!app.getBean(pe.rutafija.onboarding.infrastructure.ConductorOnboardingAccessRepository.class).findCredential(email()).orElseThrow().accountAllowed(),"PRELIMINARY_OPERATIONAL_ACCESS_DISABLED_REQUIRED");
        var retired=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/api/v1/mobile/incidents/"+text("currentIncidentId"))).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+textSecret("mobileToken")).GET().build(),HttpResponse.BodyHandlers.discarding());
        check(Set.of(401,403).contains(retired.statusCode()),"RETIRED_TOKEN_MUST_BE_REJECTED");state.put("retiredConductorTokenStatus",retired.statusCode());
        check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",5)),"EXACT_ONE_CREATION_LEDGER_PRESERVED");check(jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class).equals(state.get("termsAcceptancesBefore")),"ACCEPTANCES_PRESERVED");
        var before=JSON.valueToTree(state.get("antecedentRowHashes"));check(before.equals(JSON.valueToTree(antecedentHashes(jdbc,before))),"ANTECEDENT_ROWS_PRESERVED");
        state.put("ledgerAfter",ledger(jdbc));state.put("selectiveCleanup","PASS_ONE_ACCOUNT_DISABLED_SESSIONS_PERIOD_REVOKED_HISTORY_LEDGER_PRESERVED");state.put("antecedentPreservation","PASS");state.put("globalCleanup",false);persist();
    }
    private Map<String,Object> bridgeState(){var v=new LinkedHashMap<String,Object>();for(String k:List.of("runId","adminId","driverId","accountId","groupId","secondaryGroupId","clientStage","currentIncidentId","relatedIncidentId","currentAnnouncementId","newGroupAnnouncementId"))v.put(k,text(k));v.put("browserRefresh",secrets.get("browserRefresh"));return v;}
    private synchronized boolean simulateLostResponse(String path,String method,int status){
        String key=path.equals("/api/v1/mobile/incidents")?"Report":path.equals("/api/v1/announcements")?"Publish":path.endsWith("/closure")?"Closure":path.endsWith("/withdraw")?"Withdrawal":"";
        if(!key.isEmpty()&&"POST".equals(method)&&Set.of(200,201).contains(status)&&Boolean.TRUE.equals(state.get("lose"+key+"Once"))){state.put("lose"+key+"Once",false);state.put(Character.toLowerCase(key.charAt(0))+key.substring(1)+"LostDelivered",true);httpEvents.add(Map.of("path",path,"method",method,"backendStatus",status,"clientStatus",504,"transportFault","SYNTHETIC_RESPONSE_LOSS_AFTER_SERVER_COMMIT","at",Instant.now().toString()));try{persist();}catch(Exception failure){throw new IllegalStateException("TRANSPORT_PROOF_PERSISTENCE_FAILED");}return true;}return false;
    }
    private static void check(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
}
