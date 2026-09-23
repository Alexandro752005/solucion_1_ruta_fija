package pe.rutafija.operation.domain;

/** Estados web de una asignación; no se modela aceptación móvil en este alcance. */
public enum AssignmentStatus {
    PENDING_RESPONSE,
    SCHEDULED,
    EN_SERVICIO,
    COMPLETED,
    REJECTED,
    CANCELLED,
    EXPIRED
}
