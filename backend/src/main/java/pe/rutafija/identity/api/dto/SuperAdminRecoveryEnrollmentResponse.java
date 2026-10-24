package pe.rutafija.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SuperAdminRecoveryEnrollmentResponse(
        UUID installationId,
        String publicKeyFingerprintSha256,
        String emergencyCodeShownOnce,
        Instant enrolledAt
) {
}
