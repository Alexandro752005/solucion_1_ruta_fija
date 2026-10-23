package pe.rutafija.support;

import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.RutaFijaApplication;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.security.MessageDigest;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Exact authorized CAP25 installation. This class never loads the shared-test configuration. */
public final class L08CapacityTrial implements AutoCloseable {
    public static final String RUN="860c1cdb-b062-469a-9a3f-156ea23bb656";
    public static final String URL="jdbc:postgresql://127.0.0.1:55439/ruta_fija_test";
    public static final String BASE="http://127.0.0.1:4314";
    static final String TERMS_SHA="20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417";
    static final String PROPOSAL_SHA="cda897e21d2063589d253dd6c4d18ec39811d11cb7231988e63f7195aa8df8fb";
    private static final ObjectMapper JSON=new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public record Participant(String role,String token,String accountId,String organizationId,String driverId,String vehicleId,String groupId) {
        @Override public String toString(){return "Participant[role="+role+",accountId="+accountId+",credentials=redacted]";}
    }
    public record Prepared(ConfigurableApplicationContext app,JdbcTemplate jdbc,Participant owner,List<Participant> admins,List<Participant> conductors) {
        public Prepared { admins=List.copyOf(admins);conductors=List.copyOf(conductors); }
        @Override public String toString(){return "Prepared[synthetic25Only,credentials=redacted]";}
    }

    private final Path root,caseRoot,stateFile,privateFile,marker,evidence;
    private final Map<String,Object> state=new LinkedHashMap<>();
    private final Map<String,Object> secrets=new ConcurrentHashMap<>();
    private final List<Map<String,Object>> events=new ArrayList<>();
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Map<String,String> environment;
    private ConfigurableApplicationContext app;
    private JdbcTemplate jdbc;
    private Connection guard;
    private String phase="PREFLIGHT";

    private L08CapacityTrial(Map<String,String> environment) {
        this.environment=environment;
        root=Path.of(required(environment,"RF_L08_CAP25_ROOT")).toAbsolutePath().normalize();
        caseRoot=root.resolve("backend/.local/l08-cap25-"+RUN);
        stateFile=caseRoot.resolve("dataset-state.json");privateFile=caseRoot.resolve("dataset.private");marker=caseRoot.resolve("dataset.active");
        evidence=root.resolve("docs/evidencia/L-08/cap25-"+RUN);
    }

    public static L08CapacityTrial openFromEnvironment(boolean resume)throws Exception {
        var trial=new L08CapacityTrial(System.getenv());
        try {trial.open(resume);return trial;}catch(Exception failure){trial.recordFailure(failure);trial.close();throw failure;}
    }

    public static void main(String[] args) {
        boolean resume=args.length==1&&Set.of("--resume","--resume-capacity").contains(args[0]);
        boolean capacity=args.length==1&&Set.of("--run-capacity","--resume-capacity").contains(args[0]);
        if(!resume&&!capacity&&!(args.length==1&&"--prepare".equals(args[0]))){System.out.println("L08_CAP25_RESULT=REFUSED_EXACT_PREPARE_OR_RESUME_REQUIRED");System.exit(2);}
        try(var trial=openFromEnvironment(resume)) {
            if(Boolean.TRUE.equals(trial.state.get("cleanupStarted"))) {
                check(capacity,"RESUME_CAPACITY_REQUIRED_FOR_STARTED_CLEANUP");
                trial.cleanup(trial.cleanupReferences());
                System.out.println("L08_CAP25_RESULT=PASS_SELECTIVE_RETIREMENT_RESUMED_NO_LOAD_REPEAT");
                System.out.println("TEST_CLEANUP_RELEASED="+trial.state.get("TEST_CLEANUP_RELEASED"));
                return;
            }
            var prepared=trial.prepare();
            if(capacity) {
                trial.phase="MEASURE_NORMAL_HTTP_AND_INDEPENDENT_COMMIT_VISIBILITY";
                Path resultFile=trial.evidence.resolve("medicion.json");
                Map<String,Object> measured;
                if(Files.exists(resultFile)) {
                    measured=JSON.readValue(Files.readAllBytes(resultFile),FixtureJson.OBJECT_TYPE);
                    check(RUN.equals(measured.get("runId"))&&Boolean.TRUE.equals(measured.get("measured")),"EXISTING_MEASUREMENT_REQUIRES_REVIEW");
                    trial.state.put("measurementReusedWithoutRepeatingRequests",true);
                }else measured=L08CapacityMeasurement.run(prepared,trial.evidence);
                trial.markResult(measured);
                check("PASS_MEASURED_LOCAL_SYNTHETIC_ONLY".equals(measured.get("result")),"MEASURED_CAPACITY_CRITERION_FAILED_KEEP_SAME_CASE");
                trial.phase="COMMITTED_RETIREMENT_RACE_ON_EXACT_SYNTHETIC_EVIDENCE";
                var retirement=L08CapacityRetirementRace.run(trial.app,UUID.fromString(prepared.owner().accountId()),prepared.owner().token(),trial.ownerPassword(),trial.evidence);
                trial.state.put("retirementRace",retirement);trial.persist();
                trial.cleanup(prepared);
                System.out.println("L08_CAP25_RESULT=PASS_MEASURED_LOCAL_SYNTHETIC_ONLY");
                System.out.println("TEST_CLEANUP_RELEASED="+trial.state.get("TEST_CLEANUP_RELEASED"));
                System.out.println("EVIDENCE_FILE="+trial.root.relativize(trial.evidence.resolve("preparacion.json")));
                System.out.println("EVIDENCE_SHA256="+sha(Files.readAllBytes(trial.evidence.resolve("preparacion.json"))));
            }else System.out.println("L08_CAP25_RESULT=PREPARED_NO_LOAD_RESULT");
            System.out.println("L08_CAP25_RUN_ID="+RUN);
            System.out.println("L08_CAP25_LEDGER=1/4/20; MAIL=SIMULATED_ONLY");
        }catch(Exception failure){System.out.println("L08_CAP25_RESULT=STOPPED_KEEP_SAME_CASE; CHECK="+safeCode(failure));System.exit(1);}
    }

