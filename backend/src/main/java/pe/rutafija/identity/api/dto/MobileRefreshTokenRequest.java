package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MobileRefreshTokenRequest(
        @NotBlank(message = "El refresh token es obligatorio")
        @Size(max = 200, message = "El refresh token no tiene un formato válido")
        String refreshToken
) {
}
