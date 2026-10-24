package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.infrastructure.OrganizationRepository;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Real HTTP + PostgreSQL; delivery is always a double and every identity synthetic. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.person-identity-gate.enabled=true",
        "app.security.admin-terms-gate.enabled=true",
        "debug=false", "logging.level.root=INFO", "logging.level.org.springframework.web=WARN",
        "logging.level.org.springframework.jdbc.core=INFO", "logging.level.org.hibernate.SQL=INFO"
})
@ActiveProfiles("test")
class ManualOtpValidationHarnessIT extends NativePostgresIntegrationTest {
    @Autowired ConfigurableApplicationContext context;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrganizationRepository organizations;
    @MockitoBean OtpEmailSender sender;

    @Test
    void manualFlowPersistsAndConsumesOverHttpAndDisablesOnlyItsFixtureWithSimulatedMail() throws Exception {
        Organization unrelated = organizations.saveAndFlush(Organization.active(
                "UNRELATED SYNTHETIC ORGANIZATION", "SYNTHETIC UNRELATED", "America/Lima"));
        AtomicReference<char[]> captured = new AtomicReference<>();
        doAnswer(invocation -> {
            assertThat((String) invocation.getArgument(0)).endsWith("@rutafija.test");
            captured.set(((String) invocation.getArgument(1)).toCharArray());
            return null;
        }).when(sender).sendVerificationCode(anyString(), anyString(), any(Instant.class));

        ManualOtpDeliveryValidation.exerciseWithSimulatedMail(context, captured::get);

        verify(sender, times(1)).sendVerificationCode(anyString(), anyString(), any(Instant.class));
        assertThat(captured.get()).containsOnly('\0');
        assertThat(jdbc.queryForObject("select count(*) from pilot_account_creation where role='ADMIN'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from app_user where active=true", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from driver", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from conductor_onboarding_request where status='PENDING_REVIEW'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from organization where status='ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select status from organization where id=?", String.class, unrelated.getId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("select count(*) from organization_invitation where revoked_at is null and used_at is null", Integer.class)).isZero();
    }
}
