package pe.rutafija.terms.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record AcceptAdminTermsRequest(
        @NotBlank
        @Size(max = 40)
        String version,

        @NotBlank
        @Pattern(regexp = "[0-9a-f]{64}")
        String contentSha256,

        @NotNull
        Boolean accepted
) {
}
