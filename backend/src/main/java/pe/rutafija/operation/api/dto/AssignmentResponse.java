package pe.rutafija.operation.api.dto;

import pe.rutafija.operation.domain.Assignment;
import pe.rutafija.operation.domain.AssignmentResponseMode;
import pe.rutafija.operation.domain.AssignmentStatus;

import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id,
        AssignmentStatus status,
        AssignmentResponseMode responseMode,
        Instant responseDeadlineAt,
        Instant acceptedAt,
        Instant rejectedAt,
        String rejectionReason,
        Instant expiredAt,
        long version,
        UUID driverId,
        String driverName,
        UUID groupId,
        String groupName,
        UUID vehicleId,
        String vehiclePlate,
        String originText,
        String destinationText,
        Instant scheduledAt,
        Instant scheduledEndAt,
        Instant reservedAt,
        Instant startedAt,
        Instant completedAt,
        Instant cancelledAt,
        String cancellationReason,
        String notes,
        UUID createdById,
        String createdByName,
        Instant createdAt,
        Instant updatedAt
) {
    public static AssignmentResponse from(Assignment assignment) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getStatus(),
                assignment.getResponseMode(),
                assignment.getResponseDeadlineAt(),
                assignment.getAcceptedAt(),
                assignment.getRejectedAt(),
                assignment.getRejectionReason(),
                assignment.getExpiredAt(),
                assignment.getVersion(),
                assignment.getDriver().getId(),
                assignment.getDriver().getFullName(),
                assignment.getDriver().getGroup().getId(),
                assignment.getDriver().getGroup().getName(),
                assignment.getVehicle().getId(),
                assignment.getVehicle().getPlate(),
                assignment.getOriginText(),
                assignment.getDestinationText(),
                assignment.getScheduledAt(),
                assignment.getScheduledEndAt(),
                assignment.getReservedAt(),
                assignment.getStartedAt(),
                assignment.getCompletedAt(),
                assignment.getCancelledAt(),
                assignment.getCancellationReason(),
                assignment.getNotes(),
                assignment.getCreatedBy().getId(),
                assignment.getCreatedBy().getFullName(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }
}
