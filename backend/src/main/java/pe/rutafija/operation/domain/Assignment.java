package pe.rutafija.operation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "assignment")
public class Assignment {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private AppUser createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AssignmentStatus status;

    @Column(name = "origin_text", nullable = false, length = 250)
    private String originText;

    @Column(name = "destination_text", nullable = false, length = 250)
    private String destinationText;

    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "scheduled_end_at", nullable = false)
    private Instant scheduledEndAt;

    @Column(name = "reserved_at")
    private Instant reservedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason", length = 300)
    private String cancellationReason;

    @Column(length = 500)
    private String notes;

    @Column(name = "idempotency_key", length = 100, updatable = false)
    private String idempotencyKey;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Assignment() {
    }

    private Assignment(
            Organization organization,
            Driver driver,
            Vehicle vehicle,
            AppUser createdBy,
            String originText,
            String destinationText,
            Instant scheduledAt,
            Instant scheduledEndAt,
            String notes,
            String idempotencyKey
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.driver = Objects.requireNonNull(driver);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.status = AssignmentStatus.SCHEDULED;
        this.originText = requiredText(originText, "El origen es obligatorio");
        this.destinationText = requiredText(destinationText, "El destino es obligatorio");
        validateSchedule(scheduledAt, scheduledEndAt);
        this.scheduledAt = scheduledAt;
        this.scheduledEndAt = scheduledEndAt;
        this.notes = optionalText(notes);
        this.idempotencyKey = optionalText(idempotencyKey);
    }

    public static Assignment schedule(
            Organization organization,
            Driver driver,
            Vehicle vehicle,
            AppUser createdBy,
            String originText,
            String destinationText,
            Instant scheduledAt,
            Instant scheduledEndAt,
            String notes,
            String idempotencyKey
    ) {
        return new Assignment(
                organization,
                driver,
                vehicle,
                createdBy,
                originText,
                destinationText,
                scheduledAt,
                scheduledEndAt,
                notes,
                idempotencyKey
        );
    }

    public void updateSchedule(
            Driver driver,
            Vehicle vehicle,
            String originText,
            String destinationText,
            Instant scheduledAt,
            Instant scheduledEndAt,
            String notes
    ) {
        requireScheduledAndNotReserved();
        this.driver = Objects.requireNonNull(driver);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.originText = requiredText(originText, "El origen es obligatorio");
        this.destinationText = requiredText(destinationText, "El destino es obligatorio");
        validateSchedule(scheduledAt, scheduledEndAt);
        this.scheduledAt = scheduledAt;
        this.scheduledEndAt = scheduledEndAt;
        this.notes = optionalText(notes);
    }

    public void reserve(Instant occurredAt) {
        requireScheduledAndNotReserved();
        reservedAt = Objects.requireNonNull(occurredAt);
    }

    public void start(Instant occurredAt) {
        if (status != AssignmentStatus.SCHEDULED || reservedAt == null) {
            throw new IllegalStateException("La asignación debe estar reservada antes de iniciar el servicio");
        }
        status = AssignmentStatus.EN_SERVICIO;
        startedAt = Objects.requireNonNull(occurredAt);
    }

    public void complete(Instant occurredAt) {
        if (status != AssignmentStatus.EN_SERVICIO) {
            throw new IllegalStateException("Solo una asignación EN_SERVICIO puede completarse");
        }
        status = AssignmentStatus.COMPLETED;
        completedAt = Objects.requireNonNull(occurredAt);
    }

    public void cancel(String reason, Instant occurredAt) {
        if (status != AssignmentStatus.SCHEDULED && status != AssignmentStatus.EN_SERVICIO) {
            throw new IllegalStateException("Solo una asignación programada o en servicio puede cancelarse");
        }
        status = AssignmentStatus.CANCELLED;
        cancellationReason = requiredText(reason, "El motivo de cancelación es obligatorio");
        cancelledAt = Objects.requireNonNull(occurredAt);
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

    public Driver getDriver() {
        return driver;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public String getOriginText() {
        return originText;
    }

    public String getDestinationText() {
        return destinationText;
    }

    public Instant getScheduledAt() {
        return scheduledAt;
    }

    public Instant getScheduledEndAt() {
        return scheduledEndAt;
    }

    public Instant getReservedAt() {
        return reservedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getNotes() {
        return notes;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void requireScheduledAndNotReserved() {
        if (status != AssignmentStatus.SCHEDULED || reservedAt != null) {
            throw new IllegalStateException("Solo una asignación programada no reservada puede modificarse");
        }
    }

    private static void validateSchedule(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw new IllegalArgumentException("El fin programado debe ser posterior al inicio programado");
        }
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
