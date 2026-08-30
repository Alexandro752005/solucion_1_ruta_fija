package pe.rutafija.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vehicle")
public class Vehicle {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(nullable = false, length = 15)
    private String plate;

    @Column(length = 60)
    private String brand;

    @Column(length = 60)
    private String model;

    @Column(name = "year")
    private Short year;

    @Column(length = 40)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleStatus status;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Vehicle() {
    }

    private Vehicle(
            Organization organization,
            String plate,
            String brand,
            String model,
            Short year,
            String color
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.plate = requiredText(plate, "La placa es obligatoria").toUpperCase(Locale.ROOT);
        this.brand = optionalText(brand);
        this.model = optionalText(model);
        this.year = validateYear(year);
        this.color = optionalText(color);
        this.status = VehicleStatus.DISPONIBLE;
        this.active = true;
    }

    public static Vehicle register(
            Organization organization,
            String plate,
            String brand,
            String model,
            Short year,
            String color
    ) {
        return new Vehicle(organization, plate, brand, model, year, color);
    }

    public void update(String plate, String brand, String model, Short year, String color) {
        this.plate = requiredText(plate, "La placa es obligatoria").toUpperCase(Locale.ROOT);
        this.brand = optionalText(brand);
        this.model = optionalText(model);
        this.year = validateYear(year);
        this.color = optionalText(color);
    }

    public void changeAdministrativeStatus(VehicleStatus status) {
        VehicleStatus requested = Objects.requireNonNull(status);
        if (!requested.canBeSetAdministratively()) {
            throw new IllegalArgumentException("EN_SERVICIO solo puede ser gestionado por una asignación");
        }
        if (this.status == VehicleStatus.EN_SERVICIO) {
            throw new IllegalArgumentException("Un vehículo EN_SERVICIO solo puede liberarse al cerrar o cancelar su asignación");
        }
        this.status = requested;
        this.active = requested != VehicleStatus.INACTIVO;
    }

    public void beginService() {
        if (!active || status != VehicleStatus.DISPONIBLE) {
            throw new IllegalStateException("El vehículo debe estar DISPONIBLE para iniciar el servicio");
        }
        status = VehicleStatus.EN_SERVICIO;
    }

    public void finishService() {
        if (status != VehicleStatus.EN_SERVICIO) {
            throw new IllegalStateException("El vehículo no se encuentra EN_SERVICIO");
        }
        status = VehicleStatus.DISPONIBLE;
        active = true;
    }

    public void activate() {
        this.active = true;
        if (this.status == VehicleStatus.INACTIVO) {
            this.status = VehicleStatus.DISPONIBLE;
        }
    }

    public void deactivate() {
        this.active = false;
        this.status = VehicleStatus.INACTIVO;
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public UUID getOrganizationId() {
        return organization.getId();
    }

    public String getPlate() {
        return plate;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public Short getYear() {
        return year;
    }

    public String getColor() {
        return color;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private static Short validateYear(Short value) {
        if (value == null) {
            return null;
        }
        if (value < 1900 || value > 2100) {
            throw new IllegalArgumentException("El año del vehículo no es válido");
        }
        return value;
    }

    private static String requiredText(String value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
