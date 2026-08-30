package pe.rutafija.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class DriverVehicleLinkId implements Serializable {

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    protected DriverVehicleLinkId() {
    }

    public DriverVehicleLinkId(UUID driverId, UUID vehicleId) {
        this.driverId = Objects.requireNonNull(driverId);
        this.vehicleId = Objects.requireNonNull(vehicleId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof DriverVehicleLinkId that)) {
            return false;
        }
        return driverId.equals(that.driverId) && vehicleId.equals(that.vehicleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(driverId, vehicleId);
    }
}
