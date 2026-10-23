package pe.rutafija.identity.application;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;

@Component
public class OtpCodeGenerator {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        return String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));
    }
}
