package pe.rutafija.audit.application;

/** Do not copy arbitrary client-controlled header content into durable audit. */
public final class AuditCorrelationId {
    private AuditCorrelationId(){}
    public static String safe(String value){
        if(value==null||!value.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))return null;
        return java.util.UUID.fromString(value).toString();
    }
}
