package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class InvalidCredentialsException extends ApplicationException {
    @java.io.Serial
    private static final long serialVersionUID = -6073554907892776511L;

    public InvalidCredentialsException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                "El correo o la contraseña no son válidos"
        );
    }
}
