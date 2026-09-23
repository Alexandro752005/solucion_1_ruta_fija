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
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "driver")
public class Driver {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private TransportGroup group;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 30)
    private String phone;

    @Column(name = "document_type", nullable = false, length = 20)
    private String documentType;

    @Column(name = "document_number", nullable = false, length = 30)
    private String documentNumber;

    @Column(name = "license_number", length = 40)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability_status", nullable = false, length = 25)
    private DriverAvailabilityStatus availabilityStatus;

    @Column(name = "location_consent", nullable = false)
    private boolean locationConsent;

    @Column(nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Driver() {
    }

    private Driver(
            Organization organization,
            AppUser user,
            TransportGroup group,
            String fullName,
            String phone,
            String documentType,
            String documentNumber,
            String licenseNumber
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.user = user;
        this.group = Objects.requireNonNull(group);
        this.fullName = requiredText(fullName, "El nombre del conductor es obligatorio");
        this.phone = optionalText(phone);
        this.documentType = requiredText(documentType, "El tipo de documento es obligatorio").toUpperCase(Locale.ROOT);
        this.documentNumber = requiredText(documentNumber, "El número de documento es obligatorio")
                .toUpperCase(Locale.ROOT);
        this.licenseNumber = optionalUpperText(licenseNumber);
        this.availabilityStatus = DriverAvailabilityStatus.NO_DISPONIBLE;
        this.locationConsent = false;
        this.active = true;
    }

    public static Driver register(
            Organization organization,
            AppUser user,
            TransportGroup group,
            String fullName,
            String phone,
            String documentType,
            String documentNumber,
            String licenseNumber
    ) {
        return new Driver(
                organization,
                user,
                group,
                fullName,
                phone,
                documentType,
                documentNumber,
                licenseNumber
        );
    }

    public void update(
            AppUser user,
            TransportGroup group,
            String fullName,
            String phone,
            String documentType,
            String documentNumber,
            String licenseNumber
    ) {
        this.user = user;
        this.group = Objects.requireNonNull(group);
        this.fullName = requiredText(fullName, "El nombre del conductor es obligatorio");
        this.phone = optionalText(phone);
        this.documentType = requiredText(documentType, "El tipo de documento es obligatorio").toUpperCase(Locale.ROOT);
        this.documentNumber = requiredText(documentNumber, "El número de documento es obligatorio")
                .toUpperCase(Locale.ROOT);
        this.licenseNumber = optionalUpperText(licenseNumber);
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
        this.locationConsent = false;
        this.availabilityStatus = DriverAvailabilityStatus.NO_DISPONIBLE;
    }

    /**
     * La reserva es una consecuencia de una asignación administrada en el CRM;
     * no representa la aceptación de un conductor desde una aplicación móvil.
     */
    public void reserveForAssignment() {
        requireActive();
        if (availabilityStatus != DriverAvailabilityStatus.DISPONIBLE) {
            throw new IllegalStateException("El conductor debe estar DISPONIBLE para reservarlo");
        }
        availabilityStatus = DriverAvailabilityStatus.RESERVADO;
    }

    public void beginService() {
        requireActive();
        if (availabilityStatus != DriverAvailabilityStatus.RESERVADO) {
            throw new IllegalStateException("El conductor debe estar RESERVADO antes de iniciar el servicio");
        }
        availabilityStatus = DriverAvailabilityStatus.EN_SERVICIO;
    }

    public void completeService() {
        if (availabilityStatus != DriverAvailabilityStatus.EN_SERVICIO) {
            throw new IllegalStateException("El conductor no se encuentra EN_SERVICIO");
        }
        availabilityStatus = DriverAvailabilityStatus.DISPONIBLE;
    }

    public void releaseReservation() {
        if (availabilityStatus == DriverAvailabilityStatus.RESERVADO) {
            availabilityStatus = DriverAvailabilityStatus.DISPONIBLE;
        }
    }

    /**
     * Cambios administrativos permitidos fuera de una asignación. Los estados
     * RESERVADO y EN_SERVICIO solo pueden ser controlados por Assignment.
     */
    public void changeAdministrativeAvailability(DriverAvailabilityStatus requested) {
        requireActive();
        Objects.requireNonNull(requested);
        boolean valid = switch (availabilityStatus) {
            case DISPONIBLE -> requested == DriverAvailabilityStatus.DESCANSO
                    || requested == DriverAvailabilityStatus.NO_DISPONIBLE;
            case DESCANSO -> requested == DriverAvailabilityStatus.DISPONIBLE
                    || requested == DriverAvailabilityStatus.NO_DISPONIBLE;
            case NO_DISPONIBLE -> requested == DriverAvailabilityStatus.DISPONIBLE
                    || requested == DriverAvailabilityStatus.DESCANSO;
            case RESERVADO, EN_SERVICIO -> false;
        };
        if (!valid) {
            throw new IllegalStateException("La transición de disponibilidad no está permitida");
        }
        availabilityStatus = requested;
    }

    /**
     * Consent is a functional choice of the authenticated driver. A caller that
     * revokes it must remove the current point separately in the same service
     * transaction; historical locations are never retained.
     */
    public void recordLocationConsent(boolean granted) {
        requireActive();
        locationConsent = granted;
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

    public AppUser getUser() {
        return user;
    }

    public TransportGroup getGroup() {
        return group;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public String getLicenseNumber() {
        return licenseNumber;
    }

    public DriverAvailabilityStatus getAvailabilityStatus() {
        return availabilityStatus;
    }

    public boolean isLocationConsent() {
        return locationConsent;
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

    private void requireActive() {
        if (!active) {
            throw new IllegalStateException("El conductor no está activo");
        }
    }

    private static String requiredText(String value, String message) {
        String normalized = optionalText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }

    private static String optionalUpperText(String value) {
        String normalized = optionalText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        return normalized.isEmpty() ? null : normalized;
    }
}
