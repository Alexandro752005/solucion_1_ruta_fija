package pe.rutafija.availability.monitor;

/** Response content, network addresses and exception text never enter a result or evidence record. */
public record ProbeResult(String probeId, Outcome outcome, Integer httpStatus, long elapsedMillis) {
    public enum Outcome {
        HEALTHY, HTTP_STATUS_REJECTED, TIMEOUT, TRANSPORT_ERROR, BODY_TOO_LARGE,
        INVALID_CONTENT_TYPE, INVALID_HEALTH_PAYLOAD, INVALID_HTML_PAYLOAD
    }
    public boolean healthy() { return outcome == Outcome.HEALTHY; }
}
