package pe.rutafija.fleet.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.rutafija.fleet.domain.GroupCoordinator;
import pe.rutafija.fleet.domain.GroupCoordinatorId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupCoordinatorRepository extends JpaRepository<GroupCoordinator, GroupCoordinatorId> {

    @EntityGraph(attributePaths = {"user", "group"})
    List<GroupCoordinator> findAllByGroup_Id(UUID groupId);

    Optional<GroupCoordinator> findByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    boolean existsByGroup_IdAndUser_Id(UUID groupId, UUID userId);

    boolean existsByUser_Id(UUID userId);

    @Query("select coordinator.group.id from GroupCoordinator coordinator where coordinator.user.id = :userId")
    List<UUID> findGroupIdsByUserId(UUID userId);
}
