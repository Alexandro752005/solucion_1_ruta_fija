package pe.rutafija.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SuperAdminRecoveryChallengeResponse(
        UUID challengeId,
        UUID installationId,
        UUID accountReference,
        String nonce,
        Instant expiresAt,
        String challengeToSignUtf8
) {
}
