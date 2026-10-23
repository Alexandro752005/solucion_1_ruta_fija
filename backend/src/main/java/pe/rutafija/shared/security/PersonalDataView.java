package pe.rutafija.shared.security;

import java.util.UUID;

/** Public reference for ordinary UI responses. Original PII belongs in a restricted dossier. */
public final class PersonalDataView {
    private PersonalDataView() { }

    public static String account(UUID id) { return "CUENTA-" + id; }
    public static String driver(UUID id) { return "CONDUCTOR-" + id; }
}
