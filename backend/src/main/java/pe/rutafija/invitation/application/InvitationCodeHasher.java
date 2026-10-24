package pe.rutafija.invitation.application;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC values use purpose-specific contexts to prevent OTP/hash cross-use. */
@Component
public class InvitationCodeHasher {

    private static final byte[] CODE_CONTEXT = "ruta-fija/invitation-code/v1:".getBytes(StandardCharsets.UTF_8);
    private static final byte[] ORIGIN_CONTEXT = "ruta-fija/invitation-origin/v1:".getBytes(StandardCharsets.UTF_8);
    private final SecretKey key;

    public InvitationCodeHasher(SecretKey key) {
        this.key = key;
    }

    public String hashCode(String code, String normalizedRecipientEmail) {
        return hash(CODE_CONTEXT, code + ":" + normalizedRecipientEmail);
    }

    public String hashOrigin(String remoteAddress) {
        return hash(ORIGIN_CONTEXT, remoteAddress == null || remoteAddress.isBlank()
                ? "unknown"
                : remoteAddress.strip());
    }

    public boolean matchesCode(String code, String normalizedRecipientEmail, String expectedHash) {
        if (code == null || !code.matches("[0-9]{6}") || expectedHash == null) {
            return false;
        }
        try {
            return MessageDigest.isEqual(
                    HexFormat.of().parseHex(expectedHash),
                    HexFormat.of().parseHex(hashCode(code, normalizedRecipientEmail))
            );
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String hash(byte[] context, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            mac.update(context);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Invitation hashing is unavailable");
        }
    }
}
