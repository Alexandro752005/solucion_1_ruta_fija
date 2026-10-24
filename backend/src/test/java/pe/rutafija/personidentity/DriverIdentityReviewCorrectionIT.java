package pe.rutafija.personidentity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.security.SecureRandom;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Synthetic rollback fixtures. This opt-in suite never loads the protected historical DB harness. */
@SpringBootTest(properties = {
        "debug=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF",
        "app.security.person-identity-gate.enabled=true", "app.security.admin-terms-gate.enabled=true",
        "spring.flyway.locations=classpath:db/migration", "spring.flyway.clean-disabled=true",
        "spring.flyway.target=51", "spring.jpa.hibernate.ddl-auto=validate",
        "app.seed.enabled=false", "app.qualification.maintenance.enabled=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(DriverIdentityReviewCorrectionIT.ClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RF_IDENTITY_CORRECTION_QA_URL", matches = ".+")
@Transactional
class DriverIdentityReviewCorrectionIT {
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/rf_identity_correction_qa_20261023";
    private static final String PASSWORD = "Synthetic-Correction-Only-Password-2026";
    private static final String WRONG_DNI = "52000002";
    private static final String CORRECT_DNI = "52000001";
    private static final String NAME = "Synthetic Existing Driver";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        if (!URL.equals(required("RF_IDENTITY_CORRECTION_QA_URL")))
            throw new IllegalStateException("EXACT_DEDICATED_IDENTITY_CORRECTION_QA_DATABASE_REQUIRED");
        String applicationPassword = required("RF_LEGACY_QA_APP_PASSWORD");
        String migratorPassword = required("RF_LEGACY_QA_MIGRATOR_PASSWORD");
        observeExactTarget("rf_migrator", migratorPassword);
        observeExactTarget("rf_app", applicationPassword);
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String signingKey = Base64.getEncoder().encodeToString(secret);
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "rf_app");
        registry.add("spring.datasource.password", () -> applicationPassword);
        registry.add("spring.flyway.url", () -> URL);
        registry.add("spring.flyway.user", () -> "rf_migrator");
        registry.add("spring.flyway.password", () -> migratorPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("app.security.jwt.secret-base64", () -> signingKey);
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("MISSING_PRIVATE_IDENTITY_CORRECTION_INPUT_" + key);
        return value;
    }

    private static void observeExactTarget(String role, String password) {
        try (var connection = DriverManager.getConnection(URL, role, password)) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("select current_database(),current_user,inet_server_port()")) {
                if (!result.next() || !"rf_identity_correction_qa_20261023".equals(result.getString(1))
                        || !role.equals(result.getString(2)) || result.getInt(3) != 5432)
                    throw new IllegalStateException("DEDICATED_IDENTITY_CORRECTION_TARGET_OBSERVATION_FAILED");
            }
        } catch (SQLException failure) {
            throw new IllegalStateException("DEDICATED_IDENTITY_CORRECTION_CONNECTION_REQUIRED", failure);
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired AppUserRepository users;
    @Autowired OrganizationRepository organizations;
    @Autowired DriverRepository drivers;
    @Autowired TransportGroupRepository groups;
    @Autowired AdminTermsDocumentRepository terms;
    @Autowired InvitationCodeHasher hasher;
    @Autowired EntityManager entityManager;
    @Autowired Environment environment;
    @Autowired CorrectionClock clock;
    @MockitoBean OtpEmailSender email;
    private AppUser owner, admin, otherAdmin, conductor;
    private Organization organization;
    private Driver driver;

    @BeforeEach
    void syntheticIncorrectReviewFixture() throws Exception {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("rf_identity_correction_qa_20261023");
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("rf_app");
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(51);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version is null or not success", Integer.class)).isZero();
        assertThat(environment.getProperty("app.security.person-identity-gate.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("app.security.admin-terms-gate.enabled", Boolean.class)).isTrue();
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        String suffix = UUID.randomUUID().toString();
        organization = organizations.saveAndFlush(Organization.active("Synthetic Correction " + suffix, "Synthetic Correction", "America/Lima"));
        Organization other = organizations.saveAndFlush(Organization.active("Synthetic Other " + suffix, "Synthetic Other", "America/Lima"));
        owner = AppUser.superAdmin("owner." + suffix + "@rutafija.test", passwords.encode(PASSWORD), "Synthetic Owner");
        owner.verifyEmail(clock.instant()); owner = users.saveAndFlush(owner);
        admin = verifiedAccount(organization, "admin." + suffix + "@rutafija.test", "Synthetic ADMIN");
        otherAdmin = verifiedAccount(other, "other." + suffix + "@rutafija.test", "Synthetic Other ADMIN");
        conductor = users.saveAndFlush(AppUser.organizationUser(organization, "driver." + suffix + "@rutafija.test", passwords.encode(PASSWORD), NAME, UserRole.CONDUCTOR));
        TransportGroup group = groups.saveAndFlush(TransportGroup.active(organization, "Synthetic Existing Group", null));
        driver = drivers.saveAndFlush(Driver.register(organization, conductor, group, NAME, null, "DNI", CORRECT_DNI, "SYNTHETIC-EXISTING-LICENSE"));
        send("/api/v1/platform/identity/self-enrollment", owner,
                Map.of("currentPassword", PASSWORD, "ownerDeclaration", true, "identityReview", review("92000001"))).andExpect(status().isOk());
        send("/api/v1/platform/admins/" + admin.getId() + "/identity-review", owner, reviewCommand("42000001")).andExpect(status().isOk());
        send("/api/v1/platform/admins/" + otherAdmin.getId() + "/identity-review", owner, reviewCommand("42000002")).andExpect(status().isOk());
        AdminTermsDocument document = terms.saveAndFlush(AdminTermsDocument.publish("ADMIN_TERMS", "qa-correction-1",
                "SYNTHETIC QA ONLY TERMS. No acceptance by a real person.", clock.instant()));
        for (AppUser actor : List.of(admin, otherAdmin))
            send("/api/v1/admin/terms/acceptances", actor, Map.of("version", document.getVersion(), "contentSha256", document.getContentSha256(), "accepted", true)).andExpect(status().isOk());
        // Reproduce a human entry error solely in the reviewed HMAC root; the existing driver DNI is correct.
        send("/api/v1/drivers/" + driver.getId() + "/identity-review", admin, reviewCommand(WRONG_DNI)).andExpect(status().isOk());
        entityManager.flush(); entityManager.clear();
        clock.set(clock.instant().plusSeconds(5));
    }

    @Test
    void correctsOnlyErroneousReviewedRootPreservingExistingAccountDriverHistoryAndCredentials() throws Exception {
        Map<String, Object> originalRoot = root();
        Map<String, Object> originalAccount = account();
        Map<String, Object> originalDriver = driver();
        List<Map<String, Object>> originalLedger = ledger();
        send(openPath(), admin, opening()).andExpect(status().isConflict());
        Map<String, Object> command = correction();
        MvcResult corrected = send(correctionPath(), admin, command).andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(conductor.getId().toString()))
                .andExpect(jsonPath("$.identityVerified").value(true)).andReturn();
        assertThat(corrected.getResponse().getContentAsString()).doesNotContain(CORRECT_DNI, WRONG_DNI, "personHash", "passwordHash", NAME, conductor.getEmail());
        assertThat(root().get("person_hash").toString().trim()).isEqualTo(hash(CORRECT_DNI));
        assertThat(root().get("document_type")).isEqualTo("DNI");
        assertThat(root().get("user_id")).isEqualTo(conductor.getId());
        Map<String, Object> expectedAccount = new HashMap<>(originalAccount);
        expectedAccount.put("session_version", ((Number) originalAccount.get("session_version")).longValue() + 1L);
        assertThat(account()).isEqualTo(expectedAccount);
        assertThat(driver()).isEqualTo(originalDriver);
        assertThat(ledger()).isEqualTo(originalLedger);
        assertThat(count("select count(*) from person_identity_review_correction where user_id=?", conductor.getId())).isEqualTo(1);
        Map<String, Object> history = jdbc.queryForMap("select * from person_identity_review_correction where user_id=?", conductor.getId());
        assertThat(history.toString()).contains(originalRoot.get("person_hash").toString().trim(), hash(CORRECT_DNI));
        assertThat(mapper.readTree(history.get("old_review_metadata").toString()).path("review_reference").asText())
                .isEqualTo(originalRoot.get("review_reference"));
        send(correctionPath(), admin, command).andExpect(status().isOk());
        assertThat(count("select count(*) from person_identity_review_correction where user_id=?", conductor.getId())).isEqualTo(1);
        assertThat(account()).isEqualTo(expectedAccount);
        send(openPath(), admin, opening()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(jdbc.queryForObject("select active from driver where id=?", Boolean.class, driver.getId())).isFalse();
        verifyNoInteractions(email);
    }

    @Test
    void wrongCurrentAdminPasswordCannotCorrectTheIdentity() throws Exception {
        Map<String, Object> before = root();
        Map<String, Object> command = correction(); command.put("currentPassword", "Incorrect-Synthetic-Password");
        send(correctionPath(), admin, command).andExpect(status().isUnauthorized());
        assertThat(root()).isEqualTo(before);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        verifyNoInteractions(email);
    }

    @Test
    void otherTenantAndPlatformOwnerCannotCorrectThisConductor() throws Exception {
        Map<String, Object> before = root();
        send(correctionPath(), otherAdmin, correction()).andExpect(status().isNotFound());
        send(correctionPath(), owner, correction()).andExpect(status().isForbidden());
        assertThat(root()).isEqualTo(before);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        verifyNoInteractions(email);
    }

    @Test
    void replacementDniMustMatchTheUntouchedLegacyDriverAndRequireBothPersonalConfirmations() throws Exception {
        Map<String, Object> before = root();
        Map<String, Object> wrongDocument = correction(); wrongDocument.put("identityReview", review("52000003"));
        send(correctionPath(), admin, wrongDocument).andExpect(status().isConflict());
        for (String confirmation : List.of("dataEntryErrorConfirmed", "samePersonConfirmed")) {
            Map<String, Object> command = correction(); command.put(confirmation, false);
            send(correctionPath(), admin, command).andExpect(status().isBadRequest());
        }
        assertThat(root()).isEqualTo(before);
        assertThat(jdbc.queryForObject("select document_number from driver where id=?", String.class, driver.getId())).isEqualTo(CORRECT_DNI);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        verifyNoInteractions(email);
    }

    @Test
    void newHashAlreadyOwnedByAnotherAccountRequiresSeparateReconciliation() throws Exception {
        Map<String, Object> before = root();
        AppUser otherPerson = users.saveAndFlush(AppUser.organizationUser(organization, "collision." + UUID.randomUUID() + "@rutafija.test",
                passwords.encode(PASSWORD), "Synthetic Different Person", UserRole.CONDUCTOR));
        jdbc.update("""
                insert into person_identity(person_hash,user_id,document_type,reviewed_by_user_id,review_method,review_reference,reviewed_at)
                values(?,?,'DNI',?,'IN_PERSON','SYNTHETIC pre-existing collision fixture',?)
                """, hash(CORRECT_DNI), otherPerson.getId(), admin.getId(), Timestamp.from(clock.instant()));
        send(correctionPath(), admin, correction()).andExpect(status().isConflict());
        assertThat(root()).isEqualTo(before);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        verifyNoInteractions(email);
    }

    @Test
    void anExistingOnboardingBindingCannotBeChangedThroughThisCorrectionProcedure() throws Exception {
        Map<String, Object> before = root();
        UUID identity = UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_identity(id,email,password_hash,full_name,created_at,updated_at)
                values(?,?,?,?,?,?)
                """, identity, conductor.getEmail(), passwords.encode("Synthetic-Inert-Preliminary-Password"), NAME,
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        jdbc.update("""
                insert into conductor_person_binding(person_hash,identity_id,organization_id,user_id,driver_id,bound_at)
                values(?,?,?,?,?,?)
                """, hash(WRONG_DNI), identity, organization.getId(), conductor.getId(), driver.getId(), Timestamp.from(clock.instant()));
        send(correctionPath(), admin, correction()).andExpect(status().isConflict());
        assertThat(root()).isEqualTo(before);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        verifyNoInteractions(email);
    }

    @Test
    void operationReplayCannotBeChangedAndTheLegacyDniCannotBeOverwritten() throws Exception {
        Map<String, Object> originalDriver = driver();
        Map<String, Object> command = correction();
        send(correctionPath(), admin, command).andExpect(status().isOk());
        send(correctionPath(), admin, command).andExpect(status().isOk());
        command.put("reason", "SYNTHETIC changed correction purpose");
        send(correctionPath(), admin, command).andExpect(status().isConflict());
        assertThat(count("select count(*) from person_identity_review_correction")).isEqualTo(1);
        assertThat(driver()).isEqualTo(originalDriver);
        verifyNoInteractions(email);
    }

    @Test
    void ordinarySqlCannotUpdateOrDeleteRootOrForgeCorrectionHistory() {
        Map<String, Object> before = root();
        sqlMustFail("update person_identity set person_hash='" + hash(CORRECT_DNI) + "' where user_id='" + conductor.getId() + "'");
        sqlMustFail("delete from person_identity where user_id='" + conductor.getId() + "'");
        sqlMustFail("insert into person_identity_review_correction default values");
        assertThat(root()).isEqualTo(before);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'person_identity_review_correction','INSERT')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'person_identity_review_correction','UPDATE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'person_identity_review_correction','DELETE')", Boolean.class)).isFalse();
        assertThat(jdbc.queryForObject("select has_table_privilege(current_user,'person_identity_review_correction','TRUNCATE')", Boolean.class)).isFalse();
        String function = "public.correct_unprovisioned_conductor_identity(uuid,uuid,uuid,uuid,uuid,text,text,text,text,text,text,text,timestamp with time zone)";
        assertThat(jdbc.queryForObject("select has_function_privilege(current_user,?,'EXECUTE')", Boolean.class, function)).isTrue();
        assertThat(jdbc.queryForObject("select has_function_privilege('rf_test',?,'EXECUTE')", Boolean.class, function)).isFalse();
        verifyNoInteractions(email);
    }

    @Test
    void successfulCorrectionHistoryRemainsAppendOnlyAndCannotAuthorizeAnUnrelatedRootWrite() throws Exception {
        send(correctionPath(), admin, correction()).andExpect(status().isOk());
        Map<String, Object> correctedRoot = root();
        Map<String, Object> history = jdbc.queryForMap("select * from person_identity_review_correction where user_id=?", conductor.getId());
        sqlMustFail("update person_identity_review_correction set reason='SYNTHETIC unauthorized alteration' where user_id='" + conductor.getId() + "'");
        sqlMustFail("delete from person_identity_review_correction where user_id='" + conductor.getId() + "'");
        // Even a genuine history row in this transaction cannot authorize a second arbitrary mutation.
        sqlMustFail("update person_identity set review_reference='SYNTHETIC unauthorized alteration' where user_id='" + conductor.getId() + "'");
        assertThat(root()).isEqualTo(correctedRoot);
        assertThat(jdbc.queryForMap("select * from person_identity_review_correction where user_id=?", conductor.getId())).isEqualTo(history);
        verifyNoInteractions(email);
    }

    @Test
    void correctionExceptionNeverUnlocksTheStableKeyOrPreviouslyBoundConductorBridge() {
        UUID identity = UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_identity(id,email,password_hash,full_name,created_at,updated_at)
                values(?,?,?,?,?,?)
                """, identity, conductor.getEmail(), passwords.encode("Synthetic-Inert-Bridge-Password"), NAME,
                Timestamp.from(clock.instant()), Timestamp.from(clock.instant()));
        jdbc.update("""
                insert into conductor_person_binding(person_hash,identity_id,organization_id,user_id,driver_id,bound_at)
                values(?,?,?,?,?,?)
                """, hash(WRONG_DNI), identity, organization.getId(), conductor.getId(), driver.getId(), Timestamp.from(clock.instant()));
        jdbc.update("insert into conductor_common_person_bridge(person_hash,common_person_hash) values(?,?)", hash(WRONG_DNI), hash(WRONG_DNI));
        String fingerprint = jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1", String.class);
        sqlMustFail("update person_identity_hash_key set key_fingerprint=key_fingerprint where id=1");
        sqlMustFail("delete from person_identity_hash_key where id=1");
        sqlMustFail("update conductor_common_person_bridge set common_person_hash=common_person_hash where person_hash='" + hash(WRONG_DNI) + "'");
        sqlMustFail("delete from conductor_common_person_bridge where person_hash='" + hash(WRONG_DNI) + "'");
        assertThat(jdbc.queryForObject("select key_fingerprint from person_identity_hash_key where id=1", String.class)).isEqualTo(fingerprint);
        assertThat(count("select count(*) from conductor_common_person_bridge")).isEqualTo(1);
        assertThat(count("select count(*) from person_identity_review_correction")).isZero();
        verifyNoInteractions(email);
    }

    private void sqlMustFail(String sql) {
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            var savepoint = connection.setSavepoint();
            try {
                assertThatThrownBy(() -> {
                    try (var statement = connection.createStatement()) { statement.execute(sql); }
                }).isInstanceOf(SQLException.class);
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            return null;
        });
    }

    private AppUser verifiedAccount(Organization org, String emailAddress, String fullName) {
        AppUser user = AppUser.organizationUser(org, emailAddress, passwords.encode(PASSWORD), fullName, UserRole.ADMIN);
        user.verifyEmail(clock.instant()); return users.saveAndFlush(user);
    }
    private Map<String, Object> correction() {
        return new HashMap<>(Map.of("currentPassword", PASSWORD, "operationKey", UUID.randomUUID().toString(),
                "reason", "SYNTHETIC reviewed-root entry error; original driver remains unchanged",
                "identityReview", review(CORRECT_DNI), "dataEntryErrorConfirmed", true, "samePersonConfirmed", true));
    }
    private Map<String, Object> opening() { return Map.of("operationKey", UUID.randomUUID().toString(), "reason", "SYNTHETIC explicit legacy regularization", "identityPreservedConfirmed", true); }
    private Map<String, Object> review(String dni) { return Map.of("documentType", "DNI", "documentNumber", dni, "method", "IN_PERSON", "reference", "SYNTHETIC original visual inspection", "originalReviewed", true, "identityConfirmed", true); }
    private Map<String, Object> reviewCommand(String dni) { return Map.of("identityReview", review(dni), "reason", "SYNTHETIC authorized identity review"); }
    private String correctionPath() { return "/api/v1/drivers/" + driver.getId() + "/identity-review/corrections"; }
    private String openPath() { return "/api/v1/drivers/" + driver.getId() + "/regularization"; }
    private String hash(String dni) { return hasher.hashOrigin("conductor-person:DNI:" + dni); }
    private int count(String sql, Object... arguments) { return jdbc.queryForObject(sql, Integer.class, arguments); }
    private Map<String, Object> root() { return jdbc.queryForMap("select * from person_identity where user_id=?", conductor.getId()); }
    private Map<String, Object> account() { return jdbc.queryForMap("select id,organization_id,email,password_hash,full_name,phone,role,active,email_verified_at,session_version,created_at from app_user where id=?", conductor.getId()); }
    private Map<String, Object> driver() { return jdbc.queryForMap("select * from driver where id=?", driver.getId()); }
    private List<Map<String, Object>> ledger() { return jdbc.queryForList("select * from pilot_account_creation order by user_id"); }
    private ResultActions send(String path, AppUser actor, Object body) throws Exception { return mvc.perform(post(path).with(auth(actor)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))); }
    private RequestPostProcessor auth(AppUser actor) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name())).jwt(value -> {
            value.subject(actor.getId().toString()).claim("email", actor.getEmail()).claim("role", actor.getRole().name()).claim("sessionVersion", actor.getSessionVersion());
            if (actor.getOrganizationId() != null) value.claim("organizationId", actor.getOrganizationId().toString());
        });
    }

    @TestConfiguration
    static class ClockConfiguration { @Bean @Primary CorrectionClock correctionClock() { return new CorrectionClock(); } }
    static class CorrectionClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.now());
        void set(Instant instant) { time.set(instant); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time.get(); }
    }
}
