package pe.rutafija.qualification.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.rutafija.onboarding.application.ConductorDocumentaryCatalog;
import pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository;
import pe.rutafija.qualification.infrastructure.QualificationRepository;
import pe.rutafija.shared.exception.*;
import java.time.*;
import java.util.*;

/** One server-side policy for activation, scheduling, reservation and driving. */
@Service
public class OperationalQualificationPolicy {
    private final JdbcTemplate jdbc; private final QualificationRepository repository;
    private final ConductorDocumentaryRepository documents; private final Clock clock;
    private final boolean operationsEnabled;
    public OperationalQualificationPolicy(JdbcTemplate jdbc,QualificationRepository repository,ConductorDocumentaryRepository documents,Clock clock,
        @org.springframework.beans.factory.annotation.Value("${app.qualification.operations-enabled:true}") boolean operationsEnabled){this.jdbc=jdbc;this.repository=repository;this.documents=documents;this.clock=clock;this.operationsEnabled=operationsEnabled;}
    public UUID request(UUID driver,UUID org){var ids=jdbc.query("""
        select r.id from conductor_onboarding_request r join driver d on d.id=r.linked_driver_id
        and d.organization_id=r.organization_id and d.user_id=r.linked_user_id
        where r.linked_driver_id=? and r.organization_id=? and r.status='APPROVED'
        and r.personal_approved_at is not null and r.provisioned_at is not null
        """,(r,n)->r.getObject(1,UUID.class),driver,org);return ids.size()==1?ids.getFirst():null;}
    public String modality(UUID driver,UUID org){var id=request(driver,org);if(id==null)return null;return documents.find(id,org,null).map(s->s.modality()).orElse(null);}
    public List<String> personalBlockers(UUID driver,UUID org,Instant end){
        var out=new ArrayList<String>(); var id=request(driver,org);
        if(id==null){out.add("PERSONAL_APPROVAL_REQUIRED");return out;}
        var state=documents.find(id,org,null).orElseThrow();
        if(!ConductorDocumentaryCatalog.HASH.equals(state.catalogHash()))out.add("PERSONAL_CATALOG_REVIEW_REQUIRED");
        var docs=documents.documents(id);
        for(var req:ConductorDocumentaryCatalog.REQUIREMENTS){
            var doc=docs.stream().filter(d->d.requirementId().equals(req.requirementId())).findFirst().orElse(null);
            if(doc==null||!"CONFIRMED".equals(doc.applicability())||blank(doc.sourceReference())||blank(doc.reviewReference())
                ||!Set.of("CONFORME","NO_APLICA_JUSTIFICADO").contains(doc.result())){out.add(req.requirementId()+"_REVIEW_REQUIRED");continue;}
            if("NO_APLICA_JUSTIFICADO".equals(doc.result())){
                if(req.mandatory()||("CON-05".equals(req.requirementId())&&"COLECTIVO".equals(state.modality()))||blank(doc.condition())||blank(doc.observation()))out.add(req.requirementId()+"_APPLICABILITY_REQUIRED");
            }else if("UNKNOWN".equals(doc.expiryMode())||("DATED".equals(doc.expiryMode())&&(doc.validUntil()==null||doc.validUntil().isBefore(end)||!doc.validUntil().isAfter(Instant.now(clock)))))out.add(req.requirementId()+"_VALIDITY_REQUIRED");
        }
        return out;
    }
    public Instant personalValidUntil(UUID driver,UUID org){var id=request(driver,org);if(id==null)return null;return documents.documents(id).stream().filter(d->"CONFORME".equals(d.result())&&"DATED".equals(d.expiryMode())).map(d->d.validUntil()).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);}
    public List<String> reviewBlockers(UUID driver,UUID org,UUID vehicle,Instant end){
        var out=new ArrayList<String>();var reviews=repository.reviews(driver);String modality=modality(driver,org);
        for(var req:QualificationCatalog.REQUIREMENTS){
            var doc=reviews.stream().filter(d->d.scope().equals(req.scope())&&d.requirementId().equals(req.id())&&Objects.equals(d.vehicleId(),"VEHICLE".equals(req.scope())?vehicle:null)).findFirst().orElse(null);
            if(doc==null||!"CONFIRMED".equals(doc.applicability())||blank(doc.sourceReference())||blank(doc.reviewReference())){out.add(req.id()+"_REVIEW_REQUIRED");continue;}
            if("VEHICLE".equals(req.scope())&&doc.fields().containsKey("plate")){
                var current=jdbc.queryForList("select plate,brand,model,year from vehicle where id=? and organization_id=?",vehicle,org);
                if(current.size()!=1||!String.valueOf(current.getFirst().get("plate")).equalsIgnoreCase(doc.fields().get("plate")))out.add(req.id()+"_UNIT_CHANGED_REVIEW_REQUIRED");
                if("VEH-01".equals(req.id())&&current.size()==1){
                    for(String field:List.of("brand","model","year"))if(current.getFirst().get(field)!=null&&!String.valueOf(current.getFirst().get(field)).equalsIgnoreCase(doc.fields().getOrDefault(field,"")))out.add(req.id()+"_UNIT_CHANGED_REVIEW_REQUIRED");
                }
            }
            boolean modeRequired=("EMP-02".equals(req.id())&&"TAXI".equals(modality))||("EMP-03".equals(req.id())&&"COLECTIVO".equals(modality));
            if("NO_APLICA_JUSTIFICADO".equals(doc.result())){
                if(req.mandatory()||modeRequired||blank(doc.condition()))out.add(req.id()+"_APPLICABILITY_REQUIRED");continue;
            }
            if(!"CONFORME".equals(doc.result())||"UNKNOWN".equals(doc.expiryMode())||("DATED".equals(doc.expiryMode())&&(doc.validUntil()==null||doc.validUntil().isBefore(end)||!doc.validUntil().isAfter(Instant.now(clock)))))out.add(req.id()+"_VALIDITY_REQUIRED");
        }
        return out;
    }
    public List<String> blockers(UUID driver,UUID org,UUID vehicle,Instant start,Instant end,boolean requireActive){
        List<String> out=new ArrayList<>(personalBlockers(driver,org,end));
        if(!operationsEnabled)out.add("OPERATIONAL_FEATURE_DISABLED");
        Boolean base=jdbc.queryForObject("""
            select exists(select 1 from driver d join app_user u on u.id=d.user_id
             join organization o on o.id=d.organization_id join person_identity p on p.user_id=u.id
             where d.id=? and d.organization_id=? and u.organization_id=d.organization_id and u.role='CONDUCTOR'
              and u.active and u.email_verified_at is not null and o.status='ACTIVE' and (?=false or d.active)
              and exists(select 1 from group_driver_membership m join transport_group g on g.id=m.group_id and g.organization_id=m.organization_id
                 where m.driver_id=d.id and m.organization_id=d.organization_id and m.active and g.active))
            """,Boolean.class,driver,org,requireActive);
        if(!Boolean.TRUE.equals(base))out.add("ACCOUNT_IDENTITY_GROUP_OR_DRIVER_REQUIRED");
        if(repository.suspended(driver))out.add("OPERATIONAL_SUSPENSION");
        if(safetyDriverBlocks(driver,org))out.add("DRIVER_SAFETY_ATTENTION");
        if(vehicle!=null&&maintenanceBlocks(vehicle,org,start,end))out.add("VEHICLE_MAINTENANCE_BLOCK");
        var periods=repository.periods(driver).stream().filter(p->"AUTHORIZED".equals(p.status())&&!p.startsAt().isAfter(start)&&(p.endsAt()==null||!p.endsAt().isBefore(end))&&(vehicle==null||p.vehicleId().equals(vehicle))).toList();
        if(periods.size()!=1)out.add("AUTHORIZED_UNIT_PERIOD_REQUIRED");
        else {
            UUID selected=periods.getFirst().vehicleId();
            if(safetyVehicleBlocks(selected,org))out.add("VEHICLE_SAFETY_ATTENTION");
            if(vehicle==null&&maintenanceBlocks(selected,org,start,end))out.add("VEHICLE_MAINTENANCE_BLOCK");
            if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from vehicle where id=? and organization_id=? and active and status not in('MANTENIMIENTO','INACTIVO','ATENCION'))",Boolean.class,selected,org)))out.add("VEHICLE_ACTIVE_REQUIRED");
            if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from assignment where vehicle_id=? and organization_id=? and status='EN_SERVICIO' and driver_id<>?)",Boolean.class,selected,org,driver)))out.add("UNIT_OCCUPIED_BY_OTHER_DRIVER");
            out.addAll(reviewBlockers(driver,org,selected,end));
        }
        return List.copyOf(out);
    }
    public boolean eligible(UUID driver,UUID org,boolean requireActive){Instant now=Instant.now(clock);return blockers(driver,org,null,now,now.plusNanos(1000),requireActive).isEmpty();}
    public boolean safetyDriverBlocks(UUID driver,UUID org){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_attention h where h.driver_id=? and h.organization_id=? and not exists(select 1 from service_attention_release r where r.attention_id=h.id and r.resource_kind='DRIVER'))",Boolean.class,driver,org));}
    public boolean safetyVehicleBlocks(UUID vehicle,UUID org){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from service_attention h where h.vehicle_id=? and h.organization_id=? and not exists(select 1 from service_attention_release r where r.attention_id=h.id and r.resource_kind='VEHICLE' and r.disposition='READY'))",Boolean.class,vehicle,org));}
    public boolean maintenanceBlocks(UUID vehicle,UUID org,Instant start,Instant end){return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from vehicle_maintenance where vehicle_id=? and organization_id=? and status<>'CANCELLED' and effective_window && tstzrange(?::timestamptz,?::timestamptz,'[)'))",Boolean.class,vehicle,org,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end)));}
    public void require(UUID driver,UUID org,UUID vehicle,Instant start,Instant end){
        if(start==null||end==null||!end.isAfter(start))throw conflict("El servicio requiere un intervalo de conducción definido.");
        var out=blockers(driver,org,vehicle,start,end,true);if(!out.isEmpty())throw conflict("Habilitación operativa pendiente: "+String.join(",",out));
    }
    public void requireStart(UUID driver,UUID org,UUID vehicle,Instant scheduledStart,Instant scheduledEnd){
        Instant actual=Instant.now(clock);
        if(scheduledEnd==null||!scheduledEnd.isAfter(actual))throw conflict("El fin previsto ya pasó; se requiere una programación vigente antes de iniciar.");
        require(driver,org,vehicle,actual.isBefore(scheduledStart)?actual:scheduledStart,scheduledEnd);
    }
    public void requireActivation(UUID driver,UUID org){if(!eligible(driver,org,false))throw conflict("La aprobación personal no sustituye vigencias, revisión y vínculo autorizado.");}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
}
