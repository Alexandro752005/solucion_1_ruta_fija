package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GroupCoordinatorRequest(@NotNull UUID userId) {
}
