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
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.*;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import java.io.Console;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** One new synthetic conductor; GPS and all conductor actions come from the real phone. */
public final class L08ConnectedTrial {
    static final String RUN="a999ef52-d87f-498d-9251-1581cbb72562";
    static final UUID ADMIN=UUID.fromString("92ebc07c-1319-4850-a734-f792cb299979"),OWNER=UUID.fromString("088518c1-821c-4316-9f87-090f51ac44e0"),ORG=UUID.fromString("1a759298-7831-4aaf-9299-c3920a9cc28a");
    private static final String EMAIL="conductor.l08.a999ef52@rutafija.test",DNI="60000009",GROUP="SYNTHETIC L08 a999ef52",PLATE="SYN-L08-0",BASE="http://127.0.0.1:4311";
    private static final Path ROOT=Path.of("..").toAbsolutePath().normalize(),LOCAL=ROOT.resolve("backend/.local"),STATE=LOCAL.resolve("l08-connected-state.json"),PRIVATE=LOCAL.resolve("l08-connected.private"),MARKER=LOCAL.resolve("l08-connected.active");
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Map<String,Object> state=new LinkedHashMap<>(),secrets=new ConcurrentHashMap<>();
    private final List<Map<String,Object>> httpEvents=new CopyOnWriteArrayList<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Console console;
    private final String base=BASE;
    private final Path evidence=ROOT.resolve("docs/evidencia/L-08/conectado-"+RUN+"/resultado.json");
    private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;
    private HttpServer proxy;
    private ExecutorService proxyExecutor;
    private int backendPort;
    private String adminToken,phase="PREFLIGHT";
    private L08ConnectedTrial(Console console){this.console=console;}

    public static void main(String[] args){
        if(System.console()==null){System.out.println("CONNECTED_L08_RESULT=REFUSED_INTERACTIVE_CONSOLE_REQUIRED");System.exit(1);}
        boolean first=args.length==1&&"--serve".equals(args[0]);
        boolean resume=args.length==3&&"--serve".equals(args[0])&&"--resume-case".equals(args[1])&&RUN.equals(args[2]);
        if(!first&&!resume){System.out.println("CONNECTED_L08_RESULT=REFUSED_EXACT_MODE_REQUIRED");System.exit(1);}
        var runner=new L08ConnectedTrial(System.console());int exit=0;
        try{runner.run(resume);runner.finalizeProtectionAfterGuardClosed();}catch(Exception failure){
            try{runner.state.put("result","PENDING_KEEP_CASE");runner.state.put("failedPhase",runner.phase);if(Files.exists(STATE))runner.persist();}catch(Exception ignored){}
            System.out.println("CONNECTED_L08_RESULT=PENDING_KEEP_CASE; PHASE="+runner.phase+"; CHECK="+safeCode(failure));exit=1;
        }finally{if(runner.proxy!=null)runner.proxy.stop(0);if(runner.proxyExecutor!=null)runner.proxyExecutor.shutdownNow();if(runner.app!=null)runner.app.close();runner.adminToken=null;runner.secrets.clear();}
        if(exit!=0)System.exit(exit);
    }
    private void run(boolean resume)throws Exception{
        assertWorkspace();authorize();var config=ManualL01HumanTrial.configuration();
        check("jdbc:postgresql://127.0.0.1:5432/ruta_fija_test".equals(config.get("spring.datasource.url"))&&"rf_test".equals(config.get("spring.datasource.username")),"EXACT_TEST_TARGET_REQUIRED");
        try(Connection guard=ManualL01HumanTrial.connect(config)){
            guard.setAutoCommit(true);var inspect=new JdbcTemplate(new org.springframework.jdbc.datasource.SingleConnectionDataSource(guard,true));
            check("ruta_fija_test/rf_test".equals(inspect.queryForObject("select current_database()||'/'||current_user",String.class)),"EXACT_TEST_DATABASE_AND_ROLE_REQUIRED");
            check("50".equals(inspect.queryForObject("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1",String.class)),"EXPLICITLY_VALIDATED_V50_REQUIRED");
            check(inspect.queryForObject("select count(*) from flyway_schema_history where not success",Integer.class)==0,"SUCCESSFUL_SCHEMA_REQUIRED");
            try(var files=Files.list(LOCAL)){check(files.noneMatch(p->p.getFileName().toString().endsWith(".active")&&!p.equals(MARKER)),"OTHER_PROTECTED_CASE_ACTIVE");}
            check(Boolean.TRUE.equals(inspect.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)),"CLEANUP_LOCK_REQUIRED");
            initialize(inspect,resume);L08ConnectedGuardProbe.main(new String[0]);state.put("rollbackFixtureGuard","PASS_REFUSED_BEFORE_FIXTURE");persist();
            proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",4311),0);boot(config);
            if(!Boolean.TRUE.equals(state.get("cleanupStarted"))){phase="NORMAL_ONBOARDING_AND_QUALIFICATION";recoverResources();provision();check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",7)),"EXACT_ONE_CONDUCTOR_CREATION_REQUIRED");qualify();state.put("prepared",true);savePrivate();persist();}
            proxy.createContext("/",this::forward);proxyExecutor=Executors.newVirtualThreadPerTaskExecutor();proxy.setExecutor(proxyExecutor);proxy.start();phase="HUMAN_PHONE_INTERACTION";persist();
            console.printf("L08_CONNECTED_RUN_ID=%s%nURL_LOCAL=%s%nCORREO=SIMULADO; GPS=SOLO_TELEFONO_REAL%n",RUN,BASE);
            console.printf("Comandos: credenciales, correo, preparar-servicio, telefono-ok, gps-ok, verificar, pausar, salir.%nNo pegues salidas ni secretos en el prompt.%n");
            for(;;){String command=console.readLine("Ensayo L08> ");if(command==null){observe();return;}
                try{switch(command.strip()){
                    case "credenciales"->credentials();case "correo"->simulatedMail();case "preparar-servicio"->prepareService();
                    case "telefono-ok"->confirmHuman("humanPhoneInteractionConfirmed","CONFIRMO_INTERACCION_PERSONAL_EN_POCO");
                    case "gps-ok"->confirmHuman("humanPhysicalGpsConfirmed","CONFIRMO_GPS_REAL_CONSENTIDO_EN_PRIMER_PLANO");
                    case "verificar"->observe();case "pausar"->{observe();return;}
                    case "salir"->{observe();if("PASS_PHYSICAL_FOREGROUND_CASE_ONLY".equals(text("manualPhoneResult")))cleanup(inspect);else console.printf("CONNECTED_L08_EXIT=PENDING_KEEP_CASE; TEST_CLEANUP_RELEASED=false%n");return;}
                    default->console.printf("COMANDO_NO_RECONOCIDO; no introduzcas contrase?as en este prompt.%n");
                }}catch(Exception failure){state.put("lastFailureCode",safeCode(failure));persist();console.printf("PASO_NO_COMPLETADO; CHECK=%s; conservar caso, no reenviar autom?ticamente.%n",safeCode(failure));}
            }
        }
    }

