package pe.rutafija.invitation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Malformed codes under the request-size limit are handled as failed attempts. */
public record InvitationValidationRequest(
        @NotBlank @Size(max = 64) String code
) {
}
