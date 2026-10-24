package pe.rutafija.qualification.application;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.*;
import pe.rutafija.identity.domain.*;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.application.*;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.*;
import pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository;
import pe.rutafija.qualification.api.QualificationContract.*;
import pe.rutafija.qualification.infrastructure.QualificationRepository;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import static pe.rutafija.qualification.infrastructure.QualificationRepository.ts;

@Service
public class DriverQualificationService {
    private final QualificationRepository repository; private final OperationalQualificationPolicy policy;
    private final DriverRepository drivers;private final VehicleRepository vehicles;private final JdbcTemplate jdbc;
    private final ConductorDocumentaryRepository documentary;private final ConductorDocumentaryService documentaryService;
    private final CurrentUserService current;private final MobileDriverContextService mobile;private final AuditService audit;
    private final InvitationCodeHasher hasher;private final Clock clock;
    private final pe.rutafija.operation.application.OperationEventPublisher events;
    public DriverQualificationService(QualificationRepository repository,OperationalQualificationPolicy policy,DriverRepository drivers,
        VehicleRepository vehicles,JdbcTemplate jdbc,ConductorDocumentaryRepository documentary,ConductorDocumentaryService documentaryService,
        CurrentUserService current,MobileDriverContextService mobile,AuditService audit,InvitationCodeHasher hasher,Clock clock,pe.rutafija.operation.application.OperationEventPublisher events){
        this.repository=repository;this.policy=policy;this.drivers=drivers;this.vehicles=vehicles;this.jdbc=jdbc;
        this.documentary=documentary;this.documentaryService=documentaryService;this.current=current;this.mobile=mobile;this.audit=audit;this.hasher=hasher;this.clock=clock;
        this.events=events;
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Qualification status(UUID id){return view(owned(id,admin()),false);}
    @Transactional
    public Qualification expediente(UUID id){var actor=admin();var driver=owned(id,actor);audit.record(actor,"QUALIFICATION_EXPEDIENTE_VIEWED","DRIVER",id,Map.of());return view(driver,true);}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Qualification mobileStatus(){return view(mobile.requireMobileConsultation().driver(),false);}

    @Transactional
    public Qualification reviews(UUID id,ReviewCommand c){return mutate(id,admin(),c,"REVIEW",true,d->{
        if(!Set.of("VEHICLE","COMPANY").contains(c.scope())||("VEHICLE".equals(c.scope())!=(c.vehicleId()!=null)))throw invalid("Revisión y unidad no corresponden.");
        if(c.vehicleId()!=null)vehicle(c.vehicleId(),d.getOrganizationId());
        Set<String> seen=new HashSet<>();for(var value:c.documents()){
            var req=QualificationCatalog.requirement(value.requirementId());
            if(req==null||!req.scope().equals(c.scope())||!seen.add(req.id()))throw invalid("Requisito desconocido o duplicado.");
            validateReview(d,c.vehicleId(),req,value);
            repository.review(d.getId(),d.getOrganizationId(),c.vehicleId(),c.scope(),value,current.requireTenantActor().getId(),now());
        }
        if(!policy.eligible(d.getId(),d.getOrganizationId(),false))effects(d,"DOCUMENTARY_REVIEW_CHANGED");
    });}
    @Transactional
    public Qualification period(UUID id,PeriodCommand c){return mutate(id,admin(),c,"PERIOD_AUTHORIZED",false,d->{
        kind(c.kind());vehicle(c.vehicleId(),d.getOrganizationId());
        if((c.startAt()==null)==(c.startDate()==null)||c.endAt()!=null&&c.endDate()!=null)throw invalid("Indica fecha o instante sin duplicarlos.");
        Instant start=c.startAt()!=null?c.startAt():c.startDate().atStartOfDay(ZoneId.of("America/Lima")).toInstant();
        Instant end=c.endAt()!=null?c.endAt():c.endDate()==null?null:c.endDate().plusDays(1).atStartOfDay(ZoneId.of("America/Lima")).toInstant();
        if(end!=null&&!end.isAfter(start)||"COMPANY".equals(c.kind())&&end==null)throw invalid("La unidad empresarial requiere fin posterior al inicio.");
        Instant until=end==null?start.plusNanos(1000):end;
        if(!policy.personalBlockers(id,d.getOrganizationId(),until).isEmpty()||!policy.reviewBlockers(id,d.getOrganizationId(),c.vehicleId(),until).isEmpty())throw conflict("Revisa documentos, categoría y título durante el periodo solicitado.");
        if(repository.periods(id).stream().anyMatch(p->"AUTHORIZED".equals(p.status())&&overlap(start,end,p.startsAt(),p.endsAt())))throw conflict("El conductor ya tiene una unidad en ese periodo.");
        if(Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from driver_unit_period where vehicle_id=? and status='AUTHORIZED' and tstzrange(starts_at,ends_at,'[)') && tstzrange(?::timestamptz,?::timestamptz,'[)'))",Boolean.class,c.vehicleId(),ts(start),ts(end))))throw conflict("La unidad ya tiene un conductor en ese periodo.");
        repository.period(id,d.getOrganizationId(),c,start,end,current.requireTenantActor().getId(),now());
    });}
    @Transactional
    public Qualification revoke(UUID id,UUID period,Basic c){return mutate(id,admin(),c,"PERIOD_REVOKED:"+period,false,d->{
        var p=repository.periods(id).stream().filter(v->v.id().equals(period)).findFirst().orElseThrow(this::notFound);
        if(!"AUTHORIZED".equals(p.status()))throw conflict("El periodo ya fue revocado.");
        if(!p.startsAt().isAfter(now())&&Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from assignment where driver_id=? and vehicle_id=? and status='EN_SERVICIO')",Boolean.class,id,p.vehicleId())))throw conflict("El servicio en curso requiere relevo seguro antes de revocar el vínculo.");
        repository.revoke(id,period,current.requireTenantActor().getId(),now());effects(d,"UNIT_PERIOD_REVOKED");
    });}
    @Transactional
    public Qualification suspend(UUID id,Basic c){return mutate(id,admin(),c,"SUSPENDED",false,d->{
        safe(c.evidenceReference(),160);if(repository.suspended(id))throw conflict("La suspensión ya está registrada.");
        repository.suspend(id,true,c.evidenceReference());effects(d,"OPERATIONAL_SUSPENSION");
    });}
    @Transactional
    public Qualification resume(UUID id,Basic c){return mutate(id,admin(),c,"RESUMED",false,d->{
        if(!repository.suspended(id))throw conflict("El conductor no está suspendido.");
        repository.suspend(id,false,null);policy.requireActivation(id,d.getOrganizationId());
    });}
    @Transactional
    public Qualification activate(UUID id,Basic c){return mutate(id,admin(),c,"ACTIVATED",false,d->{policy.requireActivation(id,d.getOrganizationId());d.activate();drivers.flush();});}
    @Transactional
    public Qualification personal(UUID id,PersonalCommand c){return mutate(id,admin(),c,"PERSONAL_REVIEW",true,d->{
        if(!c.samePersonConfirmed()||!c.originalReviewed())throw invalid("Confirma identidad de la misma persona y revisión visual del original.");safe(c.reviewReference(),500);
        savePersonal(d,c,true);
    });}
    @Transactional
    public Qualification vehicleCorrection(UUID id,VehicleCorrection c){return mutate(id,admin(),c,"VEHICLE_MATERIAL_CORRECTION",true,d->{
        if(!c.originalReviewed())throw invalid("La corrección requiere revisión visual del original.");safe(c.reviewReference(),500);
        var v=vehicles.lockByIdAndOrganizationId(c.vehicleId(),d.getOrganizationId()).orElseThrow(this::notFound);
        if(repository.periods(id).stream().noneMatch(p->p.vehicleId().equals(v.getId())) && repository.requests(id).stream().noneMatch(r->v.getId().equals(r.vehicleId())))throw conflict("La unidad no corresponde al expediente.");
        String plate=c.plate().strip().toUpperCase(Locale.ROOT);
        if(v.getPlate().equalsIgnoreCase(plate))throw conflict("La placa ya corresponde al valor revisado.");
        if(vehicles.existsByOrganization_IdAndPlateIgnoreCase(d.getOrganizationId(),plate))throw conflict("La placa ya corresponde a otra unidad.");
        UUID plateRevision=UUID.randomUUID();var plateChange=pe.rutafija.audit.application.AuditChanges.masked("plate",v.getPlate(),plate);
        jdbc.update("insert into vehicle_plate_revision(id,vehicle_id,organization_id,previous_plate,corrected_plate,reviewed_by,review_reference,reason,recorded_at) values(?,?,?,?,?,?,?,?,?)",plateRevision,v.getId(),d.getOrganizationId(),v.getPlate(),plate,current.requireTenantActor().getId(),c.reviewReference(),c.reason(),ts(now()));
        v.update(plate,v.getBrand(),v.getModel(),v.getYear(),v.getColor());vehicles.flush();
        audit.record(current.requireTenantActor(),"VEHICLE_IDENTITY_CORRECTED","VEHICLE",v.getId(),Map.of("revisionId",plateRevision,"reasonReferenceId",plateRevision,"evidenceReference",plateRevision,"changes",List.of(plateChange)));
        effects(d,"VEHICLE_MATERIAL_CORRECTION");
    });}
    @Transactional
    public Qualification mobileDocuments(PersonalCommand c){var actor=mobile.requireMobileConsultation();return mutate(actor.driver().getId(),actor.user(),c,"PERSONAL_SUBMITTED",false,d->savePersonal(d,c,false));}
    @Transactional
    public Qualification unitRequest(UnitCommand c){var actor=mobile.requireMobileConsultation();return mutate(actor.driver().getId(),actor.user(),c,"UNIT_REQUESTED",false,d->{
        kind(c.kind());safe(c.reference(),160);contextSafe(d,c.reference());if(c.vehicleId()!=null)vehicle(c.vehicleId(),d.getOrganizationId());
        if(repository.requests(d.getId()).stream().anyMatch(r->"PENDING_REVIEW".equals(r.status())))throw conflict("Ya existe una solicitud de unidad pendiente.");
        repository.request(d.getId(),d.getOrganizationId(),c,now());
    });}
    @Transactional
    public Qualification unitDecision(UUID id,UUID request,UnitDecision c){return mutate(id,admin(),c,"UNIT_DECISION:"+request,false,d->{
        var value=repository.requests(id).stream().filter(r->r.id().equals(request)).findFirst().orElseThrow(this::notFound);
        if(!"PENDING_REVIEW".equals(value.status()))throw conflict("La solicitud ya fue revisada.");
        if(c.vehicleId()!=null)vehicle(c.vehicleId(),d.getOrganizationId());
        if(c.approved()){
            if(c.vehicleId()==null)throw invalid("Selecciona una unidad revisada.");vehicle(c.vehicleId(),d.getOrganizationId());
            if(!policy.reviewBlockers(id,d.getOrganizationId(),c.vehicleId(),now().plusNanos(1000)).isEmpty())throw conflict("La unidad o el servicio todavía requieren revisión.");
        }
        repository.decision(id,request,c,current.requireTenantActor().getId(),now());
    });}
    private void savePersonal(Driver d,PersonalCommand c,boolean reviewed){
        UUID request=policy.request(d.getId(),d.getOrganizationId());if(request==null)throw conflict("Se requiere aprobación personal provisionada.");
        var state=documentary.lock(request,d.getOrganizationId(),null).orElseThrow(this::notFound);
        // Reuse the approved L-01 validation; append a revision, never rewrite its approval.
        var values=documentaryService.validateQualificationDocuments(state,c.documents(),reviewed);
        for(var value:values)if("CON-01".equals(value.requirementId())){
            String number=value.fields().get("documentNumber");String type=value.fields().get("documentType");
            if(!"DNI".equals(type)||number==null||!number.matches("[0-9]{8}"))throw invalid("La identidad requiere DNI revisado.");
            String hash=hasher.hashOrigin("conductor-person:DNI:"+number);
            if(!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from person_identity where user_id=? and person_hash=?)",Boolean.class,d.getUser().getId(),hash)))throw conflict("La corrección no puede sustituir la identidad raíz de la cuenta.");
        }
        for(var value:values)documentary.appendDocument(state,value,reviewed?current.requireTenantActor().getId():null,reviewed?"ADMIN":"APPLICANT",now());
        documentary.touch(request,state.modality());
        if(reviewed){
            var docs=documentary.documents(request);var person=docs.stream().filter(v->"CON-01".equals(v.requirementId())&&"CONFORME".equals(v.result())).findFirst();
            var license=docs.stream().filter(v->"CON-02".equals(v.requirementId())&&"CONFORME".equals(v.result())).findFirst();
            if(person.isPresent()&&license.isPresent()){
                var p=person.get();d.getUser().updateDetails(d.getUser().getEmail(),p.fields().get("fullName"),d.getUser().getPhone(),UserRole.CONDUCTOR);d.update(d.getUser(),d.getGroup(),p.fields().get("fullName"),d.getPhone(),p.fields().get("documentType"),p.fields().get("documentNumber"),license.get().fields().get("licenseNumber"));drivers.flush();
            }
        }
        var refreshed=documentary.find(request,d.getOrganizationId(),null).orElseThrow();
        documentary.event(refreshed,reviewed?"ADMIN":"APPLICANT",current.requireTenantActor().getId(),c.operationKey(),ConductorDocumentaryCatalog.hash(repository.write(c)),reviewed?"POST_APPROVAL_DOCUMENTS_REVIEWED":"POST_APPROVAL_DOCUMENTS_SUBMITTED",c.reason(),now(),request);
        if(!policy.eligible(d.getId(),d.getOrganizationId(),false))effects(d,"PERSONAL_DOCUMENTS_CHANGED");
    }
    private Qualification mutate(UUID id,AppUser actor,Command c,String action,boolean full,Consumer<Driver> operation){
        jdbc.queryForObject("select id from organization where id=? for no key update",UUID.class,actor.getOrganizationId());
        UUID priorRequest=policy.request(id,actor.getOrganizationId());
        if(priorRequest!=null)documentary.lock(priorRequest,actor.getOrganizationId(),null).orElseThrow(this::notFound);
        Driver d=drivers.lockByIdAndOrganizationId(id,actor.getOrganizationId()).orElseThrow(this::notFound);
        if(actor.getRole()==UserRole.CONDUCTOR&&(d.getUser()==null||!d.getUser().getId().equals(actor.getId())))throw notFound();
        safe(c.reason(),500);contextSafe(d,c.reason());repository.lock(id,actor.getOrganizationId(),now());
        String hash=ConductorDocumentaryCatalog.hash(action+":"+repository.write(c));var prior=repository.prior(id,actor.getId(),c.operationKey());
        if(prior.isPresent()){if(!prior.get().equals(hash))throw conflict("La clave de operación ya tiene otro contenido.");return view(d,full);}
        if(repository.version(id)!=c.expectedVersion())throw conflict("El estado cambió; consulta su versión actual.");
        boolean beforeActive=d.isActive(),beforeSuspended=repository.suspended(id);long beforeVersion=repository.version(id);
        String beforeName=d.getFullName(),beforeLicense=d.getLicenseNumber();
        operation.accept(d);repository.increment(id,now());repository.event(id,d.getOrganizationId(),actor.getId(),c.operationKey(),hash,action,c.reason(),now());
        UUID operationId=jdbc.queryForObject("select id from qualification_operation where driver_id=? and actor_id=? and operation_key=?",UUID.class,id,actor.getId(),c.operationKey());
        var changes=pe.rutafija.audit.application.AuditChanges.combine(
            pe.rutafija.audit.application.AuditChanges.fields("active",beforeActive,d.isActive(),"suspended",beforeSuspended,repository.suspended(id),"version",beforeVersion,repository.version(id)),
            pe.rutafija.audit.application.AuditChanges.masked("fullName",beforeName,d.getFullName()),
            pe.rutafija.audit.application.AuditChanges.masked("licenseNumber",beforeLicense,d.getLicenseNumber()));
        audit.record(actor,"DRIVER_QUALIFICATION_CHANGED","DRIVER",id,Map.of("action",action,"version",repository.version(id),"operationId",operationId,"reasonReferenceId",operationId,"evidenceReference",operationId,"changes",changes));
        if("SUSPENDED".equals(action))events.publish(d.getOrganizationId(),d.getGroup().getId(),"driver.suspended",Map.of("driverId",id));
        return view(d,full);
    }
    private Qualification view(Driver d,boolean full){
        UUID id=d.getId(),org=d.getOrganizationId(),request=policy.request(id,org);Instant now=now();
        var blockers=policy.blockers(id,org,null,now,now.plusNanos(1000),true);var personal=policy.personalBlockers(id,org,now.plusNanos(1000));
        var reviews=repository.reviews(id).stream().map(r->full?r:new Review(r.scope(),r.requirementId(),r.vehicleId(),Map.of(),r.expiryMode(),r.validUntil(),r.applicability(),null,null,r.result(),null,r.version())).toList();
        return new Qualification(id,d.getUser()==null?null:d.getUser().getId(),request,repository.version(id),repository.suspended(id),blockers.isEmpty(),personal.isEmpty()?"CURRENT":"REVIEW_REQUIRED",policy.personalValidUntil(id,org),blockers,
            repository.periods(id),repository.requests(id),QualificationCatalog.REQUIREMENTS,reviews,full&&request!=null?documentary.documents(request):List.of(),now,repository.attention(id),
            request==null?List.of():documentary.documents(request).stream().map(v->new PersonalDocumentState(v.requirementId(),v.result(),v.applicability(),v.observation())).toList());
    }
    private void validateReview(Driver d,UUID vehicle,pe.rutafija.qualification.api.QualificationContract.Requirement req,ReviewInput value){
        if(value.fields()==null||value.fields().entrySet().stream().anyMatch(e->!req.fieldKeys().contains(e.getKey())||e.getValue()==null||e.getValue().length()>200))throw invalid("Solo campos estructurados del catálogo, sin archivos.");
        if(!Set.of("DATED","NO_EXPIRY","UNKNOWN").contains(value.expiryMode())||("DATED".equals(value.expiryMode())!=(value.validUntil()!=null))||!Set.of("CONFIRMED","POR_CONTRASTAR").contains(value.applicability())||!Set.of("CONFORME","OBSERVADO","NO_APLICA_JUSTIFICADO","POR_CONTRASTAR").contains(value.result()))throw invalid("Revisión o vigencia desconocida.");
        if(Set.of("CONFORME","NO_APLICA_JUSTIFICADO").contains(value.result())){
            if(!"CONFIRMED".equals(value.applicability())||blank(value.sourceReference())||blank(value.reviewReference()))throw conflict("Debe comprobarse la aplicabilidad y el original.");
            String mode=policy.modality(d.getId(),d.getOrganizationId());
            boolean required=req.mandatory()||("EMP-02".equals(req.id())&&"TAXI".equals(mode))||("EMP-03".equals(req.id())&&"COLECTIVO".equals(mode));
            if("NO_APLICA_JUSTIFICADO".equals(value.result())){if(required||blank(value.condition()))throw conflict("El requisito aplicable no puede omitirse.");return;}
            if("UNKNOWN".equals(value.expiryMode())||value.validUntil()!=null&&!value.validUntil().isAfter(now()))throw conflict("La vigencia debe estar comprobada y vigente.");
            if(req.fieldKeys().stream().anyMatch(k->blank(value.fields().get(k))))throw invalid("Faltan campos documentales.");
            if(value.fields().values().stream().anyMatch(v->Set.of("UNKNOWN","DESCONOCIDO","DESCONOCIDA","POR_CONTRASTAR").contains(v.strip().toUpperCase(Locale.ROOT))))throw conflict("Un dato desconocido no puede registrarse como conforme.");
            if(Set.of("VEH-02","VEH-03").contains(req.id())&&!"DATED".equals(value.expiryMode()))throw conflict("El seguro e inspección aplicables requieren fin comprobado.");
            if("VEH-02".equals(req.id())){
                String type=value.fields().get("insuranceType").toUpperCase(Locale.ROOT);
                if(!Set.of("SOAT","CAT").contains(type)||"CAT".equals(type)&&blank(value.condition()))throw conflict("SOAT/CAT requieren tipo conocido y aplicabilidad CAT explícitamente contrastada.");
            }
            if("VEH-03".equals(req.id())&&!"APROBADO".equalsIgnoreCase(value.fields().get("inspectionResult")))throw conflict("Una inspección aplicable debe constar aprobada, con su vigencia comprobada.");
            if("VEH-01".equals(req.id())&&(!"M1".equals(value.fields().get("category"))||"COLECTIVO".equals(mode)&&!Set.of("SEDAN","STATION_WAGON").contains(value.fields().get("bodyType"))))throw conflict("Categoría o carrocería no compatibles con la frontera documental aprobada.");
            if(value.fields().containsKey("plate")){
                String plate=jdbc.queryForObject("select plate from vehicle where id=? and organization_id=?",String.class,vehicle,d.getOrganizationId());
                if(!plate.equalsIgnoreCase(value.fields().get("plate")))throw conflict("El documento no corresponde a la unidad.");
            }
            if(value.fields().containsKey("modality")&&!Objects.equals(mode,value.fields().get("modality")))throw conflict("El título no corresponde a la modalidad.");
            if(value.fields().containsKey("serviceScope")&&!value.fields().get("serviceScope").toUpperCase(Locale.ROOT).contains("CHICLAYO"))throw conflict("El ámbito del título debe contrastarse para Chiclayo.");
        }
    }
    // Application transition, same transaction as suspension/review. No account,
    // approval, unit period or historical identity is deleted or reinterpreted.
    public void effects(Driver d,String reason){
        var rows=jdbc.queryForList("select id,status,vehicle_id,scheduled_at,scheduled_end_at from assignment where driver_id=? and organization_id=? and status in('PENDING_RESPONSE','SCHEDULED','EN_SERVICIO') order by id for update",d.getId(),d.getOrganizationId());
        for(var row:rows){UUID assignment=(UUID)row.get("id");boolean inService="EN_SERVICIO".equals(row.get("status"));
            if(!"OPERATIONAL_SUSPENSION".equals(reason)){
                UUID vehicle=(UUID)row.get("vehicle_id");Instant start=((java.sql.Timestamp)row.get("scheduled_at")).toInstant(),end=((java.sql.Timestamp)row.get("scheduled_end_at")).toInstant();
                boolean stillEligible=policy.blockers(d.getId(),d.getOrganizationId(),vehicle,start,end,true).isEmpty();
                if(inService)stillEligible=stillEligible&&policy.eligible(d.getId(),d.getOrganizationId(),true);
                if(stillEligible)continue;
            }
            int inserted=jdbc.update("insert into qualification_service_attention(id,organization_id,driver_id,assignment_id,kind,reason,created_at) values(?,?,?,?,?,?,?) on conflict(assignment_id,kind) do nothing",UUID.randomUUID(),d.getOrganizationId(),d.getId(),assignment,inService?"SAFE_HANDOVER_REQUIRED":"PENDING_REASSIGNMENT",reason,ts(now()));
            if(inService){if(inserted>0){boolean human="OPERATIONAL_SUSPENSION".equals(reason);UUID incidentId=UUID.randomUUID();jdbc.update("""
                insert into incident(id,organization_id,driver_id,assignment_id,reported_by,category,status,source,description,reported_at,version,created_at,updated_at)
                select ?,organization_id,driver_id,id,?,'OTRO','OPEN',?,'Habilitación interrumpida; detener conducción y coordinar relevo seguro.',?,0,?,? from assignment where id=?
                """,incidentId,human?current.requireTenantActor().getId():null,human?"CRM_WEB":"SYSTEM",ts(now()),ts(now()),ts(now()),assignment);
                events.publish(d.getOrganizationId(),d.getGroup().getId(),"incident.reported",Map.of("incidentId",incidentId,"driverId",d.getId(),"assignmentId",assignment));}
            }else {
                jdbc.update("update assignment set status='CANCELLED',cancelled_at=?,cancellation_reason=?,version=version+1,updated_at=? where id=?",ts(now()),reason,ts(now()),assignment);
                events.publish(d.getOrganizationId(),d.getGroup().getId(),"assignment.cancelled",Map.of("assignmentId",assignment,"driverId",d.getId()));
            }
        }
        // Occupation of an in-service assignment is released only by explicit safe handover/closure.
        if(!policy.eligible(d.getId(),d.getOrganizationId(),false)){
            jdbc.update("update driver set availability_status='NO_DISPONIBLE',location_consent=false,updated_at=? where id=? and availability_status<>'EN_SERVICIO'",ts(now()),d.getId());
            jdbc.update("delete from driver_current_location where driver_id=?",d.getId());
        }else jdbc.update("update driver set availability_status='NO_DISPONIBLE',updated_at=? where id=? and availability_status='RESERVADO' and not exists(select 1 from assignment where driver_id=? and status='SCHEDULED' and reserved_at is not null)",ts(now()),d.getId(),d.getId());
    }
    private Driver owned(UUID id,AppUser actor){return drivers.findByIdAndOrganization_Id(id,actor.getOrganizationId()).orElseThrow(this::notFound);}
    private AppUser admin(){var actor=current.requireTenantActor();if(actor.getRole()!=UserRole.ADMIN)throw new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"Se requiere ADMIN de la organización.");return actor;}
    private void vehicle(UUID id,UUID org){if(vehicles.lockByIdAndOrganizationId(id,org).isEmpty())throw notFound();}
    private void contextSafe(Driver d,String value){
        if(value==null)return;String text=value.toLowerCase(Locale.ROOT);
        var known=new ArrayList<String>(List.of(d.getFullName(),d.getDocumentNumber()));
        if(d.getLicenseNumber()!=null)known.add(d.getLicenseNumber());
        if(d.getUser()!=null){known.add(d.getUser().getFullName());known.add(d.getUser().getEmail());}
        if(known.stream().anyMatch(v->v!=null&&v.length()>3&&text.contains(v.toLowerCase(Locale.ROOT))))throw invalid("Usa referencias sin copiar datos personales del expediente.");
    }
    private void kind(String value){if(value==null||!Set.of("OWN","COMPANY").contains(value))throw invalid("Tipo de unidad desconocido.");}
    private void safe(String value,int max){if(blank(value)||value.length()>max||value.matches("(?s).*(?:[0-9]{6,}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+).*"))throw invalid("Usa una referencia o motivo sin datos personales ni secretos.");}
    private static boolean overlap(Instant a,Instant b,Instant c,Instant e){return(b==null||c.isBefore(b))&&(e==null||a.isBefore(e));}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private Instant now(){return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"El recurso no existe en la organización autorizada.");}
    private ApplicationException invalid(String message){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,message);}
    private ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
}
