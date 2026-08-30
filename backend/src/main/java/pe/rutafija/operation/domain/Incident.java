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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by", nullable = false, updatable = false)
    private AppUser reportedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

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
            Instant reportedAt
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.driver = Objects.requireNonNull(driver);
        this.assignment = assignment;
        this.reportedBy = Objects.requireNonNull(reportedBy);
        this.category = Objects.requireNonNull(category);
        this.status = IncidentStatus.OPEN;
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
        return new Incident(organization, driver, assignment, reportedBy, category, description, reportedAt);
    }

    public void followUp(String note, boolean resolve, AppUser actor, Instant occurredAt) {
        if (status == IncidentStatus.RESOLVED) {
            throw new IllegalStateException("Una incidencia resuelta no admite nuevos seguimientos");
        }
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

    public IncidentStatus getStatus() {
        return status;
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
