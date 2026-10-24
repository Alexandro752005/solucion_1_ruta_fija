package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class RefreshTokenReuseException extends ApplicationException {
    @java.io.Serial
    private static final long serialVersionUID = -8429902800360181922L;

    public RefreshTokenReuseException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_REFRESH_REUSE_DETECTED,
                "La sesión ya no es válida; inicie sesión nuevamente"
        );
    }
}
