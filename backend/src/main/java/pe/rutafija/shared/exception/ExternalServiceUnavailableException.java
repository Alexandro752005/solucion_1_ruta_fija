package pe.rutafija.shared.exception;

public class ExternalServiceUnavailableException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = -3410457561322729441L;

    private final ErrorCode code;
    private final String userMessage;

    public ExternalServiceUnavailableException(ErrorCode code, String userMessage) {
        super("External service unavailable");
        this.code = code;
        this.userMessage = userMessage;
    }

    public ErrorCode getCode() {
        return code;
    }

    public String getUserMessage() {
        return userMessage;
    }
}
