package pe.rutafija.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganizationCreateRequest(
        @NotBlank @Size(max = 150) String legalName,
        @Size(max = 120) String tradeName,
        @NotBlank @Size(max = 50) String timezone
) {
}
