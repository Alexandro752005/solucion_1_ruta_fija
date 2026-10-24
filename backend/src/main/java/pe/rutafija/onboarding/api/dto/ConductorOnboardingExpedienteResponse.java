package pe.rutafija.onboarding.api.dto;

import java.util.UUID;

/** PII is exposed only by the ADMIN-only expediente route for the request tenant. */
public record ConductorOnboardingExpedienteResponse(
        UUID requestId,
        String status,
        String fullName,
        String email,
        String phone
) {
}
