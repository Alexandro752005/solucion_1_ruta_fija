package pe.rutafija.identity.api.dto;

import java.time.Instant;

public record SuperAdminRecoveryCompletionResponse(
        String recoveredEmail,
        Instant recoveredAt,
        String newEmergencyCodeShownOnce
) {
}
