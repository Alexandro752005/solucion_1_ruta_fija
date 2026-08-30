package pe.rutafija.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "app.security")
public record SecurityProperties(
        @Valid @NotNull Jwt jwt,
        @Valid @NotNull RefreshCookie refreshCookie,
        @Valid @NotNull Cors cors,
        @Valid @NotNull LoginRateLimit loginRateLimit
) {

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secretBase64,
            @NotNull Duration accessTtl,
            @NotNull Duration refreshTtl
    ) {
    }

    public record RefreshCookie(
            @NotBlank String name,
            @NotBlank String path,
            @NotBlank String sameSite,
            boolean secure
    ) {
    }

    public record Cors(@NotEmpty List<@NotBlank String> allowedOrigins) {
    }

    public record LoginRateLimit(
            boolean enabled,
            @Positive int accountMaxAttempts,
            @NotNull Duration accountWindow,
            @Positive int globalMaxAttempts,
            @NotNull Duration globalWindow,
            @Positive int maxTrackedAccounts
    ) {
    }
}
