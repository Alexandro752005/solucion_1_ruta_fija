package pe.rutafija.invitation.application;

import java.time.Instant;
import java.util.UUID;

public record InvitationValidationResult(
        boolean valid,
        UUID invitationId,
        Instant retryAt
) {
    public static InvitationValidationResult valid(UUID invitationId) {
        return new InvitationValidationResult(true, invitationId, null);
    }

    public static InvitationValidationResult invalid() {
        return new InvitationValidationResult(false, null, null);
    }

    public static InvitationValidationResult limited(Instant retryAt) {
        return new InvitationValidationResult(false, null, retryAt);
    }

    public boolean isLimited() {
        return retryAt != null;
    }
}
