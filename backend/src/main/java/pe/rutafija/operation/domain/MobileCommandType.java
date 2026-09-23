package pe.rutafija.operation.domain;

/** Commands whose client event identifiers are durable idempotency boundaries. */
public enum MobileCommandType {
    ASSIGNMENT_ACCEPT,
    ASSIGNMENT_REJECT,
    ASSIGNMENT_START,
    ASSIGNMENT_COMPLETE
}
