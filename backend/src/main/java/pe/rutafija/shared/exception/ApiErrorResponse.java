package pe.rutafija.shared.exception;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        List<FieldErrorDetail> errors
) {

    public static ApiErrorResponse of(
            int status,
            ErrorCode code,
            String message,
            String path,
            String correlationId
    ) {
        return new ApiErrorResponse(
                Instant.now(),
                status,
                code.name(),
                message,
                path,
                correlationId,
                List.of()
        );
    }
}
