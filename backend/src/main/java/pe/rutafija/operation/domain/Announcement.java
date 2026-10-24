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

    @Column(name="operation_key",updatable=false) private UUID operationKey;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Column(name="payload_sha256",updatable=false,length=64) private String payloadSha256;

    protected Announcement() {
    }

    private Announcement(
            Organization organization,
            AppUser createdBy,
            String title,
            String body,
            AnnouncementAudienceType audienceType,
            UUID audienceId,
            boolean requireReadAck
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
        // Reading is a separate explicit mobile action; publication never confirms it.
        this.requireReadAck = requireReadAck;
    }

    public static Announcement publish(
            Organization organization,
            AppUser createdBy,
            String title,
            String body,
            AnnouncementAudienceType audienceType,
            UUID audienceId,
            boolean requireReadAck
    ) {
        return new Announcement(organization, createdBy, title, body, audienceType, audienceId, requireReadAck);
    }

    public UUID getId() {
        return id;
    }
    public void identifyPublication(UUID key,String hash) {
        if(operationKey!=null)throw new IllegalStateException("Publication key is immutable");
        this.operationKey=Objects.requireNonNull(key);this.payloadSha256=Objects.requireNonNull(hash);
    }
    public UUID getOperationKey(){return operationKey;}
    public String getPayloadSha256(){return payloadSha256;}

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
