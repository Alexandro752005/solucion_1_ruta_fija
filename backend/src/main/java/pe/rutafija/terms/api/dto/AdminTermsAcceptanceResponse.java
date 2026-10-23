package pe.rutafija.terms.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminTermsAcceptanceResponse(
        UUID acceptanceId,
        UUID adminUserId,
        UUID organizationId,
        String documentVersion,
        String contentSha256,
        boolean synthetic,
        Instant acceptedAt
) {
}
