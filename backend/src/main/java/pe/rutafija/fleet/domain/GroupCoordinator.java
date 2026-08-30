package pe.rutafija.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import pe.rutafija.identity.domain.AppUser;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "group_coordinator")
public class GroupCoordinator {

    @EmbeddedId
    private GroupCoordinatorId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("groupId")
    @JoinColumn(name = "group_id", nullable = false)
    private TransportGroup group;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false)
    private AppUser assignedBy;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    protected GroupCoordinator() {
    }

    private GroupCoordinator(TransportGroup group, AppUser user, AppUser assignedBy, Instant assignedAt) {
        this.id = new GroupCoordinatorId(group.getId(), user.getId());
        this.group = Objects.requireNonNull(group);
        this.user = Objects.requireNonNull(user);
        this.assignedBy = Objects.requireNonNull(assignedBy);
        this.assignedAt = Objects.requireNonNull(assignedAt);
    }

    public static GroupCoordinator assign(
            TransportGroup group,
            AppUser user,
            AppUser assignedBy,
            Instant assignedAt
    ) {
        return new GroupCoordinator(group, user, assignedBy, assignedAt);
    }

    public TransportGroup getGroup() {
        return group;
    }

    public AppUser getUser() {
        return user;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }
}
