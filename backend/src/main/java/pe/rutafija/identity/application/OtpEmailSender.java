package pe.rutafija.identity.application;

import java.time.Instant;

/** Provider boundary for one-time verification messages. */
public interface OtpEmailSender {

    void sendVerificationCode(String recipient, String code, Instant expiresAt);
}
