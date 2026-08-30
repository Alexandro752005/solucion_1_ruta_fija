package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.GroupCoordinatorRepository;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.operation.api.dto.IncidentCreateRequest;
import pe.rutafija.operation.api.dto.IncidentFollowUpRequest;
import pe.rutafija.operation.api.dto.IncidentResponse;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.Incident;
import pe.rutafija.operation.domain.IncidentCategory;
import pe.rutafija.operation.domain.IncidentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.IncidentRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Incidencias registradas por el personal del CRM web. */
@Service
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final AssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final GroupCoordinatorRepository groupCoordinatorRepository;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public IncidentService(
            IncidentRepository incidentRepository,
            AssignmentRepository assignmentRepository,
            DriverRepository driverRepository,
            GroupCoordinatorRepository groupCoordinatorRepository,
            CurrentUserService currentUserService,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.driverRepository = driverRepository;
        this.groupCoordinatorRepository = groupCoordinatorRepository;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> listIncidents(
            UUID driverId,
            IncidentStatus status,
            IncidentCategory category,
            Instant from,
            Instant to,
            Pageable pageable
    ) {
        AppUser actor = requireOperationalActor();
        if (driverId != null) {
            assertDriverVisible(actor, findTenantDriver(driverId, actor.getOrganizationId()));
        }
        Page<Incident> incidents = actor.getRole() == UserRole.COORDINADOR
                ? incidentRepository.searchVisibleToCoordinator(
                        actor.getOrganizationId(), actor.getId(), driverId, status, category, from, to, pageable
                )
                : incidentRepository.search(actor.getOrganizationId(), driverId, status, category, from, to, pageable);
        return PageResponse.from(incidents, IncidentResponse::from);
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID incidentId) {
        AppUser actor = requireOperationalActor();
        Incident incident = findTenantIncident(incidentId, actor.getOrganizationId());
        assertDriverVisible(actor, incident.getDriver());
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse createIncident(IncidentCreateRequest request) {
        AppUser actor = requireOperationalActor();
        Driver driver = findTenantDriver(request.driverId(), actor.getOrganizationId());
        assertDriverVisible(actor, driver);
        Assignment assignment = request.assignmentId() == null
                ? null
                : findTenantAssignment(request.assignmentId(), actor.getOrganizationId());
        if (assignment != null) {
            assertDriverVisible(actor, assignment.getDriver());
            if (!assignment.getDriver().getId().equals(driver.getId())) {
                throw new ApplicationException(
                        HttpStatus.BAD_REQUEST,
                        ErrorCode.VALIDATION_ERROR,
                        "La asignación indicada no pertenece al conductor"
                );
            }
        }

        Incident incident = incidentRepository.saveAndFlush(Incident.report(
                actor.getOrganization(),
                driver,
                assignment,
                actor,
                request.category(),
                request.description(),
                Instant.now(clock)
        ));
        auditService.record(actor, "INCIDENT_REPORTED_FROM_CRM", "INCIDENT", incident.getId(), Map.of(
                "category", incident.getCategory().name(),
                "source", "CRM_WEB"
        ));
        eventPublisher.publish(actor.getOrganizationId(), incident.getDriver().getGroup().getId(), "incident.reported", eventData(incident));
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse followUpIncident(UUID incidentId, IncidentFollowUpRequest request) {
        AppUser actor = requireOperationalActor();
        Incident incident = findTenantIncident(incidentId, actor.getOrganizationId());
        assertDriverVisible(actor, incident.getDriver());
        assertVersion(incident, request.version());
        try {
            incident.followUp(request.note(), request.resolve(), actor, Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        incident = incidentRepository.saveAndFlush(incident);
        auditService.record(actor, "INCIDENT_FOLLOWED_UP", "INCIDENT", incident.getId(), Map.of(
                "resolved", request.resolve()
        ));
        eventPublisher.publish(actor.getOrganizationId(), incident.getDriver().getGroup().getId(), "incident.followed-up", eventData(incident));
        return IncidentResponse.from(incident);
    }

    private AppUser requireOperationalActor() {
        AppUser actor = currentUserService.requireTenantActor();
        if (actor.getRole() != UserRole.ADMINISTRADOR && actor.getRole() != UserRole.COORDINADOR) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La operación web requiere el rol ADMINISTRADOR o COORDINADOR"
            );
        }
        return actor;
    }

    private Incident findTenantIncident(UUID incidentId, UUID organizationId) {
        return incidentRepository.findByIdAndOrganization_Id(incidentId, organizationId)
                .orElseThrow(this::notFound);
    }

    private Assignment findTenantAssignment(UUID assignmentId, UUID organizationId) {
        return assignmentRepository.findByIdAndOrganization_Id(assignmentId, organizationId)
                .orElseThrow(this::notFound);
    }

    private Driver findTenantDriver(UUID driverId, UUID organizationId) {
        return driverRepository.findByIdAndOrganization_Id(driverId, organizationId)
                .orElseThrow(this::notFound);
    }

    private void assertDriverVisible(AppUser actor, Driver driver) {
        if (actor.getRole() == UserRole.COORDINADOR
                && !groupCoordinatorRepository.existsByGroup_IdAndUser_Id(driver.getGroup().getId(), actor.getId())) {
            throw notFound();
        }
    }

    private void assertVersion(Incident incident, Long requestVersion) {
        if (requestVersion == null || incident.getVersion() != requestVersion) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    "La incidencia fue modificada por otro usuario; actualice la información"
            );
        }
    }

    private Map<String, Object> eventData(Incident incident) {
        return Map.of(
                "incidentId", incident.getId().toString(),
                "status", incident.getStatus().name(),
                "category", incident.getCategory().name(),
                "driverId", incident.getDriver().getId().toString()
        );
    }

    private ApplicationException invalidState(String message) {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INCIDENT_INVALID_STATE,
                message == null || message.isBlank() ? "La transición de la incidencia no está permitida" : message
        );
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe"
        );
    }
}
