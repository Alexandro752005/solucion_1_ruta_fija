package pe.rutafija.fleet.api.dto;

import pe.rutafija.fleet.domain.TransportGroup;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransportGroupResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        List<GroupCoordinatorResponse> coordinators,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransportGroupResponse from(
            TransportGroup group,
            List<GroupCoordinatorResponse> coordinators
    ) {
        return new TransportGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.isActive(),
                List.copyOf(coordinators),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
