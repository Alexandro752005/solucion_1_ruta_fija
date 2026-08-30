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
import org.hibernate.annotations.CreationTimestamp;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.organization.domain.Organization;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "announcement")
public class Announcement {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private AppUser createdBy;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience_type", nullable = false, length = 20)
    private AnnouncementAudienceType audienceType;

    @Column(name = "audience_id")
    private UUID audienceId;

    @Column(name = "require_read_ack", nullable = false)
    private boolean requireReadAck;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Announcement() {
    }

    private Announcement(
            Organization organization,
            AppUser createdBy,
            String title,
            String body,
            AnnouncementAudienceType audienceType,
            UUID audienceId
    ) {
        this.id = UUID.randomUUID();
        this.organization = Objects.requireNonNull(organization);
        this.createdBy = Objects.requireNonNull(createdBy);
        this.title = requiredText(title, "El título del comunicado es obligatorio");
        this.body = requiredText(body, "El contenido del comunicado es obligatorio");
        this.audienceType = Objects.requireNonNull(audienceType);
        this.audienceId = audienceType == AnnouncementAudienceType.ORGANIZATION
                ? null
                : Objects.requireNonNull(audienceId);
        // No existe cliente móvil ni portal del conductor para confirmar lectura en este alcance.
        this.requireReadAck = false;
    }

    public static Announcement publish(
            Organization organization,
            AppUser createdBy,
            String title,
            String body,
            AnnouncementAudienceType audienceType,
            UUID audienceId
    ) {
        return new Announcement(organization, createdBy, title, body, audienceType, audienceId);
    }

    public UUID getId() {
        return id;
    }

    public Organization getOrganization() {
        return organization;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public String getTitle() {
        return title;
    }

    public String getBody() {
        return body;
    }

    public AnnouncementAudienceType getAudienceType() {
        return audienceType;
    }

    public UUID getAudienceId() {
        return audienceId;
    }

    public boolean isRequireReadAck() {
        return requireReadAck;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    private static String requiredText(String value, String message) {
        if (value == null || value.strip().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.strip();
    }
}
