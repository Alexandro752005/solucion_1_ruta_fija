package pe.rutafija.identity.application;

import org.springframework.http.HttpStatus;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

public final class RecoveryProofRejectedException extends ApplicationException {
    @java.io.Serial
    private static final long serialVersionUID = -6822411992764496672L;

    public RecoveryProofRejectedException() {
        super(
                HttpStatus.BAD_REQUEST,
                ErrorCode.SUPER_ADMIN_RECOVERY_INVALID,
                "La solicitud de recuperación no es válida"
        );
    }
}
