package pe.rutafija.onboarding.api.dto;

import java.util.UUID;

public record ConductorOnboardingAcceptedResponse(
        UUID requestId,
        String status
) {
}
