package pe.rutafija.fleet.api.dto;

import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

import java.time.Instant;
import java.util.UUID;

public record DriverResponse(
        UUID id,
        UUID userId,
        UUID groupId,
        String groupName,
        String fullName,
        String phone,
        String documentType,
        String documentNumber,
        String licenseNumber,
        DriverAvailabilityStatus availabilityStatus,
        boolean locationConsent,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static DriverResponse from(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getUser() == null ? null : driver.getUser().getId(),
                driver.getGroup().getId(),
                driver.getGroup().getName(),
                driver.getFullName(),
                driver.getPhone(),
                driver.getDocumentType(),
                driver.getDocumentNumber(),
                driver.getLicenseNumber(),
                driver.getAvailabilityStatus(),
                driver.isLocationConsent(),
                driver.isActive(),
                driver.getCreatedAt(),
                driver.getUpdatedAt()
        );
    }
}
