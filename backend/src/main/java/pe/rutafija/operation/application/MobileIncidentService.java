package pe.rutafija.operation.application;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.operation.api.dto.IncidentResponse;
import pe.rutafija.operation.api.dto.mobile.MobileIncidentCloseRequest;
import pe.rutafija.operation.api.dto.mobile.MobileIncidentCreateRequest;
import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.Incident;
import pe.rutafija.operation.domain.IncidentSource;
import pe.rutafija.operation.domain.IncidentStatus;
import pe.rutafija.operation.infrastructure.AssignmentRepository;
import pe.rutafija.operation.infrastructure.IncidentRepository;
import pe.rutafija.shared.api.PageResponse;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.MobileDriverActor;
import pe.rutafija.shared.security.MobileDriverContextService;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Incident endpoints limited to the authenticated conductor and their assignments. */
@Service
public class MobileIncidentService {

    private final MobileDriverContextService mobileDriverContextService;
    private final IncidentRepository incidentRepository;
    private final AssignmentRepository assignmentRepository;
    private final AuditService auditService;
    private final OperationEventPublisher eventPublisher;
    private final Clock clock;

    public MobileIncidentService(
            MobileDriverContextService mobileDriverContextService,
            IncidentRepository incidentRepository,
            AssignmentRepository assignmentRepository,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(Pageable pageable) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Page<Incident> incidents = incidentRepository.findMobileIncidents(
                actor.user().getOrganizationId(), actor.driver().getId(), pageable
        );
        return PageResponse.from(incidents, IncidentResponse::from);
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID incidentId) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        return IncidentResponse.from(findOwnIncident(actor, incidentId));
    }

    @Transactional
    public IncidentResponse create(MobileIncidentCreateRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Assignment assignment = request.assignmentId() == null
                ? null
                : assignmentRepository.findMobileAssignment(
                        request.assignmentId(), actor.user().getOrganizationId(), actor.driver().getId()
                ).orElseThrow(this::notFound);
        Incident incident = incidentRepository.saveAndFlush(Incident.reportFromMobile(
                actor.user().getOrganization(),
                actor.driver(),
                assignment,
                actor.user(),
                request.category(),
                request.description(),
                Instant.now(clock)
        ));
        auditService.record(actor.user(), "INCIDENT_REPORTED_FROM_MOBILE", "INCIDENT", incident.getId(), Map.of(
                "category", incident.getCategory().name(),
                "source", incident.getSource().name()
        ));
        eventPublisher.publish(
                actor.user().getOrganizationId(),
                actor.driver().getGroup().getId(),
                "incident.reported",
                Map.of(
                        "incidentId", incident.getId().toString(),
                        "driverId", actor.driver().getId().toString(),
                        "status", incident.getStatus().name(),
                        "source", incident.getSource().name()
                )
        );
        return IncidentResponse.from(incident);
    }

    @Transactional
    public IncidentResponse close(UUID incidentId, MobileIncidentCloseRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileDriver();
        Incident incident = findOwnIncident(actor, incidentId);
        if (incident.getSource() != IncidentSource.MOBILE_APP
                || !incident.getReportedBy().getId().equals(actor.user().getId())) {
            throw notFound();
        }
        if (incident.getStatus() == IncidentStatus.CLOSED_BY_DRIVER
                && incident.getDriverClosedBy() != null
                && incident.getDriverClosedBy().getId().equals(actor.user().getId())) {
            return IncidentResponse.from(incident);
        }
        assertVersion(incident, request.version());
        IncidentStatus previousStatus = incident.getStatus();
        try {
            incident.closeByMobileReporter(actor.user(), Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        incident = incidentRepository.saveAndFlush(incident);
        auditService.record(actor.user(), "INCIDENT_CLOSED_BY_MOBILE_REPORTER", "INCIDENT", incident.getId(), Map.of(
                "previousStatus", previousStatus.name(),
                "finalStatus", incident.getStatus().name()
        ));
        eventPublisher.publish(
                actor.user().getOrganizationId(),
                actor.driver().getGroup().getId(),
                "incident.closed-by-driver",
                Map.of(
                        "incidentId", incident.getId().toString(),
                        "driverId", actor.driver().getId().toString(),
                        "status", incident.getStatus().name()
                )
        );
        return IncidentResponse.from(incident);
    }

    private Incident findOwnIncident(MobileDriverActor actor, UUID incidentId) {
        return incidentRepository.findByIdAndOrganization_IdAndDriver_Id(
                        incidentId, actor.user().getOrganizationId(), actor.driver().getId()
                )
                .orElseThrow(this::notFound);
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El recurso solicitado no existe"
        );
    }

    private void assertVersion(Incident incident, Long version) {
        if (version == null || incident.getVersion() != version) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    "La incidencia fue modificada; actualice los datos antes de cerrarla"
            );
        }
    }

    private ApplicationException invalidState(String message) {
        return new ApplicationException(
                HttpStatus.CONFLICT,
                ErrorCode.INCIDENT_INVALID_STATE,
                message == null || message.isBlank()
                        ? "La incidencia ya no admite cierre por el conductor"
                        : message
        );
    }
}
