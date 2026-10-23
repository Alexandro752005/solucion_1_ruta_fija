package pe.rutafija.invitation.api.dto;

import java.time.Instant;
import java.util.UUID;

/** The raw code is returned only once, when the ADMIN creates the invitation. */
public record CreatedInvitationResponse(
        UUID id,
        String recipientEmail,
        String code,
        Instant issuedAt,
        Instant expiresAt
) {
}
