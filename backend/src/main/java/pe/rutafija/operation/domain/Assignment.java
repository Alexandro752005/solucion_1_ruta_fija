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

    @Column(name="service_type_version_id")
    private UUID serviceTypeVersionId;

    @jakarta.persistence.Column(name="service_id", updatable=false)
    private UUID serviceId;
    @Column(name="handover_id",updatable=false)
    private UUID handoverId;
    @Column(name="transferred_at")
    private Instant transferredAt;
    public UUID getHandoverId(){return handoverId;}
    public Instant getTransferredAt(){return transferredAt;}
    public void captureHandover(UUID id){if(handoverId!=null)throw new IllegalStateException("El relevo es inmutable.");handoverId=Objects.requireNonNull(id);}
    public void transfer(Instant now){if(status!=AssignmentStatus.EN_SERVICIO||startedAt==null||now.isBefore(startedAt))throw new IllegalStateException("El relevo requiere un tramo iniciado.");status=AssignmentStatus.TRANSFERRED;transferredAt=now;}

    public UUID getServiceId(){return serviceId;}
    public void captureService(UUID id){
        if(serviceId!=null)throw new IllegalStateException("El servicio de una oferta es inmutable.");
        serviceId=Objects.requireNonNull(id);
    }
    @Column(name="margin_before_minutes")
    private Integer marginBeforeMinutes;
    @Column(name="margin_after_minutes")
    private Integer marginAfterMinutes;

    public void captureServiceType(UUID version,int before,int after) {
        if(serviceTypeVersionId!=null||before<0||after<0)throw new IllegalStateException("La captura de márgenes es inmutable y requiere configuración expresa.");
        serviceTypeVersionId=Objects.requireNonNull(version);marginBeforeMinutes=before;marginAfterMinutes=after;
    }
    public UUID getServiceTypeVersionId(){return serviceTypeVersionId;}
    public Integer getMarginBeforeMinutes(){return marginBeforeMinutes;}
    public Integer getMarginAfterMinutes(){return marginAfterMinutes;}

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

    @Column(name = "vehicle_plate_snapshot",length=15)
    private String vehiclePlateSnapshot;

    @Column(name="route_version_id", updatable=false)
    private UUID routeVersionId;

    @Column(name="creation_payload_sha256",length=64,updatable=false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    private String creationPayloadSha256;

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

    @Column(name="interrupted_at")
    private Instant interruptedAt;
    @Column(name="interruption_reason",length=32)
    private String interruptionReason;

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

    public void captureRoute(UUID versionId,String payloadSha256) {
        if(routeVersionId!=null)throw new IllegalStateException("La versión capturada no se sustituye");
        routeVersionId=Objects.requireNonNull(versionId);creationPayloadSha256=Objects.requireNonNull(payloadSha256);
    }
    public UUID getRouteVersionId(){return routeVersionId;}
    public String getCreationPayloadSha256(){return creationPayloadSha256;}

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
        this.vehiclePlateSnapshot = vehicle.getPlate();
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
            if (responseDeadlineAt == null || responseDeadlineAt.isAfter(scheduledAt)) {
                throw new IllegalArgumentException("El plazo de respuesta no puede superar el inicio programado");
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
        this.vehiclePlateSnapshot = vehicle.getPlate();
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

    public void interrupt(Instant serverNow,String reason) {
        if(status!=AssignmentStatus.EN_SERVICIO||startedAt==null||serverNow.isBefore(startedAt))
            throw new IllegalStateException("La interrupción requiere una ejecución iniciada.");
        status=AssignmentStatus.INTERRUPTED;interruptedAt=Objects.requireNonNull(serverNow);interruptionReason=Objects.requireNonNull(reason);
    }
    public Instant getInterruptedAt(){return interruptedAt;}
    public String getInterruptionReason(){return interruptionReason;}

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

    public String getVehiclePlateSnapshot(){return vehiclePlateSnapshot;}

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
