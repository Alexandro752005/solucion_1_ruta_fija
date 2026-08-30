package pe.rutafija.fleet.infrastructure;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.rutafija.fleet.domain.DriverVehicleLink;
import pe.rutafija.fleet.domain.DriverVehicleLinkId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DriverVehicleLinkRepository extends JpaRepository<DriverVehicleLink, DriverVehicleLinkId> {

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    List<DriverVehicleLink> findAllByDriver_IdAndActiveTrue(UUID driverId);

    @EntityGraph(attributePaths = {"driver", "vehicle"})
    Optional<DriverVehicleLink> findByDriver_IdAndVehicle_Id(UUID driverId, UUID vehicleId);

    boolean existsByDriver_IdAndVehicle_IdAndActiveTrue(UUID driverId, UUID vehicleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update DriverVehicleLink link
               set link.primaryVehicle = false
             where link.driver.id = :driverId
               and link.active = true
            """)
    int clearPrimaryForDriver(@Param("driverId") UUID driverId);
}
