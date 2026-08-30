package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class InvalidRefreshTokenException extends ApplicationException {

    public InvalidRefreshTokenException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_TOKEN_INVALID,
                "La sesión no es válida o ha expirado"
        );
    }
}
