package pe.rutafija.identity.application;

import jakarta.annotation.PostConstruct;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.shared.config.SecurityProperties;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final SecurityProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, SecurityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void validateConfiguration() {
        if (properties.jwt().accessTtl().isZero() || properties.jwt().accessTtl().isNegative()) {
            throw new IllegalStateException("JWT access TTL must be positive");
        }
        if (properties.jwt().refreshTtl().isZero() || properties.jwt().refreshTtl().isNegative()) {
            throw new IllegalStateException("JWT refresh TTL must be positive");
        }
    }

    public String issueAccessToken(AppUser user) {
        Instant issuedAt = Instant.now(clock);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .id(UUID.randomUUID().toString())
                .issuer(properties.jwt().issuer())
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(properties.jwt().accessTtl()))
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .claim("fullName", user.getFullName())
                .claim("role", user.getRole().name());
        if (user.getOrganizationId() != null) {
            claims.claim("organizationId", user.getOrganizationId().toString());
        }

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    public long accessTokenExpiresInSeconds() {
        return properties.jwt().accessTtl().toSeconds();
    }
}
