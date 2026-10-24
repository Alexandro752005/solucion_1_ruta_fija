package pe.rutafija.operation.api.dto.mobile;

import java.util.UUID;

/** Scalar result only; no payload, credentials or location data are echoed. */
public record MobileSyncEventResult(
        UUID eventId,
        MobileSyncEventStatus status,
        String serverState,
        String errorCode
) {
}
