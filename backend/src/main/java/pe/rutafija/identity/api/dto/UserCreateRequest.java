package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.identity.domain.UserRole;

public record UserCreateRequest(
        @NotBlank @Email @Size(max = 180) String email,
        @NotBlank @Size(min = 12, max = 128) String password,
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 30) String phone,
        @NotNull UserRole role
) {
}
