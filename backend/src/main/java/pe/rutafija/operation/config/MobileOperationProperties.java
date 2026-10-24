package pe.rutafija.operation.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Retention and clock-tolerance limits applied by the server, never by clients. */
@Validated
@ConfigurationProperties(prefix = "app.mobile")
public record MobileOperationProperties(
        @NotNull @DurationMin(seconds = 30) Duration locationTtl,
        @NotNull @DurationMin(seconds = 30) Duration locationCaptureMaxAge,
        @NotNull @DurationMin(seconds = 15) Duration pendingExpirySweep
) {
    public MobileOperationProperties {
        if((locationTtl!=null&&locationTtl.compareTo(Duration.ofDays(7))>0)
                ||(locationCaptureMaxAge!=null&&locationCaptureMaxAge.compareTo(Duration.ofDays(7))>=0))
            throw new IllegalArgumentException("GPS current TTL and capture tolerance must remain within the seven-day maximum.");
    }
}
