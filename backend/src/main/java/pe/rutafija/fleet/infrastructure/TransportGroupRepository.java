package pe.rutafija.fleet.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.fleet.domain.TransportGroup;

import java.util.Optional;
import java.util.UUID;

public interface TransportGroupRepository extends JpaRepository<TransportGroup, UUID> {

    @EntityGraph(attributePaths = "organization")
    @Query("""
            select transportGroup from TransportGroup transportGroup
             where transportGroup.organization.id = :organizationId
               and (:active is null or transportGroup.active = :active)
               and (
                    :search = ''
                    or lower(transportGroup.name) like lower(concat('%', :search, '%'))
                    or lower(coalesce(transportGroup.description, '')) like lower(concat('%', :search, '%'))
               )
            """)
    Page<TransportGroup> search(
            @Param("organizationId") UUID organizationId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "organization")
    @Query("""
            select transportGroup from TransportGroup transportGroup
             where transportGroup.organization.id = :organizationId
               and exists (
                    select 1 from GroupCoordinator membership
                     where membership.group = transportGroup
                       and membership.user.id = :coordinatorId
               )
               and (:active is null or transportGroup.active = :active)
               and (
                    :search = ''
                    or lower(transportGroup.name) like lower(concat('%', :search, '%'))
                    or lower(coalesce(transportGroup.description, '')) like lower(concat('%', :search, '%'))
               )
            """)
    Page<TransportGroup> searchVisibleToCoordinator(
            @Param("organizationId") UUID organizationId,
            @Param("coordinatorId") UUID coordinatorId,
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "organization")
    Optional<TransportGroup> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndNameIgnoreCase(UUID organizationId, String name);
}
