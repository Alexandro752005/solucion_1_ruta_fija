package pe.rutafija.livemap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.fleet.domain.*;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.identity.application.JwtService;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.operation.application.OrganizationSchedulingLock;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.terms.domain.AdminTermsContentHash;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Real bearer filters, Spring transactions and PostgreSQL on a disposable dedicated cluster only.
 * Ordinary fixtures roll back. Explicit race/export cases commit synthetic rows on that cluster;
 * they never clean, truncate, connect to, migrate or inherit fixtures from a shared database.
 */
@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@Import(LiveMapIntegrationTest.ClockConfiguration.class)
@EnabledIfEnvironmentVariable(named="RF_MAP_TEST_URL", matches=".+")
@Transactional
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveMapIntegrationTest {
    private static final String SECRET=secret();
    @DynamicPropertySource static void isolated(DynamicPropertyRegistry properties) {
        String url=required("RF_MAP_TEST_URL");
        URI target=URI.create(url.replaceFirst("^jdbc:",""));
        if(!url.startsWith("jdbc:postgresql://")||!"127.0.0.1".equals(target.getHost())||target.getPort()!=54432
                ||!"/rf_map_test".equals(target.getPath())||target.getQuery()!=null||target.getUserInfo()!=null
                ||!"rf_map_test".equals(required("RF_MAP_TEST_USERNAME")))
            throw new IllegalStateException("MAP_TEST_REQUIRES_EXCLUSIVE_127_0_0_1_54432_RF_MAP_TEST");
        properties.add("spring.datasource.url",()->url);
        properties.add("spring.datasource.username",()->required("RF_MAP_TEST_USERNAME"));
        properties.add("spring.datasource.password",()->required("RF_MAP_TEST_PASSWORD"));
        properties.add("spring.flyway.url",()->url);
        properties.add("spring.flyway.user",()->required("RF_MAP_TEST_USERNAME"));
        properties.add("spring.flyway.password",()->required("RF_MAP_TEST_PASSWORD"));
        properties.add("spring.flyway.enabled",()->true);
        properties.add("spring.flyway.locations",()->"classpath:db/migration");
        properties.add("spring.jpa.hibernate.ddl-auto",()->"validate");
        properties.add("app.seed.enabled",()->false);
        properties.add("app.mobile.maintenance-enabled",()->false);
        properties.add("app.qualification.maintenance.enabled",()->false);
        properties.add("app.security.jwt.secret-base64",()->SECRET);
        properties.add("app.security.admin-terms-gate.enabled",()->true);
        properties.add("app.security.person-identity-gate.enabled",()->true);
        properties.add("app.security.cors.allowed-origins",()->"http://127.0.0.1:4300");
        properties.add("app.mobile.location-ttl",()->"PT5M");
        properties.add("app.mobile.location-capture-max-age",()->"PT5M");
        for(String category:List.of("org.springframework.web","org.springframework.security","org.springframework.jdbc.core","org.hibernate.SQL","org.hibernate.orm.jdbc.bind"))
            properties.add("logging.level."+category,()->"OFF");
    }
    private static String required(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing isolated setting "+key);return value;}
    private static String secret(){String configured=System.getenv("RF_MAP_TEST_JWT_SECRET");if(configured!=null&&!configured.isBlank())return configured;byte[] bytes=new byte[32];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entities;
    @Autowired OrganizationRepository organizations;
    @Autowired AppUserRepository users;
    @Autowired TransportGroupRepository groups;
    @Autowired DriverRepository drivers;
    @Autowired VehicleRepository vehicles;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Autowired MapClock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired OrganizationSchedulingLock scheduling;
    @Autowired AdminTermsDocumentRepository termDocuments;
    @MockitoBean OtpEmailSender email;
    Instant now;
    Organization sharedRaceOrganization;
    AppUser sharedRaceAdmin;

    @BeforeAll void dedicatedCommittedSyntheticActorForRaces()throws Exception {
        exclusiveIdentityAndClock();
        Path root=repositoryRoot();
        String approvedContent=Files.readString(root.resolve("docs/legal/publicado/terminos-admin-v1.0.0.md"));
        if(!AdminTermsContentHash.sha256(approvedContent).equals("20ffd9525cb19ec51387bf11859cf34100a5117b00de8f428bfeb8b9a4dc6417"))
            throw new IllegalStateException("ISOLATED_FIXTURE_REQUIRES_EXACT_EXISTING_APPROVED_TERMS_BYTES");
        new TransactionTemplate(transactions).execute(status->{
            if(termDocuments.findFirstByDocumentKeyAndSyntheticFalseOrderByCreatedAtDescIdDesc("ADMIN_TERMS").isEmpty())
                termDocuments.saveAndFlush(AdminTermsDocument.publish("ADMIN_TERMS","1.0.0",approvedContent,now));
            sharedRaceOrganization=organizations.saveAndFlush(Organization.active("SYNTHETIC MAP RACE "+UUID.randomUUID(),null,"America/Lima"));
            sharedRaceAdmin=account(sharedRaceOrganization,UserRole.ADMIN,"SYNTHETIC isolated race ADMIN");
            identity(sharedRaceAdmin,sharedRaceAdmin.getId());acceptTerms(sharedRaceAdmin);
            return null;
        });
    }

    @BeforeEach void exclusiveIdentityAndClock(){
        var identity=jdbc.queryForMap("select current_database() db,current_user principal,inet_server_port() port");
        assertThat(identity.get("db")).isEqualTo("rf_map_test");
        assertThat(identity.get("principal")).isEqualTo("rf_map_test");
        assertThat(identity.get("port")).isEqualTo(54432);
        now=Instant.now().truncatedTo(ChronoUnit.SECONDS);clock.set(now);
    }

    @Test void mobileReceptionFeedsOnlyTheSameTenantAndUsesTheExactMinimalProjection()throws Exception {
        var fixture=fixture("SYNTHETIC map driver",0);var foreign=fixture("SYNTHETIC other tenant",0);
        var response=tree(feed(fixture.adminToken()).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertThat(response.path("items")).hasSize(1);
        var item=response.path("items").get(0);
        assertThat(item.path("driverId").asText()).isEqualTo(fixture.driverId().toString());
        assertThat(item.path("vehicleId").asText()).isEqualTo(fixture.vehicleId().toString());
        assertThat(item.path("driverId").asText()).isNotEqualTo(foreign.driverId().toString());
        var fields=new HashSet<String>();item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("driverId","vehicleId","driverName","plate","latitude","longitude","capturedAt","receivedAt","expiresAt","freshness");
        assertThat(item.path("capturedAt").asText()).isEqualTo(now.toString());
        assertThat(item.path("freshness").asText()).isEqualTo("CURRENT");
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where driver_id=?",Integer.class,fixture.driverId())).isEqualTo(1);
    }

    @Test void unauthenticatedAndOtherRolesCannotReadEitherEndpoint()throws Exception {
        var fixture=fixture("SYNTHETIC roles",0);
        var platform=users.saveAndFlush(AppUser.superAdmin(unique("platform"),"synthetic-hash","SYNTHETIC platform"));
        platform.verifyEmail(now);users.flush();identity(platform,fixture.adminId());
        for(String endpoint:List.of("/api/v1/live-map/vehicles","/api/v1/live-map/groups")) {
            mvc.perform(get(endpoint)).andExpect(status().isUnauthorized());
            mvc.perform(get(endpoint).header("Authorization","Bearer "+fixture.mobileToken())).andExpect(status().isForbidden());
            mvc.perform(get(endpoint).header("Authorization","Bearer "+jwt.issueAccessToken(platform))).andExpect(status().isForbidden());
        }
    }

    @Test void realTermsGateRemainsMandatory()throws Exception {
        var fixture=fixture("SYNTHETIC gates",0);
        var noTerms=account(organizations.findById(fixture.orgId()).orElseThrow(),UserRole.ADMIN,"SYNTHETIC missing terms");
        identity(noTerms,fixture.adminId());
        feed(jwt.issueAccessToken(noTerms)).andExpect(status().isForbidden());
    }

    @Test void realIdentityGateRemainsMandatory()throws Exception {
        var fixture=fixture("SYNTHETIC gates",0);
        var noIdentity=account(organizations.findById(fixture.orgId()).orElseThrow(),UserRole.ADMIN,"SYNTHETIC missing identity");
        acceptTerms(noIdentity);
        feed(jwt.issueAccessToken(noIdentity)).andExpect(status().isForbidden());
    }

    @Test void groupFilterUsesActiveMembershipNotTheLegacyPrimaryAndDoesNotEnumerateForeignGroups()throws Exception {
        var fixture=fixture("SYNTHETIC groups",0);var foreign=fixture("SYNTHETIC foreign group",0);
        var second=groups.saveAndFlush(TransportGroup.active(organizations.findById(fixture.orgId()).orElseThrow(),"SYNTHETIC second",null));
        jdbc.update("update group_driver_membership set active=false where driver_id=?",fixture.driverId());
        jdbc.update("insert into group_driver_membership(group_id,driver_id,organization_id,source) values(?,?,?,'ADMIN')",second.getId(),fixture.driverId(),fixture.orgId());
        assertThat(tree(feed(fixture.adminToken(),"groupId",fixture.groupId().toString()).andExpect(status().isOk())).path("items")).isEmpty();
        assertThat(tree(feed(fixture.adminToken(),"groupId",second.getId().toString()).andExpect(status().isOk())).path("items")).hasSize(1);
        assertThat(tree(feed(fixture.adminToken(),"groupId",foreign.groupId().toString()).andExpect(status().isOk())).path("items")).isEmpty();
        var listed=tree(mvc.perform(get("/api/v1/live-map/groups").header("Authorization","Bearer "+fixture.adminToken()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")));
        assertThat(listed.path("items")).hasSize(2);
        assertThat(listed.toString()).doesNotContain(foreign.groupId().toString(),"organizationId","driverId");
    }

    @Test void wildcardQuoteAndBackslashInNamesAreLiteralAndFiltersAreBounded()throws Exception {
        var fixture=fixture("SYNTHETIC 50%_\\' driver",0);
        assertThat(tree(feed(fixture.adminToken(),"driverName","%_\\'").andExpect(status().isOk())).path("items")).hasSize(1);
        assertThat(tree(feed(fixture.adminToken(),"driverName","' OR true --").andExpect(status().isOk())).path("items")).isEmpty();
        feed(fixture.adminToken(),"driverName","x".repeat(101)).andExpect(status().isBadRequest());
        feed(fixture.adminToken(),"limit","6").andExpect(status().isBadRequest());
        feed(fixture.adminToken(),"groupId","invalid").andExpect(status().isBadRequest());
        for(int limit:List.of(5,10,15,20))feed(fixture.adminToken(),"limit",String.valueOf(limit)).andExpect(status().isOk()).andExpect(jsonPath("$.limit").value(limit));
    }

    @Test void freshnessChangesAt120SecondsAndTheRealFiveMinuteTtlRemovesThePoint()throws Exception {
        var fixture=fixture("SYNTHETIC freshness",0);
        clock.set(now.plusSeconds(119));feed(fixture.adminToken()).andExpect(jsonPath("$.items[0].freshness").value("CURRENT"));
        clock.set(now.plusSeconds(120));feed(fixture.adminToken()).andExpect(jsonPath("$.items[0].freshness").value("STALE"))
                .andExpect(jsonPath("$.items[0].capturedAt").value(now.toString()));
        clock.set(now.plusSeconds(299));feed(fixture.adminToken()).andExpect(jsonPath("$.items[0].freshness").value("STALE"));
        clock.set(now.plusSeconds(300));feed(fixture.adminToken()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test void consentRevocationRemovesSnapshotButPreservesBoundedExistingServiceEvidence()throws Exception {
        var fixture=fixture("SYNTHETIC revoke",0);
        revoke(fixture).andExpect(status().isOk());
        feed(fixture.adminToken()).andExpect(jsonPath("$.items").isEmpty());
        capture(fixture,now).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_CONSENT_REQUIRED"));
        assertThat(jdbc.queryForObject("select count(*) from driver_current_location where driver_id=?",Integer.class,fixture.driverId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where driver_id=?",Integer.class,fixture.driverId())).isEqualTo(1);
    }

    @Test void completingThroughTheExistingMobileCommandRemovesSnapshotAndBlocksLaterCapture()throws Exception {
        var fixture=fixture("SYNTHETIC close",0);
        complete(fixture).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        feed(fixture.adminToken()).andExpect(jsonPath("$.items").isEmpty());
        capture(fixture,now).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LOCATION_STATE_NOT_ALLOWED"));
        assertThat(jdbc.queryForObject("select ended_at is not null from service_execution_segment where id=?",Boolean.class,fixture.segmentId())).isTrue();
    }

    @Test void backgroundAndPermissionsCannotPublishAnAdditionalPoint()throws Exception {
        var fixture=fixture("SYNTHETIC permissions",0);
        for(String denied:List.of("foreground","permissionGranted","locationServiceEnabled")) {
            var payload=point(now);payload.put(denied,false);
            mvc.perform(put("/api/v1/mobile/location/current").header("Authorization","Bearer "+fixture.mobileToken())
                    .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(payload))).andExpect(status().isConflict());
        }
        assertThat(jdbc.queryForObject("select count(*) from service_gps_point where driver_id=?",Integer.class,fixture.driverId())).isEqualTo(1);
    }

    @Test void invalidCurrentResourceStateIsFilteredEvenIfAnOldSnapshotRowRemains()throws Exception {
        var fixture=fixture("SYNTHETIC defense",0);
        jdbc.update("update driver set location_consent=false where id=?",fixture.driverId());
        feed(fixture.adminToken()).andExpect(jsonPath("$.items").isEmpty());
        jdbc.update("update driver set location_consent=true where id=?",fixture.driverId());
        jdbc.update("update vehicle set status='DISPONIBLE' where id=?",fixture.vehicleId());
        feed(fixture.adminToken()).andExpect(jsonPath("$.items").isEmpty());
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void zz01ConcurrentRevocationCommitsBeforeTheWaitingCaptureWhichMustThenBeRejected()throws Exception {
        var fixture=new TransactionTemplate(transactions).execute(status->uncheckedSharedFixture("SYNTHETIC committed revoke race"));
        race(fixture,false);
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void zz02ConcurrentCompletionCommitsBeforeTheWaitingCaptureWhichMustThenBeRejected()throws Exception {
        var fixture=new TransactionTemplate(transactions).execute(status->uncheckedSharedFixture("SYNTHETIC committed close race"));
        race(fixture,true);
    }

    private void race(Fixture fixture,boolean close)throws Exception {
        var locked=new CountDownLatch(1);var attempted=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var first=executor.submit(()->new TransactionTemplate(transactions).execute(status->{
                scheduling.acquire(fixture.orgId());locked.countDown();
                try {
                    if(!attempted.await(10,TimeUnit.SECONDS))throw new IllegalStateException("RACE_CAPTURE_NOT_STARTED");
                    int ownerPid=jdbc.queryForObject("select pg_backend_pid()",Integer.class);
                    boolean waitingOnThisTransaction=false;
                    for(int observation=0;observation<250;observation++) {
                        jdbc.execute("select pg_stat_clear_snapshot()");
                        waitingOnThisTransaction=Boolean.TRUE.equals(jdbc.queryForObject("""
                            select exists(select 1 from pg_stat_activity activity
                              where activity.datname=current_database() and activity.usename=current_user
                                and activity.wait_event_type='Lock'
                                and ?=any(pg_blocking_pids(activity.pid)))
                            """,Boolean.class,ownerPid));
                        if(waitingOnThisTransaction)break;
                        Thread.sleep(20);
                    }
                    assertThat(waitingOnThisTransaction)
                            .as("The second real PostgreSQL session must wait on this exact revocation/completion transaction")
                            .isTrue();
                    return (close?complete(fixture):revoke(fixture)).andExpect(status().isOk()).andReturn().getResponse().getStatus();
                }catch(Exception failure){throw new IllegalStateException("SYNTHETIC_RACE_FIRST_FAILED",failure);}
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var second=executor.submit(()->{attempted.countDown();return capture(fixture,now).andReturn().getResponse().getStatus();});
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(20,TimeUnit.SECONDS)).isEqualTo(409);
            var remaining=tree(feed(fixture.adminToken()).andExpect(status().isOk())).path("items");
            for(var item:remaining)assertThat(item.path("driverId").asText()).isNotEqualTo(fixture.driverId().toString());
            assertThat(jdbc.queryForObject("select count(*) from driver_current_location where driver_id=?",Integer.class,fixture.driverId())).isZero();
        } finally {executor.shutdownNow();}
    }

    @Test @Transactional(propagation=Propagation.NOT_SUPPORTED)
    void zzzExportSyntheticBrowserFixtureOnlyWhenTheExactPrivatePathIsExplicitlyRequested()throws Exception {
        String requested=System.getenv("RF_MAP_BROWSER_FIXTURE_PATH");assumeTrue(requested!=null&&!requested.isBlank(),"Optional private browser fixture not requested");
        assumeTrue(System.getenv("RF_MAP_TEST_JWT_SECRET")!=null,"Export requires explicit shared isolated JWT secret");
        Path root=repositoryRoot();
        Path allowed=root.resolve(".runtime/live-map-isolated/browser-fixture.json").normalize();
        Path destination=Path.of(requested).toAbsolutePath().normalize();
        if("EXCLUSIVE_AUDIT_2026_11_13".equals(System.getenv("RF_MAP_ISOLATED_AUDIT"))){
            Path auditRoot=root.resolve(".runtime/auditoria-integral-2026-11-13/postgresql/mapa").toRealPath();
            Path parent=destination.getParent().toRealPath();
            if(!parent.getParent().toRealPath().equals(auditRoot)
                    ||!parent.getFileName().toString().matches("maven-[0-9a-f-]{36}")
                    ||!destination.getFileName().toString().equals("browser-fixture.json"))
                throw new IllegalStateException("EXPORT_REQUIRES_EXACT_NEW_AUDIT_FIXTURE_DIRECTORY");
            allowed=parent.resolve("browser-fixture.json");
        }
        if(!destination.equals(allowed)||!Files.isDirectory(destination.getParent())||Files.exists(destination))
            throw new IllegalStateException("EXPORT_REQUIRES_EXACT_NEW_PRIVATE_BROWSER_FIXTURE_PATH");
        var fixture=new TransactionTemplate(transactions).execute(status->uncheckedSharedFixture("SYNTHETIC Browser Map Driver"));
        String adminPassword="Synthetic-Map-"+UUID.randomUUID()+"!";
        String mobilePassword="Synthetic-Map-"+UUID.randomUUID()+"!";
        var credentials=new TransactionTemplate(transactions).execute(status->{
            String adminEmail=jdbc.queryForObject("select email from app_user where id=?",String.class,fixture.adminId());
            String mobileEmail=jdbc.queryForObject("select u.email from app_user u join driver d on d.user_id=u.id where d.id=?",String.class,fixture.driverId());
            jdbc.update("update app_user set password_hash=? where id=?",passwords.encode(adminPassword),fixture.adminId());
            jdbc.update("update app_user set password_hash=? where id=(select user_id from driver where id=?)",passwords.encode(mobilePassword),fixture.driverId());
            return Map.of("adminEmail",adminEmail,"adminPassword",adminPassword,"mobileEmail",mobileEmail,"mobilePassword",mobilePassword);
        });
        var output=new LinkedHashMap<String,Object>();output.put("synthetic",true);output.put("organizationId",fixture.orgId());
        output.put("adminId",fixture.adminId());output.put("driverId",fixture.driverId());output.put("vehicleId",fixture.vehicleId());
        output.put("groupId",fixture.groupId());output.put("assignmentId",fixture.assignmentId());output.put("serviceId",fixture.serviceId());
        output.put("segmentId",fixture.segmentId());output.put("adminToken",fixture.adminToken());output.put("mobileToken",fixture.mobileToken());
        output.put("jwtSecret",SECRET);output.put("capturedAt",now);output.put("expiresAt",now.plusSeconds(300));
        output.putAll(credentials);
        Files.write(destination,json.writeValueAsBytes(output),StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);
    }

    private Fixture uncheckedSharedFixture(String name){try{return participant(sharedRaceOrganization,sharedRaceAdmin,name,0);}catch(Exception failure){throw new IllegalStateException("SYNTHETIC_FIXTURE_FAILED",failure);}}
    private Fixture fixture(String name,int age)throws Exception {
        var org=organizations.saveAndFlush(Organization.active("SYNTHETIC MAP "+UUID.randomUUID(),null,"America/Lima"));
        var admin=account(org,UserRole.ADMIN,"SYNTHETIC map ADMIN");identity(admin,admin.getId());acceptTerms(admin);
        return participant(org,admin,name,age);
    }
    private Fixture participant(Organization org,AppUser admin,String name,int age)throws Exception {
        String suffix=UUID.randomUUID().toString().substring(0,8);
        var conductor=account(org,UserRole.CONDUCTOR,name);identity(conductor,admin.getId());
        var group=groups.saveAndFlush(TransportGroup.active(org,"SYNTHETIC Map Group "+suffix,null));
        var driver=drivers.saveAndFlush(Driver.register(org,conductor,group,name,null,"DNI",String.format(Locale.ROOT,"7%07d",Math.floorMod(UUID.randomUUID().hashCode(),10_000_000)),null));
        var vehicle=vehicles.saveAndFlush(Vehicle.register(org,"MAP-"+UUID.randomUUID().toString().substring(0,6),"SYNTHETIC","SYNTHETIC",(short)2025,null));
        approval(admin,conductor,driver);
        jdbc.update("update driver set availability_status='EN_SERVICIO',location_consent=true where id=?",driver.getId());
        jdbc.update("update vehicle set status='EN_SERVICIO' where id=?",vehicle.getId());
        jdbc.update("insert into group_driver_membership(group_id,driver_id,organization_id,source) values(?,?,?,'ADMIN') on conflict(group_id,driver_id) do update set active=true",group.getId(),driver.getId(),org.getId());
        UUID route=UUID.randomUUID(),routeVersion=UUID.randomUUID(),type=UUID.randomUUID(),typeVersion=UUID.randomUUID();
        jdbc.update("insert into route_definition(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,?,?,?,?)",route,org.getId(),"SYNTHETIC Map Route "+suffix,routeVersion,ts(now),ts(now));
        jdbc.update("insert into route_version(id,route_id,organization_id,revision,name,stops,content_sha256,created_by,created_at) values(?,?,?,1,?,?::jsonb,?,?,?)",routeVersion,route,org.getId(),"SYNTHETIC Map Route","[{\"key\":\"s\",\"name\":\"SYNTHETIC origin\",\"kind\":\"START\"},{\"key\":\"m\",\"name\":\"SYNTHETIC middle\",\"kind\":\"INTERMEDIATE\"},{\"key\":\"e\",\"name\":\"SYNTHETIC terminal\",\"kind\":\"END\"}]","a".repeat(64),admin.getId(),ts(now));
        jdbc.update("insert into service_type(id,organization_id,name,current_version_id,created_at,updated_at) values(?,?,?,?,?,?)",type,org.getId(),"SYNTHETIC Map Type "+suffix,typeVersion,ts(now),ts(now));
        jdbc.update("insert into service_type_version(id,type_id,organization_id,revision,name,before_minutes,after_minutes,content_sha256,created_by,created_at) values(?,?,?,1,?,0,0,?,?,?)",typeVersion,type,org.getId(),"SYNTHETIC Map Type","b".repeat(64),admin.getId(),ts(now));
        UUID service=UUID.randomUUID(),assignment=UUID.randomUUID(),segment=UUID.randomUUID();
        Instant started=now.minusSeconds(600),end=now.plusSeconds(3600),deadline=started.minusSeconds(30),accepted=started.minusSeconds(60);
        jdbc.update("insert into scheduled_service(id,organization_id,created_by,created_at,updated_at,scheduled_at,scheduled_end_at,route_version_id,service_type_version_id) values(?,?,?,?,?,?,?,?,?)",service,org.getId(),admin.getId(),ts(started.minusSeconds(120)),ts(started),ts(started),ts(end),routeVersion,typeVersion);
        jdbc.update("""
                insert into assignment(id,organization_id,driver_id,vehicle_id,created_by,status,origin_text,destination_text,
                  scheduled_at,scheduled_end_at,response_mode,response_deadline_at,created_at,updated_at,
                  service_id,route_version_id,service_type_version_id,margin_before_minutes,margin_after_minutes)
                values(?,?,?,?,?,'PENDING_RESPONSE','SYNTHETIC origin','SYNTHETIC terminal',?,?,'MOBILE_CONFIRMATION',?,?,?,?,?,?,0,0)
                """,assignment,org.getId(),driver.getId(),vehicle.getId(),admin.getId(),ts(started),ts(end),ts(deadline),ts(started.minusSeconds(120)),ts(started),service,routeVersion,typeVersion);
        jdbc.update("update assignment set status='SCHEDULED',accepted_at=?,reserved_at=? where id=?",ts(accepted),ts(accepted),assignment);
        jdbc.update("update assignment set status='EN_SERVICIO',started_at=? where id=?",ts(started),assignment);
        jdbc.update("insert into service_execution_segment(id,service_id,organization_id,assignment_id,driver_id,vehicle_id,ordinal,started_at,start_recorded_at,started_by,start_event_id) values(?,?,?,?,?,?,1,?,?,?,?)",segment,service,org.getId(),assignment,driver.getId(),vehicle.getId(),ts(started),ts(started),conductor.getId(),UUID.randomUUID());
        entities.clear();
        var result=new Fixture(org.getId(),admin.getId(),driver.getId(),vehicle.getId(),group.getId(),assignment,service,segment,
                jwt.issueAccessToken(admin),jwt.issueMobileAccessToken(conductor,driver.getId(),"INCORPORATION"));
        capture(result,now.minusSeconds(age)).andExpect(status().isNoContent());
        return result;
    }
    private AppUser account(Organization org,UserRole role,String name){var user=AppUser.organizationUser(org,unique(role.name()),"SYNTHETIC-NOT-A-PASSWORD",name,role);user.verifyEmail(now.minusSeconds(60));return users.saveAndFlush(user);}
    private String unique(String prefix){return prefix.toLowerCase(Locale.ROOT)+"."+UUID.randomUUID()+"@rutafija.test";}
    private void identity(AppUser user,UUID reviewer){jdbc.update("insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at) values(?,?,'DNI',?,'IN_PERSON','SYNTHETIC MAP ISOLATED',?)",UUID.randomUUID().toString().replace("-","")+UUID.randomUUID().toString().replace("-",""),user.getId(),reviewer,ts(now.minusSeconds(60)));}
    private void acceptTerms(AppUser admin){
        var docs=jdbc.queryForList("select id from admin_terms_document where document_key='ADMIN_TERMS' and not synthetic order by created_at desc,id desc limit 1",UUID.class);
        if(docs.isEmpty())throw new IllegalStateException("ISOLATED_APPROVED_TERMS_MIGRATION_REQUIRED");
        jdbc.update("insert into admin_terms_acceptance(id,admin_user_id,organization_id,document_id,document_version,content_sha256,accepted_at) select ?,?,?,id,version,content_sha256,? from admin_terms_document where id=?",UUID.randomUUID(),admin.getId(),admin.getOrganizationId(),ts(now),docs.getFirst());
    }
    private void approval(AppUser admin,AppUser conductor,Driver driver){
        UUID identity=UUID.randomUUID(),invitation=UUID.randomUUID();
        jdbc.update("insert into conductor_onboarding_identity(id,email,password_hash,full_name,email_verified_at,created_at,updated_at) values(?,?,?,?,?,?,?)",identity,conductor.getEmail(),"SYNTHETIC-NOT-A-PASSWORD",conductor.getFullName(),ts(now.minusSeconds(60)),ts(now.minusSeconds(120)),ts(now));
        jdbc.update("insert into organization_invitation(id,organization_id,issuer_user_id,recipient_email,code_hash,issued_at,expires_at,used_at) values(?,?,?,?,?,?,?,?)",invitation,conductor.getOrganizationId(),admin.getId(),conductor.getEmail(),"c".repeat(64),ts(now.minusSeconds(3600)),ts(now.plusSeconds(3600)),ts(now.minusSeconds(120)));
        jdbc.update("insert into conductor_onboarding_request(id,identity_id,organization_id,invitation_id,idempotency_key,status,review_rounds,submitted_at,reviewed_by_admin_id,reviewed_at,personal_approved_at,linked_user_id,linked_driver_id,provisioned_at) values(?,?,?,?,?,'APPROVED',1,?,?,?,?,?,?,?)",UUID.randomUUID(),identity,conductor.getOrganizationId(),invitation,"SYNTHETIC-MAP-"+UUID.randomUUID(),ts(now.minusSeconds(120)),admin.getId(),ts(now.minusSeconds(60)),ts(now.minusSeconds(60)),conductor.getId(),driver.getId(),ts(now.minusSeconds(60)));
    }
    private ResultActions feed(String token,String... filter)throws Exception {var request=get("/api/v1/live-map/vehicles").header("Authorization","Bearer "+token);for(int index=0;index<filter.length;index+=2)request.param(filter[index],filter[index+1]);entities.clear();return mvc.perform(request);}
    private JsonNode tree(ResultActions response)throws Exception{return json.readTree(response.andReturn().getResponse().getContentAsByteArray());}
    private Map<String,Object> point(Instant captured){return new HashMap<>(Map.of("latitude",new BigDecimal("-6.770000"),"longitude",new BigDecimal("-79.840000"),"accuracyM",5,"capturedAt",captured,"permissionGranted",true,"locationServiceEnabled",true,"foreground",true));}
    private ResultActions capture(Fixture f,Instant captured)throws Exception {return mvc.perform(put("/api/v1/mobile/location/current").header("Authorization","Bearer "+f.mobileToken()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(point(captured))));}
    private ResultActions revoke(Fixture f)throws Exception{return mvc.perform(put("/api/v1/mobile/location/consent").header("Authorization","Bearer "+f.mobileToken()).contentType(MediaType.APPLICATION_JSON).content("{\"consent\":false}"));}
    private ResultActions complete(Fixture f)throws Exception{return mvc.perform(post("/api/v1/mobile/assignments/"+f.assignmentId()+"/complete").header("Authorization","Bearer "+f.mobileToken()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("version",0,"clientEventId",UUID.randomUUID(),"occurredAt",clock.instant()))));}
    private static Timestamp ts(Instant time){return Timestamp.from(time);}
    private static Path repositoryRoot(){
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        if(Files.isRegularFile(root.resolve("pom.xml"))&&root.getFileName().toString().equals("backend"))root=root.getParent();
        if(!Files.isRegularFile(root.resolve("backend/pom.xml")))throw new IllegalStateException("MAP_TEST_REPOSITORY_ROOT_REQUIRED");
        return root;
    }
    private record Fixture(UUID orgId,UUID adminId,UUID driverId,UUID vehicleId,UUID groupId,UUID assignmentId,UUID serviceId,UUID segmentId,String adminToken,String mobileToken){@Override public String toString(){return "SyntheticMapFixture[private credentials redacted]";}}
    @TestConfiguration static class ClockConfiguration {@Bean @Primary MapClock mapClock(){return new MapClock();}}
    static class MapClock extends Clock {
        private volatile Instant instant=Instant.now();void set(Instant value){instant=value;}
        @Override public ZoneId getZone(){return ZoneOffset.UTC;}
        @Override public Clock withZone(ZoneId zone){return this;}
        @Override public Instant instant(){return instant;}
    }
}
