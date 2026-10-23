package pe.rutafija.reporting.application;

import org.springframework.stereotype.Service;
import pe.rutafija.operation.application.*;
import pe.rutafija.reporting.api.ServiceReportContract.Emission;
import java.util.*;

@Service
public class ServiceReportExport {
    private final ReportExportService renderer;
    public ServiceReportExport(ReportExportService renderer){this.renderer=renderer;}
    public ReportExport render(Emission e,ReportExportFormat format){
        var v=e.report();var rows=new ArrayList<List<String>>();
        rows.add(List.of("RULE",v.ruleVersion()));rows.add(List.of("SCOPE",v.scope()));rows.add(List.of("PERIOD",v.from().toString(),v.to().toString(),v.zoneId()));
        rows.add(List.of("TOTAL_SCHEDULED",Long.toString(v.totals().scheduled())));rows.add(List.of("TOTAL_COMPLETED",Long.toString(v.totals().completed())));
        rows.add(List.of("TOTAL_CANCELLED",Long.toString(v.totals().cancelled())));rows.add(List.of("TOTAL_INTERRUPTED",Long.toString(v.totals().interrupted())));
        rows.add(List.of("TOTAL_PENDING",Long.toString(v.totals().pending())));rows.add(List.of("TOTAL_TRANSFERRED",Long.toString(v.totals().transferred())));
        rows.add(List.of("COMPLETION_PERCENT",v.totals().completionPercent()==null?"NO_DENOMINATOR":v.totals().completionPercent().toPlainString()));
        rows.add(List.of("EXCLUDED_LEGACY",Long.toString(v.coverage().excludedLegacyOffers())));rows.add(List.of("WITHOUT_FIRST_OFFER",Long.toString(v.coverage().canonicalServicesWithoutFirstOffer())));
        for(var i:v.items())rows.add(List.of(i.serviceId().toString(),Long.toString(i.serviceVersion()),i.status(),text(i.cohortScheduledAt()),text(i.currentScheduledAt()),Boolean.toString(i.validCompletion()),text(i.originalStartedAt()),text(i.originalEndedAt()),text(i.effectiveStartedAt()),text(i.effectiveEndedAt()),text(i.completionRecordedAt()),Boolean.toString(i.declaredClosed()),i.terminalResult(),text(i.correctionIds()),text(i.driverIds()),text(i.vehicleIds()),text(i.groupIds())));
        return renderer.tabular(format,"Servicios - "+e.id()+" - v"+e.revision(),"Corte "+e.issuedAt()+" | SHA256 "+e.contentSha256(),
            List.of("Servicio","Version","Estado","Cohorte","Programacion vigente","Completado valido","Inicio original","Fin original","Inicio efectivo","Fin efectivo","Cierre registrado","Cierre declarado","Terminal","Correcciones","Conductores","Vehiculos","Grupos"),rows,"servicios-"+e.id()+"-v"+e.revision());
    }
    private static String text(Object value){return value==null?"":value.toString();}
}
