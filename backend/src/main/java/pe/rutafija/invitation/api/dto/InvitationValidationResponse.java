package pe.rutafija.invitation.api.dto;

import java.util.UUID;

/** Exposes no organization identifier, name, or other tenant data. */
public record InvitationValidationResponse(
        boolean valid,
        UUID invitationId
) {
    public static InvitationValidationResponse invalid() {
        return new InvitationValidationResponse(false, null);
    }
}
