package pe.rutafija.fleet.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.api.dto.DriverCreateRequest;
import pe.rutafija.fleet.api.dto.DriverDetailResponse;
import pe.rutafija.fleet.api.dto.DriverResponse;
import pe.rutafija.fleet.api.dto.DriverUpdateRequest;
import pe.rutafija.fleet.api.dto.DriverVehicleResponse;
import pe.rutafija.fleet.api.dto.GroupCoordinatorRequest;
import pe.rutafija.fleet.api.dto.GroupCoordinatorResponse;
import pe.rutafija.fleet.api.dto.TransportGroupCreateRequest;
import pe.rutafija.fleet.api.dto.TransportGroupResponse;
import pe.rutafija.fleet.api.dto.TransportGroupUpdateRequest;
import pe.rutafija.fleet.api.dto.VehicleCreateRequest;
import pe.rutafija.fleet.api.dto.VehicleResponse;
import pe.rutafija.fleet.api.dto.VehicleStatusRequest;
import pe.rutafija.fleet.api.dto.VehicleUpdateRequest;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.domain.DriverVehicleLink;
import pe.rutafija.fleet.domain.GroupCoordinator;
import pe.rutafija.fleet.domain.TransportGroup;
import pe.rutafija.fleet.domain.Vehicle;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.DriverVehicleLinkRepository;
import pe.rutafija.fleet.infrastructure.GroupCoordinatorRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.fleet.infrastructure.VehicleRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.operation.domain.AssignmentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FleetManagementService {

    private static final List<AssignmentStatus> ACTIVE_OPERATION_STATUSES = List.of(
            AssignmentStatus.SCHEDULED,
            AssignmentStatus.EN_SERVICIO
    );

    private final TransportGroupRepository groupRepository;
    private final GroupCoordinatorRepository groupCoordinatorRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverVehicleLinkRepository driverVehicleLinkRepository;
    private final AppUserRepository userRepository;
    private final AssignmentRepository assignmentRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;

    public FleetManagementService(
            TransportGroupRepository groupRepository,
            GroupCoordinatorRepository groupCoordinatorRepository,
            DriverRepository driverRepository,
            VehicleRepository vehicleRepository,
            DriverVehicleLinkRepository driverVehicleLinkRepository,
            AppUserRepository userRepository,
            AssignmentRepository assignmentRepository,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock
    ) {
        this.groupRepository = groupRepository;
        this.groupCoordinatorRepository = groupCoordinatorRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverVehicleLinkRepository = driverVehicleLinkRepository;
        this.userRepository = userRepository;
        this.assignmentRepository = assignmentRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransportGroupResponse> listGroups(
            String search,
            Boolean active,
            Pageable pageable
    ) {
        AppUser actor = currentUserService.requireTenantActor();
        String normalizedSearch = normalizeSearch(search);
        Page<TransportGroup> groups = actor.getRole() == UserRole.COORDINADOR
                ? groupRepository.searchVisibleToCoordinator(
                        actor.getOrganizationId(),
                        actor.getId(),
                        normalizedSearch,
                        active,
                        pageable
                )
                : groupRepository.search(actor.getOrganizationId(), normalizedSearch, active, pageable);
        return PageResponse.from(groups, this::groupResponse);
    }

    @Transactional
    public TransportGroupResponse createGroup(TransportGroupCreateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        if (groupRepository.existsByOrganization_IdAndNameIgnoreCase(
                actor.getOrganizationId(),
                request.name().strip()
        )) {
            throw conflict("Ya existe un grupo con ese nombre");
        }

        TransportGroup group = groupRepository.save(TransportGroup.active(
                actor.getOrganization(),
                request.name(),
                request.description()
        ));
        auditService.record(actor, "GROUP_CREATED", "TRANSPORT_GROUP", group.getId(), Map.of());
        return groupResponse(group);
    }

    @Transactional
    public TransportGroupResponse updateGroup(UUID groupId, TransportGroupUpdateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        TransportGroup group = findTenantGroup(groupId, actor.getOrganizationId());
        if (!group.getName().equalsIgnoreCase(request.name().strip())
                && groupRepository.existsByOrganization_IdAndNameIgnoreCase(
                        actor.getOrganizationId(),
                        request.name().strip()
                )) {
            throw conflict("Ya existe un grupo con ese nombre");
        }
        if (!request.active() && group.isActive() && driverRepository.existsByGroup_IdAndActiveTrue(group.getId())) {
            throw conflict("No puede desactivar un grupo que todavía tiene conductores activos");
        }

        group.update(request.name(), request.description());
        if (request.active()) {
            group.activate();
        } else {
            group.deactivate();
        }
        auditService.record(
                actor,
                request.active() ? "GROUP_UPDATED" : "GROUP_DISABLED",
                "TRANSPORT_GROUP",
                group.getId(),
                Map.of("active", group.isActive())
        );
        return groupResponse(group);
    }

    @Transactional
    public TransportGroupResponse assignCoordinator(UUID groupId, GroupCoordinatorRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        TransportGroup group = findTenantGroup(groupId, actor.getOrganizationId());
        if (!group.isActive()) {
            throw conflict("No puede asignar coordinadores a un grupo inactivo");
        }
        AppUser coordinator = findTenantUser(request.userId(), actor.getOrganizationId());
        if (!coordinator.isActive() || coordinator.getRole() != UserRole.COORDINADOR) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.DRIVER_NOT_ELIGIBLE,
                    "El usuario seleccionado no es un coordinador activo"
            );
        }
        if (groupCoordinatorRepository.findByGroup_IdAndUser_Id(groupId, coordinator.getId()).isEmpty()) {
            groupCoordinatorRepository.save(GroupCoordinator.assign(
                    group,
                    coordinator,
                    actor,
                    Instant.now(clock)
            ));
            auditService.record(
                    actor,
                    "GROUP_COORDINATOR_ASSIGNED",
                    "TRANSPORT_GROUP",
                    group.getId(),
                    Map.of("coordinatorId", coordinator.getId().toString())
            );
        }
        return groupResponse(group);
    }

    @Transactional
    public void removeCoordinator(UUID groupId, UUID userId) {
        AppUser actor = currentUserService.requireTenantActor();
        TransportGroup group = findTenantGroup(groupId, actor.getOrganizationId());
        GroupCoordinator assignment = groupCoordinatorRepository.findByGroup_IdAndUser_Id(group.getId(), userId)
                .orElseThrow(this::notFound);
        groupCoordinatorRepository.delete(assignment);
        auditService.record(
                actor,
                "GROUP_COORDINATOR_REMOVED",
                "TRANSPORT_GROUP",
                group.getId(),
                Map.of("coordinatorId", userId.toString())
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> listDrivers(
            UUID groupId,
            DriverAvailabilityStatus status,
            String search,
            Boolean active,
            Pageable pageable
    ) {
        AppUser actor = currentUserService.requireTenantActor();
        if (groupId != null) {
            TransportGroup group = findTenantGroup(groupId, actor.getOrganizationId());
            assertGroupVisibleTo(actor, group);
        }
        String normalizedSearch = normalizeSearch(search);
        Page<Driver> drivers = actor.getRole() == UserRole.COORDINADOR
                ? driverRepository.searchVisibleToCoordinator(
                        actor.getOrganizationId(),
                        actor.getId(),
                        groupId,
                        status,
                        active,
                        normalizedSearch,
                        pageable
                )
                : driverRepository.search(
                        actor.getOrganizationId(),
                        groupId,
                        status,
                        active,
                        normalizedSearch,
                        pageable
                );
        return PageResponse.from(drivers, DriverResponse::from);
    }

    @Transactional(readOnly = true)
    public PageResponse<DriverResponse> listGroupDrivers(UUID groupId, Pageable pageable) {
        AppUser actor = currentUserService.requireTenantActor();
        TransportGroup group = findTenantGroup(groupId, actor.getOrganizationId());
        assertGroupVisibleTo(actor, group);
        return listDrivers(groupId, null, null, null, pageable);
    }

    @Transactional(readOnly = true)
    public DriverDetailResponse getDriver(UUID driverId) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        assertGroupVisibleTo(actor, driver.getGroup());
        return driverDetailResponse(driver);
    }

    @Transactional
    public DriverResponse createDriver(DriverCreateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        if (driverRepository.existsByOrganization_IdAndDocumentNumberIgnoreCase(
                actor.getOrganizationId(),
                request.documentNumber().strip()
        )) {
            throw conflict("Ya existe un conductor con ese número de documento");
        }
        TransportGroup group = findActiveTenantGroup(request.groupId(), actor.getOrganizationId());
        AppUser user = resolveDriverUser(request.userId(), actor.getOrganizationId(), null);

        Driver driver = driverRepository.save(Driver.register(
                actor.getOrganization(),
                user,
                group,
                request.fullName(),
                request.phone(),
                request.documentType(),
                request.documentNumber(),
                request.licenseNumber()
        ));
        auditService.record(actor, "DRIVER_CREATED", "DRIVER", driver.getId(), Map.of());
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse updateDriver(UUID driverId, DriverUpdateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        if (!driver.getDocumentNumber().equalsIgnoreCase(request.documentNumber().strip())
                && driverRepository.existsByOrganization_IdAndDocumentNumberIgnoreCase(
                        actor.getOrganizationId(),
                        request.documentNumber().strip()
                )) {
            throw conflict("Ya existe un conductor con ese número de documento");
        }

        TransportGroup group = findActiveTenantGroup(request.groupId(), actor.getOrganizationId());
        AppUser user = resolveDriverUser(request.userId(), actor.getOrganizationId(), driver.getId());
        driver.update(
                user,
                group,
                request.fullName(),
                request.phone(),
                request.documentType(),
                request.documentNumber(),
                request.licenseNumber()
        );
        auditService.record(actor, "DRIVER_UPDATED", "DRIVER", driver.getId(), Map.of());
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse activateDriver(UUID driverId) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        findActiveTenantGroup(driver.getGroup().getId(), actor.getOrganizationId());
        if (driver.getUser() != null && !driver.getUser().isActive()) {
            throw invalidDriver("No puede activar un conductor con un usuario asociado inactivo");
        }
        driver.activate();
        auditService.record(actor, "DRIVER_ACTIVATED", "DRIVER", driver.getId(), Map.of());
        return DriverResponse.from(driver);
    }

    @Transactional
    public DriverResponse deactivateDriver(UUID driverId) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        if (assignmentRepository.existsByDriver_IdAndStatusIn(driver.getId(), ACTIVE_OPERATION_STATUSES)) {
            throw conflict("No puede desactivar un conductor con asignaciones programadas o en servicio");
        }
        driver.deactivate();
        auditService.record(actor, "DRIVER_DISABLED", "DRIVER", driver.getId(), Map.of());
        return DriverResponse.from(driver);
    }

    @Transactional(readOnly = true)
    public PageResponse<VehicleResponse> listVehicles(
            pe.rutafija.fleet.domain.VehicleStatus status,
            String search,
            Boolean active,
            Pageable pageable
    ) {
        AppUser actor = currentUserService.requireTenantActor();
        Page<Vehicle> vehicles = vehicleRepository.search(
                actor.getOrganizationId(),
                status,
                active,
                normalizeSearch(search),
                pageable
        );
        return PageResponse.from(vehicles, VehicleResponse::from);
    }

    @Transactional(readOnly = true)
    public VehicleResponse getVehicle(UUID vehicleId) {
        AppUser actor = currentUserService.requireTenantActor();
        return VehicleResponse.from(findTenantVehicle(vehicleId, actor.getOrganizationId()));
    }

    @Transactional
    public VehicleResponse createVehicle(VehicleCreateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        if (vehicleRepository.existsByOrganization_IdAndPlateIgnoreCase(
                actor.getOrganizationId(),
                request.plate().strip()
        )) {
            throw conflict("Ya existe un vehículo con esa placa");
        }

        Vehicle vehicle = vehicleRepository.save(Vehicle.register(
                actor.getOrganization(),
                request.plate(),
                request.brand(),
                request.model(),
                request.year(),
                request.color()
        ));
        auditService.record(actor, "VEHICLE_CREATED", "VEHICLE", vehicle.getId(), Map.of());
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse updateVehicle(UUID vehicleId, VehicleUpdateRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        Vehicle vehicle = findTenantVehicle(vehicleId, actor.getOrganizationId());
        if (!vehicle.getPlate().equalsIgnoreCase(request.plate().strip())
                && vehicleRepository.existsByOrganization_IdAndPlateIgnoreCase(
                        actor.getOrganizationId(),
                        request.plate().strip()
                )) {
            throw conflict("Ya existe un vehículo con esa placa");
        }

        vehicle.update(
                request.plate(),
                request.brand(),
                request.model(),
                request.year(),
                request.color()
        );
        auditService.record(actor, "VEHICLE_UPDATED", "VEHICLE", vehicle.getId(), Map.of());
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public VehicleResponse changeVehicleStatus(UUID vehicleId, VehicleStatusRequest request) {
        AppUser actor = currentUserService.requireTenantActor();
        Vehicle vehicle = findTenantVehicle(vehicleId, actor.getOrganizationId());
        if (request.status() != pe.rutafija.fleet.domain.VehicleStatus.DISPONIBLE
                && assignmentRepository.existsByVehicle_IdAndStatusIn(vehicle.getId(), ACTIVE_OPERATION_STATUSES)) {
            throw conflict("No puede cambiar la disponibilidad del vehículo con asignaciones programadas o en servicio");
        }
        try {
            vehicle.changeAdministrativeStatus(request.status());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VEHICLE_NOT_ELIGIBLE,
                    exception.getMessage()
            );
        }
        auditService.record(
                actor,
                "VEHICLE_STATUS_CHANGED",
                "VEHICLE",
                vehicle.getId(),
                Map.of("status", vehicle.getStatus().name())
        );
        return VehicleResponse.from(vehicle);
    }

    @Transactional
    public DriverDetailResponse linkVehicle(UUID driverId, UUID vehicleId) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        Vehicle vehicle = findTenantVehicle(vehicleId, actor.getOrganizationId());
        ensureLinkEligible(driver, vehicle);

        DriverVehicleLink link = driverVehicleLinkRepository.findByDriver_IdAndVehicle_Id(
                driver.getId(),
                vehicle.getId()
        ).orElse(null);
        if (link == null) {
            driverVehicleLinkRepository.save(DriverVehicleLink.active(
                    driver,
                    vehicle,
                    false,
                    Instant.now(clock)
            ));
            auditService.record(actor, "DRIVER_VEHICLE_LINKED", "DRIVER", driver.getId(), Map.of(
                    "vehicleId", vehicle.getId().toString()
            ));
        } else if (!link.isActive()) {
            link.activate(false);
            auditService.record(actor, "DRIVER_VEHICLE_LINKED", "DRIVER", driver.getId(), Map.of(
                    "vehicleId", vehicle.getId().toString()
            ));
        }
        return driverDetailResponse(driver);
    }

    @Transactional
    public void unlinkVehicle(UUID driverId, UUID vehicleId) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        findTenantVehicle(vehicleId, actor.getOrganizationId());
        DriverVehicleLink link = driverVehicleLinkRepository.findByDriver_IdAndVehicle_Id(driver.getId(), vehicleId)
                .filter(DriverVehicleLink::isActive)
                .orElseThrow(this::notFound);
        if (assignmentRepository.existsByVehicle_IdAndStatusIn(vehicleId, ACTIVE_OPERATION_STATUSES)) {
            throw conflict("No puede desvincular un vehículo con asignaciones programadas o en servicio");
        }
        link.deactivate();
        auditService.record(actor, "DRIVER_VEHICLE_UNLINKED", "DRIVER", driver.getId(), Map.of(
                "vehicleId", vehicleId.toString()
        ));
    }

    @Transactional
    public DriverDetailResponse markPrimaryVehicle(UUID driverId, UUID vehicleId) {
        AppUser actor = currentUserService.requireTenantActor();
        Driver driver = findTenantDriver(driverId, actor.getOrganizationId());
        findTenantVehicle(vehicleId, actor.getOrganizationId());
        DriverVehicleLink existing = driverVehicleLinkRepository.findByDriver_IdAndVehicle_Id(driver.getId(), vehicleId)
                .filter(DriverVehicleLink::isActive)
                .orElseThrow(this::notFound);
        if (!existing.isPrimary()) {
            driverVehicleLinkRepository.clearPrimaryForDriver(driver.getId());
            DriverVehicleLink link = driverVehicleLinkRepository.findByDriver_IdAndVehicle_Id(driver.getId(), vehicleId)
                    .orElseThrow(this::notFound);
            link.markPrimary();
            auditService.record(actor, "DRIVER_PRIMARY_VEHICLE_CHANGED", "DRIVER", driver.getId(), Map.of(
                    "vehicleId", vehicleId.toString()
            ));
        }
        return driverDetailResponse(driver);
    }

    private TransportGroupResponse groupResponse(TransportGroup group) {
        List<GroupCoordinatorResponse> coordinators = groupCoordinatorRepository.findAllByGroup_Id(group.getId())
                .stream()
                .map(GroupCoordinatorResponse::from)
                .sorted(Comparator.comparing(GroupCoordinatorResponse::fullName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return TransportGroupResponse.from(group, coordinators);
    }

    private DriverDetailResponse driverDetailResponse(Driver driver) {
        List<DriverVehicleResponse> vehicles = driverVehicleLinkRepository.findAllByDriver_IdAndActiveTrue(driver.getId())
                .stream()
                .map(DriverVehicleResponse::from)
                .sorted(Comparator.comparing(DriverVehicleResponse::primary).reversed()
                        .thenComparing(DriverVehicleResponse::plate, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return DriverDetailResponse.of(DriverResponse.from(driver), vehicles);
    }

    private AppUser resolveDriverUser(UUID userId, UUID organizationId, UUID currentDriverId) {
        if (userId == null) {
            return null;
        }
        AppUser user = findTenantUser(userId, organizationId);
        if (!user.isActive() || user.getRole() != UserRole.CONDUCTOR) {
            throw invalidDriver("El usuario asociado debe ser un conductor activo");
        }
        driverRepository.findByUser_Id(userId)
                .filter(existing -> !existing.getId().equals(currentDriverId))
                .ifPresent(existing -> {
                    throw conflict("El usuario ya está vinculado a otro conductor");
                });
        return user;
    }

    private void ensureLinkEligible(Driver driver, Vehicle vehicle) {
        if (!driver.isActive()) {
            throw invalidDriver("El conductor no está activo");
        }
        if (!vehicle.isActive()) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VEHICLE_NOT_ELIGIBLE,
                    "El vehículo no está activo"
            );
        }
    }

    private void assertGroupVisibleTo(AppUser actor, TransportGroup group) {
        if (actor.getRole() == UserRole.COORDINADOR
                && !groupCoordinatorRepository.existsByGroup_IdAndUser_Id(group.getId(), actor.getId())) {
            throw notFound();
        }
    }

    private TransportGroup findTenantGroup(UUID groupId, UUID organizationId) {
        return groupRepository.findByIdAndOrganization_Id(groupId, organizationId)
                .orElseThrow(this::notFound);
    }

    private TransportGroup findActiveTenantGroup(UUID groupId, UUID organizationId) {
        TransportGroup group = findTenantGroup(groupId, organizationId);
        if (!group.isActive()) {
            throw invalidDriver("El grupo seleccionado no está activo");
        }
        return group;
    }

    private Driver findTenantDriver(UUID driverId, UUID organizationId) {
        return driverRepository.findByIdAndOrganization_Id(driverId, organizationId)
                .orElseThrow(this::notFound);
    }

    private Vehicle findTenantVehicle(UUID vehicleId, UUID organizationId) {
        return vehicleRepository.findByIdAndOrganization_Id(vehicleId, organizationId)
                .orElseThrow(this::notFound);
    }

    private AppUser findTenantUser(UUID userId, UUID organizationId) {
        return userRepository.findByIdAndOrganization_Id(userId, organizationId)
                .orElseThrow(this::notFound);
    }

    private static String normalizeSearch(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.strip().toLowerCase(Locale.ROOT);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe"
        );
    }

    private ApplicationException invalidDriver(String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, ErrorCode.DRIVER_NOT_ELIGIBLE, message);
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
