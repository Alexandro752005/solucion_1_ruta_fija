package pe.rutafija.support;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualOtpCaseRecoveryTest {
    private final Instant now = Instant.parse("2026-10-16T18:00:00Z");

    @Test void sameCaseDoesNotNeedANewHistoricalAdminSlot() {
        var state = snapshot(4, 0, 0, now.minusSeconds(5), null, null);
        state.requireContinuable();
        assertThat(state.recentSends()).isEqualTo(1);
    }

    @Test void threeRecentSendsBlockContinuationWithoutClearingThem() {
        var state = snapshot(1, 0, 0, now.minusSeconds(20), now.minusSeconds(10), now);
        assertThatThrownBy(state::requireContinuable).isInstanceOfSatisfying(ManualOtpCaseRecovery.Refused.class,
                failure -> assertThat(failure.code).isEqualTo("SEND_WINDOW_EXHAUSTED_WAIT_NO_RESET"));
        assertThat(state.challenge().sends()).hasSize(3);
    }

    @Test void exactSixtyMinuteBoundaryUsesTheSameStrictCutoffAsTheBackend() {
        var state = snapshot(1, 0, 0, now.minusSeconds(3600), now.minusSeconds(3599), now);
        state.requireContinuable();
        assertThat(state.recentSends()).isEqualTo(2);
    }

    @Test void unrelatedResourcesOrARequestRefuseTheCase() {
        assertThatThrownBy(snapshot(1, 1, 0, now, null, null)::requireContinuable)
                .isInstanceOf(ManualOtpCaseRecovery.Refused.class);
        assertThatThrownBy(snapshot(1, 0, 1, now, null, null)::requireContinuable)
                .isInstanceOf(ManualOtpCaseRecovery.Refused.class);
    }

    private ManualOtpCaseRecovery.Snapshot snapshot(int adminCreations, int unrelated, int requests,
                                                   Instant send1, Instant send2, Instant send3) {
        return new ManualOtpCaseRecovery.Snapshot(UUID.randomUUID(),
                new ManualOtpCaseRecovery.Root(UUID.randomUUID(), UUID.randomUUID(), "INACTIVE", false),
                new ManualOtpCaseRecovery.Challenge(UUID.randomUUID(), UUID.randomUUID(), send1, send2, send3,
                        0, null, false, false, false, false, true, false, true),
                now, unrelated, adminCreations, 1, requests, 0, 0, 0, 0);
    }
}
