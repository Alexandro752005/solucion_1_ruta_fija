package pe.rutafija.support;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Correspondence policy only, fictitious IDs/times; no database, SMTP or HTTP. */
class ManualOtpEvidenceVerificationTest {
    private final UUID run=UUID.randomUUID();
    private final Instant issued=Instant.parse("2026-10-17T05:00:00Z");
    private final Instant consumed=issued.plusSeconds(60);

    @Test void acceptsMatchingAttemptPendingRequestAtomicTimesAndCleanup() {
        assertThatCode(() -> verify(state(false,0), stored("manual-otp-"+run,true,consumed), consumed.plusMillis(40),1))
                .doesNotThrowAnyException();
    }
    @Test void rejectsAnotherRunEvenWhenTheCaseIsCorrect() {
        assertThatThrownBy(() -> verify(state(false,0), stored("manual-otp-"+UUID.randomUUID(),true,consumed),consumed,1))
                .isInstanceOfSatisfying(ManualOtpEvidenceVerification.VerificationFailure.class,
                        failure -> org.assertj.core.api.Assertions.assertThat(failure.code).isEqualTo("ATTEMPT_IDENTIFIERS_DO_NOT_MATCH"));
    }
    @Test void rejectsAStateBeyondPendingUnlinkedReview() {
        assertThatThrownBy(() -> verify(state(false,0), stored("manual-otp-"+run,false,consumed),consumed,1))
                .isInstanceOf(ManualOtpEvidenceVerification.VerificationFailure.class);
    }
    @Test void rejectsConsumptionAtOrBeyondTheFiveMinuteBoundary() {
        for (Instant outside : new Instant[]{issued.minusNanos(1),issued.plusSeconds(300),issued.plusSeconds(301)})
            assertThatThrownBy(() -> verify(state(false,0),stored("manual-otp-"+run,true,outside),outside.plusSeconds(1),1))
                    .isInstanceOf(ManualOtpEvidenceVerification.VerificationFailure.class);
    }
    @Test void doesNotTreatTheObservationAsTheStoredTime() {
        assertThatCode(() -> verify(state(false,0),stored("manual-otp-"+run,true,consumed),consumed.plusNanos(900),1))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> verify(state(false,0),stored("manual-otp-"+run,true,consumed),consumed.minusNanos(1),1))
                .isInstanceOf(ManualOtpEvidenceVerification.VerificationFailure.class);
    }
    @Test void refusesIncompleteCleanupOrRemainingAccess() {
        assertThatThrownBy(() -> verify(state(true,0),stored("manual-otp-"+run,true,consumed),consumed,1))
                .isInstanceOf(ManualOtpEvidenceVerification.VerificationFailure.class);
        assertThatThrownBy(() -> verify(state(false,1),stored("manual-otp-"+run,true,consumed),consumed,1))
                .isInstanceOf(ManualOtpEvidenceVerification.VerificationFailure.class);
    }
    @Test void requiresTheEarlierRevokedInvitationToRemain() {
        assertThatThrownBy(() -> verify(state(false,0),stored("manual-otp-"+run,true,consumed),consumed,0))
                .isInstanceOf(ManualOtpEvidenceVerification.VerificationFailure.class);
    }

    private void verify(ManualOtpCaseRecovery.Snapshot state, ManualOtpEvidenceVerification.StoredConsumption stored,
                        Instant observation,int earlier) {
        ManualOtpEvidenceVerification.validate(state,stored,run,observation,earlier);
    }
    private ManualOtpEvidenceVerification.StoredConsumption stored(String key,boolean pending,Instant at) {
        return new ManualOtpEvidenceVerification.StoredConsumption(UUID.randomUUID(),key,key,pending,true,issued,at,at,at,at);
    }
    private ManualOtpCaseRecovery.Snapshot state(boolean active,int accesses) {
        return new ManualOtpCaseRecovery.Snapshot(UUID.randomUUID(),
                new ManualOtpCaseRecovery.Root(UUID.randomUUID(),UUID.randomUUID(),active?"ACTIVE":"INACTIVE",false),
                new ManualOtpCaseRecovery.Challenge(UUID.randomUUID(),UUID.randomUUID(),issued,null,null,0,null,
                        false,false,true,true,false,true,true),
                consumed.plusSeconds(1),0,1,1,1,0,accesses,0,0);
    }
}
