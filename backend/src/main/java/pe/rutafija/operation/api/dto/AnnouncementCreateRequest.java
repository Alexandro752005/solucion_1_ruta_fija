package pe.rutafija.operation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.rutafija.operation.domain.AnnouncementAudienceType;

import java.util.UUID;

public record AnnouncementCreateRequest(
        @NotBlank @Size(max = 160) String title,
        @NotBlank @Size(max = 5000) String body,
        @NotNull AnnouncementAudienceType audienceType,
        UUID audienceId,
        Boolean requireReadAck
) {
}
