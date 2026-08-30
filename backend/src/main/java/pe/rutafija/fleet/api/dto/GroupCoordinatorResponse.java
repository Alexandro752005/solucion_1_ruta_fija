package pe.rutafija.fleet.api.dto;

import pe.rutafija.fleet.domain.GroupCoordinator;

import java.time.Instant;
import java.util.UUID;

public record GroupCoordinatorResponse(
        UUID userId,
        String fullName,
        String email,
        Instant assignedAt
) {
    public static GroupCoordinatorResponse from(GroupCoordinator coordinator) {
        return new GroupCoordinatorResponse(
                coordinator.getUser().getId(),
                coordinator.getUser().getFullName(),
                coordinator.getUser().getEmail(),
                coordinator.getAssignedAt()
        );
    }
}
