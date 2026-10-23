package pe.rutafija.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class GroupCoordinatorId implements Serializable {

    @Column(name = "group_id")
    private UUID groupId;

    @Column(name = "user_id")
    private UUID userId;

    protected GroupCoordinatorId() {
    }

    public GroupCoordinatorId(UUID groupId, UUID userId) {
        this.groupId = Objects.requireNonNull(groupId);
        this.userId = Objects.requireNonNull(userId);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GroupCoordinatorId that)) {
            return false;
        }
        return groupId.equals(that.groupId) && userId.equals(that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, userId);
    }
}
