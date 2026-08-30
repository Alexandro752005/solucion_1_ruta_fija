package pe.rutafija.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "organization")
public class Organization {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 150)
    private String legalName;

    @Column(name = "trade_name", length = 120)
    private String tradeName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrganizationStatus status;

    @Column(nullable = false, length = 50)
    private String timezone;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Organization() {
    }

    private Organization(String legalName, String tradeName, String timezone) {
        this.id = UUID.randomUUID();
        this.legalName = requiredText(legalName, "La razón social es obligatoria");
        this.tradeName = optionalText(tradeName);
        this.status = OrganizationStatus.ACTIVE;
        this.timezone = requiredText(timezone, "La zona horaria es obligatoria");
    }

    public static Organization active(String legalName, String tradeName, String timezone) {
        return new Organization(legalName, tradeName, timezone);
    }

    public void update(String legalName, String tradeName, String timezone, OrganizationStatus status) {
        this.legalName = requiredText(legalName, "La razón social es obligatoria");
        this.tradeName = optionalText(tradeName);
        this.timezone = requiredText(timezone, "La zona horaria es obligatoria");
        this.status = Objects.requireNonNull(status);
    }

    public UUID getId() {
        return id;
    }

    public String getLegalName() {
        return legalName;
    }

    public String getTradeName() {
        return tradeName;
    }

    public OrganizationStatus getStatus() {
        return status;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return status == OrganizationStatus.ACTIVE;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
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
