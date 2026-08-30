package pe.rutafija.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "driver_vehicle_link")
public class DriverVehicleLink {

    @EmbeddedId
    private DriverVehicleLinkId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("driverId")
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("vehicleId")
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @Column(name = "is_primary", nullable = false)
    private boolean primaryVehicle;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private Instant linkedAt;

    protected DriverVehicleLink() {
    }

    private DriverVehicleLink(Driver driver, Vehicle vehicle, boolean primary, Instant linkedAt) {
        this.id = new DriverVehicleLinkId(driver.getId(), vehicle.getId());
        this.driver = Objects.requireNonNull(driver);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.primaryVehicle = primary;
        this.active = true;
        this.linkedAt = Objects.requireNonNull(linkedAt);
    }

    public static DriverVehicleLink active(Driver driver, Vehicle vehicle, boolean primary, Instant linkedAt) {
        return new DriverVehicleLink(driver, vehicle, primary, linkedAt);
    }

    public void activate(boolean primary) {
        this.active = true;
        this.primaryVehicle = primary;
    }

    public void deactivate() {
        this.active = false;
        this.primaryVehicle = false;
    }

    public void markPrimary() {
        this.active = true;
        this.primaryVehicle = true;
    }

    public void clearPrimary() {
        this.primaryVehicle = false;
    }

    public Driver getDriver() {
        return driver;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public boolean isPrimary() {
        return primaryVehicle;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }
}
