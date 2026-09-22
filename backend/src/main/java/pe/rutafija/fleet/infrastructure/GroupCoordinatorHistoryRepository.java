package pe.rutafija.fleet.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;
import pe.rutafija.fleet.domain.GroupCoordinator;
import pe.rutafija.fleet.domain.GroupCoordinatorId;

import java.util.List;
import java.util.UUID;

/**
 * Read-only access to group_coordinator records retained for technical history.
 *
 * <p>F2.2 deliberately omits save and delete operations: group membership no
 * longer grants authority and the application must not create new records.</p>
 */
public interface GroupCoordinatorHistoryRepository extends Repository<GroupCoordinator, GroupCoordinatorId> {

    @EntityGraph(attributePaths = {"user", "group"})
    List<GroupCoordinator> findAllByGroup_Id(UUID groupId);
}
