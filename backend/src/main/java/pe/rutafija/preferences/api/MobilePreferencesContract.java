package pe.rutafija.preferences.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class MobilePreferencesContract {
    private MobilePreferencesContract() { }
    public enum TextSize { NORMAL, LARGE, EXTRA_LARGE }
    public record View(long version, boolean optionalNoticeAlerts, TextSize textSize,
                       boolean mandatoryNoticesAlwaysVisible) { }
    public record Update(@NotNull @Min(0) Long version, @NotNull Boolean optionalNoticeAlerts,
                         @NotNull TextSize textSize) { }
}
