package pe.rutafija.onboarding.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ConductorOnboardingRequestSummaryResponse(
        UUID requestId,
        String status,
        short reviewRounds,
        Instant submittedAt
) {
}
