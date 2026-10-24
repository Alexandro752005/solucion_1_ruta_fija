package pe.rutafija.availability;

import org.junit.jupiter.api.Test;
import pe.rutafija.availability.domain.AvailabilityPolicy;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Synthetic clock/pure state: not production uptime, an external alert or human attendance. */
class AvailabilityPolicyTest {
    private final Instant start=Instant.parse("2026-10-21T00:00:00Z");
    private AvailabilityPolicy policy(){return new AvailabilityPolicy(3,2,()->UUID.fromString("c6f859b3-1a61-4a43-81e9-41034626da01"));}
    private void failThree(AvailabilityPolicy p){p.observe(start,false);p.observe(start.plusSeconds(60),false);p.observe(start.plusSeconds(120),false);}
    @Test void successfulSamplesDoNotInventAnIncidentOrAlert(){var p=policy();assertThat(p.observe(start,true)).isEmpty();assertThat(p.observe(start.plusSeconds(60),true)).isEmpty();assertThat(p.incidentOpen()).isFalse();}
    @Test void transientFailureNeedsConsecutiveSustainedEvidence(){var p=policy();p.observe(start,false);p.observe(start.plusSeconds(60),false);assertThat(p.suspectedFailure()).isTrue();assertThat(p.observe(start.plusSeconds(120),true)).isEmpty();assertThat(p.suspectedFailure()).isFalse();assertThat(p.observe(start.plusSeconds(180),false)).isEmpty();}
    @Test void threeFailuresAtSixtySecondsConfirmWithinFiveMinutes(){var p=policy();p.observe(start,false);p.observe(start.plusSeconds(60),false);var e=p.observe(start.plusSeconds(120),false).getFirst();assertThat(e.kind()).isEqualTo(AvailabilityPolicy.Kind.CONFIRMED_FAILURE);assertThat(e.firstFailureAt()).isEqualTo(start);assertThat(e.detectionSeconds()).isEqualTo(120);assertThat(e.detectionWithinFiveMinutes()).isTrue();assertThat(e.recoverySeconds()).isNull();assertThat(p.incidentOpen()).isTrue();}
    @Test void delayedObservationsDoNotFalselyMeetDetectionObjective(){var p=policy();p.observe(start,false);p.observe(start.plusSeconds(180),false);var e=p.observe(start.plusSeconds(360),false).getFirst();assertThat(e.detectionSeconds()).isEqualTo(360);assertThat(e.detectionWithinFiveMinutes()).isFalse();}
    @Test void detectionOneNanosecondBeyondFiveMinutesIsNotRoundedIntoSuccess(){var p=policy();p.observe(start,false);p.observe(start.plusSeconds(180),false);assertThat(p.observe(start.plusSeconds(300).plusNanos(1),false).getFirst().detectionWithinFiveMinutes()).isFalse();}
    @Test void repeatedFailuresEmitOneConfirmationForTheSameOpenIncident(){var p=policy();failThree(p);assertThat(p.observe(start.plusSeconds(180),false)).isEmpty();assertThat(p.observe(start.plusSeconds(240),false)).isEmpty();assertThat(p.incidentOpen()).isTrue();}
    @Test void aSingleHealthyResponseDoesNotDeclareRecovery(){var p=policy();failThree(p);assertThat(p.observe(start.plusSeconds(180),true)).isEmpty();assertThat(p.incidentOpen()).isTrue();}
    @Test void failureResetsRecoveryVerificationWithoutCreatingAnotherIncident(){var p=policy();failThree(p);p.observe(start.plusSeconds(180),true);assertThat(p.observe(start.plusSeconds(240),false)).isEmpty();assertThat(p.observe(start.plusSeconds(300),true)).isEmpty();assertThat(p.observe(start.plusSeconds(360),true)).hasSize(1);}
    @Test void recoveryIsMeasuredFromConfirmedDetectionNotPreventiveReview(){var p=policy();failThree(p);p.observe(start.plusSeconds(180),true);var e=p.observe(start.plusSeconds(240),true).getFirst();assertThat(e.recoverySeconds()).isEqualTo(120);assertThat(e.confirmedAt()).isEqualTo(start.plusSeconds(120));assertThat(e.recoveryWithinThirtyMinutes()).isTrue();assertThat(p.incidentOpen()).isFalse();}
    @Test void exactThirtyMinuteRecoveryBoundaryIsIncluded(){var p=policy();failThree(p);p.observe(start.plusSeconds(1860),true);var e=p.observe(start.plusSeconds(1920),true).getFirst();assertThat(e.recoverySeconds()).isEqualTo(1800);assertThat(e.recoveryWithinThirtyMinutes()).isTrue();}
    @Test void laterRecoveryIsRecordedWithoutTurningItIntoSuccessAgainstTheObjective(){var p=policy();failThree(p);p.observe(start.plusSeconds(1861),true);var e=p.observe(start.plusSeconds(1921),true).getFirst();assertThat(e.recoveryWithinThirtyMinutes()).isFalse();assertThat(e.recoverySeconds()).isEqualTo(1801);}
    @Test void recoveryOneNanosecondBeyondThirtyMinutesIsNotRoundedIntoSuccess(){var p=policy();failThree(p);p.observe(start.plusSeconds(1860),true);assertThat(p.observe(start.plusSeconds(1920).plusNanos(1),true).getFirst().recoveryWithinThirtyMinutes()).isFalse();}
    @Test void clockRegressionOrDuplicateObservationIsRejectedWithoutResettingIncident(){var p=policy();failThree(p);assertThatThrownBy(()->p.observe(start.plusSeconds(119),true)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->p.observe(start.plusSeconds(120),true)).isInstanceOf(IllegalArgumentException.class);assertThat(p.incidentOpen()).isTrue();assertThat(p.observe(start.plusSeconds(180),true)).isEmpty();}
    @Test void invalidThresholdsCannotMakeOneResponseAConfirmedIncidentOrRecovery(){assertThatThrownBy(()->new AvailabilityPolicy(1,2,UUID::randomUUID)).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new AvailabilityPolicy(3,1,UUID::randomUUID)).isInstanceOf(IllegalArgumentException.class);}
}
