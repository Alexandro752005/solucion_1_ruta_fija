package pe.rutafija.operation.api.dto.mobile;

import java.util.List;

public record MobileSyncEventsResponse(List<MobileSyncEventResult> results) {
    public MobileSyncEventsResponse {
        results = List.copyOf(results);
    }
}
