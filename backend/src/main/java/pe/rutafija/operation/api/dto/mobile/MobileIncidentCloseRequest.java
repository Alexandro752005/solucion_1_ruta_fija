package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** The version is checked by the server; actor, tenant and closure time are server-derived. */
public record MobileIncidentCloseRequest(
        @NotNull @Min(0) Long version
) {
}
