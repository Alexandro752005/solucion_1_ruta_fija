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
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "incident")
public class Incident {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private Assignment assignment;

    @Column(name="execution_segment_id",updatable=false)
    private UUID executionSegmentId;
    public UUID getExecutionSegmentId(){return executionSegmentId;}
    public void linkExecutionSegment(UUID id){
        if(assignment==null||executionSegmentId!=null)throw new IllegalStateException("La incidencia requiere una participación propia inequívoca.");
        executionSegmentId=Objects.requireNonNull(id);
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by", updatable = false)
    private AppUser reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity=IncidentSeverity.UNASSESSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsible_admin_id")
    private AppUser responsibleAdmin;

    @Column(name="previous_incident_id",updatable=false)
    private UUID previousIncidentId;

    @Column(name="relation_reason",updatable=false,length=20)
    private String relationReason;

    @Column(name="workflow_revision",nullable=false)
    private long workflowRevision;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentSource source;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "reported_at", nullable = false, updatable = false)
    private Instant reportedAt;

    @Column(name = "follow_up_note", length = 1000)
    private String followUpNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "followed_up_by")
    private AppUser followedUpBy;

    @Column(name = "followed_up_at")
    private Instant followedUpAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_closed_by")
    private AppUser driverClosedBy;

    @Column(name = "driver_closed_at")
    private Instant driverClosedAt;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Incident() {
    }

    private Incident(
            Organization organization,
            Driver driver,
            Assignment assignment,
            AppUser reportedBy,
            IncidentCategory category,
            String description,
            Instant reportedAt,
            IncidentSource source
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.driver = Objects.requireNonNull(driver);
        this.assignment = assignment;
        this.reportedBy = Objects.requireNonNull(reportedBy);
        this.category = Objects.requireNonNull(category);
        this.status = IncidentStatus.OPEN;
        this.source = Objects.requireNonNull(source);
        this.description = requiredText(description, "La descripción de la incidencia es obligatoria");
        this.reportedAt = Objects.requireNonNull(reportedAt);
    }

    public static Incident report(
            Organization organization,
            Driver driver,
            Assignment assignment,
            AppUser reportedBy,
            IncidentCategory category,
            String description,
            Instant reportedAt
    ) {
        return new Incident(
                organization, driver, assignment, reportedBy, category, description, reportedAt, IncidentSource.CRM_WEB
        );
    }

    public static Incident reportFromMobile(
            Organization organization,
            Driver driver,
            Assignment assignment,
            AppUser reportedBy,
            IncidentCategory category,
            String description,
            Instant reportedAt
    ) {
        return new Incident(
                organization, driver, assignment, reportedBy, category, description, reportedAt, IncidentSource.MOBILE_APP
        );
    }

    public void followUp(String note, boolean resolve, AppUser actor, Instant occurredAt) {
        if (status == IncidentStatus.RESOLVED || status == IncidentStatus.CLOSED_BY_DRIVER) {
            throw new IllegalStateException("Una incidencia cerrada no admite reapertura ni nuevos seguimientos");
        }
        workflowRevision++;
        followUpNote = requiredText(note, "La nota de seguimiento es obligatoria");
        followedUpBy = Objects.requireNonNull(actor);
        followedUpAt = Objects.requireNonNull(occurredAt);
        if (resolve) {
            status = IncidentStatus.RESOLVED;
            resolvedAt = occurredAt;
        } else {
            status = IncidentStatus.FOLLOW_UP;
        }
    }

    /**
     * The MOBILE reporter may close their own report exactly once. The event is
     * retained as a terminal state; an ADMIN cannot reopen it through follow-up.
     */
    public boolean closeByMobileReporter(AppUser actor, Instant occurredAt) {
        AppUser requiredActor = Objects.requireNonNull(actor);
        Instant requiredOccurredAt = Objects.requireNonNull(occurredAt);
        if (source != IncidentSource.MOBILE_APP || !reportedBy.getId().equals(requiredActor.getId())) {
            throw new IllegalStateException("Solo el conductor que reportó desde móvil puede cerrar esta incidencia");
        }
        if (status == IncidentStatus.CLOSED_BY_DRIVER && driverClosedBy != null
                && driverClosedBy.getId().equals(requiredActor.getId())) {
            return false;
        }
        if (driverClosedAt != null || driverClosedBy != null) {
            throw new IllegalStateException("El conductor ya cerró esta incidencia y no puede modificarla nuevamente");
        }
        if (status != IncidentStatus.OPEN && status != IncidentStatus.FOLLOW_UP) {
            throw new IllegalStateException("Esta incidencia ya no admite cierre por el conductor");
        }
        status = IncidentStatus.CLOSED_BY_DRIVER;
        driverClosedBy = requiredActor;
        driverClosedAt = requiredOccurredAt;
        return true;
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

    public Assignment getAssignment() {
        return assignment;
    }

    public AppUser getReportedBy() {
        return reportedBy;
    }

    public IncidentCategory getCategory() {
        return category;
    }

    public IncidentSeverity getSeverity(){return severity;}
    public AppUser getResponsibleAdmin(){return responsibleAdmin;}
    public UUID getPreviousIncidentId(){return previousIncidentId;}
    public String getRelationReason(){return relationReason;}
    public void assess(IncidentSeverity value){
        requireOpenForAttention();
        if(value==null||value==IncidentSeverity.UNASSESSED)throw new IllegalArgumentException("Selecciona una gravedad explícita");
        severity=value;workflowRevision++;
    }
    public void assignResponsible(AppUser value){
        requireOpenForAttention();responsibleAdmin=Objects.requireNonNull(value);workflowRevision++;
    }
    public void recordAdditionalInformation(){requireOpenForAttention();workflowRevision++;}
    /** Called during new report preparation only; the database also protects the link. */
    public void relatePrevious(UUID previous,String reason){
        if(createdAt!=null||previousIncidentId!=null)throw new IllegalStateException("El vínculo original no se modifica");
        previousIncidentId=Objects.requireNonNull(previous);relationReason=Objects.requireNonNull(reason);
    }
    private void requireOpenForAttention(){
        if(status==IncidentStatus.RESOLVED||status==IncidentStatus.CLOSED_BY_DRIVER)throw new IllegalStateException("La incidencia está cerrada");
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public IncidentSource getSource() {
        return source;
    }

    public String getDescription() {
        return description;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public String getFollowUpNote() {
        return followUpNote;
    }

    public AppUser getFollowedUpBy() {
        return followedUpBy;
    }

    public Instant getFollowedUpAt() {
        return followedUpAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public AppUser getDriverClosedBy() {
        return driverClosedBy;
    }

    public Instant getDriverClosedAt() {
        return driverClosedAt;
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

    private static String requiredText(String value, String message) {
        if (value == null || value.strip().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }
}
