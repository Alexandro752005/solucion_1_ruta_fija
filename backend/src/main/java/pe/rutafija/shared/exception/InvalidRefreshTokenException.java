package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class InvalidRefreshTokenException extends ApplicationException {
    @java.io.Serial
    private static final long serialVersionUID = -5081090206533403630L;

    public InvalidRefreshTokenException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_TOKEN_INVALID,
                "La sesión no es válida o ha expirado"
        );
    }
}
