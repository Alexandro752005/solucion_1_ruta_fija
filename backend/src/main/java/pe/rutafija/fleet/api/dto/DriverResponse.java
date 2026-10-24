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
        Instant updatedAt,
        DriverAvailabilityStatus declaredAvailabilityStatus,
        Instant declaredAvailabilityAt
) {
    public static DriverResponse from(Driver driver) {
        return new DriverResponse(
                driver.getId(),
                driver.getUser() == null ? null : driver.getUser().getId(),
                driver.getGroup().getId(),
                driver.getGroup().getName(),
                pe.rutafija.shared.security.PersonalDataView.driver(driver.getId()),
                null,
                null,
                null,
                null,
                driver.getAvailabilityStatus(),
                driver.isLocationConsent(),
                driver.isActive(),
                driver.getCreatedAt(),
                driver.getUpdatedAt(),
                driver.getDeclaredAvailabilityStatus(),
                driver.getDeclaredAvailabilityAt()
        );
    }
}
