package pe.rutafija.identity.application;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class OtpCodeHasher {

    private static final byte[] CONTEXT = "ruta-fija/email-otp/v1:".getBytes(StandardCharsets.UTF_8);
    private final SecretKey secretKey;

    public OtpCodeHasher(SecretKey secretKey) {
        this.secretKey = secretKey;
    }

    public String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(secretKey);
            mac.update(CONTEXT);
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("OTP hashing is unavailable");
        }
    }

    public boolean matches(String code, String expectedHash) {
        if (code == null || expectedHash == null || !code.matches("[0-9]{6}")) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(expectedHash),
                    HexFormat.of().parseHex(hash(code))
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
