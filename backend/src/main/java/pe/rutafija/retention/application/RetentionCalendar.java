package pe.rutafija.retention.application;

import java.time.*;

/** Calendar policy, not 180 days. No clock or wall-time default is hidden here. */
public final class RetentionCalendar {
    public static final ZoneId AUDIT_ZONE=ZoneId.of("America/Lima");
    private RetentionCalendar() { }
    public static Instant auditDue(Instant occurredAt){return occurredAt.atZone(AUDIT_ZONE).plusMonths(6).toInstant();}
    public static Instant maximumAuditHold(Instant occurredAt){return auditDue(occurredAt).plus(Duration.ofDays(30));}
    public static Instant reportDue(Instant actualPilotEnd){return actualPilotEnd.plus(Duration.ofDays(90));}
    public static Instant personalDataDue(Instant actualPilotEnd){return actualPilotEnd.plus(Duration.ofDays(30));}
    public static Instant termsAcceptanceDue(Instant actualPilotEnd){return actualPilotEnd.plus(Duration.ofDays(90));}
    public static Instant recoveryEvidenceDue(Instant actualTerminalEvent){return actualTerminalEvent.plus(Duration.ofDays(30));}
    public static boolean ordinaryAuditVisible(Instant occurredAt,Instant now){return now.isBefore(auditDue(occurredAt));}
}
