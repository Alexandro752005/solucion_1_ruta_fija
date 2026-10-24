package pe.rutafija.onboarding.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Ordinary administrative view: references/state only, never documentary fields or identity hashes. */
public record DriverIncorporationStatusResponse(
        UUID driverId, UUID accountId, UUID requestId, String incorporationStatus,
        boolean personalApproved, boolean accountProvisioned, boolean accountEnabled,
        boolean identityReviewed, boolean groupActive, boolean driverEnabled,
        String personalDocumentsStatus, long activeVehicleLinks, boolean unitCardinalitySatisfied,
        boolean operationalEnabled, List<String> blockers, Instant observedAt
) {
    public DriverIncorporationStatusResponse {
        blockers = List.copyOf(blockers);
    }
}
