package pe.rutafija.invitation.api.dto;

import java.time.Instant;
import java.util.UUID;

public record InvitationSummaryResponse(
        UUID id,
        String recipientEmail,
        Instant issuedAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant usedAt
) {
}
