package pe.rutafija.onboarding.application;

import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.Requirement;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Approved pilot schema; an unresolved external condition is never promoted to compliance. */
public final class ConductorDocumentaryCatalog {
    public static final String ID = "L01-CHICLAYO";
    public static final String VERSION = "0.1.0";
    public static final String CANONICAL = "L01-CHICLAYO|0.1.0|CON-01:DNI|CON-02:LICENSE:M1:A-IIA,A-IIB,A-IIIA,A-IIIB,A-IIIC|CON-03:AUTHORIZATION:CONDITIONAL|CON-04:EXTRA:UNKNOWN|CON-05:COLECTIVO:ELIGIBILITY|NO_FILES|VISUAL_REVIEW|RETENTION30D";
    public static final String HASH = hash(CANONICAL);
    public static final List<Requirement> REQUIREMENTS = List.of(
            new Requirement("CON-01", "Identidad del postulante", true,
                    List.of("documentType", "documentNumber", "fullName", "issuer")),
            new Requirement("CON-02", "Licencia compatible con el servicio M1", true,
                    List.of("licenseNumber", "licenseClass", "licenseCategory", "restrictions", "issuer")),
            new Requirement("CON-03", "Habilitacion personal aplicable", false,
                    List.of("titleNumber", "authority", "serviceScope", "operatorReference")),
            new Requirement("CON-04", "Otros requisitos personales aplicables", false,
                    List.of("requirementName", "minimumReference")),
            new Requirement("CON-05", "Condiciones personales de colectivo", false,
                    List.of("eligibilityResult", "reviewDate")));

    private ConductorDocumentaryCatalog() { }

    public static Requirement requirement(String id) {
        return REQUIREMENTS.stream().filter(item -> item.requirementId().equals(id)).findFirst().orElse(null);
    }

    public static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required", exception);
        }
    }
}
