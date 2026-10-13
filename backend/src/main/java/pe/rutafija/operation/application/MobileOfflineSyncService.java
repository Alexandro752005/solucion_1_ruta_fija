package pe.rutafija.operation.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import pe.rutafija.operation.api.dto.AssignmentResponse;
import pe.rutafija.operation.api.dto.mobile.MobileAssignmentCommandRequest;
import pe.rutafija.operation.api.dto.mobile.MobileAvailabilityResponse;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventRequest;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventResult;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventsRequest;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventsResponse;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventStatus;
import pe.rutafija.operation.api.dto.mobile.MobileSyncEventType;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Foreground-only reconciliation endpoint for the bounded mobile outbox. Each
 * command executes in its own existing transaction. Processing stops at the
 * first definitive result so the device never skips a command in FIFO order.
 */
@Service
public class MobileOfflineSyncService {

    private final MobileAssignmentService mobileAssignmentService;
    private final MobileDriverService mobileDriverService;

    public MobileOfflineSyncService(
            MobileAssignmentService mobileAssignmentService,
            MobileDriverService mobileDriverService
    ) {
        this.mobileAssignmentService = mobileAssignmentService;
        this.mobileDriverService = mobileDriverService;
    }

    public MobileSyncEventsResponse synchronize(MobileSyncEventsRequest request) {
        assertNoRepeatedEventIds(request.events());
        List<MobileSyncEventResult> results = new ArrayList<>();
        for (MobileSyncEventRequest event : request.events()) {
            MobileSyncEventResult result;
            try {
                result = process(event);
            } catch (ApplicationException exception) {
                result = failure(event, exception);
            }
            results.add(result);
            if (result.status() == MobileSyncEventStatus.CONFLICT
                    || result.status() == MobileSyncEventStatus.REJECTED) {
                break;
            }
        }
        return new MobileSyncEventsResponse(results);
    }

    private MobileSyncEventResult process(MobileSyncEventRequest event) {
        return switch (event.type()) {
            case AVAILABILITY_CHANGE -> availability(event);
            case SERVICE_START -> assignment(event, true);
            case SERVICE_COMPLETE -> assignment(event, false);
        };
    }

    private MobileSyncEventResult availability(MobileSyncEventRequest event) {
        assertAvailabilityEvent(event);
        MobileCommandExecution<MobileAvailabilityResponse> execution = mobileDriverService.syncAvailability(event);
        return success(
                event,
                execution.replayed(),
                execution.value().status().name()
        );
    }

    private MobileSyncEventResult assignment(MobileSyncEventRequest event, boolean start) {
        assertServiceEvent(event);
        MobileAssignmentCommandRequest request = new MobileAssignmentCommandRequest(
                event.eventId(), event.version(), event.occurredAt()
        );
        MobileCommandExecution<AssignmentResponse> execution = start
                ? mobileAssignmentService.start(event.assignmentId(), request)
                : mobileAssignmentService.complete(event.assignmentId(), request);
        return success(event, execution.replayed(), execution.value().status().name());
    }

    private MobileSyncEventResult success(MobileSyncEventRequest event, boolean replayed, String serverState) {
        return new MobileSyncEventResult(
                event.eventId(),
                replayed ? MobileSyncEventStatus.ALREADY_APPLIED : MobileSyncEventStatus.APPLIED,
                serverState,
                null
        );
    }

    private MobileSyncEventResult failure(MobileSyncEventRequest event, ApplicationException exception) {
        MobileSyncEventStatus status = exception.getStatus() == HttpStatus.CONFLICT
                ? MobileSyncEventStatus.CONFLICT
                : MobileSyncEventStatus.REJECTED;
        return new MobileSyncEventResult(event.eventId(), status, null, exception.getCode().name());
    }

    private void assertNoRepeatedEventIds(List<MobileSyncEventRequest> events) {
        Set<java.util.UUID> ids = new HashSet<>();
        for (MobileSyncEventRequest event : events) {
            if (!ids.add(event.eventId())) {
                throw invalid("eventId no puede repetirse dentro de la misma sincronizacion");
            }
        }
    }

    private void assertAvailabilityEvent(MobileSyncEventRequest event) {
        if (event.assignmentId() != null
                || event.version() != null
                || event.targetAvailability() == null
                || event.expectedAvailabilityUpdatedAt() == null) {
            throw invalid("AVAILABILITY_CHANGE requiere destino y token de disponibilidad, sin asignacion ni version");
        }
    }

    private void assertServiceEvent(MobileSyncEventRequest event) {
        if (event.assignmentId() == null
                || event.version() == null
                || event.targetAvailability() != null
                || event.expectedAvailabilityUpdatedAt() != null) {
            throw invalid("SERVICE_START y SERVICE_COMPLETE requieren asignacion y version solamente");
        }
    }

    private ApplicationException invalid(String message) {
        return new ApplicationException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message);
    }
}