    private void open(boolean resume)throws Exception {
        check("L08_CAP25_01_AUTHORIZED".equals(environment.get("RF_L08_CAP25_AUTHORIZATION")),"EXACT_CAP25_AUTHORIZATION_REQUIRED");
        check(URL.equals(environment.get("RF_L08_CAP25_URL")),"EXACT_CAP25_TARGET_REQUIRED");
        check(Files.isRegularFile(root.resolve("backend/pom.xml"))&&Files.isRegularFile(root.resolve("docs/operacion/L-08-CAP25-01-validacion-aislada-propuesta.md")),"EXPECTED_WORKSPACE_REQUIRED");
        check(PROPOSAL_SHA.equals(sha(Files.readAllBytes(root.resolve("docs/operacion/L-08-CAP25-01-validacion-aislada-propuesta.md")))),"AUTHORIZED_PROPOSAL_BYTES_REQUIRED");
        var authorization=JSON.readTree(Files.readAllBytes(root.resolve("docs/evidencia/L-08/CAP25-01-autorizacion.json")));
        check(RUN.equals(authorization.path("caseId").asText())&&"L08-CAP25-01".equals(authorization.path("id").asText())&&"AUTHORIZED_NOT_EXECUTED".equals(authorization.path("status").asText())&&PROPOSAL_SHA.equals(authorization.path("proposalSha256").asText())&&authorization.path("port").asInt()==55439&&authorization.path("maximumSyntheticAccounts").path("CONDUCTOR").asInt()==20&&authorization.path("maximumSyntheticAccounts").path("ADMIN").asInt()==4&&authorization.path("maximumSyntheticAccounts").path("SUPER_ADMIN").asInt()==1&&!authorization.path("writesTo5432Or55438Authorized").asBoolean(true),"EXACT_RECORDED_CAP25_AUTHORIZATION_REQUIRED");
        assertNoLinks(caseRoot);check(Files.isDirectory(caseRoot)&&Files.isRegularFile(caseRoot.resolve("infrastructure-manifest.json")),"OWNED_INFRASTRUCTURE_MANIFEST_REQUIRED");
        var infrastructure=JSON.readTree(Files.readAllBytes(caseRoot.resolve("infrastructure-manifest.json")));
        check(RUN.equals(infrastructure.path("caseId").asText())&&"L08_CAP25_SYNTHETIC_ISOLATED_25".equals(infrastructure.path("purpose").asText())&&"127.0.0.1".equals(infrastructure.path("host").asText())&&infrastructure.path("port").asInt()==55439&&"ruta_fija_test".equals(infrastructure.path("database").asText())&&"rf_cap_owner".equals(infrastructure.path("owner").asText())&&PROPOSAL_SHA.equals(infrastructure.path("proposalSha256").asText())&&"50".equals(infrastructure.path("schemaVersion").asText())&&caseRoot.resolve("cluster/data").equals(Path.of(infrastructure.path("dataDirectory").asText()).toAbsolutePath().normalize()),"EXACT_OWNED_INFRASTRUCTURE_REQUIRED");
        assertPrivateDirectory();
        try(var socket=new ServerSocket()){socket.bind(new InetSocketAddress("127.0.0.1",4314));}
        guard=DriverManager.getConnection(URL,"rf_test",required(environment,"RF_L08_CAP25_TEST_PASSWORD"));
        guard.setAutoCommit(true);var inspect=new JdbcTemplate(new SingleConnectionDataSource(guard,true));
        assertTarget(inspect);
        check(Boolean.TRUE.equals(inspect.queryForObject("select pg_try_advisory_lock(7518440514)",Boolean.class)),"CAP25_CASE_ALREADY_RUNNING");
        check(inspect.queryForObject("select count(*) from pilot_lifecycle",Integer.class)==0,"NO_ACTUAL_PILOT_LIFECYCLE_ALLOWED");
        if(Files.exists(stateFile)) {
            check(resume&&Files.isRegularFile(privateFile)&&Files.isRegularFile(marker)&&RUN.equals(Files.readString(marker).strip()),"EXPLICIT_SAME_CASE_RESUME_REQUIRED");
            state.putAll(JSON.readValue(Files.readAllBytes(stateFile),FixtureJson.OBJECT_TYPE));
            check(RUN.equals(text(state,"runId"))&&!Boolean.TRUE.equals(state.get("retired")),"SAME_OPEN_DATASET_REQUIRED");
            secrets.putAll(JSON.readValue(Files.readAllBytes(privateFile),FixtureJson.OBJECT_TYPE));
            if(state.get("httpEvents") instanceof List<?> old)for(Object event:old)events.add(FixtureJson.object(event));
            check(Objects.equals(secrets.get("jwtFingerprint"),sha(required(environment,"RF_L08_CAP25_JWT").getBytes(StandardCharsets.US_ASCII))),"SAME_RUNTIME_KEY_REQUIRED");
            state.put("resumeCount",((Number)state.getOrDefault("resumeCount",0)).intValue()+1);
        }else {
            check(!resume&&!Files.exists(privateFile)&&!Files.exists(marker),"PARTIAL_CASE_MUST_BE_REVIEWED");
            check(inspect.queryForObject("select count(*) from app_user",Integer.class)==0&&inspect.queryForObject("select count(*) from pilot_account_creation",Integer.class)==0,"EMPTY_NEW_AUTHORIZED_INSTALLATION_REQUIRED");
            check(inspect.queryForObject("select count(*) from person_identity",Integer.class)==0&&inspect.queryForObject("select count(*) from person_identity_hash_key",Integer.class)==0,"NEW_IDENTITY_INSTALLATION_REQUIRED");
            state.put("runId",RUN);state.put("startedAtUtc",Instant.now().toString());state.put("database","ruta_fija_test@127.0.0.1:55439");state.put("role","rf_test");state.put("schema","V50");
            state.put("result","PREPARING_NO_LOAD_RESULT");state.put("executionOrigin","AUTOMATED_SYNTHETIC_ONLY");state.put("mail","SIMULATED_ONLY");state.put("gps","SIMULATED_ONLY_NO_PHYSICAL_CLAIM");state.put("realParticipants",false);state.put("externalSmtpAttempts",0);state.put("otpResendAttempts",0);
            state.put("proposalSha256",PROPOSAL_SHA);state.put("historicalCaps",Map.of("SUPER_ADMIN",1,"ADMIN",4,"CONDUCTOR",20));state.put("actors",new LinkedHashMap<>());state.put("organizations",new LinkedHashMap<>());
            secrets.put("jwtFingerprint",sha(required(environment,"RF_L08_CAP25_JWT").getBytes(StandardCharsets.US_ASCII)));
            Files.writeString(marker,RUN,StandardOpenOption.CREATE_NEW);savePrivate();persist();
        }
        checkCaps(inspect);boot();
    }

