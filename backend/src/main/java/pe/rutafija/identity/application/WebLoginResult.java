package pe.rutafija.identity.application;

public record WebLoginResult(IssuedSession session, EmailOtpTicket verification) {

    public boolean requiresEmailVerification() {
        return verification != null;
    }
}
