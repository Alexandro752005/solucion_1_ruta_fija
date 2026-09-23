package pe.rutafija.operation.api.dto.mobile;

import jakarta.validation.constraints.NotNull;

public record MobileLocationConsentRequest(@NotNull Boolean consent) {
}
