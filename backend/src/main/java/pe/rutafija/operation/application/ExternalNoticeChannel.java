package pe.rutafija.operation.application;

/** Provider boundary. Availability is not a delivery receipt or a device acknowledgement. */
public interface ExternalNoticeChannel {
    record Availability(boolean configured,String reason){}
    Availability availability();
}