    private void boot()throws Exception {
        phase="BOOT_ISOLATED_REAL_BACKEND_WITH_SIMULATED_MAIL";
        var properties=new HashMap<String,Object>();
        properties.put("spring.profiles.active","test");properties.put("spring.config.location","classpath:/application.yml,classpath:/application-test.yml");properties.put("spring.config.import","");
        properties.put("spring.datasource.url",URL);properties.put("spring.datasource.username","rf_test");properties.put("spring.datasource.password",required(environment,"RF_L08_CAP25_TEST_PASSWORD"));
        properties.put("spring.datasource.hikari.maximum-pool-size",30);properties.put("spring.datasource.hikari.minimum-idle",2);properties.put("spring.datasource.hikari.connection-timeout",15000);
        properties.put("spring.flyway.enabled",false);properties.put("spring.flyway.url",URL);properties.put("spring.flyway.user","rf_migrator");properties.put("spring.flyway.password",required(environment,"RF_L08_CAP25_MIGRATOR_PASSWORD"));properties.put("spring.jpa.hibernate.ddl-auto","validate");
        properties.put("server.address","127.0.0.1");properties.put("server.port",4314);properties.put("server.forward-headers-strategy","none");
        properties.put("spring.autoconfigure.exclude","org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration");properties.put("spring.mail.host","");properties.put("spring.mail.username","");properties.put("spring.mail.password","");
        properties.put("app.seed.enabled",false);properties.put("app.mobile.offline-sync.enabled",false);properties.put("app.qualification.maintenance.enabled",false);properties.put("app.mobile.maintenance-enabled",false);properties.put("app.retention.technical-enabled",true);
        properties.put("app.security.admin-terms-gate.enabled",true);properties.put("app.security.person-identity-gate.enabled",true);properties.put("app.security.cors.allowed-origins",BASE);properties.put("app.security.jwt.secret-base64",required(environment,"RF_L08_CAP25_JWT"));properties.put("app.security.refresh-cookie.secure",false);
        properties.put("debug",false);properties.put("trace",false);properties.put("spring.main.banner-mode","off");properties.put("logging.level.root","ERROR");
        for(String logger:List.of("org.springframework.boot.SpringApplication","org.springframework.web","org.springframework.security","org.springframework.jdbc.core","org.hibernate.SQL","org.hibernate.orm.jdbc.bind","org.hibernate.engine.jdbc.spi.SqlExceptionHelper","pe.rutafija.shared.exception.GlobalExceptionHandler"))properties.put("logging.level."+logger,"OFF");
        var env=new StandardEnvironment();env.getPropertySources().addFirst(new MapPropertySource("exactAuthorizedL08CapacityInstallation",properties));
        var boot=new SpringApplication(RutaFijaApplication.class);boot.setEnvironment(env);boot.setAddCommandLineProperties(false);boot.setRegisterShutdownHook(false);
        OtpEmailSender mail=(recipient,code,expiry)->{
            check(recipient.matches("(?:sa|admin[0-9]{2}|conductor[0-9]{2})\\.cap25@rutafija\\.test"),"SYNTHETIC_DATASET_RECIPIENT_ONLY");
            secrets.put("otp:"+recipient,code);secrets.put("otpExpires:"+recipient,expiry.toString());
            try{savePrivate();}catch(Exception failure){throw new IllegalStateException("PRIVATE_MAIL_STATE_NOT_SAVED");}
        };
        boot.addInitializers(context->context.addBeanFactoryPostProcessor(factory->{
            var registry=(BeanDefinitionRegistry)factory;
            if(registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
            check(registry.containsBeanDefinition("smtpOtpEmailSender"),"EXPECTED_SMTP_ADAPTER_REQUIRED");registry.removeBeanDefinition("smtpOtpEmailSender");factory.registerSingleton("smtpOtpEmailSender",mail);
        }));
        app=boot.run();jdbc=app.getBean(JdbcTemplate.class);assertTarget(jdbc);check(app.getBean(OtpEmailSender.class)==mail,"NO_EXTERNAL_MAIL_ALLOWED");
        check(app.getEnvironment().getProperty("app.security.admin-terms-gate.enabled",Boolean.class,false)&&app.getEnvironment().getProperty("app.security.person-identity-gate.enabled",Boolean.class,false),"NORMAL_IDENTITY_AND_TERMS_GATES_REQUIRED");
    }

    public Prepared prepare()throws Exception {
        try {
            phase="IMPORT_EXACT_ALREADY_APPROVED_TERMS";publishApprovedTerms();
            phase="SYNTHETIC_OWNER_BOOTSTRAP";Participant owner=owner();
            var admins=new ArrayList<Participant>();var conductors=new ArrayList<Participant>();
            for(int index=1;index<=4;index++) {
                phase="NORMAL_ORGANIZATION_AND_ADMIN_"+index;String org=organization(index,owner.token());Participant admin=admin(index,org,owner.token());admins.add(admin);
                for(int offset=1;offset<=5;offset++){int number=(index-1)*5+offset;phase="NORMAL_CONDUCTOR_ONBOARDING_"+number;conductors.add(conductor(number,org,admin.token()));}
            }
            check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",4,"CONDUCTOR",20)),"EXACT_AUTHORIZED_25_HISTORICAL_CREATIONS_REQUIRED");
            check(jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class)==4,"FOUR_AUTOMATED_SYNTHETIC_ACCEPTANCES_REQUIRED");
            check(jdbc.queryForObject("select count(*) from person_identity",Integer.class)==25,"TWENTY_FIVE_DISTINCT_REVIEWED_IDENTITIES_REQUIRED");
            check(jdbc.queryForObject("select count(*) from organization",Integer.class)==4,"EXACT_FOUR_ORGANIZATIONS_REQUIRED");
            check(jdbc.queryForObject("select count(*) from pilot_lifecycle",Integer.class)==0,"NO_PILOT_LIFECYCLE_REQUIRED");
            // Renew ordinary sessions only after all setup has finished, so early
            // participants do not enter the measurement with aged access tokens.
            owner=withToken(owner,login("sa.cap25@rutafija.test",password("owner"),false));
            for(int i=0;i<admins.size();i++)admins.set(i,withToken(admins.get(i),login(String.format(Locale.ROOT,"admin%02d.cap25@rutafija.test",i+1),password("admin-"+(i+1)),false)));
            for(int i=0;i<conductors.size();i++)conductors.set(i,withToken(conductors.get(i),login(String.format(Locale.ROOT,"conductor%02d.cap25@rutafija.test",i+1),password("conductor-"+(i+1)),true)));
            state.put("ledger",ledger(jdbc));state.put("termsAcceptances",4);state.put("identityBindings",25);state.putIfAbsent("preparedAtUtc",Instant.now().toString());state.put("result","PREPARED_NO_LOAD_RESULT");state.put("loadMeasured",false);persist();
            return new Prepared(app,jdbc,owner,admins,conductors);
        }catch(Exception failure){recordFailure(failure);throw failure;}
    }

