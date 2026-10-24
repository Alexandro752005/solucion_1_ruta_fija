package pe.rutafija.onboarding.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Actual independent HTTP request transactions race on a committed synthetic fixture.
 * A separate exact-name disposable database is mandatory. No TRUNCATE, cleanup or
 * historical harness is used; the owner may drop this isolated database afterwards.
 */
@SpringBootTest(properties = {
        "debug=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF",
        "app.security.person-identity-gate.enabled=true", "app.security.admin-terms-gate.enabled=true",
        "spring.flyway.locations=classpath:db/migration", "spring.flyway.clean-disabled=true",
        "spring.flyway.target=50", "spring.jpa.hibernate.ddl-auto=validate",
        "app.seed.enabled=false", "app.qualification.maintenance.enabled=false",
        "spring.datasource.hikari.connection-init-sql=SET statement_timeout TO 15000"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RF_LEGACY_CONCURRENCY_QA_URL", matches = ".+")
class LegacyConductorRegularizationConcurrencyIT {
    private static final String DATABASE = "rf_legacy_concurrency_qa_20261023";
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/" + DATABASE;
    private static final String PASSWORD = "Synthetic-Concurrent-Only-Password-2026";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        if (!URL.equals(required("RF_LEGACY_CONCURRENCY_QA_URL"))) {
            throw new IllegalStateException("EXACT_DEDICATED_LEGACY_CONCURRENCY_DATABASE_REQUIRED");
        }
        String appPassword = required("RF_LEGACY_QA_APP_PASSWORD");
        String migratorPassword = required("RF_LEGACY_QA_MIGRATOR_PASSWORD");
        observeExactTarget("rf_migrator", migratorPassword);
        observeExactTarget("rf_app", appPassword);
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String jwtSecret = Base64.getEncoder().encodeToString(secret);
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "rf_app");
        registry.add("spring.datasource.password", () -> appPassword);
        registry.add("spring.flyway.url", () -> URL);
        registry.add("spring.flyway.user", () -> "rf_migrator");
        registry.add("spring.flyway.password", () -> migratorPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("app.security.jwt.secret-base64", () -> jwtSecret);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("MISSING_PRIVATE_INPUT_" + key);
        return value;
    }

    private static void observeExactTarget(String role, String password) {
        try (var connection = DriverManager.getConnection(URL, role, password)) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("select current_database(),current_user,inet_server_port()")) {
                if (!result.next() || !DATABASE.equals(result.getString(1)) || !role.equals(result.getString(2))
                        || result.getInt(3) != 5432) {
                    throw new IllegalStateException("DEDICATED_CONCURRENCY_TARGET_OBSERVATION_FAILED");
                }
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("DEDICATED_CONCURRENCY_CONNECTION_REQUIRED", failure);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @Autowired PasswordEncoder passwords;
    @Autowired OrganizationRepository organizations;
    @Autowired AppUserRepository users;
    @Autowired DriverRepository drivers;
    @Autowired TransportGroupRepository groups;
    @Autowired AdminTermsDocumentRepository terms;
    @MockitoBean OtpEmailSender email;

    @Test
    void simultaneousOpeningPreservesExactlyOneDossierForTheSameAndDifferentOperationKeys() throws Exception {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo(DATABASE);
        assertThat(count("select count(*) from flyway_schema_history where success")).isEqualTo(50);
        assertThat(count("select count(*) from flyway_schema_history where version is null or not success")).isZero();
        // The fixture commits. Refuse re-runs against an already populated target rather than erase its evidence.
        assertThat(count("select count(*) from app_user")).isZero();
        assertThat(count("select count(*) from pilot_account_creation")).isZero();
        Fixture fixture = transactions.execute(ignored -> createCommittedFixture());
        assertThat(fixture).isNotNull();
        List<Map<String, Object>> accounts = jdbc.queryForList("select id,email,password_hash,email_verified_at,role,organization_id from app_user order by id");
        List<Map<String, Object>> ledger = jdbc.queryForList("select * from pilot_account_creation order by user_id");

        String sharedOperation = UUID.randomUUID().toString();
        List<Result> same = race(fixture.admin(), fixture.sameOperationDriver(), sharedOperation, sharedOperation);
        assertThat(same).extracting(Result::status).containsExactlyInAnyOrder(200, 200);
        assertThat(same.get(0).body().path("requestId").asText()).isEqualTo(same.get(1).body().path("requestId").asText()).isNotBlank();
        assertSingleAssociation(fixture.sameOperationDriver());

        List<Result> different = race(fixture.admin(), fixture.differentOperationDriver(), UUID.randomUUID().toString(), UUID.randomUUID().toString());
        assertThat(different).extracting(Result::status).containsExactlyInAnyOrder(200, 409);
        assertSingleAssociation(fixture.differentOperationDriver());

        assertThat(count("select count(*) from conductor_onboarding_request")).isEqualTo(2);
        assertThat(count("select count(*) from conductor_person_binding")).isEqualTo(2);
        assertThat(count("select count(*) from conductor_onboarding_review_event where action='LEGACY_REGULARIZATION_OPENED'")).isEqualTo(2);
        assertThat(count("select count(*) from conductor_onboarding_request where status='PENDING_REVIEW' and personal_approved_at is null and provisioned_at is null and linked_user_id is null and linked_driver_id is null")).isEqualTo(2);
        assertThat(count("select count(*) from driver where not active")).isEqualTo(2);
        assertThat(count("select count(*) from email_otp_challenge")).isZero();
        assertThat(jdbc.queryForList("select id,email,password_hash,email_verified_at,role,organization_id from app_user order by id")).isEqualTo(accounts);
        assertThat(jdbc.queryForList("select * from pilot_account_creation order by user_id")).isEqualTo(ledger);
        verifyNoInteractions(email);
    }

    private Fixture createCommittedFixture() {
        try {
            String suffix = UUID.randomUUID().toString();
            Instant now = Instant.now();
            Organization organization = organizations.saveAndFlush(Organization.active("Synthetic Race " + suffix, "Synthetic Race", "America/Lima"));
            AppUser owner = AppUser.superAdmin("race.owner." + suffix + "@rutafija.test", passwords.encode(PASSWORD), "Synthetic Race Owner");
            owner.verifyEmail(now);
            owner = users.saveAndFlush(owner);
            AppUser admin = AppUser.organizationUser(organization, "race.admin." + suffix + "@rutafija.test", passwords.encode(PASSWORD), "Synthetic Race ADMIN", UserRole.ADMIN);
            admin.verifyEmail(now);
            admin = users.saveAndFlush(admin);
            TransportGroup group = groups.saveAndFlush(TransportGroup.active(organization, "Synthetic Race Group", null));
            AppUser first = users.saveAndFlush(AppUser.organizationUser(organization, "race.first." + suffix + "@rutafija.test", passwords.encode(PASSWORD), "Synthetic First Conductor", UserRole.CONDUCTOR));
            AppUser second = users.saveAndFlush(AppUser.organizationUser(organization, "race.second." + suffix + "@rutafija.test", passwords.encode(PASSWORD), "Synthetic Second Conductor", UserRole.CONDUCTOR));
            Driver firstDriver = drivers.saveAndFlush(Driver.register(organization, first, group, "Synthetic First Conductor", null, "DNI", "51000011", "SYNTHETIC-FIRST-LICENSE"));
            Driver secondDriver = drivers.saveAndFlush(Driver.register(organization, second, group, "Synthetic Second Conductor", null, "DNI", "51000012", "SYNTHETIC-SECOND-LICENSE"));
            Map<String, Object> ownerCommand = Map.of("currentPassword", PASSWORD, "ownerDeclaration", true, "identityReview", review("91000011"));
            requireOk(send("/api/v1/platform/identity/self-enrollment", owner, ownerCommand));
            requireOk(send("/api/v1/platform/admins/" + admin.getId() + "/identity-review", owner, reviewCommand("41000011")));
            AdminTermsDocument document = terms.saveAndFlush(AdminTermsDocument.publish("ADMIN_TERMS", "qa-concurrency-1", "SYNTHETIC CONCURRENCY QA TERMS. No real-person legal acceptance.", now));
            requireOk(send("/api/v1/admin/terms/acceptances", admin, Map.of("version", document.getVersion(), "contentSha256", document.getContentSha256(), "accepted", true)));
            requireOk(send("/api/v1/drivers/" + firstDriver.getId() + "/identity-review", admin, reviewCommand("51000011")));
            requireOk(send("/api/v1/drivers/" + secondDriver.getId() + "/identity-review", admin, reviewCommand("51000012")));
            return new Fixture(admin, firstDriver.getId(), secondDriver.getId());
        } catch (Exception failure) {
            throw new IllegalStateException("SYNTHETIC_CONCURRENCY_FIXTURE_FAILED", failure);
        }
    }

    private List<Result> race(AppUser admin, UUID driverId, String firstKey, String secondKey) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> concurrentOpening(admin, driverId, firstKey, ready, go));
            var second = executor.submit(() -> concurrentOpening(admin, driverId, secondKey, ready, go));
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
                go.countDown();
                return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
            } finally {
                go.countDown();
                executor.shutdownNow();
            }
        }
    }

    private Result concurrentOpening(AppUser admin, UUID driverId, String key, CountDownLatch ready, CountDownLatch go) throws Exception {
        ready.countDown();
        if (!go.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("CONCURRENT_START_TIMEOUT");
        return send("/api/v1/drivers/" + driverId + "/regularization", admin,
                Map.of("operationKey", key, "reason", "SYNTHETIC concurrent legacy regularization", "identityPreservedConfirmed", true));
    }

    private void assertSingleAssociation(UUID driverId) {
        assertThat(count("select count(*) from conductor_person_binding where driver_id=?", driverId)).isEqualTo(1);
        assertThat(count("select count(*) from conductor_onboarding_request request join conductor_person_binding binding on binding.identity_id=request.identity_id where binding.driver_id=?", driverId)).isEqualTo(1);
    }

    private Result send(String path, AppUser actor, Map<String, Object> command) throws Exception {
        MvcResult result = mvc.perform(post(path).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name())).jwt(value -> {
                    value.subject(actor.getId().toString()).claim("email", actor.getEmail()).claim("role", actor.getRole().name()).claim("sessionVersion", actor.getSessionVersion());
                    if (actor.getOrganizationId() != null) value.claim("organizationId", actor.getOrganizationId().toString());
                })).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(command))).andReturn();
        return new Result(result.getResponse().getStatus(), mapper.readTree(result.getResponse().getContentAsByteArray()));
    }

    private static Map<String, Object> review(String dni) {
        return Map.of("documentType", "DNI", "documentNumber", dni, "method", "IN_PERSON", "reference", "SYNTHETIC concurrent QA visual review", "originalReviewed", true, "identityConfirmed", true);
    }
    private static Map<String, Object> reviewCommand(String dni) {
        return Map.of("identityReview", review(dni), "reason", "SYNTHETIC authorized concurrent QA review");
    }
    private static void requireOk(Result result) { assertThat(result.status()).isEqualTo(200); }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private record Fixture(AppUser admin, UUID sameOperationDriver, UUID differentOperationDriver) { }
    private record Result(int status, JsonNode body) { }
}
