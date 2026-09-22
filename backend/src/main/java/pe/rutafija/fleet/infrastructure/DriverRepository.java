package pe.rutafija.fleet.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;

import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {

    @EntityGraph(attributePaths = {"organization", "group", "user"})
    @Query("""
            select driver from Driver driver
             where driver.organization.id = :organizationId
               and (:groupId is null or driver.group.id = :groupId)
               and (:status is null or driver.availabilityStatus = :status)
               and (:active is null or driver.active = :active)
               and (
                    :search = ''
                    or lower(driver.fullName) like lower(concat('%', :search, '%'))
                    or lower(driver.documentNumber) like lower(concat('%', :search, '%'))
               )
            """)
    Page<Driver> search(
            @Param("organizationId") UUID organizationId,
            @Param("groupId") UUID groupId,
            @Param("status") DriverAvailabilityStatus status,
            @Param("active") Boolean active,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"organization", "group", "user"})
    Optional<Driver> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = {"organization", "group", "user"})
    Optional<Driver> findByUser_Id(UUID userId);

    boolean existsByUser_Id(UUID userId);

    boolean existsByOrganization_IdAndDocumentNumberIgnoreCase(UUID organizationId, String documentNumber);

    boolean existsByGroup_IdAndActiveTrue(UUID groupId);
}
