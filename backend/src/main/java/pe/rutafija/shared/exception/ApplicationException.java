package pe.rutafija.shared.exception;

import org.springframework.http.HttpStatus;

public class ApplicationException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = -4608782410356792063L;

    private final HttpStatus status;
    private final ErrorCode code;

    public ApplicationException(HttpStatus status, ErrorCode code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }
}
