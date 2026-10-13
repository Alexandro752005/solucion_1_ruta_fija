package pe.rutafija.operation.api.dto.mobile;

/** The only state-changing events admitted by the bounded M8 outbox. */
public enum MobileSyncEventType {
    AVAILABILITY_CHANGE,
    SERVICE_START,
    SERVICE_COMPLETE
}
