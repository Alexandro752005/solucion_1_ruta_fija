package pe.rutafija.identity.infrastructure;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.exception.ExternalServiceUnavailableException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SmtpOtpEmailSender implements OtpEmailSender {

    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
            .ofPattern("HH:mm 'hora de Lima'")
            .withZone(ZoneId.of("America/Lima"));

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String username;
    private final String password;
    private final String allowedSender;
    private final Set<String> allowedRecipients;

    public SmtpOtpEmailSender(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password,
            @Value("${app.otp.email.allowed-sender:}") String allowedSender,
            @Value("${app.otp.email.allowed-recipients:}") String allowedRecipients
    ) {
        this.mailSenderProvider = mailSenderProvider;
        this.username = username;
        this.password = password;
        this.allowedSender = allowedSender;
        this.allowedRecipients = Arrays.stream(allowedRecipients.split(","))
                .map(String::strip)
                .filter(value -> !value.isBlank())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void sendVerificationCode(String recipient, String code, Instant expiresAt) {
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();
        if (mailSender == null
                || username.isBlank()
                || password.isBlank()
                || !username.equalsIgnoreCase(allowedSender.strip())
                || !allowedRecipients.contains(normalizeRecipient(recipient))) {
            throw deliveryUnavailable();
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(username);
            helper.setTo(recipient);
            helper.setSubject("Código de verificación de Ruta Fija");
            helper.setText("Tu código de verificación es: " + code
                    + "\n\nVence a las " + EXPIRY_FORMAT.format(expiresAt)
                    + ". Si no solicitaste este código, puedes ignorar este mensaje.", false);
            mailSender.send(message);
        } catch (MessagingException | MailException exception) {
            // Keep provider details, message bodies, and credentials out of application logs.
            throw deliveryUnavailable();
        }
    }

    private ExternalServiceUnavailableException deliveryUnavailable() {
        return new ExternalServiceUnavailableException(
                ErrorCode.EMAIL_DELIVERY_FAILED,
                "No se pudo enviar el código de verificación. Inténtalo nuevamente más tarde."
        );
    }

    private String normalizeRecipient(String recipient) {
        return recipient == null ? "" : recipient.strip().toLowerCase(Locale.ROOT);
    }
}
