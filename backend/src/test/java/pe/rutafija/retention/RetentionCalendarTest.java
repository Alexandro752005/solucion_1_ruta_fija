package pe.rutafija.retention;

import org.junit.jupiter.api.Test;
import pe.rutafija.retention.application.RetentionCalendar;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class RetentionCalendarTest {
    @Test void clampsToLastDayWithoutReplacingSixCalendarMonthsWith180Days(){
        var start=ZonedDateTime.of(2026,8,31,23,59,59,123000000,ZoneId.of("America/Lima")).toInstant();
        assertThat(RetentionCalendar.auditDue(start)).isEqualTo(ZonedDateTime.of(2027,2,28,23,59,59,123000000,ZoneId.of("America/Lima")).toInstant()).isNotEqualTo(start.plus(Duration.ofDays(180)));
    }
    @Test void leapYearFebruaryAndLimaLocalDayArePreserved(){
        var start=Instant.parse("2027-09-01T04:59:59Z");
        assertThat(RetentionCalendar.auditDue(start)).isEqualTo(Instant.parse("2028-03-01T04:59:59Z"));
    }
    @Test void ordinaryReadEndsAtExactDeadlineAndHoldDoesNotRenewTheAnchor(){
        var event=Instant.parse("2026-01-31T15:00:00Z");var due=RetentionCalendar.auditDue(event);
        assertThat(RetentionCalendar.ordinaryAuditVisible(event,due.minusNanos(1))).isTrue();assertThat(RetentionCalendar.ordinaryAuditVisible(event,due)).isFalse();
        assertThat(RetentionCalendar.maximumAuditHold(event)).isEqualTo(due.plus(Duration.ofDays(30)));
    }
    @Test void reportDeadlineUsesActualPilotEndAnd90Days(){var end=Instant.parse("2026-10-28T05:00:00Z");assertThat(RetentionCalendar.reportDue(end)).isEqualTo(Instant.parse("2027-01-26T05:00:00Z"));}
}
