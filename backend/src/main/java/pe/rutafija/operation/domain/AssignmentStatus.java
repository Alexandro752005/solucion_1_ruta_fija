package pe.rutafija.operation.domain;

/** Persisted online offer/execution states; interruption never certifies completion. */
public enum AssignmentStatus {
    PENDING_RESPONSE,
    SCHEDULED,
    EN_SERVICIO,
    INTERRUPTED,
    TRANSFERRED,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
