package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record DriverUpdateRequest(
        UUID userId,
        @NotNull UUID groupId,
        @NotBlank @Size(max = 160) String fullName,
        @Size(max = 30) String phone,
        @NotBlank @Size(max = 20) String documentType,
        @NotBlank @Size(max = 30) String documentNumber,
        @Size(max = 40) String licenseNumber
) {
}
