package pe.rutafija.identity.application;

public record MobileLoginResult(MobileIssuedSession session, EmailOtpTicket verification) {

    public boolean requiresEmailVerification() {
        return verification != null;
    }
}
