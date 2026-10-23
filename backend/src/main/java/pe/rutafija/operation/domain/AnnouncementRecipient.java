package pe.rutafija.operation.domain;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
/** Read projection of the original publication snapshot; audited writes use JDBC. */
@Entity @Table(name="announcement_recipient") @IdClass(AnnouncementRecipient.Key.class)
public class AnnouncementRecipient {
    @Id @Column(name="announcement_id") private UUID announcementId;
    @Id @Column(name="user_id") private UUID userId;
    @Column(name="organization_id") private UUID organizationId;
    @Column(name="withdrawn_at") private Instant withdrawnAt;
    protected AnnouncementRecipient() {}
    public record Key(UUID announcementId,UUID userId) implements Serializable {}
}
