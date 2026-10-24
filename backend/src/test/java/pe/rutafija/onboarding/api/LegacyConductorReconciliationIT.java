package pe.rutafija.onboarding.api;

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
import pe.rutafija.identity.application.EmailOtpService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.terms.domain.AdminTermsDocument;
import pe.rutafija.terms.infrastructure.AdminTermsDocumentRepository;

import java.security.SecureRandom;
import java.sql.DriverManager;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Synthetic, rollback-only integration against a dedicated local database.
 * Never inherits the historical harness: that harness globally truncates ruta_fija_test.
 * The URL is checked and observed before Spring/Flyway can perform any write.
 */
@SpringBootTest(properties = {
        "debug=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF",
        "app.security.person-identity-gate.enabled=true", "app.security.admin-terms-gate.enabled=true",
        "spring.flyway.locations=classpath:db/migration", "spring.flyway.clean-disabled=true",
        "spring.flyway.target=50", "spring.jpa.hibernate.ddl-auto=validate",
        "app.seed.enabled=false", "app.qualification.maintenance.enabled=false"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@Import(LegacyConductorReconciliationIT.ClockConfiguration.class)
@EnabledIfEnvironmentVariable(named = "RF_LEGACY_QA_URL", matches = ".+")
@Transactional
class LegacyConductorReconciliationIT {
    private static final String URL = "jdbc:postgresql://127.0.0.1:5432/rf_legacy_qa_20261023";
    private static final String PASSWORD = "Synthetic-Legacy-Only-Password-2026";
    private static final String DRIVER_DNI = "51000001";
    private static final String LICENSE = "SYNTHETIC-LICENSE-LEGACY";
    private static final String NAME = "Synthetic Legacy Conductor";

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        if (!URL.equals(required("RF_LEGACY_QA_URL"))) {
            throw new IllegalStateException("EXACT_DEDICATED_LEGACY_QA_DATABASE_REQUIRED");
        }
        String applicationPassword = required("RF_LEGACY_QA_APP_PASSWORD");
        String migratorPassword = required("RF_LEGACY_QA_MIGRATOR_PASSWORD");
        observeExactTarget("rf_migrator", migratorPassword);
        observeExactTarget("rf_app", applicationPassword);
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        String jwtSecret = Base64.getEncoder().encodeToString(secret);
        registry.add("spring.datasource.url", () -> URL);
        registry.add("spring.datasource.username", () -> "rf_app");
        registry.add("spring.datasource.password", () -> applicationPassword);
        registry.add("spring.flyway.url", () -> URL);
        registry.add("spring.flyway.user", () -> "rf_migrator");
        registry.add("spring.flyway.password", () -> migratorPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        registry.add("app.security.jwt.secret-base64", () -> jwtSecret);
    }

    private static void observeExactTarget(String role, String password) {
        try (var connection = DriverManager.getConnection(URL, role, password)) {
            connection.setReadOnly(true);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("select current_database(),current_user,inet_server_port()")) {
                if (!result.next() || !"rf_legacy_qa_20261023".equals(result.getString(1))
                        || !role.equals(result.getString(2)) || result.getInt(3) != 5432) {
                    throw new IllegalStateException("DEDICATED_LEGACY_QA_TARGET_OBSERVATION_FAILED");
                }
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("DEDICATED_LEGACY_QA_CONNECTION_REQUIRED", failure);
        }
    }

    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("MISSING_PRIVATE_LEGACY_QA_INPUT_" + key);
        return value;
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
    @Autowired Environment environment;
    @Autowired ReconciliationClock clock;
    @Autowired EmailOtpService otpService;
    @Autowired EntityManager entityManager;
    @MockitoBean OtpEmailSender email;
    private Organization organization;
    private AppUser owner, admin, otherAdmin, conductor;
    private Driver driver;
    private TransportGroup group;

    @BeforeEach
    void syntheticFixture() throws Exception {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("rf_legacy_qa_20261023");
        assertThat(jdbc.queryForObject("select current_user", String.class)).isEqualTo("rf_app");
        assertThat(environment.getProperty("app.security.person-identity-gate.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("app.security.admin-terms-gate.enabled", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where success", Integer.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version is null or not success", Integer.class)).isZero();
        clock.set(Instant.now().truncatedTo(ChronoUnit.MICROS));
        String suffix = UUID.randomUUID().toString();
        organization = organizations.saveAndFlush(Organization.active("Synthetic Legacy Organization " + suffix, "Synthetic Legacy", "America/Lima"));
        Organization otherOrganization = organizations.saveAndFlush(Organization.active("Synthetic Other Organization " + suffix, "Synthetic Other", "America/Lima"));
        owner = AppUser.superAdmin("owner." + suffix + "@rutafija.test", passwords.encode(PASSWORD), "Synthetic Owner");
        owner.verifyEmail(clock.instant());
        owner = users.saveAndFlush(owner);
        admin = verifiedAccount(organization, "admin." + suffix + "@rutafija.test", UserRole.ADMIN, "Synthetic ADMIN");
        otherAdmin = verifiedAccount(otherOrganization, "other." + suffix + "@rutafija.test", UserRole.ADMIN, "Synthetic Other ADMIN");
        conductor = users.saveAndFlush(AppUser.organizationUser(organization, "legacy." + suffix + "@rutafija.test", passwords.encode(PASSWORD), NAME, UserRole.CONDUCTOR));
        group = groups.saveAndFlush(TransportGroup.active(organization, "Synthetic Legacy Group", null));
        driver = drivers.saveAndFlush(Driver.register(organization, conductor, group, NAME, "999999999", "DNI", DRIVER_DNI, LICENSE));
        send("/api/v1/platform/identity/self-enrollment", owner,
                Map.of("currentPassword", PASSWORD, "ownerDeclaration", true, "identityReview", review("91000001")))
                .andExpect(status().isOk());
        send("/api/v1/platform/admins/" + admin.getId() + "/identity-review", owner, reviewCommand("41000001"))
                .andExpect(status().isOk());
        send("/api/v1/platform/admins/" + otherAdmin.getId() + "/identity-review", owner, reviewCommand("41000002"))
                .andExpect(status().isOk());
        AdminTermsDocument document = terms.saveAndFlush(AdminTermsDocument.publish("ADMIN_TERMS", "qa-legacy-1",
                "SYNTHETIC QA TERMS ONLY. No legal acceptance by a real person.", clock.instant()));
        for (AppUser actor : List.of(admin, otherAdmin)) {
            send("/api/v1/admin/terms/acceptances", actor,
                    Map.of("version", document.getVersion(), "contentSha256", document.getContentSha256(), "accepted", true))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void reviewedLegacyDossierLinksSameIdentityThenOtpAllowsIncorporationWithoutOperationalPermission() throws Exception {
        reviewDriver(DRIVER_DNI);
        Map<String, Object> accountBefore = accountSnapshot();
        Map<String, Object> driverBefore = driverSnapshot();
        List<Map<String, Object>> ledgerBefore = ledger();
        Map<String, Object> open = opening();
        JsonNode openingResult = node(send(openPath(), admin, open).andExpect(status().isOk()).andReturn());
        UUID requestId = UUID.fromString(openingResult.path("requestId").asText());
        JsonNode opened = node(mvc.perform(get(path(requestId, "documentary-expediente")).with(auth(admin)))
                .andExpect(status().isOk()).andReturn());
        assertThat(opened.path("existingAccountRegularization").asBoolean()).isTrue();
        assertThat(opened.path("existingUserId").asText()).isEqualTo(conductor.getId().toString());
        assertThat(opened.path("existingDriverId").asText()).isEqualTo(driver.getId().toString());
        assertThat(opened.path("status").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(opened.path("personalApproved").asBoolean()).isFalse();
        assertThat(opened.path("provisionedUserId").isNull() || opened.path("provisionedUserId").isMissingNode()).isTrue();
        MvcResult incorporation = mvc.perform(get("/api/v1/drivers/" + driver.getId() + "/incorporation-status").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.incorporationStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.accountProvisioned").value(false))
                .andExpect(jsonPath("$.personalApproved").value(false)).andReturn();
        assertThat(incorporation.getResponse().getContentAsString())
                .doesNotContain(DRIVER_DNI, LICENSE, conductor.getEmail(), NAME, "personHash", "person_hash", "passwordHash");
        mvc.perform(get("/api/v1/drivers/" + driver.getId() + "/incorporation-status").with(auth(otherAdmin)))
                .andExpect(status().isNotFound());
        send(openPath(), admin, open).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(requestId.toString()));
        assertPreserved(accountBefore, driverBefore, ledgerBefore);
        assertThat(count("select count(*) from conductor_onboarding_request")).isEqualTo(1);
        assertThat(count("select count(*) from conductor_onboarding_review_event where request_id=? and action='LEGACY_REGULARIZATION_OPENED'", requestId)).isEqualTo(1);
        assertThat(count("select count(*) from organization_invitation where used_at is null")).isZero();
        mobileLogin().andExpect(status().isConflict());
        verifyNoInteractions(email);

        JsonNode reviewed = saveDocuments(requestId, opened.path("version").asLong());
        JsonNode approved = node(send(path(requestId, "approve"), admin,
                decision(reviewed.path("version").asLong())).andExpect(status().isOk())
                .andExpect(jsonPath("$.personalApproved").value(true)).andReturn());
        mobileLogin().andExpect(status().isConflict());
        Map<String, Object> provision = decision(approved.path("version").asLong());
        provision.put("groupId", group.getId());
        send(path(requestId, "provision-existing"), admin, provision).andExpect(status().isOk())
                .andExpect(jsonPath("$.provisionedUserId").value(conductor.getId().toString()))
                .andExpect(jsonPath("$.provisionedDriverId").value(driver.getId().toString()))
                .andExpect(jsonPath("$.operationalEnabled").value(false));
        send(path(requestId, "provision-existing"), admin, provision).andExpect(status().isOk());
        assertPreserved(accountBefore, driverBefore, ledgerBefore);
        assertThat(count("select count(*) from conductor_onboarding_review_event where request_id=? and action='EXISTING_ACCOUNT_LINKED'", requestId)).isEqualTo(1);
        assertThat(count("select count(*) from conductor_common_person_bridge")).isEqualTo(1);
        assertThat(count("select count(*) from app_user where email=?", conductor.getEmail())).isEqualTo(1);
        verifyNoInteractions(email);

        AtomicReference<String> sentCode = new AtomicReference<>();
        doAnswer(invocation -> { sentCode.set(invocation.getArgument(1)); return null; })
                .when(email).sendVerificationCode(eq(conductor.getEmail()), any(), any());
        JsonNode challenge = node(mobileLogin().andExpect(status().isAccepted())
                .andExpect(jsonPath("$.emailVerificationRequired").value(true)).andReturn());
        String verificationToken = challenge.path("verification").path("verificationToken").asText();
        assertThat(verificationToken).isNotBlank();
        assertThat(sentCode.get()).matches("[0-9]{6}");
        String wrongCode = "000000".equals(sentCode.get()) ? "111111" : "000000";
        sendPublic("/api/v1/mobile/auth/otp/verify", Map.of("verificationToken", verificationToken, "code", wrongCode))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
        JsonNode session = node(sendPublic("/api/v1/mobile/auth/otp/verify",
                Map.of("verificationToken", verificationToken, "code", sentCode.get()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessScope").value("INCORPORATION")).andReturn());
        String accessToken = session.path("accessToken").asText();
        mvc.perform(get("/api/v1/mobile/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/mobile/availability").header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DISPONIBLE\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("DRIVER_INACTIVE"));
        sendPublic("/api/v1/mobile/auth/otp/verify", Map.of("verificationToken", verificationToken, "code", sentCode.get()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EMAIL_OTP_INVALID"));
        assertThat(jdbc.queryForObject("select email_verified_at is not null from app_user where id=?", Boolean.class, conductor.getId())).isTrue();
        assertThat(jdbc.queryForObject("select active from driver where id=?", Boolean.class, driver.getId())).isFalse();
        verify(email, times(1)).sendVerificationCode(eq(conductor.getEmail()), any(), any());
    }

    @Test
    void onlySameTenantAdminCanOpenAndSeeLegacyDossier() throws Exception {
        reviewDriver(DRIVER_DNI);
        send(openPath(), otherAdmin, opening()).andExpect(status().isNotFound());
        send(openPath(), owner, opening()).andExpect(status().isForbidden());
        JsonNode opened = node(send(openPath(), admin, opening()).andExpect(status().isOk()).andReturn());
        UUID id = UUID.fromString(opened.path("requestId").asText());
        mvc.perform(get(path(id, "documentary-expediente")).with(auth(otherAdmin))).andExpect(status().isNotFound());
        send(path(id, "provision-existing"), otherAdmin, decision(0)).andExpect(status().isNotFound());
        send(path(id, "provision-existing"), owner, decision(0)).andExpect(status().isForbidden());
        verifyNoInteractions(email);
    }

    @Test
    void reviewedIdentityMustMatchLegacyDriverAndOpeningNeverRepairsDocumentsSilently() throws Exception {
        send(openPath(), admin, opening()).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        reviewDriver("51000002");
        send(openPath(), admin, opening()).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        assertThat(jdbc.queryForObject("select document_number from driver where id=?", String.class, driver.getId())).isEqualTo(DRIVER_DNI);
        assertThat(jdbc.queryForObject("select email_verified_at is null from app_user where id=?", Boolean.class, conductor.getId())).isTrue();
        verifyNoInteractions(email);
    }

    @Test
    void confirmationAndOperationPayloadAreRequiredAndCannotBeReusedForAnotherMeaning() throws Exception {
        reviewDriver(DRIVER_DNI);
        Map<String, Object> unconfirmed = opening();
        unconfirmed.put("identityPreservedConfirmed", false);
        send(openPath(), admin, unconfirmed).andExpect(status().isBadRequest());
        Map<String, Object> operation = opening();
        send(openPath(), admin, operation).andExpect(status().isOk());
        operation.put("reason", "SYNTHETIC changed purpose");
        send(openPath(), admin, operation).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_onboarding_request")).isEqualTo(1);
        verifyNoInteractions(email);
    }

    @Test
    void approvalAndExplicitProvisionAreRequiredAndOrdinaryNewAccountProvisionCannotAdoptLegacy() throws Exception {
        reviewDriver(DRIVER_DNI);
        JsonNode opened = node(send(openPath(), admin, opening()).andExpect(status().isOk()).andReturn());
        UUID id = UUID.fromString(opened.path("requestId").asText());
        Map<String, Object> command = decision(opened.path("version").asLong());
        command.put("groupId", group.getId());
        send(path(id, "approve"), admin, decision(opened.path("version").asLong())).andExpect(status().isConflict());
        send(path(id, "provision-existing"), admin, command).andExpect(status().isConflict());
        JsonNode reviewed = saveDocuments(id, opened.path("version").asLong());
        JsonNode approved = node(send(path(id, "approve"), admin, decision(reviewed.path("version").asLong()))
                .andExpect(status().isOk()).andReturn());
        command = decision(approved.path("version").asLong());
        command.put("groupId", group.getId());
        send(path(id, "provision"), admin, command).andExpect(status().isConflict());
        assertThat(count("select count(*) from app_user")).isEqualTo(4);
        assertThat(count("select count(*) from driver")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select provisioned_at is null from conductor_onboarding_request where id=?", Boolean.class, id)).isTrue();
        verifyNoInteractions(email);
    }

    @Test
    void approvedLegacyCannotChangeGroupOrCreateAnotherIdentityByProvisioning() throws Exception {
        reviewDriver(DRIVER_DNI);
        JsonNode opened = node(send(openPath(), admin, opening()).andExpect(status().isOk()).andReturn());
        UUID id = UUID.fromString(opened.path("requestId").asText());
        JsonNode reviewed = saveDocuments(id, opened.path("version").asLong());
        JsonNode approved = node(send(path(id, "approve"), admin, decision(reviewed.path("version").asLong()))
                .andExpect(status().isOk()).andReturn());
        TransportGroup replacement = groups.saveAndFlush(TransportGroup.active(organization, "Synthetic Different Group", null));
        Map<String, Object> command = decision(approved.path("version").asLong());
        command.put("groupId", replacement.getId());
        send(path(id, "provision-existing"), admin, command).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select group_id from driver where id=?", UUID.class, driver.getId())).isEqualTo(group.getId());
        assertThat(count("select count(*) from app_user")).isEqualTo(4);
        assertThat(count("select count(*) from pilot_account_creation")).isEqualTo(4);
        verifyNoInteractions(email);
    }

    @Test
    void returnedLegacyDossierCanBeResubmittedByItsAdminWithoutGrantingApproval() throws Exception {
        reviewDriver(DRIVER_DNI);
        JsonNode opened = node(send(openPath(), admin, opening()).andExpect(status().isOk()).andReturn());
        UUID id = UUID.fromString(opened.path("requestId").asText());
        JsonNode reviewed = saveDocuments(id, 0, true);
        Map<String, Object> returnedCommand = decision(reviewed.path("version").asLong());
        returnedCommand.put("deadline", clock.instant().plus(Duration.ofDays(5)).toString());
        JsonNode returned = node(send(path(id, "return"), admin, returnedCommand)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RETURNED")).andReturn());
        Map<String, Object> resume = decision(returned.path("version").asLong());
        send(path(id, "resubmit-existing"), otherAdmin, resume).andExpect(status().isNotFound());
        send(path(id, "resubmit-existing"), owner, resume).andExpect(status().isForbidden());
        send(path(id, "resubmit-existing"), admin, resume).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.personalApproved").value(false));
        send(path(id, "resubmit-existing"), admin, resume).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_REVIEW"));
        assertThat(jdbc.queryForObject("select personal_approved_at is null and provisioned_at is null from conductor_onboarding_request where id=?", Boolean.class, id)).isTrue();
        assertThat(count("select count(*) from conductor_onboarding_request")).isEqualTo(1);
        mobileLogin().andExpect(status().isConflict());
        verifyNoInteractions(email);
    }

    @Test
    void openingAndProvisionPreserveExistingOtpChallengeAndItsAttemptAndSendBudget() throws Exception {
        reviewDriver(DRIVER_DNI);
        otpService.beginForLogin(conductor);
        entityManager.flush();
        Map<String, Object> challengeBefore = jdbc.queryForMap("select * from email_otp_challenge where user_id=?", conductor.getId());
        JsonNode opened = node(send(openPath(), admin, opening()).andExpect(status().isOk()).andReturn());
        UUID id = UUID.fromString(opened.path("requestId").asText());
        JsonNode reviewed = saveDocuments(id, 0);
        JsonNode approved = node(send(path(id, "approve"), admin, decision(reviewed.path("version").asLong()))
                .andExpect(status().isOk()).andReturn());
        Map<String, Object> command = decision(approved.path("version").asLong());
        // The existing group is the default; no replacement group may be inferred.
        send(path(id, "provision-existing"), admin, command).andExpect(status().isOk());
        assertThat(jdbc.queryForMap("select * from email_otp_challenge where user_id=?", conductor.getId())).isEqualTo(challengeBefore);
        assertThat(jdbc.queryForObject("select email_verified_at is null from app_user where id=?", Boolean.class, conductor.getId())).isTrue();
        verify(email, times(1)).sendVerificationCode(eq(conductor.getEmail()), any(), any());
    }

    @Test
    void inactiveLegacyAccountCannotOpenOrBeReactivatedByRegularization() throws Exception {
        reviewDriver(DRIVER_DNI);
        jdbc.update("update app_user set active=false where id=?", conductor.getId());
        entityManager.clear();
        send(openPath(), admin, opening()).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        assertThat(jdbc.queryForObject("select active from app_user where id=?", Boolean.class, conductor.getId())).isFalse();
        assertThat(count("select count(*) from pilot_account_creation")).isEqualTo(4);
        verifyNoInteractions(email);
    }

    @Test
    void inactiveGroupCannotBeUsedToProvisionAnApprovedExistingAccount() throws Exception {
        reviewDriver(DRIVER_DNI);
        JsonNode opened = node(send(openPath(), admin, opening()).andExpect(status().isOk()).andReturn());
        UUID id = UUID.fromString(opened.path("requestId").asText());
        JsonNode reviewed = saveDocuments(id, 0);
        JsonNode approved = node(send(path(id, "approve"), admin, decision(reviewed.path("version").asLong()))
                .andExpect(status().isOk()).andReturn());
        jdbc.update("update transport_group set active=false where id=?", group.getId());
        entityManager.clear();
        send(path(id, "provision-existing"), admin, decision(approved.path("version").asLong())).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("select provisioned_at is null from conductor_onboarding_request where id=?", Boolean.class, id)).isTrue();
        assertThat(jdbc.queryForObject("select active from transport_group where id=?", Boolean.class, group.getId())).isFalse();
        assertThat(jdbc.queryForObject("select active from driver where id=?", Boolean.class, driver.getId())).isFalse();
        verifyNoInteractions(email);
    }

    @Test
    void existingPreliminaryEmailIsNeverSilentlyAdoptedIntoTheLegacyDossier() throws Exception {
        reviewDriver(DRIVER_DNI);
        UUID existingIdentity = UUID.randomUUID();
        jdbc.update("""
                insert into conductor_onboarding_identity(id,email,password_hash,full_name,created_at,updated_at)
                values(?,?,?,?,?,?)
                """, existingIdentity, conductor.getEmail(), passwords.encode("Synthetic-Different-Preliminary-Password"),
                "Synthetic Different Preliminary Declaration", java.sql.Timestamp.from(clock.instant()), java.sql.Timestamp.from(clock.instant()));
        Map<String, Object> preliminaryBefore = jdbc.queryForMap("select * from conductor_onboarding_identity where id=?", existingIdentity);
        send(openPath(), admin, opening()).andExpect(status().isConflict());
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        assertThat(count("select count(*) from conductor_person_binding")).isZero();
        assertThat(jdbc.queryForMap("select * from conductor_onboarding_identity where id=?", existingIdentity)).isEqualTo(preliminaryBefore);
        assertThat(count("select count(*) from app_user")).isEqualTo(4);
        verifyNoInteractions(email);
    }

    @Test
    void suspendedOrganizationCannotOpenARegularizationWithAnOlderAdminSession() throws Exception {
        reviewDriver(DRIVER_DNI);
        jdbc.update("update organization set status='SUSPENDED' where id=?", organization.getId());
        entityManager.clear();
        send(openPath(), admin, opening()).andExpect(status().isUnauthorized());
        assertThat(count("select count(*) from conductor_onboarding_request")).isZero();
        assertThat(jdbc.queryForObject("select status from organization where id=?", String.class, organization.getId())).isEqualTo("SUSPENDED");
        assertThat(jdbc.queryForObject("select active from driver where id=?", Boolean.class, driver.getId())).isFalse();
        verifyNoInteractions(email);
    }

    private void reviewDriver(String dni) throws Exception {
        send("/api/v1/drivers/" + driver.getId() + "/identity-review", admin, reviewCommand(dni)).andExpect(status().isOk());
        entityManager.flush();
        entityManager.clear();
    }

    private JsonNode saveDocuments(UUID id, long version) throws Exception {
        return saveDocuments(id, version, false);
    }

    private JsonNode saveDocuments(UUID id, long version, boolean licenseObserved) throws Exception {
        List<Map<String, Object>> documents = new ArrayList<>();
        documents.add(document("CON-01", Map.of("documentType", "DNI", "documentNumber", DRIVER_DNI, "fullName", NAME, "issuer", "SYNTHETIC"), "CONFORME"));
        Map<String, Object> license = document("CON-02", Map.of("licenseNumber", LICENSE, "licenseClass", "A", "licenseCategory", "A-IIA", "restrictions", "NINGUNA", "issuer", "SYNTHETIC"), "CONFORME");
        license.put("expiryMode", "DATED");
        license.put("validUntil", clock.instant().plus(Duration.ofDays(60)).toString());
        if (licenseObserved) {
            license.put("result", "OBSERVADO");
            license.put("observation", "SYNTHETIC documentary correction required");
        }
        documents.add(license);
        for (String key : List.of("CON-03", "CON-04", "CON-05")) documents.add(document(key, Map.of(), "NO_APLICA_JUSTIFICADO"));
        return node(mvc.perform(put(path(id, "documentary-expediente")).with(auth(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("expectedVersion", version, "operationKey", UUID.randomUUID().toString(), "modality", "TAXI", "documents", documents))))
                .andExpect(status().isOk()).andReturn());
    }

    private Map<String, Object> document(String key, Map<String, String> fields, String result) {
        Map<String, Object> value = new HashMap<>();
        value.put("requirementId", key); value.put("fields", fields); value.put("result", result);
        value.put("expiryMode", "NO_EXPIRY"); value.put("applicability", "CONFIRMED");
        value.put("condition", "SYNTHETIC applicability decision, no external legal claim");
        value.put("sourceReference", "SYNTHETIC controlled reference");
        value.put("reviewReference", "SYNTHETIC visual review");
        value.put("observation", "SYNTHETIC explicit test-only non-applicability");
        return value;
    }

    private AppUser verifiedAccount(Organization org, String address, UserRole role, String name) {
        AppUser user = AppUser.organizationUser(org, address, passwords.encode(PASSWORD), name, role);
        user.verifyEmail(clock.instant());
        return users.saveAndFlush(user);
    }

    private Map<String, Object> opening() {
        return new HashMap<>(Map.of("operationKey", UUID.randomUUID().toString(), "reason", "SYNTHETIC explicit legacy regularization", "identityPreservedConfirmed", true));
    }

    private Map<String, Object> decision(long version) {
        return new HashMap<>(Map.of("expectedVersion", version, "operationKey", UUID.randomUUID().toString(), "reason", "SYNTHETIC controlled documentary decision", "reiteration", false));
    }

    private Map<String, Object> review(String dni) {
        return Map.of("documentType", "DNI", "documentNumber", dni, "method", "IN_PERSON", "reference", "SYNTHETIC visual review", "originalReviewed", true, "identityConfirmed", true);
    }

    private Map<String, Object> reviewCommand(String dni) { return Map.of("identityReview", review(dni), "reason", "SYNTHETIC authorized identity review"); }
    private String openPath() { return "/api/v1/drivers/" + driver.getId() + "/regularization"; }
    private String path(UUID id, String suffix) { return "/api/v1/onboarding/requests/" + id + "/" + suffix; }
    private JsonNode node(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private ResultActions send(String path, AppUser actor, Object body) throws Exception { return mvc.perform(post(path).with(auth(actor)).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))); }
    private ResultActions sendPublic(String path, Object body) throws Exception { return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body))); }
    private ResultActions mobileLogin() throws Exception {
        entityManager.flush();
        entityManager.clear();
        return sendPublic("/api/v1/mobile/auth/login", Map.of("email", conductor.getEmail(), "password", PASSWORD));
    }
    private int count(String sql, Object... arguments) { return jdbc.queryForObject(sql, Integer.class, arguments); }

    private RequestPostProcessor auth(AppUser actor) {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_" + actor.getRole().name())).jwt(value -> {
            value.subject(actor.getId().toString()).claim("email", actor.getEmail()).claim("role", actor.getRole().name()).claim("sessionVersion", actor.getSessionVersion());
            if (actor.getOrganizationId() != null) value.claim("organizationId", actor.getOrganizationId().toString());
        });
    }

    private Map<String, Object> accountSnapshot() { return jdbc.queryForMap("select id,organization_id,email,password_hash,role,active,email_verified_at,session_version,created_at from app_user where id=?", conductor.getId()); }
    private Map<String, Object> driverSnapshot() { return jdbc.queryForMap("select id,user_id,organization_id,group_id,full_name,phone,document_type,document_number,license_number,active,created_at from driver where id=?", driver.getId()); }
    private List<Map<String, Object>> ledger() { return jdbc.queryForList("select user_id,role,created_at from pilot_account_creation order by user_id"); }
    private void assertPreserved(Map<String, Object> account, Map<String, Object> originalDriver, List<Map<String, Object>> historicalLedger) {
        Map<String, Object> expectedAccount = new HashMap<>(account);
        // Opening invalidates pre-regularization sessions exactly once; credentials and identity stay unchanged.
        expectedAccount.put("session_version", ((Number) account.get("session_version")).longValue() + 1L);
        assertThat(accountSnapshot()).isEqualTo(expectedAccount);
        assertThat(driverSnapshot()).isEqualTo(originalDriver);
        assertThat(ledger()).isEqualTo(historicalLedger);
    }

    @TestConfiguration
    static class ClockConfiguration {
        @Bean @Primary ReconciliationClock reconciliationClock() { return new ReconciliationClock(); }
    }

    static class ReconciliationClock extends Clock {
        private final AtomicReference<Instant> time = new AtomicReference<>(Instant.now());
        void set(Instant now) { time.set(now); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return time.get(); }
    }
}
