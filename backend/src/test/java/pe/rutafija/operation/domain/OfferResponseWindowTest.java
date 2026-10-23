package pe.rutafija.operation.domain;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OfferResponseWindowTest {
    private final Instant now = Instant.parse("2026-10-19T20:00:00Z");

    @Test void capsTheDeadlineAtTenMinutes() {
        assertThat(OfferResponseWindow.deadline(now,now.plusSeconds(7200))).isEqualTo(now.plusSeconds(600));
    }

    @Test void usesTheExactEarlierStartIncludingSubsecondPrecision() {
        Instant start=now.plusNanos(1_234_000);
        assertThat(OfferResponseWindow.deadline(now,start)).isEqualTo(start);
    }

    @Test void equalTenMinuteStartHasNoArtificialGap() {
        assertThat(OfferResponseWindow.deadline(now,now.plusSeconds(600))).isEqualTo(now.plusSeconds(600));
    }

    @Test void currentOrPastStartCannotProduceALiveOffer() {
        assertThatIllegalArgumentException().isThrownBy(()->OfferResponseWindow.deadline(now,now));
        assertThatIllegalArgumentException().isThrownBy(()->OfferResponseWindow.deadline(now,now.minusNanos(1)));
    }
}
