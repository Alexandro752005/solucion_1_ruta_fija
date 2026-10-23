package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class RefreshTokenReuseException extends ApplicationException {

    public RefreshTokenReuseException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_REFRESH_REUSE_DETECTED,
                "La sesión ya no es válida; inicie sesión nuevamente"
        );
    }
}
