package pe.rutafija.identity.application;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class OpaqueTokenService {

    private static final int TOKEN_BYTES = 32;
    private static final String MOBILE_TOKEN_PREFIX = "m1.";
    private static final Pattern WEB_TOKEN = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern MOBILE_TOKEN = Pattern.compile("m1\\.[A-Za-z0-9_-]{43}");
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * The prefix is outside the random Base64URL alphabet because it includes a dot.
     * It lets the server reject a web refresh token at a mobile endpoint (and vice versa)
     * before the opaque token lookup, without storing a second copy of a session secret.
     */
    public String generateMobile() {
        return MOBILE_TOKEN_PREFIX + generate();
    }

    public boolean isWebRefreshToken(String rawToken) {
        return rawToken != null && WEB_TOKEN.matcher(rawToken).matches();
    }

    public boolean isMobileRefreshToken(String rawToken) {
        return rawToken != null && MOBILE_TOKEN.matcher(rawToken).matches();
    }

    public String hash(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("A refresh token is required");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
