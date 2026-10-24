package pe.rutafija.identity.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ConductorPasswordResetCompletedResponse(
        UUID recoveryId,
        String status,
        Instant completedAt
) {
}
