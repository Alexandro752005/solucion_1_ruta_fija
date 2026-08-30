package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public final class InvalidCredentialsException extends ApplicationException {

    public InvalidCredentialsException() {
        super(
                HttpStatus.UNAUTHORIZED,
                ErrorCode.AUTH_INVALID_CREDENTIALS,
                "El correo o la contraseña no son válidos"
        );
    }
}
