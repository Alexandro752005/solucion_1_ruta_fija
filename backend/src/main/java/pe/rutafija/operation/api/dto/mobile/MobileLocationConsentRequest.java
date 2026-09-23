package pe.rutafija.operation.api.dto.mobile;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Consentimiento funcional del conductor para conservar una única ubicación vigente.")
public record MobileLocationConsentRequest(@NotNull Boolean consent) {
}
