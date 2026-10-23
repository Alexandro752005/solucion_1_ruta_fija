package pe.rutafija.qualification.application;

import pe.rutafija.qualification.api.QualificationContract.Requirement;
import java.util.List;

/** Approved internal review schema. A real local title must still be contrasted. */
public final class QualificationCatalog {
    private QualificationCatalog() { }
    public static final String ID="L02-CHICLAYO", VERSION="0.2.0", HASH="e53a74f0cd2526d229bb5c49508fe3a274ff070705701c771f6528c773ea8a42";
    public static final List<Requirement> REQUIREMENTS=List.of(
        new Requirement("VEHICLE","VEH-01","Identidad y categoría documentadas",true,List.of("reference","plate","category","bodyType","brand","model","year")),
        new Requirement("VEHICLE","VEH-02","SOAT o CAT compatible",true,List.of("insuranceType","issuer","reference","plate","serviceScope")),
        new Requirement("VEHICLE","VEH-03","Inspección técnica aplicable",false,List.of("reference","center","plate","inspectionType","inspectionResult")),
        new Requirement("VEHICLE","VEH-04","Título de habilitación compatible",false,List.of("titleName","titleNumber","authority","holderReference","plate","modality","serviceScope")),
        new Requirement("COMPANY","EMP-01","Identidad y título de la organización",true,List.of("operatorReference","titleNumber","authority","modality","serviceScope")),
        new Requirement("COMPANY","EMP-02","Condiciones del servicio taxi",false,List.of("variant","titleReference","fleetScope","serviceScope")),
        new Requirement("COMPANY","EMP-03","Título y ruta de colectivo",false,List.of("titleReference","authority","routeReference","serviceScope","unitsScope")));
    public static Requirement requirement(String id) { return REQUIREMENTS.stream().filter(r->r.id().equals(id)).findFirst().orElse(null); }
}