    private void authorize()throws Exception{
        var spec=JSON.readTree(Files.readAllBytes(ROOT.resolve("specs/decisiones-L08-2026-10-21.json")));
        check(spec.path("newPersistentCaseAuthorized").asBoolean()&&RUN.equals(spec.path("connectedCase").path("runId").asText())&&spec.path("connectedCase").path("maxNewConductor").asInt()==1,"EXACT_L08_CON01_AUTHORIZATION_REQUIRED");
        var registry=JSON.readTree(Files.readAllBytes(ROOT.resolve("docs/evidencia/L-08/autorizaciones-2026-10-22.json")));JsonNode approval=null;
        for(var decision:registry.path("decisions"))if("L08-CON-01".equals(decision.path("id").asText()))approval=decision;
        check(approval!=null&&"AUTORIZADA_USUARIO".equals(approval.path("status").asText())&&RUN.equals(approval.path("runId").asText())&&approval.path("maxNewConductorAccounts").asInt()==1&&ADMIN.toString().equals(approval.path("adminId").asText())&&OWNER.toString().equals(approval.path("superAdminId").asText())&&ORG.toString().equals(approval.path("organizationId").asText()),"EXACT_RECORDED_PARTICIPANTS_REQUIRED");
        Path proposal=ROOT.resolve(registry.path("proposal").asText()).normalize();
        check(proposal.startsWith(ROOT)&&sha(Files.readAllBytes(proposal)).equals(registry.path("proposalSha256").asText()),"EXACT_AUTHORIZED_PROPOSAL_REQUIRED");
    }
    private void initialize(JdbcTemplate inspect,boolean resume)throws Exception{
        if(Files.exists(STATE)){
            check(resume,"EXPLICIT_SAME_CASE_RESUME_REQUIRED");state.putAll(JSON.readValue(Files.readAllBytes(STATE),Map.class));
            check(RUN.equals(text("runId"))&&!Boolean.TRUE.equals(state.get("TEST_CLEANUP_RELEASED")),"SAME_OPEN_L08_CASE_ONLY");
            check(Files.isRegularFile(MARKER)&&RUN.equals(Files.readString(MARKER).strip())&&Files.isRegularFile(PRIVATE),"SAME_CASE_PROTECTION_REQUIRED");
            secrets.putAll(JSON.readValue(Files.readAllBytes(PRIVATE),Map.class));
            if(state.get("httpEvents") instanceof List<?> events)for(Object event:events)httpEvents.add((Map<String,Object>)event);
            var counts=ledger(inspect);check(counts.get("SUPER_ADMIN")==1&&counts.get("ADMIN")==3&&Set.of(6,7).contains(counts.get("CONDUCTOR")),"ORIGINAL_LEDGER_WITH_AT_MOST_ONE_NEW_ACCOUNT");
            state.put("resumeCount",((Number)state.getOrDefault("resumeCount",0)).intValue()+1);
        }else{
            check(!resume&&!Files.exists(MARKER)&&!Files.exists(PRIVATE),"PARTIAL_CASE_KEEP_STATE_AND_REVIEW");
            check(ledger(inspect).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",6)),"EXACT_PREAUTHORIZED_LEDGER_REQUIRED");
            check(inspect.queryForObject("select count(*) from app_user where email=?",Integer.class,EMAIL)==0&&inspect.queryForObject("select count(*) from conductor_onboarding_identity where email=?",Integer.class,EMAIL)==0&&inspect.queryForObject("select count(*) from driver where document_type='DNI' and document_number=?",Integer.class,DNI)==0,"SYNTHETIC_IDENTITY_COLLISION");
            check(inspect.queryForObject("select count(*) from app_user where id=? and organization_id=? and role='ADMIN' and active and email_verified_at is not null",Integer.class,ADMIN,ORG)==1,"ELIGIBLE_EXISTING_ADMIN_REQUIRED");
            check(inspect.queryForObject("select count(*) from app_user where id=? and organization_id is null and role='SUPER_ADMIN' and active",Integer.class,OWNER)==1,"ELIGIBLE_EXISTING_OWNER_REQUIRED");
            check(inspect.queryForObject("select count(*) from pilot_lifecycle",Integer.class)==0,"NO_ACTUAL_PILOT_START_ALLOWED");
            state.put("runId",RUN);state.put("organizationId",ORG.toString());state.put("adminId",ADMIN.toString());state.put("ownerId",OWNER.toString());state.put("email",EMAIL);state.put("dni",DNI);
            state.put("ledgerBefore",ledger(inspect));state.put("termsAcceptancesBefore",inspect.queryForObject("select count(*) from admin_terms_acceptance",Integer.class));state.put("antecedentRowHashes",antecedents(inspect,null));
            state.put("result","EN_CURSO_PHYSICAL_CASE_ONLY");state.put("TEST_CLEANUP_RELEASED",false);state.put("physicalGpsAutomated",false);state.put("externalMailAttempts",0);
            secrets.put("password","L08-Synthetic-"+UUID.randomUUID());Files.writeString(MARKER,RUN,StandardOpenOption.CREATE_NEW);savePrivate();
        }
        persist();
    }
    private void boot(Map<String,Object> config)throws Exception{
        phase="BOOT_REAL_BACKEND_SIMULATED_MAIL";String key=Files.readString(LOCAL.resolve("l01-human-trial-jwt.private"),StandardCharsets.US_ASCII).strip();
        config.putAll(ManualL01HumanTrialServer.runtimeProperties(key));config.put("server.port",0);config.put("app.security.cors.allowed-origins",BASE);config.put("app.security.refresh-cookie.secure",false);config.put("app.qualification.maintenance.enabled",false);config.put("app.mobile.maintenance-enabled",false);
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("validatedL08PhysicalConnectedTest",config));var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
        OtpEmailSender simulated=(recipient,code,expiry)->{check(EMAIL.equals(recipient),"SYNTHETIC_RECIPIENT_ONLY");secrets.put("simulatedOtp:"+recipient,code);try{savePrivate();}catch(Exception failure){throw new IllegalStateException("PRIVATE_SIMULATED_MAIL_STATE_FAILED");}};
        boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{var registry=(BeanDefinitionRegistry)factory;if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);check(registry.containsBeanDefinition("smtpOtpEmailSender"),"SMTP_ADAPTER_REQUIRED");registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",simulated);}));
        app=boot.run();check(app.getBean(OtpEmailSender.class)==simulated,"MAIL_DOUBLE_REQUIRED");jdbc=app.getBean(JdbcTemplate.class);backendPort=((org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext)app).getWebServer().getPort();
        check(app.getEnvironment().getProperty("app.security.admin-terms-gate.enabled",Boolean.class,false)&&app.getEnvironment().getProperty("app.security.person-identity-gate.enabled",Boolean.class,false),"NORMAL_SECURITY_GATES_REQUIRED");
        check(app.getBean(InvitationCodeHasher.class).hashOrigin("person-identity:key-fingerprint:v1").equals(jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1",String.class)),"STABLE_IDENTITY_KEY_REQUIRED");
        adminToken=admin();call("/onboarding/requests","GET",null,adminToken,200);
    }
    private String admin(){return app.getBean(JwtService.class).issueAccessToken(app.getBean(AppUserRepository.class).findById(ADMIN).orElseThrow());}
    private int participantIndex(){return 0;}
    private String fixtureKey(String suffix){return RUN+":"+suffix;}
    private String email(){return EMAIL;}
    private String text(String key){return Objects.toString(state.get(key),"");}
    private void recoverResources(){
        var groups=jdbc.queryForList("select id::text from transport_group where organization_id=? and name=?",String.class,ORG,GROUP);check(groups.size()<=1,"AMBIGUOUS_CASE_GROUP");if(!groups.isEmpty())state.put("groupId",groups.getFirst());
        var vehicles=jdbc.queryForList("select id::text from vehicle where organization_id=? and plate=?",String.class,ORG,PLATE);check(vehicles.size()<=1,"AMBIGUOUS_CASE_VEHICLE");if(!vehicles.isEmpty())state.put("vehicleId",vehicles.getFirst());
    }
    private void provision()throws Exception{
        phase="NORMAL_ONBOARDING_HTTP";
        if(!state.containsKey("groupId"))state.put("groupId",call("/groups","POST",Map.of("name",GROUP,"description","Synthetic connected trial only"),adminToken,201).path("id").asText());persist();
        if(!state.containsKey("vehicleId"))state.put("vehicleId",call("/vehicles","POST",Map.of("plate",("SYN-L08-"+participantIndex()),"brand","SYNTHETIC","model","L08","year",2026),adminToken,201).path("id").asText());persist();
        var requests=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=?",String.class,email());
        if(!requests.isEmpty())state.put("requestId",requests.getFirst());
        if(!state.containsKey("requestId")){
            if(!secrets.containsKey("invitationCode")){var invite=call("/invitations","POST",Map.of("recipientEmail",email()),adminToken,201);secrets.put("invitationCode",invite.path("code").asText());state.put("invitationId",invite.path("id").asText());savePrivate();persist();}
            if(!secrets.containsKey("verificationToken")){
                var start=call("/onboarding/requests","POST",Map.of("email",email(),"password",secrets.get("password"),"fullName","SYNTHETIC L08 CONDUCTOR "+participantIndex(),"invitationCode",secrets.get("invitationCode")),null,202);
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
        var fields=List.of(Map.of("documentType","DNI","documentNumber",text("dni"),"fullName","SYNTHETIC L08 CONDUCTOR "+participantIndex(),"issuer","SYNTHETIC"),Map.of("licenseNumber","SYN-L08-LICENSE-"+participantIndex(),"licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"));
        for(int i=0;i<5;i++){var d=new LinkedHashMap<String,Object>();d.put("requirementId","CON-0"+(i+1));d.put("fields",i<2?fields.get(i):Map.of());d.put("expiryMode",i==1?"DATED":"NO_EXPIRY");if(i==1)d.put("validUntil",Instant.now().plus(Duration.ofDays(90)).toString());d.put("applicability","CONFIRMED");d.put("condition","Synthetic applicability only");d.put("sourceReference","Synthetic original review; not a real title");d.put("result",i<2?"CONFORME":"NO_APLICA_JUSTIFICADO");d.put("reviewReference","Synthetic controlled review");d.put("observation","Synthetic optional requirement not applicable");out.add(d);}return out;
    }

    private Map<String,Object> reviews(String scope,long version){
        var command=new LinkedHashMap<String,Object>();command.put("expectedVersion",version);command.put("operationKey",fixtureKey("review-")+scope);
        command.put("reason","SYNTHETIC-CONNECTED-REVIEW");command.put("scope",scope);if(scope.equals("VEHICLE"))command.put("vehicleId",text("vehicleId"));
        var docs=new ArrayList<Map<String,Object>>();
        for(var r:pe.rutafija.qualification.application.QualificationCatalog.REQUIREMENTS)if(r.scope().equals(scope)){
            var fields=new LinkedHashMap<String,String>();for(String key:r.fieldKeys())fields.put(key,switch(key){case "plate"->("SYN-L08-"+participantIndex());case "category"->"M1";case "bodyType"->"SEDAN";case "brand"->"SYNTHETIC";case "model"->"L08";case "year"->"2026";case "insuranceType"->"SOAT";case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"SYNTHETIC CHICLAYO";default->"SYNTHETIC reference";});
            var d=new LinkedHashMap<String,Object>();d.put("requirementId",r.id());d.put("fields",fields);d.put("expiryMode","DATED");d.put("validUntil",Instant.now().plus(Duration.ofDays(60)).toString());d.put("applicability","CONFIRMED");d.put("condition","SYNTHETIC explicit applicability");d.put("sourceReference","SYNTHETIC not a real title");d.put("result",r.id().equals("EMP-03")?"NO_APLICA_JUSTIFICADO":"CONFORME");d.put("reviewReference","SYNTHETIC visual review");docs.add(d);
        }command.put("documents",docs);return command;
    }

    private void qualify()throws Exception{
        String path="/drivers/"+text("driverId")+"/qualification";var q=call(path,"GET",null,adminToken,200);
        if(Boolean.TRUE.equals(state.get("prepared"))){state.put("resumeQualificationObservedOnly",true);return;}
        if(!Boolean.TRUE.equals(state.get("vehicleReviewed"))){q=call(path+"/reviews","POST",reviews("VEHICLE",q.path("version").asLong()),adminToken,200);state.put("vehicleReviewed",true);persist();}
        if(!Boolean.TRUE.equals(state.get("companyReviewed"))){q=call(path+"/reviews","POST",reviews("COMPANY",q.path("version").asLong()),adminToken,200);state.put("companyReviewed",true);persist();}
        if(!state.containsKey("periodId")){
            for(var period:q.path("periods"))if(period.path("vehicleId").asText().equals(text("vehicleId"))&&"AUTHORIZED".equals(period.path("status").asText()))state.put("periodId",period.path("id").asText());
            if(!state.containsKey("periodId")){q=call(path+"/periods","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",fixtureKey("period"),"reason","SYNTHETIC L08 normal period","vehicleId",text("vehicleId"),"kind","OWN","startAt",Instant.now().minusSeconds(60).toString()),adminToken,200);state.put("periodId",q.path("periods").get(q.path("periods").size()-1).path("id").asText());}persist();
        }
        if(!q.path("operationalEnabled").asBoolean())q=call(path+"/activate","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",fixtureKey("activate"),"reason","SYNTHETIC L08 explicit activation"),adminToken,200);
        check(q.path("operationalEnabled").asBoolean(),"NORMAL_QUALIFICATION_REQUIRED");
    }
    private void prepareService()throws Exception{
        phase="PREPARE_NORMAL_SERVICE_AFTER_PHONE_AVAILABILITY";adminToken=admin();
        if(state.containsKey("offerId")){console.printf("L08_SERVICE=EXISTING_CASE_OFFER; consultar la misma oferta.%n");return;}
        check(Boolean.TRUE.equals(state.get("phoneLoginObserved")),"HUMAN_PHONE_LOGIN_REQUIRED");
        check("DISPONIBLE".equals(jdbc.queryForObject("select availability_status from driver where id=? and organization_id=?",String.class,UUID.fromString(text("driverId")),ORG)),"CONDUCTOR_MUST_DECLARE_OWN_AVAILABILITY");
        if(!state.containsKey("routeVersionId")){
            var route=call("/routes","POST",Map.of("name","SYNTHETIC L08 route "+RUN,"operationKey",fixtureKey("route"),"reason","SYNTHETIC_NO_PHYSICAL_TERMINAL_GEOMETRY","stops",List.of(Map.of("key","start","kind","START","name","SYNTHETIC start"),Map.of("key","middle","kind","INTERMEDIATE","name","SYNTHETIC middle"),Map.of("key","end","kind","END","name","SYNTHETIC end without physical geometry"))),adminToken,200);
            state.put("routeId",route.path("id").asText());state.put("routeVersionId",route.path("currentVersionId").asText());persist();
        }
        if(!state.containsKey("typeVersionId")){
            var type=call("/service-types","POST",Map.of("name","SYNTHETIC L08 type "+RUN,"operationKey",fixtureKey("type"),"reason","SYNTHETIC_FOREGROUND_TRIAL","beforeMinutes",0,"afterMinutes",0),adminToken,200);
            state.put("typeId",type.path("id").asText());state.put("typeVersionId",type.path("currentVersionId").asText());persist();
        }
        var existing=jdbc.queryForList("select id::text,service_id::text from assignment where organization_id=? and idempotency_key=?",ORG,fixtureKey("offer"));check(existing.size()<=1,"AMBIGUOUS_CASE_OFFER");
        if(!existing.isEmpty()){state.put("offerId",existing.getFirst().get("id"));state.put("serviceId",existing.getFirst().get("service_id"));}
        else{
            if(!state.containsKey("scheduledAt")){state.put("scheduledAt",Instant.now().plusSeconds(1800).truncatedTo(java.time.temporal.ChronoUnit.MICROS).toString());persist();}Instant planned=Instant.parse(text("scheduledAt"));
            var offer=call("/assignments","POST",Map.of("driverId",text("driverId"),"vehicleId",text("vehicleId"),"routeVersionId",text("routeVersionId"),"serviceTypeVersionId",text("typeVersionId"),"originText","SYNTHETIC foreground start","destinationText","SYNTHETIC end without calibrated geometry","scheduledAt",planned,"scheduledEndAt",planned.plusSeconds(1800)),adminToken,201);
            state.put("offerId",offer.path("id").asText());state.put("serviceId",offer.path("serviceId").asText());
        }
        state.put("physicalTerminalGeometryCalibrated",false);persist();console.printf("L08_SERVICE=PREPARED_PENDING_PERSONAL_ACCEPTANCE; OFFER_ID=%s%n",text("offerId"));
    }
    private void credentials(){
        check("MOSTRAR_SOLO_LOCAL".equals(console.readLine("Terminal privada, sin grabaci?n. Escribe MOSTRAR_SOLO_LOCAL: ")),"LOCAL_CREDENTIAL_DISPLAY_CANCELLED");
        console.printf("CONDUCTOR exclusivamente sint?tico: %s%nContrase?a de prueba, copiar solo al tel?fono: %s%nNo compartir ni capturar esta salida.%n",EMAIL,secrets.get("password"));
    }
    private void simulatedMail(){String code=Objects.toString(secrets.get("simulatedOtp:"+EMAIL),"");check(!code.isBlank(),"NO_SIMULATED_MESSAGE_AVAILABLE");console.printf("OTP SIMULADO solo para entrada local: %s%nNo reenviar ni compartir.%n",code);}
    private void confirmHuman(String field,String phrase)throws Exception{check(phrase.equals(console.readLine("Confirmaci?n personal exacta (%s): ",phrase)),"PERSONAL_CONFIRMATION_NOT_RECORDED");state.put(field,true);state.put(field+"At",Instant.now().toString());persist();}

    private void forward(HttpExchange exchange){
        try{
            String path=exchange.getRequestURI().getPath();if(!path.startsWith("/api/v1/mobile/")){reply(exchange,404,new byte[0],"application/json");return;}
            byte[] body=exchange.getRequestBody().readNBytes(1024*1024+1);check(body.length<=1024*1024,"REQUEST_SIZE_REFUSED");
            if(path.equals("/api/v1/mobile/auth/login")){var login=JSON.readTree(body);if(login==null||!EMAIL.equalsIgnoreCase(login.path("email").asText().strip())){reply(exchange,403,JSON.writeValueAsBytes(Map.of("code","L08_SYNTHETIC_ACCOUNT_ONLY","message","Solo la cuenta sint?tica autorizada de este ensayo.")),"application/json");return;}}
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+exchange.getRequestURI())).timeout(Duration.ofSeconds(30));
            exchange.getRequestHeaders().forEach((name,values)->{if(!Set.of("host","connection","content-length","upgrade").contains(name.toLowerCase(Locale.ROOT)))values.forEach(value->request.header(name,value));});
            Instant started=Instant.now();var response=client.send(request.method(exchange.getRequestMethod(),HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
            record(path,exchange.getRequestMethod(),response.statusCode(),"PHONE_PROXY_REAL_BACKEND",started);observeResponse(path,exchange.getRequestMethod(),body,response);
            response.headers().map().forEach((name,values)->{if(!Set.of("connection","content-length","transfer-encoding").contains(name.toLowerCase(Locale.ROOT)))exchange.getResponseHeaders().put(name,values);});
            reply(exchange,response.statusCode(),response.body(),response.headers().firstValue("content-type").orElse("application/json"));
        }catch(Exception failure){try{reply(exchange,502,JSON.writeValueAsBytes(Map.of("code","L08_TRANSPORT_NOT_CONFIRMED","message","No se confirm? la operaci?n; consulte el estado antes de repetir.")),"application/json");}catch(Exception ignored){}}finally{exchange.close();}
    }
    private synchronized void observeResponse(String path,String method,byte[] body,HttpResponse<byte[]> response)throws Exception{
        int status=response.statusCode();
        if(path.equals("/api/v1/mobile/auth/login")){if(status==200){var login=JSON.readTree(response.body());check(text("accountId").equals(login.path("user").path("id").asText()),"EXACT_PHONE_ACCOUNT_REQUIRED");state.put("phoneLoginObserved",true);}if(status==401)state.put("invalidPasswordRejectedObserved",true);}
        if(path.equals("/api/v1/mobile/availability")&&method.equals("PUT")&&status==200)state.put("phoneAvailabilityObserved",true);
        if(path.equals("/api/v1/mobile/location/consent")&&status==200&&JSON.readTree(body).path("consent").asBoolean())state.put("phoneConsentObserved",true);
        if(path.equals("/api/v1/mobile/location/current")&&status==204)state.put("phoneLocationAcceptedObserved",true);
        if(state.containsKey("offerId")&&status==200)for(String action:List.of("accept","start","complete"))if(path.equals("/api/v1/mobile/assignments/"+text("offerId")+"/"+action))state.put("phone-"+action+"-observed",true);
        if(path.equals("/api/v1/mobile/auth/logout")&&status==204)state.put("phoneLogoutObserved",true);persist();
    }
    private static void reply(HttpExchange exchange,int status,byte[] body,String contentType)throws Exception{exchange.getResponseHeaders().set("Content-Type",contentType);exchange.getResponseHeaders().set("Cache-Control","no-store");exchange.sendResponseHeaders(status,body.length==0?-1:body.length);if(body.length>0)exchange.getResponseBody().write(body);}
    private JsonNode call(String path,String method,Object body,String token,int expected)throws Exception{
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+backendPort+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Origin",BASE).header("Content-Type","application/json");
        if(token!=null)request.header("Authorization","Bearer "+token);
        if(path.equals("/onboarding/requests")&&method.equals("POST"))request.header("Idempotency-Key",fixtureKey("onboarding"));
        if(path.equals("/assignments")&&method.equals("POST"))request.header("Idempotency-Key",fixtureKey("offer"));
        Instant started=Instant.now();var response=client.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());
        record("/api/v1"+path,method,response.statusCode(),"SYNTHETIC_ADMIN_SETUP_HTTP",started);
        if(response.statusCode()!=expected){try{String code=JSON.readTree(response.body()).path("code").asText();if(code.matches("[A-Z][A-Z0-9_]{0,100}"))state.put("lastServerErrorCode",code);}catch(Exception ignored){}throw new IllegalStateException("HTTP_EXPECTATION_FAILED");}
        return response.body().length==0?JSON.createObjectNode():JSON.readTree(response.body());
    }
    private void record(String path,String method,int status,String channel,Instant started){httpEvents.add(Map.of("path",path,"method",method,"status",status,"channel",channel,"startedAt",started.toString(),"finishedAt",Instant.now().toString()));}
    private Map<String,Integer> ledger(JdbcTemplate db){var out=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))out.put(role,db.queryForObject("select count(*) from pilot_account_creation where role=?",Integer.class,role));return out;}
    private Map<String,Object> antecedents(JdbcTemplate db,JsonNode baseline)throws Exception{
        var result=new LinkedHashMap<String,Object>();
        for(String table:List.of("pilot_account_creation","admin_terms_acceptance","scheduled_service","assignment","incident","announcement","audit_event","report_emission")){
            String key=table.equals("pilot_account_creation")?"user_id":"id";var rows=new TreeMap<String,String>();
            for(var row:db.queryForList("select "+key+"::text row_id,encode(sha256(convert_to(to_jsonb(t)::text,'UTF8')),'hex') row_hash from "+table+" t order by "+key)){
                String id=row.get("row_id").toString();if(baseline!=null&&!baseline.path(table).has(id))continue;
                rows.put(id,row.get("row_hash").toString());
            }result.put(table,rows);
        }return result;
    }
    private synchronized void savePrivate()throws Exception{
        Path temporary=PRIVATE.resolveSibling("l08-connected-"+UUID.randomUUID()+".private");
        ManualL01HumanTrialServer.writeRestricted(temporary,JSON.writeValueAsBytes(secrets));
        Files.move(temporary,PRIVATE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
    }
    private synchronized void persist()throws Exception{
        state.put("phase",phase);state.put("observedAt",Instant.now().toString());state.put("httpEvents",List.copyOf(httpEvents));
        Path temporary=STATE.resolveSibling("l08-state-"+UUID.randomUUID()+".tmp");
        Files.write(temporary,JSON.writeValueAsBytes(state),StandardOpenOption.CREATE_NEW);
        Files.move(temporary,STATE,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        Files.createDirectories(evidence.getParent());
        var safe=JSON.convertValue(state,new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});safe.remove("dni");safe.remove("email");
        byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(safe);
        Files.write(evidence.resolveSibling("observacion-"+UUID.randomUUID()+".json"),bytes,StandardOpenOption.CREATE_NEW);
        Files.write(evidence,bytes);
    }
    private void observe()throws Exception{
        check(jdbc!=null,"BACKEND_REQUIRED_FOR_OBSERVATION");
        boolean complete=false;long gps=0;
        if(!text("serviceId").isEmpty()){
            complete="COMPLETED".equals(jdbc.queryForObject("select status from scheduled_service where id=? and organization_id=?",String.class,UUID.fromString(text("serviceId")),ORG));
            gps=jdbc.queryForObject("select count(*) from service_gps_point p join service_execution_segment e on e.id=p.segment_id where e.service_id=? and p.driver_id=?",Long.class,UUID.fromString(text("serviceId")),UUID.fromString(text("driverId")));
        }
        boolean pass=Boolean.TRUE.equals(state.get("humanPhoneInteractionConfirmed"))&&Boolean.TRUE.equals(state.get("humanPhysicalGpsConfirmed"))
            &&Boolean.TRUE.equals(state.get("phoneLoginObserved"))&&Boolean.TRUE.equals(state.get("phoneAvailabilityObserved"))
            &&Boolean.TRUE.equals(state.get("phoneConsentObserved"))&&Boolean.TRUE.equals(state.get("phone-start-observed"))
            &&Boolean.TRUE.equals(state.get("phone-complete-observed"))&&Boolean.TRUE.equals(state.get("phoneLogoutObserved"))&&complete&&gps>0;
        state.put("persistedCaseGpsPointCount",gps);state.put("persistedCaseServiceCompleted",complete);
        state.put("manualPhoneResult",pass?"PASS_PHYSICAL_FOREGROUND_CASE_ONLY":"PENDING");
        state.put("batteryOrTalkBackAccreditedByThisResult",false);state.put("terminalGeometryAccredited",false);state.put("pilotEnabled",false);persist();
        console.printf("L08_CONNECTED_RUN_ID=%s%nMANUAL_PHONE_RESULT=%s%nEVIDENCE_FILE=%s%nEVIDENCE_SHA256=%s%nTEST_CLEANUP_RELEASED=%s%n",RUN,state.get("manualPhoneResult"),ROOT.relativize(evidence),sha(Files.readAllBytes(evidence)),state.get("TEST_CLEANUP_RELEASED"));
    }
    private void cleanup(JdbcTemplate inspect)throws Exception{
        phase="EXACT_SELECTIVE_RETIREMENT";check("PASS_PHYSICAL_FOREGROUND_CASE_ONLY".equals(state.get("manualPhoneResult")),"PERSONAL_PHONE_AND_PERSISTENCE_REQUIRED");state.put("cleanupStarted",true);persist();adminToken=admin();
        var q=call("/drivers/"+text("driverId")+"/qualification","GET",null,adminToken,200);
        for(var p:q.path("periods"))if("AUTHORIZED".equals(p.path("status").asText()))q=call("/drivers/"+text("driverId")+"/qualification/periods/"+p.path("id").asText()+"/revoke","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",fixtureKey("retire-period"),"reason","SYNTHETIC L08 selective retirement"),adminToken,200);
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
            UUID account=UUID.fromString(text("accountId")),driver=UUID.fromString(text("driverId"));var user=app.getBean(AppUserRepository.class).findById(account).orElseThrow();
            check(EMAIL.equals(user.getEmail())&&ORG.equals(user.getOrganizationId())&&user.getRole()==UserRole.CONDUCTOR,"EXACT_SYNTHETIC_FIXTURE_REQUIRED");
            check(jdbc.queryForObject("select count(*) from assignment where driver_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')",Integer.class,driver)==0,"NO_LIVE_COMMITMENTS_REQUIRED");
            check(jdbc.queryForObject("select count(*) from service_execution_segment where driver_id=? and ended_at is null",Integer.class,driver)==0,"NO_OPEN_SEGMENTS_REQUIRED");
            app.getBean(DriverRepository.class).findById(driver).orElseThrow().deactivate();user.deactivate();
            app.getBean(VehicleRepository.class).findById(UUID.fromString(text("vehicleId"))).orElseThrow().deactivate();app.getBean(TransportGroupRepository.class).findById(UUID.fromString(text("groupId"))).orElseThrow().deactivate();
            app.getBean(RefreshTokenRepository.class).revokeActiveByUserId(account,Instant.now());
            app.getBean(pe.rutafija.audit.application.AuditService.class).recordSystem(user.getOrganization(),"L08_SYNTHETIC_FIXTURE_DISABLED","APP_USER",account,Map.of("caseRunId",RUN,"syntheticOnly",true));
        });
        check(ledger(inspect).equals(Map.of("SUPER_ADMIN",1,"ADMIN",3,"CONDUCTOR",7)),"CONSUMED_LEDGER_PRESERVED");
        check(Objects.equals(inspect.queryForObject("select count(*) from admin_terms_acceptance",Integer.class),state.get("termsAcceptancesBefore")),"ACCEPTANCES_PRESERVED");
        var baseline=JSON.valueToTree(state.get("antecedentRowHashes"));check(baseline.equals(JSON.valueToTree(antecedents(inspect,baseline))),"ANTECEDENT_ROWS_PRESERVED");
        state.put("selectiveCleanup","PASS_EXACT_CASE_DISABLED_SESSIONS_AND_PERIOD_REVOKED_HISTORY_PRESERVED");state.put("ledgerAfter",ledger(inspect));persist();
    }
    private void finalizeProtectionAfterGuardClosed()throws Exception{
        if(!Objects.toString(state.get("selectiveCleanup"),"").startsWith("PASS_"))return;
        check(Files.isRegularFile(MARKER)&&RUN.equals(Files.readString(MARKER).strip()),"EXACT_OWNED_MARKER_REQUIRED");
        try(var connection=ManualL01HumanTrial.connect(ManualL01HumanTrial.configuration());var query=connection.createStatement();var row=query.executeQuery("select pg_try_advisory_lock(7518440514)")){
            check(row.next()&&row.getBoolean(1),"OWNED_SESSION_LOCK_MUST_ALREADY_BE_RELEASED");
        }
        persist();Files.delete(MARKER);Files.deleteIfExists(PRIVATE);secrets.clear();
        state.put("TEST_CLEANUP_RELEASED",true);persist();console.printf("TEST_CLEANUP_RELEASED=true%n");
    }
    private static void assertWorkspace()throws Exception{check(Files.isRegularFile(ROOT.resolve("backend/pom.xml"))&&Files.isRegularFile(ROOT.resolve("mobile/pubspec.yaml")),"EXPECTED_WORKSPACE_REQUIRED");}
    private static String sha(byte[] content)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
    private static String safeCode(Exception failure){String message=failure.getMessage();return message!=null&&message.matches("[A-Z][A-Z0-9_]{2,100}")?message:"DETAILS_REDACTED_KEEP_CASE";}
    private static void check(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
}
