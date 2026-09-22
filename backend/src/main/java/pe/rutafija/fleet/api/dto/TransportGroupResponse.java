package pe.rutafija.fleet.api.dto;

import pe.rutafija.fleet.domain.TransportGroup;

import java.time.Instant;
import java.util.UUID;

public record TransportGroupResponse(
        UUID id,
        String name,
        String description,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
    public static TransportGroupResponse from(TransportGroup group) {
        return new TransportGroupResponse(
                group.getId(),
                group.getName(),
                group.getDescription(),
                group.isActive(),
                group.getCreatedAt(),
                group.getUpdatedAt()
        );
    }
}
