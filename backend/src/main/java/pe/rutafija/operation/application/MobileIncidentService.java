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
    private final ServiceExecution execution;
    private final IncidentWorkflow workflow;

    public MobileIncidentService(
            MobileDriverContextService mobileDriverContextService,
            IncidentRepository incidentRepository,
            AssignmentRepository assignmentRepository,
            AuditService auditService,
            OperationEventPublisher eventPublisher,
            Clock clock,ServiceExecution execution,IncidentWorkflow workflow
    ) {
        this.mobileDriverContextService = mobileDriverContextService;
        this.incidentRepository = incidentRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
        this.execution=execution;
        this.workflow=workflow;
    }

    @Transactional(readOnly = true)
    public PageResponse<IncidentResponse> list(Pageable pageable) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        Page<Incident> incidents = incidentRepository.findMobileIncidents(
                actor.user().getOrganizationId(), actor.driver().getId(), pageable
        );
        return PageResponse.from(incidents, workflow::view);
    }

    @Transactional(readOnly = true)
    public IncidentResponse get(UUID incidentId) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        return workflow.view(findOwnIncident(actor, incidentId));
    }

    @Transactional
    public IncidentResponse create(MobileIncidentCreateRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        workflow.beforeWrite(actor.user());
        var replay=workflow.replayReport(actor.user(),request.operationKey(),request);if(replay.isPresent())return replay.get();
        Assignment assignment = request.assignmentId() == null
                ? null
                : assignmentRepository.findMobileAssignment(
                        request.assignmentId(), actor.user().getOrganizationId(), actor.driver().getId()
                ).orElseThrow(this::notFound);
        if(assignment==null||assignment.getStartedAt()==null)mobileDriverContextService.requireMobileDriver();
        Incident incident = Incident.reportFromMobile(
                actor.user().getOrganization(),
                actor.driver(),
                assignment,
                actor.user(),
                request.category(),
                request.description(),
                Instant.now(clock)
        );
        workflow.prepareReport(incident,actor.user(),request.severity(),request.previousIncidentId(),request.relationReason());
        execution.linkIncident(incident,assignment);incident=incidentRepository.saveAndFlush(incident);
        workflow.recordReport(incident,actor.user(),request.operationKey(),request);
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
        return workflow.view(incident);
    }

    @Transactional
    public IncidentResponse close(UUID incidentId, MobileIncidentCloseRequest request) {
        MobileDriverActor actor = mobileDriverContextService.requireMobileConsultation();
        workflow.beforeWrite(actor.user());
        Incident incident = findOwnIncident(actor, incidentId);
        if (incident.getSource() != IncidentSource.MOBILE_APP
                || !incident.getReportedBy().getId().equals(actor.user().getId())) {
            throw notFound();
        }
        if (incident.getStatus() == IncidentStatus.CLOSED_BY_DRIVER
                && incident.getDriverClosedBy() != null
                && incident.getDriverClosedBy().getId().equals(actor.user().getId())) {
            return workflow.view(incident);
        }
        assertVersion(incident, request.version());
        IncidentStatus previousStatus = incident.getStatus();
        try {
            incident.closeByMobileReporter(actor.user(), Instant.now(clock));
        } catch (IllegalStateException exception) {
            throw invalidState(exception.getMessage());
        }
        incident = incidentRepository.saveAndFlush(incident);
        workflow.recordDriverClosure(incident,actor.user());
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
        return workflow.view(incident);
    }

    @Transactional(readOnly=true)
    public java.util.List<pe.rutafija.operation.api.IncidentWorkflowContract.Action> history(UUID id){
        var actor=mobileDriverContextService.requireMobileConsultation();return workflow.ownHistory(findOwnIncident(actor,id));
    }
    @Transactional(readOnly=true)
    public pe.rutafija.operation.api.IncidentWorkflowContract.Operation operation(UUID key){
        var actor=mobileDriverContextService.requireMobileConsultation();return workflow.operation(actor.user(),key);
    }
    @Transactional
    public IncidentResponse information(UUID id,pe.rutafija.operation.api.IncidentWorkflowContract.Information command){
        var actor=mobileDriverContextService.requireMobileConsultation();workflow.beforeWrite(actor.user());
        return workflow.information(findOwnIncident(actor,id),actor.user(),command);
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
