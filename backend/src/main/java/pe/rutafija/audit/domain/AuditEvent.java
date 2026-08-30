package pe.rutafija.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id")
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(name = "entity_type", length = 60)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @Column(name = "correlation_id", length = 80)
    private String correlationId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    public AuditEvent(
            Organization organization,
            AppUser user,
            String action,
            String entityType,
            UUID entityId,
            String correlationId,
            Map<String, Object> metadata,
            Instant occurredAt
    ) {
        this.id = UUID.randomUUID();
        this.organization = organization;
        this.user = user;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.correlationId = correlationId;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.occurredAt = occurredAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organization == null ? null : organization.getId();
    }

    public UUID getUserId() {
        return user == null ? null : user.getId();
    }

    public String getUserFullName() {
        return user == null ? null : user.getFullName();
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public Map<String, Object> getMetadata() {
        return metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
