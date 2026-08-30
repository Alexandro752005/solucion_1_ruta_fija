package pe.rutafija.fleet.infrastructure;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.domain.VehicleStatus;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    @EntityGraph(attributePaths = "organization")
    @Query("""
            select vehicle from Vehicle vehicle
             where vehicle.organization.id = :organizationId
               and (:status is null or vehicle.status = :status)
               and (:active is null or vehicle.active = :active)
               and (
                    :search = ''
                    or lower(vehicle.plate) like lower(concat('%', :search, '%'))
                    or lower(coalesce(vehicle.brand, '')) like lower(concat('%', :search, '%'))
                    or lower(coalesce(vehicle.model, '')) like lower(concat('%', :search, '%'))
               )
            """)
    Page<Vehicle> search(
            @Param("organizationId") UUID organizationId,
            @Param("status") VehicleStatus status,
            @Param("active") Boolean active,
            @Param("search") String search,
            Pageable pageable
    );

    @EntityGraph(attributePaths = "organization")
    Optional<Vehicle> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndPlateIgnoreCase(UUID organizationId, String plate);
}
