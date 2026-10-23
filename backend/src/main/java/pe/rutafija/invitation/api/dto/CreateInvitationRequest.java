package pe.rutafija.invitation.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateInvitationRequest(
        @NotBlank @Email @Size(max = 180) String recipientEmail
) {
}
