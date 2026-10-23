package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.invitation.application.InvitationCodeGenerator;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.api.dto.StartConductorOnboardingRequest;
import pe.rutafija.onboarding.application.ConductorOnboardingService;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** No global cleanup, no migrations. Every synthetic change, including ledger inserts, rolls back. */
@SpringBootTest(properties = {"app.security.person-identity-gate.enabled=true", "app.security.admin-terms-gate.enabled=true",
        "debug=false", "logging.level.root=WARN", "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF", "logging.level.org.springframework.jdbc.core=OFF"})
@ActiveProfiles("test")
@Import(ManualOtpCaseRecoveryIT.NoScheduledWork.class)
class ManualOtpCaseRecoveryIT {
    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ConductorOnboardingService onboarding;
    @Autowired OrganizationRepository organizations;
    @Autowired InvitationCodeGenerator codes;
    @Autowired InvitationCodeHasher hashes;
    @MockitoBean OtpEmailSender sender;

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        NativePostgresTestDatabase.load().registerSpringProperties(registry);
        registry.add("spring.flyway.enabled", () -> false);
    }

    @TestConfiguration static class NoScheduledWork {
        @Bean static BeanFactoryPostProcessor removeScheduler() {
            return factory -> {
                if (factory instanceof BeanDefinitionRegistry registry
                        && registry.containsBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME))
                    registry.removeBeanDefinition(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME);
            };
        }
    }

    @Test void continuesSameIdentityPreservingSendsLedgerOldRevocationAndUnrelatedDataThenConsumes() {
        AtomicReference<String> code = new AtomicReference<>();
        doAnswer(call -> { code.set(call.getArgument(1)); return null; }).when(sender)
                .sendVerificationCode(anyString(), anyString(), any(Instant.class));
        inRollback((fixture, recipient) -> {
            Organization unrelated = organizations.saveAndFlush(Organization.active("UNRELATED ROLLBACK " + UUID.randomUUID(), "SYNTHETIC", "America/Lima"));
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            var prior = ManualOtpCaseRecovery.inspect(jdbc, fixture.runId(), recipient);
            prior.requireContinuable();
            UUID nextInvitation = UUID.randomUUID();
            String invitationCode = codes.generate();
            int ledger = count("select count(*) from pilot_account_creation where role='ADMIN'");
            var savedLimits = limits(prior.challenge().identityId());
            ManualOtpCaseRecovery.prepareContinuation(jdbc, fixture.runId(), recipient, nextInvitation,
                    hashes.hashCode(invitationCode, recipient), Instant.now());
            assertThat(limits(prior.challenge().identityId())).isEqualTo(savedLimits);
            assertThat(count("select count(*) from organization_invitation where id=? and revoked_at is not null and used_at is null", fixture.invitationId())).isEqualTo(1);
            var next = onboarding.start(request(recipient, invitationCode), "manual-otp-" + UUID.randomUUID(), "127.0.0.1");
            var afterIssue = ManualOtpCaseRecovery.inspect(jdbc, fixture.runId(), recipient);
            assertThat(afterIssue.challenge().identityId()).isEqualTo(prior.challenge().identityId());
            assertThat(afterIssue.challenge().sends()).hasSize(2).startsWith(prior.challenge().sends().getFirst());
            assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(ledger);
            assertThat(count("select count(*) from app_user where id=? and active=false", fixture.adminId())).isEqualTo(1);
            var accepted = onboarding.verify(next.verificationToken(), code.get());
            assertThat(accepted.status()).isEqualTo("PENDING_REVIEW");
            ManualOtpDeliveryValidation.cleanupExactFixture(context, jdbc, transaction,
                    new ManualOtpDeliveryValidation.Fixture(UUID.randomUUID(), fixture.organizationId(), fixture.adminId(), nextInvitation, invitationCode));
            assertThat(count("select count(*) from conductor_onboarding_request where id=? and linked_user_id is null", accepted.requestId())).isEqualTo(1);
            assertThat(count("select count(*) from app_user where email=?", recipient)).isZero();
            assertThat(count("select count(*) from driver where organization_id=?", fixture.organizationId())).isZero();
            assertThat(count("select count(*) from organization where id=? and status='ACTIVE'", unrelated.getId())).isEqualTo(1);
            assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(ledger);
            verify(sender, times(2)).sendVerificationCode(anyString(), anyString(), any(Instant.class));
        });
    }

    @Test void refusesExhaustedSendWindowWithoutPreparingInvitationOrResettingState() {
        inRollback((fixture, recipient) -> {
            var state = ManualOtpCaseRecovery.inspect(jdbc, fixture.runId(), recipient);
            jdbc.update("update conductor_onboarding_email_challenge set send_at_2=?,send_at_3=? where identity_id=?",
                    Timestamp.from(Instant.now().minusSeconds(120)), Timestamp.from(Instant.now().minusSeconds(60)), state.challenge().identityId());
            var before = limits(state.challenge().identityId());
            assertThatThrownBy(() -> ManualOtpCaseRecovery.prepareContinuation(jdbc, fixture.runId(), recipient, UUID.randomUUID(),
                    hashes.hashCode(codes.generate(), recipient), Instant.now()))
                    .isInstanceOfSatisfying(ManualOtpCaseRecovery.Refused.class, failure -> assertThat(failure.code).isEqualTo("SEND_WINDOW_EXHAUSTED_WAIT_NO_RESET"));
            assertThat(limits(state.challenge().identityId())).isEqualTo(before);
            assertThat(count("select count(*) from organization_invitation where organization_id=?", fixture.organizationId())).isEqualTo(1);
            assertThat(count("select count(*) from organization where id=? and status='INACTIVE'", fixture.organizationId())).isEqualTo(1);
            verify(sender, times(1)).sendVerificationCode(anyString(), anyString(), any(Instant.class));
        });
    }

    @Test void refusesAnUnknownRunIdWithoutChangingTheKnownCase() {
        inRollback((fixture, recipient) -> {
            var before = ManualOtpCaseRecovery.inspect(jdbc, fixture.runId(), recipient);
            assertThatThrownBy(() -> ManualOtpCaseRecovery.inspect(jdbc, UUID.randomUUID(), recipient))
                    .isInstanceOfSatisfying(ManualOtpCaseRecovery.Refused.class, failure -> assertThat(failure.code).isEqualTo("EXACT_SYNTHETIC_CASE_NOT_FOUND"));
            var after = ManualOtpCaseRecovery.inspect(jdbc, fixture.runId(), recipient);
            assertThat(after.root()).isEqualTo(before.root());
            assertThat(after.challenge()).isEqualTo(before.challenge());
        });
    }

    @Test void refusesAChangedCaseWithoutReactivatingOrClearingItsFailedAttempt() {
        inRollback((fixture, recipient) -> {
            var prior = ManualOtpCaseRecovery.inspect(jdbc, fixture.runId(), recipient);
            jdbc.update("update conductor_onboarding_email_challenge set failed_attempts=1 where identity_id=?", prior.challenge().identityId());
            assertThatThrownBy(() -> ManualOtpCaseRecovery.prepareContinuation(jdbc, fixture.runId(), recipient, UUID.randomUUID(),
                    hashes.hashCode(codes.generate(), recipient), Instant.now()))
                    .isInstanceOfSatisfying(ManualOtpCaseRecovery.Refused.class, failure -> assertThat(failure.code).isEqualTo("CASE_OTP_FAILURE_OR_BLOCK_REQUIRES_REVIEW"));
            assertThat(count("select failed_attempts from conductor_onboarding_email_challenge where identity_id=?", prior.challenge().identityId())).isEqualTo(1);
            assertThat(count("select count(*) from organization where id=? and status='INACTIVE'", fixture.organizationId())).isEqualTo(1);
        });
    }

    private void inRollback(BiConsumer<ManualOtpDeliveryValidation.Fixture, String> action) {
        int baselineLedger = count("select count(*) from pilot_account_creation where role='ADMIN'");
        assertThat(baselineLedger).as("A rollback-only fixture needs one currently available test slot").isLessThan(4);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        AtomicReference<ManualOtpDeliveryValidation.Fixture> created = new AtomicReference<>();
        String recipient = "rollback.otp." + UUID.randomUUID() + "@rutafija.test";
        transaction.executeWithoutResult(status -> {
            status.setRollbackOnly();
            assertThat(jdbc.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, 7_518_440_514L)).isTrue();
            var fixture = ManualOtpDeliveryValidation.createFixture(context, transaction, jdbc, recipient);
            created.set(fixture);
            onboarding.start(request(recipient, fixture.invitationCode()), "manual-otp-" + fixture.runId(), "127.0.0.1");
            ManualOtpDeliveryValidation.cleanupExactFixture(context, jdbc, transaction, fixture);
            action.accept(fixture, recipient);
        });
        assertThat(count("select count(*) from pilot_account_creation where role='ADMIN'")).isEqualTo(baselineLedger);
        assertThat(count("select count(*) from organization where id=?", created.get().organizationId())).isZero();
        assertThat(count("select count(*) from pilot_account_creation where user_id=?", created.get().adminId())).isZero();
        assertThat(count("select count(*) from conductor_onboarding_identity where email=?", recipient)).isZero();
        System.out.println("SIMULATED_OTP_CASE_TEST_ROLLBACK=PASS_NO_GLOBAL_CLEANUP_NO_COMMITTED_LEDGER_OR_SEND_CHANGES");
    }

    private Object limits(UUID identityId) {
        return jdbc.queryForMap("select send_at_1,send_at_2,send_at_3,failed_attempts,blocked_until from conductor_onboarding_email_challenge where identity_id=?", identityId);
    }
    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }
    private StartConductorOnboardingRequest request(String recipient, String invitation) {
        return new StartConductorOnboardingRequest(recipient, "Synthetic-Password-Only!49", "SYNTHETIC MANUAL APPLICANT", null, invitation);
    }
}
