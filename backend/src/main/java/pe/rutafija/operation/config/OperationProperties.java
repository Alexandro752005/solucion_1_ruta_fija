package pe.rutafija.operation.config;

import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "app.operations")
public record OperationProperties(@NotNull @DurationMin(seconds = 30) Duration streamTicketTtl) {
}
