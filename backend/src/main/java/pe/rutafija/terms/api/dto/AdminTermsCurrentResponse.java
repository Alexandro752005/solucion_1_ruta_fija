package pe.rutafija.terms.api.dto;

import java.time.Instant;

public record AdminTermsCurrentResponse(
        String documentKey,
        String version,
        String content,
        String contentSha256,
        boolean synthetic,
        boolean accepted,
        Instant acceptedAt
) {
}
