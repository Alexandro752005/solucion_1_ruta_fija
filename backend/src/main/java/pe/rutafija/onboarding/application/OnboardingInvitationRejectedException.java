package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

public class OnboardingInvitationRejectedException extends ApplicationException {
    @java.io.Serial
    private static final long serialVersionUID = -9063495426057973732L;

    public OnboardingInvitationRejectedException(boolean limited) {
        super(
                limited ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                limited ? ErrorCode.INVITATION_VALIDATION_LIMITED : ErrorCode.AUTH_INVALID_CREDENTIALS,
                limited
                        ? "La validación de invitaciones está pausada temporalmente."
                        : "El código o el destinatario no coincide."
        );
    }
}
