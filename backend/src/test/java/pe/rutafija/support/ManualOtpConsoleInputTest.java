package pe.rutafija.support;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Fictitious values only; no database, HTTP, SMTP or real OTP. */
class ManualOtpConsoleInputTest {
    private final MutableClock clock = new MutableClock();
    private final Instant deadline = clock.instant().plusSeconds(300);

    @Test void acceptsSixFictitiousDigitsWithoutPrintingTheValue() {
        ArrayList<String> notices = new ArrayList<>();
        char[] input = "246810".toCharArray();
        assertThat(ManualOtpDeliveryValidation.readOtp(() -> input, deadline, clock, notices::add)).isSameAs(input);
        assertThat(notices).isEmpty();
    }

    @Test void allowsTwoEmptyLocalReadsThenOneValueWithoutMovingDeadline() {
        ArrayDeque<char[]> inputs = new ArrayDeque<>();
        inputs.add(new char[0]); inputs.add(new char[0]); inputs.add("246810".toCharArray());
        AtomicInteger reads = new AtomicInteger();
        ArrayList<String> notices = new ArrayList<>();
        char[] result = ManualOtpDeliveryValidation.readOtp(() -> {
            reads.incrementAndGet(); clock.now = clock.now.plusSeconds(20); return inputs.remove();
        }, deadline, clock, notices::add);
        assertThat(result).containsExactly('2','4','6','8','1','0');
        assertThat(reads).hasValue(3);
        assertThat(notices).hasSize(2).allMatch(t -> !t.contains("246810"));
        assertThat(deadline).isEqualTo(Instant.parse("2026-10-16T12:05:00Z"));
    }

    @Test void stopsAfterThreeEmptyReads() {
        AtomicInteger reads = new AtomicInteger();
        assertThatThrownBy(() -> ManualOtpDeliveryValidation.readOtp(() -> { reads.incrementAndGet(); return new char[0]; },
                deadline, clock, ignored -> { })).isInstanceOf(RuntimeException.class);
        assertThat(reads).hasValue(3);
    }

    @Test void rejectsMalformedNonEmptyValueWithoutLocalRetryAndErasesIt() {
        for (String malformed : new String[]{"12345", "1234567", "12a456", "１２３４５６", "      "}) {
            char[] value = malformed.toCharArray();
            AtomicInteger reads = new AtomicInteger();
            assertThatThrownBy(() -> ManualOtpDeliveryValidation.readOtp(() -> { reads.incrementAndGet(); return value; },
                    deadline, clock, ignored -> { })).isInstanceOf(RuntimeException.class);
            assertThat(reads).hasValue(1);
            assertThat(value).containsOnly('\0');
        }
    }

    @Test void rejectsCancellationWithoutRetry() {
        AtomicInteger reads = new AtomicInteger();
        assertThatThrownBy(() -> ManualOtpDeliveryValidation.readOtp(() -> { reads.incrementAndGet(); return null; },
                deadline, clock, ignored -> { })).isInstanceOf(RuntimeException.class);
        assertThat(reads).hasValue(1);
    }

    @Test void expiryAtTheExactBoundaryPreventsAnotherRead() {
        AtomicInteger reads = new AtomicInteger();
        Supplier<char[]> input = () -> { reads.incrementAndGet(); clock.now = deadline; return new char[0]; };
        assertThatThrownBy(() -> ManualOtpDeliveryValidation.readOtp(input, deadline, clock, ignored -> { }))
                .isInstanceOf(RuntimeException.class);
        assertThat(reads).hasValue(1);
    }

    @Test void valueEnteredAfterOriginalExpiryIsErasedAndRejected() {
        char[] value = "246810".toCharArray();
        assertThatThrownBy(() -> ManualOtpDeliveryValidation.readOtp(() -> { clock.now = deadline.plusNanos(1); return value; },
                deadline, clock, ignored -> { })).isInstanceOf(RuntimeException.class);
        assertThat(value).containsOnly('\0');
    }

    @Test void alreadyExpiredDoesNotReadAnyInput() {
        clock.now = deadline;
        AtomicInteger reads = new AtomicInteger();
        assertThatThrownBy(() -> ManualOtpDeliveryValidation.readOtp(() -> { reads.incrementAndGet(); return new char[0]; },
                deadline, clock, ignored -> { })).isInstanceOf(RuntimeException.class);
        assertThat(reads).hasValue(0);
    }

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-16T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
