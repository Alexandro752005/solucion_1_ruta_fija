package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record MobileSyncEventsRequest(
        @NotEmpty @Size(max = 10) List<@Valid MobileSyncEventRequest> events
) {
    public MobileSyncEventsRequest {
        events = List.copyOf(events);
    }
}