    private void publishApprovedTerms()throws Exception {
        var folder=root.resolve("docs/legal/publicado");byte[] bytes=Files.readAllBytes(folder.resolve("terminos-admin-v1.0.0.md"));
        String content=ApprovedAdminTermsPublication.validateApproval(bytes,JSON.readTree(Files.readAllBytes(folder.resolve("terminos-admin-v1.0.0.aprobacion.json"))),TERMS_SHA);
        var repository=app.getBean(AdminTermsDocumentRepository.class);
        var document=repository.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc("ADMIN_TERMS").orElse(null);
        if(document==null) {
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->repository.saveAndFlush(AdminTermsDocument.publish("ADMIN_TERMS","1.0.0",content,Instant.now())));
            document=repository.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc("ADMIN_TERMS").orElseThrow();
        }
        check("1.0.0".equals(document.getVersion())&&TERMS_SHA.equals(document.getContentSha256())&&content.equals(document.getContent())&&!document.isSynthetic(),"IMMUTABLE_TERMS_CORRESPONDENCE_REQUIRED");
        state.put("termsDocumentId",document.getId().toString());state.put("termsVersion","1.0.0");state.put("termsSha256",TERMS_SHA);persist();
    }

    private Participant owner()throws Exception {
        var saved=actor("owner");String email="sa.cap25@rutafija.test";String password=password("owner");
        var repository=app.getBean(AppUserRepository.class);
        var matches=jdbc.queryForList("select id::text from app_user where email=? and role='SUPER_ADMIN' and organization_id is null and full_name='SYNTHETIC CAP25 OWNER'",String.class,email);
        check(matches.size()<=1,"AMBIGUOUS_OWNER_KEEP_CASE");
        if(matches.isEmpty()) {
            check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",0,"ADMIN",0,"CONDUCTOR",0))&&jdbc.queryForObject("select count(*) from app_user",Integer.class)==0,"NEW_SYNTHETIC_OWNER_BOOTSTRAP_ONLY");
            // Same test bootstrap facility as ManualL01HumanTrialServer: factory,
            // ordinary encoder/repository, verified synthetic email, normal ledger trigger.
            String hash=app.getBean(PasswordEncoder.class).encode(password);
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
                AppUser owner=AppUser.superAdmin(email,hash,"SYNTHETIC CAP25 OWNER");owner.verifyEmail(Instant.now());owner=repository.saveAndFlush(owner);saved.put("accountId",owner.getId().toString());
            });persist();
        }else saved.put("accountId",matches.getFirst());
        String token=login(email,password,false);
        if(jdbc.queryForObject("select count(*) from person_identity where user_id=?",Integer.class,UUID.fromString(text(saved,"accountId")))==0)
            call("/platform/identity/self-enrollment","POST",Map.of("currentPassword",password,"ownerDeclaration",true,"identityReview",identity("80000001","SYNTHETIC CAP OWNER REVIEW")),token,200);
        check(app.getBean(InvitationCodeHasher.class).hashOrigin("person-identity:key-fingerprint:v1").equals(jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1",String.class)),"STABLE_DATASET_IDENTITY_KEY_REQUIRED");
        saved.put("identityVerified",true);saved.put("prepared",true);persist();return participant("SUPER_ADMIN",token,saved,null);
    }

    private String organization(int number,String ownerToken)throws Exception {
        var organizations=map(state,"organizations");String key="org-"+number,name="SYNTHETIC CAP25 ORG "+number;
        var existing=jdbc.queryForList("select id::text from organization where legal_name=?",String.class,name);check(existing.size()<=1,"AMBIGUOUS_ORGANIZATION_KEEP_CASE");
        if(!existing.isEmpty())organizations.put(key,existing.getFirst());
        if(!organizations.containsKey(key)){organizations.put(key,call("/organizations","POST",Map.of("legalName",name,"tradeName","SYNTHETIC ONLY","timezone","America/Lima"),ownerToken,201).path("id").asText());persist();}
        return text(organizations,key);
    }

    private Participant admin(int number,String org,String ownerToken)throws Exception {
        String key="admin-"+number,email=String.format(Locale.ROOT,"admin%02d.cap25@rutafija.test",number);var saved=actor(key);String password=password(key);
        var matches=jdbc.queryForList("select id::text from app_user where email=? and organization_id=? and role='ADMIN'",String.class,email,UUID.fromString(org));check(matches.size()<=1,"AMBIGUOUS_ADMIN_KEEP_CASE");
        if(!matches.isEmpty())saved.put("accountId",matches.getFirst());
        if(!saved.containsKey("accountId")) {
            checkCaps(jdbc);check(ledger(jdbc).get("ADMIN")<4,"ADMIN_HISTORICAL_QUOTA_EXHAUSTED");
            var created=call("/platform/admins","POST",Map.of("organizationId",org,"email",email,"fullName","SYNTHETIC CAP25 ADMIN "+number,"reason","CAP25 AUTOMATED SYNTHETIC DATASET ONLY","identityReview",identity("8100000"+number,"SYNTHETIC CAP ADMIN REVIEW "+number)),ownerToken,200);
            check("PENDING_CLAIM".equals(created.path("status").asText()),"NORMAL_INACTIVE_ADMIN_REQUIRED");saved.put("accountId",created.path("adminUserId").asText());saved.put("activationId",created.path("activationId").asText());secrets.put(key+":credential",created.path("credential").asText());savePrivate();persist();
        }
        UUID id=UUID.fromString(text(saved,"accountId"));
        check(!Boolean.TRUE.equals(saved.get("prepared"))||Boolean.TRUE.equals(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,id)),"RETIRED_ADMIN_MUST_NOT_BE_REACTIVATED");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("select active from app_user where id=?",Boolean.class,id))) {
            if(!secrets.containsKey(key+":activationToken")) {
                check(secrets.containsKey(key+":credential"),"SAME_ADMIN_CREDENTIAL_NOT_AVAILABLE_KEEP_ACCOUNT");
                var claimed=call("/admin-activation/claim","POST",Map.of("credential",secrets.get(key+":credential"),"newPassword",password),null,200);
                secrets.put(key+":activationToken",claimed.path("activationToken").asText());secrets.remove(key+":credential");savePrivate();
            }
            Map<String,String> scoped=Map.of("X-Admin-Activation-Token",Objects.toString(secrets.get(key+":activationToken")));
            var status=call("/admin-activation/status","GET",null,null,scoped,200);
            if(!status.path("emailVerified").asBoolean()) {
                if(!secrets.containsKey(key+":verificationToken")) {
                    check(!Boolean.TRUE.equals(saved.get("otpBeginAttempted")),"EXISTING_OTP_MUST_BE_RECOVERED_WITHOUT_RESEND");saved.put("otpBeginAttempted",true);persist();
                    var ticket=call("/admin-activation/otp/begin","POST",null,null,scoped,200);secrets.put(key+":verificationToken",ticket.path("verificationToken").asText());savePrivate();
                }
                status=call("/admin-activation/otp/verify","POST",Map.of("verificationToken",secrets.get(key+":verificationToken"),"code",otp(email)),null,scoped,200);
            }
            if(!status.path("termsAccepted").asBoolean()) {
                var terms=call("/admin-activation/terms","GET",null,null,scoped,200);check("1.0.0".equals(terms.path("version").asText())&&TERMS_SHA.equals(terms.path("contentSha256").asText())&&!terms.path("synthetic").asBoolean(true),"EXACT_PUBLISHED_TERMS_REQUIRED");
                if(!Boolean.TRUE.equals(saved.get("negativeTermsGateVerified"))){
                    check(status.path("passwordSet").asBoolean()&&status.path("emailVerified").asBoolean()&&status.path("identityVerified").asBoolean(),"OTHER_ACTIVATION_PRECONDITIONS_REQUIRED");
                    var gate=call("/admin-activation/complete","POST",null,null,scoped,409);
                    check("RESOURCE_CONFLICT".equals(gate.path("code").asText())&&"Falta la aceptacion expresa de los terminos aprobados actualmente vigentes.".equals(gate.path("message").asText()),"TERMS_SPECIFIC_GATE_REQUIRED");saved.put("negativeTermsGateVerified",true);persist();
                }
                call("/admin-activation/terms","POST",Map.of("version","1.0.0","contentSha256",TERMS_SHA,"accepted",true),null,scoped,200);
                saved.put("acceptanceOrigin","AUTOMATED_SYNTHETIC_DATASET_NOT_A_HUMAN_ACCEPTANCE");persist();
            }
            status=call("/admin-activation/complete","POST",null,null,scoped,200);check("COMPLETED".equals(status.path("status").asText()),"NORMAL_ADMIN_COMPLETION_REQUIRED");
        }
        String token=login(email,password,false);call("/groups","GET",null,token,200);
        if(!Boolean.TRUE.equals(saved.get("acceptanceOriginAudited"))) {
            var organization=app.getBean(OrganizationRepository.class).findById(UUID.fromString(org)).orElseThrow();
            app.getBean(AuditService.class).recordSystem(organization,"L08_CAP25_AUTOMATED_TERMS_ACCEPTED","APP_USER",id,Map.of("runId",RUN,"source","AUTOMATED_SYNTHETIC_ONLY","syntheticOnly",true,"documentVersion","1.0.0","contentSha256",TERMS_SHA));saved.put("acceptanceOriginAudited",true);
        }
        saved.put("acceptanceOrigin","AUTOMATED_SYNTHETIC_DATASET_NOT_A_HUMAN_ACCEPTANCE");saved.put("organizationId",org);saved.put("prepared",true);persist();return participant("ADMIN",token,saved,org);
    }

    private Participant conductor(int number,String org,String adminToken)throws Exception {
        String key="conductor-"+number,email=String.format(Locale.ROOT,"conductor%02d.cap25@rutafija.test",number),dni=String.format(Locale.ROOT,"820000%02d",number);var saved=actor(key);String password=password(key);
        String groupName="SYNTHETIC CAP25 GROUP "+number,plate="SYN-C25-"+number;
        recoverResource(saved,"groupId","select id::text from transport_group where organization_id=? and name=?",org,groupName);
        recoverResource(saved,"vehicleId","select id::text from vehicle where organization_id=? and plate=?",org,plate);
        if(!saved.containsKey("groupId")){saved.put("groupId",call("/groups","POST",Map.of("name",groupName,"description","SYNTHETIC CAP25 ONLY"),adminToken,201).path("id").asText());persist();}
        if(!saved.containsKey("vehicleId")){saved.put("vehicleId",call("/vehicles","POST",Map.of("plate",plate,"brand","SYNTHETIC","model","CAP25","year",2026),adminToken,201).path("id").asText());persist();}
        var requests=jdbc.queryForList("select r.id::text from conductor_onboarding_request r join conductor_onboarding_identity i on i.id=r.identity_id where i.email=? and r.organization_id=?",String.class,email,UUID.fromString(org));check(requests.size()<=1,"AMBIGUOUS_ONBOARDING_KEEP_SAME_PERSON");
        if(!requests.isEmpty())saved.put("requestId",requests.getFirst());
        if(!saved.containsKey("requestId")) {
            if(!secrets.containsKey(key+":invitationCode")) {
                check(jdbc.queryForObject("select count(*) from organization_invitation where organization_id=? and recipient_email=?",Integer.class,UUID.fromString(org),email)==0,"EXISTING_INVITATION_NEEDS_SAME_CASE_REVIEW");
                var invite=call("/invitations","POST",Map.of("recipientEmail",email),adminToken,201);saved.put("invitationId",invite.path("id").asText());secrets.put(key+":invitationCode",invite.path("code").asText());savePrivate();persist();
            }
            if(!secrets.containsKey(key+":verificationToken")) {
                check(!Boolean.TRUE.equals(saved.get("onboardingBeginAttempted")),"EXISTING_ONBOARDING_MUST_BE_RECOVERED_WITHOUT_RESEND");saved.put("onboardingBeginAttempted",true);persist();
                var start=call("/onboarding/requests","POST",Map.of("email",email,"password",password,"fullName","SYNTHETIC CAP25 CONDUCTOR "+number,"invitationCode",secrets.get(key+":invitationCode")),null,Map.of("Idempotency-Key",RUN+":"+key),202);
                secrets.put(key+":verificationToken",start.path("verificationToken").asText());savePrivate();
            }
            var verified=call("/onboarding/otp/verify","POST",Map.of("verificationToken",secrets.get(key+":verificationToken"),"code",otp(email)),null,202);saved.put("requestId",verified.path("requestId").asText());persist();
        }
        String path="/onboarding/requests/"+text(saved,"requestId");var dossier=call(path+"/documentary-expediente","GET",null,adminToken,200);
        if(!dossier.path("personalApproved").asBoolean()) {
            var body=new LinkedHashMap<String,Object>();body.put("expectedVersion",dossier.path("version").asLong());body.put("operationKey",RUN+":"+key+":personal");body.put("modality","TAXI");body.put("documents",personalDocs(number,dni));
            dossier=call(path+"/documentary-expediente","PUT",body,adminToken,200);
            dossier=call(path+"/approve","POST",Map.of("expectedVersion",dossier.path("version").asLong(),"operationKey",RUN+":"+key+":approve","reason","SYNTHETIC CAP25 NORMAL DOCUMENTARY REVIEW"),adminToken,200);
        }
        if(dossier.path("provisionedDriverId").isNull()||dossier.path("provisionedDriverId").isMissingNode()) {
            checkCaps(jdbc);check(ledger(jdbc).get("CONDUCTOR")<20,"CONDUCTOR_HISTORICAL_QUOTA_EXHAUSTED");
            dossier=call(path+"/provision","POST",Map.of("expectedVersion",dossier.path("version").asLong(),"operationKey",RUN+":"+key+":provision","reason","SYNTHETIC CAP25 NORMAL PROVISION","groupId",text(saved,"groupId")),adminToken,200);
        }
        saved.put("accountId",dossier.path("provisionedUserId").asText());saved.put("driverId",dossier.path("provisionedDriverId").asText());saved.put("organizationId",org);persist();
        qualify(number,saved,adminToken);
        String token=login(email,password,true);var availability=call("/mobile/availability","GET",null,token,200);
        if(!Boolean.TRUE.equals(saved.get("prepared"))&&!"DISPONIBLE".equals(availability.path("status").asText())) {
            check(Set.of("NO_DISPONIBLE","DESCANSO").contains(availability.path("status").asText()),"EXISTING_OPERATION_MUST_NOT_BE_RESET_FOR_PREPARATION");
            availability=call("/mobile/availability","PUT",Map.of("status","DISPONIBLE"),token,200);
        }
        if(!Boolean.TRUE.equals(saved.get("prepared")))check("DISPONIBLE".equals(availability.path("status").asText()),"NORMAL_AVAILABLE_CONDUCTOR_REQUIRED");
        else saved.put("existingAvailabilityReusedWithoutReset",true);
        saved.put("prepared",true);persist();return participant("CONDUCTOR",token,saved,org);
    }

    private void qualify(int number,Map<String,Object> saved,String adminToken)throws Exception {
        String path="/drivers/"+text(saved,"driverId")+"/qualification",key="conductor-"+number;var q=call(path,"GET",null,adminToken,200);
        if(Boolean.TRUE.equals(saved.get("prepared"))){check(q.path("operationalEnabled").asBoolean(),"RETIRED_OR_CHANGED_QUALIFICATION_REQUIRES_REVIEW");return;}
        if(!Boolean.TRUE.equals(saved.get("vehicleReviewed"))){q=call(path+"/reviews","POST",reviews(number,"VEHICLE",q.path("version").asLong(),saved),adminToken,200);saved.put("vehicleReviewed",true);persist();}
        if(!Boolean.TRUE.equals(saved.get("companyReviewed"))){q=call(path+"/reviews","POST",reviews(number,"COMPANY",q.path("version").asLong(),saved),adminToken,200);saved.put("companyReviewed",true);persist();}
        if(!saved.containsKey("periodId")) {
            for(var period:q.path("periods"))if(text(saved,"vehicleId").equals(period.path("vehicleId").asText())&&"AUTHORIZED".equals(period.path("status").asText()))saved.put("periodId",period.path("id").asText());
            if(!saved.containsKey("periodId")){q=call(path+"/periods","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",RUN+":"+key+":period","reason","SYNTHETIC CAP25 UNIT PERIOD","vehicleId",text(saved,"vehicleId"),"kind","OWN","startAt",Instant.now().minusSeconds(60).toString()),adminToken,200);saved.put("periodId",q.path("periods").get(q.path("periods").size()-1).path("id").asText());}persist();
        }
        if(!q.path("operationalEnabled").asBoolean())q=call(path+"/activate","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",RUN+":"+key+":activate","reason","SYNTHETIC CAP25 NORMAL ACTIVATION"),adminToken,200);
        check(q.path("operationalEnabled").asBoolean(),"NORMAL_QUALIFICATION_REQUIRED");
    }

    private List<Map<String,Object>> personalDocs(int number,String dni) {
        var fields=List.of(Map.of("documentType","DNI","documentNumber",dni,"fullName","SYNTHETIC CAP25 CONDUCTOR "+number,"issuer","SYNTHETIC"),Map.of("licenseNumber","SYN-C25-LICENSE-"+number,"licenseClass","A","licenseCategory","A-IIA","restrictions","NINGUNA","issuer","SYNTHETIC"));
        var out=new ArrayList<Map<String,Object>>();
        for(int i=0;i<5;i++){var d=new LinkedHashMap<String,Object>();d.put("requirementId","CON-0"+(i+1));d.put("fields",i<2?fields.get(i):Map.of());d.put("expiryMode",i==1?"DATED":"NO_EXPIRY");if(i==1)d.put("validUntil",Instant.now().plus(Duration.ofDays(90)).toString());d.put("applicability","CONFIRMED");d.put("condition","SYNTHETIC applicability only");d.put("sourceReference","SYNTHETIC ORIGINAL NO REAL TITLE");d.put("result",i<2?"CONFORME":"NO_APLICA_JUSTIFICADO");d.put("reviewReference","SYNTHETIC CONTROLLED REVIEW");d.put("observation","SYNTHETIC optional requirement not applicable");out.add(d);}return out;
    }
    private Map<String,Object> reviews(int number,String scope,long version,Map<String,Object> saved) {
        var command=new LinkedHashMap<String,Object>();command.put("expectedVersion",version);command.put("operationKey",RUN+":conductor-"+number+":review-"+scope);command.put("reason","SYNTHETIC CAP25 NORMAL REVIEW");command.put("scope",scope);if("VEHICLE".equals(scope))command.put("vehicleId",text(saved,"vehicleId"));
        var docs=new ArrayList<Map<String,Object>>();
        for(var requirement:pe.rutafija.qualification.application.QualificationCatalog.REQUIREMENTS)if(requirement.scope().equals(scope)) {
            var fields=new LinkedHashMap<String,String>();for(String field:requirement.fieldKeys())fields.put(field,switch(field){case "plate"->"SYN-C25-"+number;case "category"->"M1";case "bodyType"->"SEDAN";case "brand"->"SYNTHETIC";case "model"->"CAP25";case "year"->"2026";case "insuranceType"->"SOAT";case "inspectionResult"->"APROBADO";case "modality"->"TAXI";case "serviceScope"->"SYNTHETIC CHICLAYO";default->"SYNTHETIC reference";});
            var d=new LinkedHashMap<String,Object>();d.put("requirementId",requirement.id());d.put("fields",fields);d.put("expiryMode","DATED");d.put("validUntil",Instant.now().plus(Duration.ofDays(60)).toString());d.put("applicability","CONFIRMED");d.put("condition","SYNTHETIC applicability");d.put("sourceReference","SYNTHETIC NOT A REAL TITLE");d.put("result",requirement.id().equals("EMP-03")?"NO_APLICA_JUSTIFICADO":"CONFORME");d.put("reviewReference","SYNTHETIC visual review");docs.add(d);
        }command.put("documents",docs);return command;
    }

    public JsonNode call(String path,String method,Object body,String token,int expected)throws Exception{return call(path,method,body,token,Map.of(),expected);}
    public JsonNode call(String path,String method,Object body,String token,Map<String,String> headers,int expected)throws Exception {
        check(path.startsWith("/")&&!path.contains("://")&&!path.contains(".."),"LOCAL_API_PATH_REQUIRED");
        String correlation=UUID.randomUUID().toString();var request=HttpRequest.newBuilder(URI.create(BASE+"/api/v1"+path)).timeout(Duration.ofSeconds(30)).header("Origin",BASE).header("Content-Type","application/json").header("X-Correlation-Id",correlation).header("User-Agent","RutaFija-CAP25-Automated-Synthetic/1");
        if(token!=null)request.header("Authorization","Bearer "+token);headers.forEach(request::header);
        Instant started=Instant.now();HttpResponse<byte[]> response;
        try{response=http.send(request.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(JSON.writeValueAsBytes(body))).build(),HttpResponse.BodyHandlers.ofByteArray());}
        catch(Exception failure){state.put("uncertainRequest",Map.of("method",method,"path",path,"startedAtUtc",started.toString(),"correlationId",correlation));persist();throw new IllegalStateException("HTTP_RESULT_UNCERTAIN_KEEP_CASE_NO_AUTOMATIC_RETRY");}
        JsonNode parsed=response.body().length==0?JSON.createObjectNode():JSON.readTree(response.body());
        var event=new LinkedHashMap<String,Object>();event.put("sequence",events.size()+1);event.put("method",method);event.put("path",path);event.put("startedAtUtc",started.toString());event.put("finishedAtUtc",Instant.now().toString());event.put("status",response.statusCode());event.put("correlationId",correlation);event.put("origin","AUTOMATED_SYNTHETIC_DATASET_PREPARATION");
        if(response.statusCode()>=400){String code=parsed.path("code").asText();event.put("serverCode",code.matches("[A-Z][A-Z0-9_]{1,79}")?code:"REDACTED_UNKNOWN_CODE");}
        events.add(event);persist();check(response.statusCode()==expected,"HTTP_EXPECTATION_FAILED_"+response.statusCode());return parsed;
    }

    public String login(String email,String password,boolean mobile)throws Exception {
        var result=call(mobile?"/mobile/auth/login":"/auth/login","POST",Map.of("email",email,"password",password),null,200);String token=result.path("accessToken").asText();check(!token.isBlank(),"NORMAL_LOGIN_ACCESS_TOKEN_REQUIRED");return token;
    }
    public ConfigurableApplicationContext app(){return app;}
    public JdbcTemplate jdbc(){return jdbc;}
    public Path evidenceDirectory(){return evidence;}
    public Map<String,Object> state(){return state;}
    public void markResult(Map<String,Object> result)throws Exception{state.put("measurement",result);persist();}
    /** In-memory handoff only for the approved synthetic technical procedure. Never print or archive. */
    String ownerPassword(){return Objects.toString(secrets.get("owner:password"),"");}

    private Prepared cleanupReferences()throws Exception {
        boolean accountsRetired=jdbc.queryForObject("select count(*) from app_user where active",Integer.class)==0;
        var owner=participant("SUPER_ADMIN",accountsRetired?"":login("sa.cap25@rutafija.test",password("owner"),false),actor("owner"),null);
        var admins=new ArrayList<Participant>();var conductors=new ArrayList<Participant>();
        for(int i=1;i<=4;i++){
            var a=actor("admin-"+i);String token=accountsRetired?"":login(String.format(Locale.ROOT,"admin%02d.cap25@rutafija.test",i),password("admin-"+i),false);
            admins.add(participant("ADMIN",token,a,text(a,"organizationId")));
        }
        for(int i=1;i<=20;i++){var a=actor("conductor-"+i);conductors.add(participant("CONDUCTOR","",a,text(a,"organizationId")));}
        return new Prepared(app,jdbc,owner,admins,conductors);
    }

    /** Explicitly authorized retirement of this isolated dataset only. No delete
     * of accounts, acceptances, plans, offers, audit or historical quota rows. */
    private void cleanup(Prepared prepared)throws Exception {
        phase="SELECTIVE_DATASET_RETIREMENT";
        check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",4,"CONDUCTOR",20)),"CAP25_LEDGER_REQUIRED_BEFORE_RETIREMENT");
        check("PASS_MEASURED_LOCAL_SYNTHETIC_ONLY".equals(JSON.readTree(Files.readAllBytes(evidence.resolve("medicion.json"))).path("result").asText()),"ARCHIVED_MEASUREMENT_PASS_REQUIRED_BEFORE_RETIREMENT");
        check("PASS_ISOLATED_COMMITTED_RETIREMENT_RACE_SYNTHETIC_ONLY".equals(JSON.readTree(Files.readAllBytes(evidence.resolve("retiro-concurrencia.json"))).path("result").asText()),"ARCHIVED_RETIREMENT_RACE_REQUIRED");
        state.put("cleanupStarted",true);state.put("TEST_CLEANUP_RELEASED",false);persist();
        boolean alreadyInactive=jdbc.queryForObject("select count(*) from app_user where active",Integer.class)==0;
        if(!alreadyInactive){
            check(jdbc.queryForObject("select count(*) from app_user where active",Integer.class)==25,"PARTIAL_ACCOUNT_RETIREMENT_REQUIRES_REVIEW");
            for(var admin:prepared.admins()) {
                var services=jdbc.queryForList("select id::text from scheduled_service where organization_id=? and status in('PENDING_RESPONSE','CONFIRMED') order by id",String.class,UUID.fromString(admin.organizationId()));
                for(String service:services){
                    var view=call("/scheduled-services/"+service,"GET",null,admin.token(),200);
                    call("/scheduled-services/"+service+"/cancel","POST",Map.of("expectedVersion",view.path("service").path("version").asLong(),"operationKey",RUN+":retire:"+service,"reason","SYNTHETIC CAP25 DATASET RETIREMENT"),admin.token(),200);
                }
            }
            for(var driver:prepared.conductors()) {
                var admin=prepared.admins().stream().filter(a->a.organizationId().equals(driver.organizationId())).findFirst().orElseThrow();
                var q=call("/drivers/"+driver.driverId()+"/qualification","GET",null,admin.token(),200);
                var periods=new ArrayList<String>();for(var p:q.path("periods"))if("AUTHORIZED".equals(p.path("status").asText()))periods.add(p.path("id").asText());
                for(String period:periods)q=call("/drivers/"+driver.driverId()+"/qualification/periods/"+period+"/revoke","POST",Map.of("expectedVersion",q.path("version").asLong(),"operationKey",RUN+":retire:"+period,"reason","SYNTHETIC CAP25 DATASET RETIREMENT"),admin.token(),200);
            }
            check(jdbc.queryForObject("select count(*) from assignment where status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO')",Integer.class)==0&&jdbc.queryForObject("select count(*) from service_execution_segment where ended_at is null",Integer.class)==0,"NO_LIVE_CAP25_COMMITMENTS_REQUIRED");
            new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
                for(var person:allParticipants(prepared)){
                    var user=app.getBean(AppUserRepository.class).findById(UUID.fromString(person.accountId())).orElseThrow();
                    check(user.getEmail().endsWith(".cap25@rutafija.test")&&person.role().equals(user.getRole().name()),"EXACT_CAP25_ACCOUNT_REQUIRED");
                    if(person.driverId()!=null){
                        app.getBean(DriverRepository.class).findById(UUID.fromString(person.driverId())).orElseThrow().deactivate();
                        app.getBean(VehicleRepository.class).findById(UUID.fromString(person.vehicleId())).orElseThrow().deactivate();
                        app.getBean(TransportGroupRepository.class).findById(UUID.fromString(person.groupId())).orElseThrow().deactivate();
                    }
                    user.deactivate();app.getBean(RefreshTokenRepository.class).revokeActiveByUserId(user.getId(),Instant.now());
                    app.getBean(AuditService.class).recordSystem(user.getOrganization(),"L08_CAP25_SYNTHETIC_FIXTURE_DISABLED","APP_USER",user.getId(),Map.of("caseRunId",RUN,"syntheticOnly",true));
                }
            });
        }
        check(jdbc.queryForObject("select count(*) from app_user",Integer.class)==25&&jdbc.queryForObject("select count(*) from app_user where active",Integer.class)==0,"EXACT_25_ACCOUNTS_DISABLED_REQUIRED");
        check(jdbc.queryForObject("select count(*) from refresh_token where revoked_at is null",Integer.class)==0,"ALL_CAP25_REFRESH_SESSIONS_REVOKED_REQUIRED");
        check(jdbc.queryForObject("select count(*) from driver where active",Integer.class)==0&&jdbc.queryForObject("select count(*) from vehicle where active",Integer.class)==0&&jdbc.queryForObject("select count(*) from transport_group where active",Integer.class)==0,"ALL_CAP25_FLEET_RESOURCES_DISABLED_REQUIRED");
        check(ledger(jdbc).equals(Map.of("SUPER_ADMIN",1,"ADMIN",4,"CONDUCTOR",20))&&jdbc.queryForObject("select count(*) from admin_terms_acceptance",Integer.class)==4,"CAP25_HISTORY_AND_ACCEPTANCES_PRESERVED");
        state.put("selectiveRetirement","PASS_DATASET_DISABLED_SESSIONS_PERIODS_REVOKED_HISTORY_LEDGER_PRESERVED");
        state.put("retired",true);state.put("ledgerAfter",ledger(jdbc));state.put("TEST_CLEANUP_RELEASED",false);persist();
        // Archive before releasing only this case's marker. Launcher verifies
        // server closure and cluster ownership separately after this JVM exits.
        Files.write(evidence.resolve("retiro-selectivo.json"),JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(Map.of("runId",RUN,"result",state.get("selectiveRetirement"),"ledger",ledger(jdbc),"termsAcceptances",4,"at",Instant.now().toString())),StandardOpenOption.CREATE_NEW);
        check(Files.isRegularFile(marker)&&RUN.equals(Files.readString(marker).strip()),"EXACT_CAP25_MARKER_REQUIRED_FOR_RELEASE");
        Files.delete(marker);state.put("TEST_CLEANUP_RELEASED",true);state.put("loadMeasured",true);state.put("result","PASS_MEASURED_LOCAL_SYNTHETIC_ONLY");persist();
        // The private case directory and saved secrets are retained for diagnosis;
        // these inactive synthetic credentials no longer authenticate.
    }
    private static List<Participant> allParticipants(Prepared prepared){var all=new ArrayList<Participant>();all.add(prepared.owner());all.addAll(prepared.admins());all.addAll(prepared.conductors());return all;}

    private static Map<String,Object> identity(String dni,String reference){return Map.of("documentType","DNI","documentNumber",dni,"method","IN_PERSON","reference",reference,"originalReviewed",true,"identityConfirmed",true);}
    private Participant participant(String role,String token,Map<String,Object> saved,String org){return new Participant(role,token,text(saved,"accountId"),org,optional(saved,"driverId"),optional(saved,"vehicleId"),optional(saved,"groupId"));}
    private static Participant withToken(Participant p,String token){return new Participant(p.role(),token,p.accountId(),p.organizationId(),p.driverId(),p.vehicleId(),p.groupId());}
    private void recoverResource(Map<String,Object> saved,String key,String sql,String org,String name){var ids=jdbc.queryForList(sql,String.class,UUID.fromString(org),name);check(ids.size()<=1,"AMBIGUOUS_RESOURCE_KEEP_CASE");if(!ids.isEmpty())saved.put(key,ids.getFirst());}
    private String password(String key)throws Exception {String name=key+":password";if(!secrets.containsKey(name)){check(!actor(key).containsKey("accountId"),"SAME_ACCOUNT_PASSWORD_NOT_AVAILABLE_KEEP_CASE");secrets.put(name,"CAP25-Synthetic-"+UUID.randomUUID());savePrivate();}return Objects.toString(secrets.get(name));}
    private String otp(String email){String code=Objects.toString(secrets.get("otp:"+email),"");Instant expiry=Instant.parse(Objects.toString(secrets.get("otpExpires:"+email),Instant.EPOCH.toString()));check(code.matches("[0-9]{6}")&&Instant.now().isBefore(expiry),"SAME_SIMULATED_OTP_REQUIRED_NO_AUTOMATIC_RESEND");return code;}
    private Map<String,Object> actor(String key){return map(map(state,"actors"),key);}
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Map<String,Object> parent,String key){Object current=parent.get(key);if(current==null){var created=new LinkedHashMap<String,Object>();parent.put(key,created);return created;}check(current instanceof Map<?,?>,"CASE_STATE_SHAPE_INVALID");return (Map<String,Object>)current;}
    private static String text(Map<String,Object> source,String key){return Objects.toString(source.get(key),"");}
    private static String optional(Map<String,Object> source,String key){String value=text(source,key);return value.isEmpty()?null:value;}
    private static String required(Map<String,String> env,String key){String value=env.get(key);check(value!=null&&!value.isBlank(),"PRIVATE_ENVIRONMENT_VALUE_REQUIRED");return value;}
    private static Map<String,Integer> ledger(JdbcTemplate db){var out=new LinkedHashMap<String,Integer>();for(String role:List.of("SUPER_ADMIN","ADMIN","CONDUCTOR"))out.put(role,db.queryForObject("select count(*) from pilot_account_creation where role=?",Integer.class,role));return out;}
    private static void checkCaps(JdbcTemplate db){var values=ledger(db);check(values.get("SUPER_ADMIN")<=1&&values.get("ADMIN")<=4&&values.get("CONDUCTOR")<=20,"AUTHORIZED_HISTORICAL_CAP_EXCEEDED");}
    private static void assertTarget(JdbcTemplate db){
        var target=db.queryForMap("select current_database() db,current_user principal,current_setting('port') port,host(inet_server_addr()) host");check("ruta_fija_test".equals(target.get("db"))&&"rf_test".equals(target.get("principal"))&&"55439".equals(target.get("port"))&&"127.0.0.1".equals(target.get("host")),"EXACT_ISOLATED_DATABASE_AND_ROLE_REQUIRED");
        check("50".equals(db.queryForObject("select version from flyway_schema_history where success and version is not null order by installed_rank desc limit 1",String.class))&&db.queryForObject("select count(*) from flyway_schema_history where not success",Integer.class)==0,"EXPLICITLY_MIGRATED_V50_REQUIRED");
        check(!Boolean.TRUE.equals(db.queryForObject("select rolsuper or rolcreatedb or rolcreaterole from pg_roles where rolname=current_user",Boolean.class)),"RESTRICTED_RUNTIME_ROLE_REQUIRED");
    }
    private void assertPrivateDirectory()throws Exception {
        var view=Files.getFileAttributeView(caseRoot,AclFileAttributeView.class,LinkOption.NOFOLLOW_LINKS);check(view!=null,"WINDOWS_PRIVATE_ACL_REQUIRED");var owner=view.getOwner();
        for(var entry:view.getAcl())if(entry.type()==AclEntryType.ALLOW&&entry.permissions().contains(AclEntryPermission.READ_DATA))check(entry.principal().equals(owner),"PRIVATE_CASE_ACL_EXPOSES_OTHER_PRINCIPAL");
    }
    private static void assertNoLinks(Path path)throws Exception{Path current=path.getRoot();for(Path part:path){current=current.resolve(part);if(Files.exists(current,LinkOption.NOFOLLOW_LINKS)){var attrs=Files.readAttributes(current,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);check(!attrs.isSymbolicLink()&&!attrs.isOther(),"LINK_OR_REPARSE_PATH_REJECTED");}}}
    private synchronized void savePrivate()throws Exception{writeAtomic(privateFile,JSON.writeValueAsBytes(secrets));}
    public synchronized void persist()throws Exception {
        if(state.isEmpty())return;state.put("phase",phase);state.put("lastUpdatedAtUtc",Instant.now().toString());state.put("httpEvents",new ArrayList<>(events));
        byte[] bytes=JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);writeAtomic(stateFile,bytes);Files.createDirectories(evidence);writeAtomic(evidence.resolve("preparacion.json"),bytes);
    }
    private static void writeAtomic(Path destination,byte[] content)throws Exception{Path temporary=destination.resolveSibling(destination.getFileName()+"."+UUID.randomUUID()+".tmp");Files.write(temporary,content,StandardOpenOption.CREATE_NEW);try{Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException failure){Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}}
    private void recordFailure(Exception failure){if(state.isEmpty())return;try{state.put("result","STOPPED_KEEP_SAME_CASE_NO_LOAD_PASS");state.put("failure",Map.of("phase",phase,"check",safeCode(failure),"atUtc",Instant.now().toString()));persist();}catch(Exception ignored){}}
    private static String sha(byte[] content)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));}
    private static String safeCode(Exception failure){String message=failure.getMessage();return message!=null&&message.matches("[A-Z][A-Z0-9_]{2,110}")?message:"DETAILS_REDACTED_KEEP_CASE";}
    private static void check(boolean condition,String code){if(!condition)throw new IllegalStateException(code);}
    @Override public void close(){if(app!=null){app.close();app=null;}if(guard!=null){try{guard.close();}catch(Exception ignored){}guard=null;}secrets.clear();}
}
