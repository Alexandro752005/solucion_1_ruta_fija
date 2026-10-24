package pe.rutafija.operation.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Q-053: the server alone determines the acceptance window. */
public final class OfferResponseWindow {
    private static final Duration DURATION = Duration.ofMinutes(10);

    private OfferResponseWindow() { }

    public static Instant deadline(Instant emittedAt, Instant scheduledStart) {
        Objects.requireNonNull(emittedAt);
        Objects.requireNonNull(scheduledStart);
        if (!scheduledStart.isAfter(emittedAt)) {
            throw new IllegalArgumentException("La oferta necesita un inicio futuro y un plazo de aceptación vigente.");
        }
        Instant maximum = emittedAt.plus(DURATION);
        return scheduledStart.isBefore(maximum) ? scheduledStart : maximum;
    }
}
