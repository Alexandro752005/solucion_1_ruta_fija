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

    @Enumerated(EnumType.STRING)
    @Column(name = "response_mode", nullable = false, length = 30)
    private AssignmentResponseMode responseMode;

    @Column(name = "response_deadline_at")
    private Instant responseDeadlineAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Column(name = "expired_at")
    private Instant expiredAt;

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
            String idempotencyKey,
            AssignmentResponseMode responseMode,
            Instant responseDeadlineAt
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.driver = Objects.requireNonNull(driver);
        this.vehicle = Objects.requireNonNull(vehicle);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.originText = requiredText(originText, "El origen es obligatorio");
        this.destinationText = requiredText(destinationText, "El destino es obligatorio");
        validateSchedule(scheduledAt, scheduledEndAt);
        this.scheduledAt = scheduledAt;
        this.scheduledEndAt = scheduledEndAt;
        this.notes = optionalText(notes);
        this.idempotencyKey = optionalText(idempotencyKey);
        this.responseMode = Objects.requireNonNull(responseMode);

        if (responseMode == AssignmentResponseMode.MOBILE_CONFIRMATION) {
            if (responseDeadlineAt == null || !responseDeadlineAt.isBefore(scheduledAt)) {
                throw new IllegalArgumentException("El plazo de respuesta debe ser anterior al inicio programado");
            }
            this.status = AssignmentStatus.PENDING_RESPONSE;
            this.responseDeadlineAt = responseDeadlineAt;
        } else {
            this.status = AssignmentStatus.SCHEDULED;
            this.responseDeadlineAt = null;
        }
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
                organization, driver, vehicle, createdBy, originText, destinationText,
                scheduledAt, scheduledEndAt, notes, idempotencyKey,
                AssignmentResponseMode.ADMIN_DIRECT, null
        );
    }

    public static Assignment requestMobileConfirmation(
            Organization organization,
            Driver driver,
            Vehicle vehicle,
            AppUser createdBy,
            String originText,
            String destinationText,
            Instant scheduledAt,
            Instant scheduledEndAt,
            String notes,
            String idempotencyKey,
            Instant responseDeadlineAt
    ) {
        return new Assignment(
                organization, driver, vehicle, createdBy, originText, destinationText,
                scheduledAt, scheduledEndAt, notes, idempotencyKey,
                AssignmentResponseMode.MOBILE_CONFIRMATION, responseDeadlineAt
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
        if (responseMode != AssignmentResponseMode.ADMIN_DIRECT) {
            throw new IllegalStateException("Una solicitud de confirmacion movil no puede reprogramarse desde el CRM");
        }
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
        if (responseMode != AssignmentResponseMode.ADMIN_DIRECT) {
            throw new IllegalStateException("Solo una asignacion ADMIN_DIRECT puede reservarse desde el CRM");
        }
        requireScheduledAndNotReserved();
        reservedAt = Objects.requireNonNull(occurredAt);
    }

    /** Verifies a pending mobile request before the driver reservation is changed. */
    public void requirePendingMobileResponse(Instant occurredAt) {
        Instant now = Objects.requireNonNull(occurredAt);
        if (responseMode != AssignmentResponseMode.MOBILE_CONFIRMATION || status != AssignmentStatus.PENDING_RESPONSE) {
            throw new IllegalStateException("La asignacion no esta pendiente de respuesta movil");
        }
        if (!now.isBefore(responseDeadlineAt)) {
            throw new IllegalStateException("El plazo de respuesta de la asignacion ya vencio");
        }
    }

    public void acceptMobileResponse(Instant occurredAt) {
        requirePendingMobileResponse(occurredAt);
        status = AssignmentStatus.SCHEDULED;
        acceptedAt = occurredAt;
        reservedAt = occurredAt;
    }

    public void rejectMobileResponse(String reason, Instant occurredAt) {
        requirePendingMobileResponse(occurredAt);
        status = AssignmentStatus.REJECTED;
        rejectedAt = occurredAt;
        rejectionReason = optionalText(reason);
    }

    /** Returns true only when this invocation changes a due request to EXPIRED. */
    public boolean expireIfDue(Instant occurredAt) {
        Instant now = Objects.requireNonNull(occurredAt);
        if (responseMode != AssignmentResponseMode.MOBILE_CONFIRMATION
                || status != AssignmentStatus.PENDING_RESPONSE
                || now.isBefore(responseDeadlineAt)) {
            return false;
        }
        status = AssignmentStatus.EXPIRED;
        expiredAt = now;
        return true;
    }

    public boolean isPendingMobileResponse() {
        return responseMode == AssignmentResponseMode.MOBILE_CONFIRMATION
                && status == AssignmentStatus.PENDING_RESPONSE;
    }

    public void start(Instant occurredAt) {
        if (status != AssignmentStatus.SCHEDULED || reservedAt == null) {
            throw new IllegalStateException("La asignacion debe estar reservada antes de iniciar el servicio");
        }
        status = AssignmentStatus.EN_SERVICIO;
        startedAt = Objects.requireNonNull(occurredAt);
    }

    public void complete(Instant occurredAt) {
        if (status != AssignmentStatus.EN_SERVICIO) {
            throw new IllegalStateException("Solo una asignacion EN_SERVICIO puede completarse");
        }
        status = AssignmentStatus.COMPLETED;
        completedAt = Objects.requireNonNull(occurredAt);
    }

    public void cancel(String reason, Instant occurredAt) {
        if (status != AssignmentStatus.PENDING_RESPONSE
                && status != AssignmentStatus.SCHEDULED
                && status != AssignmentStatus.EN_SERVICIO) {
            throw new IllegalStateException("Solo una asignacion pendiente, programada o en servicio puede cancelarse");
        }
        status = AssignmentStatus.CANCELLED;
        cancellationReason = requiredText(reason, "El motivo de cancelacion es obligatorio");
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

    public AssignmentResponseMode getResponseMode() {
        return responseMode;
    }

    public Instant getResponseDeadlineAt() {
        return responseDeadlineAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }

    public Instant getRejectedAt() {
        return rejectedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Instant getExpiredAt() {
        return expiredAt;
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
            throw new IllegalStateException("Solo una asignacion programada no reservada puede modificarse");
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
