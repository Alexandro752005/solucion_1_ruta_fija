package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuperAdminRecoveryStartRequest(
        @NotBlank @Email @Size(max = 180) String accountEmail,
        @NotBlank @Email @Size(max = 180) String newEmail
) {
}
