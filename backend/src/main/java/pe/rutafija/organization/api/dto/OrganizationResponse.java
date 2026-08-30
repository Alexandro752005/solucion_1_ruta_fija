package pe.rutafija.organization.api.dto;

import pe.rutafija.organization.domain.Organization;
import pe.rutafija.organization.domain.OrganizationStatus;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String legalName,
        String tradeName,
        OrganizationStatus status,
        String timezone,
        Instant createdAt,
        Instant updatedAt
) {
    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(
                organization.getId(),
                organization.getLegalName(),
                organization.getTradeName(),
                organization.getStatus(),
                organization.getTimezone(),
                organization.getCreatedAt(),
                organization.getUpdatedAt()
        );
    }
}
