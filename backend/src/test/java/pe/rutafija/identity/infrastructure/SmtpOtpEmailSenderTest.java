package pe.rutafija.identity.infrastructure;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSender;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.shared.exception.ExternalServiceUnavailableException;

import java.time.Instant;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmtpOtpEmailSenderTest {

    private static final String SENDER = "rutafija.dev1@gmail.com";
    private static final String AUTHORIZED_RECIPIENT = "sandrito10xyz@gmail.com";
    private static final String SECRET = "application-password-must-not-leak";

    @Test
    void sendsPlainOtpOnlyToTheConfiguredAuthorizedRecipient() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        OtpEmailSender sender = sender(javaMailSender, SENDER, AUTHORIZED_RECIPIENT);

        sender.sendVerificationCode(
                AUTHORIZED_RECIPIENT,
                "012345",
                Instant.parse("2026-10-14T17:05:00Z")
        );

        verify(javaMailSender).send(mimeMessage);
        assertThat(((InternetAddress) mimeMessage.getFrom()[0]).getAddress()).isEqualTo(SENDER);
        assertThat(((InternetAddress) mimeMessage.getRecipients(Message.RecipientType.TO)[0]).getAddress())
                .isEqualTo(AUTHORIZED_RECIPIENT);
        assertThat(mimeMessage.getSubject()).contains("verificación");
        assertThat(mimeMessage.getContent().toString()).contains("012345");
    }

    @Test
    void rejectsEveryRecipientOutsideTheExplicitLocalPilotAllowlist() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        OtpEmailSender sender = sender(javaMailSender, SENDER, AUTHORIZED_RECIPIENT);

        assertThatThrownBy(() -> sender.sendVerificationCode(
                "another-recipient@example.com",
                "012345",
                Instant.parse("2026-10-14T17:05:00Z")
        ))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("External service unavailable");

        verifyNoInteractions(javaMailSender);
    }

    @Test
    void hidesProviderAuthenticationDetailsFromTheCaller() throws Exception {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailAuthenticationException("SMTP rejected " + SECRET))
                .when(javaMailSender)
                .send(mimeMessage);
        OtpEmailSender sender = sender(javaMailSender, SENDER, AUTHORIZED_RECIPIENT);

        assertThatThrownBy(() -> sender.sendVerificationCode(
                AUTHORIZED_RECIPIENT,
                "012345",
                Instant.parse("2026-10-14T17:05:00Z")
        ))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("External service unavailable")
                .hasNoCause()
                .hasNoSuppressedExceptions();
    }

    @Test
    void rejectsAConfiguredSenderOutsideTheApprovedPilotAccount() {
        JavaMailSender javaMailSender = mock(JavaMailSender.class);
        OtpEmailSender sender = sender(
                javaMailSender,
                "other-account@gmail.com",
                AUTHORIZED_RECIPIENT
        );

        assertThatThrownBy(() -> sender.sendVerificationCode(
                AUTHORIZED_RECIPIENT,
                "012345",
                Instant.parse("2026-10-14T17:05:00Z")
        ))
                .isInstanceOf(ExternalServiceUnavailableException.class)
                .hasMessage("External service unavailable");

        verifyNoInteractions(javaMailSender);
    }

    private OtpEmailSender sender(
            JavaMailSender javaMailSender,
            String configuredSender,
            String recipients
    ) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("mailSender", javaMailSender);
        ObjectProvider<JavaMailSender> provider = beanFactory.getBeanProvider(JavaMailSender.class);
        return new SmtpOtpEmailSender(provider, configuredSender, SECRET, SENDER, recipients);
    }
}
