package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

public class OnboardingOtpRejectedException extends ApplicationException {

    public OnboardingOtpRejectedException(boolean blocked) {
        super(
                blocked ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                blocked ? ErrorCode.EMAIL_OTP_LOCKED : ErrorCode.EMAIL_OTP_INVALID,
                blocked
                        ? "Se alcanzó el límite de validación. Inténtalo nuevamente después del bloqueo."
                        : "El código de verificación no es válido."
        );
    }
}
