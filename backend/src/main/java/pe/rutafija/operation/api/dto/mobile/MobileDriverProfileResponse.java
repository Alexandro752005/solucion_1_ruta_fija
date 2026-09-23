package pe.rutafija.operation.api.dto.mobile;

import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.DriverVehicleLink;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** The mobile profile contains no administrator/tenant data beyond the driver's own scope. */
public record MobileDriverProfileResponse(
        UUID driverId,
        String fullName,
        UUID groupId,
        String groupName,
        DriverAvailabilityStatus availabilityStatus,
        boolean locationConsent,
        MobileVehicleResponse primaryVehicle
) {
    public static MobileDriverProfileResponse from(Driver driver, List<DriverVehicleLink> vehicleLinks) {
        MobileVehicleResponse primary = vehicleLinks.stream()
                .filter(DriverVehicleLink::isPrimary)
                .map(link -> new MobileVehicleResponse(
                        link.getVehicle().getId(), link.getVehicle().getPlate(), link.getVehicle().getStatus()
                ))
                .min(Comparator.comparing(MobileVehicleResponse::plate))
                .orElse(null);
        return new MobileDriverProfileResponse(
                driver.getId(),
                driver.getFullName(),
                driver.getGroup().getId(),
                driver.getGroup().getName(),
                driver.getAvailabilityStatus(),
                driver.isLocationConsent(),
                primary
        );
    }
}
