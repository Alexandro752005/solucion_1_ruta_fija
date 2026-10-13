package pe.rutafija.operation.api.dto.mobile;

/** Stable outcome for one submitted offline event. */
public enum MobileSyncEventStatus {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    REJECTED
}
