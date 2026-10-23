package pe.rutafija.identity.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record VerifyConductorRecoveryOtpRequest(
        @NotNull UUID recoveryId,
        @NotBlank @Size(max = 128) String verificationToken,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String code
) {
}
