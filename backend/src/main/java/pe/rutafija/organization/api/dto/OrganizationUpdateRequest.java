package pe.rutafija.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.organization.domain.OrganizationStatus;

public record OrganizationUpdateRequest(
        @NotBlank @Size(max = 150) String legalName,
        @Size(max = 120) String tradeName,
        @NotBlank @Size(max = 50) String timezone,
        @NotNull OrganizationStatus status
) {
}
