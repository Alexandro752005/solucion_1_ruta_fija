package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.fleet.infrastructure.TransportGroupRepository;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.*;
import pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository;
import pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository.State;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingAccessRepository;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository;
import pe.rutafija.onboarding.infrastructure.LegacyConductorRegularizationRepository;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.pilotquota.application.PilotCreationQuotaService;
import pe.rutafija.personidentity.application.PersonIdentityService;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.terms.application.AdminTermsService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ConductorDocumentaryService {
    private static final Set<String> RESULTS=Set.of("PENDING","CONFORME","OBSERVADO","NO_APLICA_JUSTIFICADO","POR_CONTRASTAR");
    private static final Set<String> LICENSE_CATEGORIES=Set.of("A-IIA","A-IIB","A-IIIA","A-IIIB","A-IIIC");
    private static final Pattern ACCESS=Pattern.compile("oa1\\.[A-Za-z0-9_-]{43}");
    private static final Pattern UNSAFE_PUBLIC_TEXT=Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}|[0-9]{6,}");
    private final ConductorDocumentaryRepository repository;
    private final ConductorOnboardingAccessRepository access;
    private final ConductorOnboardingRepository invitationGuards;
    private final CurrentUserService current;
    private final OpaqueTokenService tokens;
    private final InvitationCodeHasher hasher;
    private final AppUserRepository users;
    private final DriverRepository drivers;
    private final TransportGroupRepository groups;
    private final OrganizationRepository organizations;
    private final PilotCreationQuotaService quota;
    private final AuditService audit;
    private final Clock clock;
    private final PersonIdentityService identities;
    private final LegacyConductorRegularizationRepository legacy;
    private final AdminTermsService terms;

    public ConductorDocumentaryService(ConductorDocumentaryRepository repository,
            ConductorOnboardingAccessRepository access,ConductorOnboardingRepository invitationGuards,CurrentUserService current,OpaqueTokenService tokens,
            InvitationCodeHasher hasher,AppUserRepository users,DriverRepository drivers,
            TransportGroupRepository groups,OrganizationRepository organizations,PilotCreationQuotaService quota,
            AuditService audit,Clock clock,PersonIdentityService identities,
            LegacyConductorRegularizationRepository legacy,AdminTermsService terms) {
        this.repository=repository;this.access=access;this.invitationGuards=invitationGuards;this.current=current;this.tokens=tokens;this.hasher=hasher;
        this.users=users;this.drivers=drivers;this.groups=groups;this.organizations=organizations;
        this.quota=quota;this.audit=audit;this.clock=clock;
        this.identities=identities;
        this.legacy=legacy;this.terms=terms;
    }

    @Transactional
    public Dossier adminDossier(UUID id) {
        AppUser actor=admin();
        State state=repository.find(id,actor.getOrganizationId(),null).orElseThrow(this::notFound);
        audit.record(actor,"CONDUCTOR_DOCUMENTARY_EXPEDIENTE_VIEWED","CONDUCTOR_ONBOARDING_REQUEST",id,Map.of());
        return dossier(state,true);
    }

    @Transactional(readOnly=true)
    public Dossier applicantDossier(UUID id,String token) {
        UUID identity=applicant(token);
        return dossier(repository.find(id,null,identity).orElseThrow(this::notFound),false);
    }

    @Transactional
    public Dossier saveAdmin(UUID id,SaveDocuments command) {
        AppUser actor=admin();
        State state=lockAdmin(id,actor);
        String hash=payload("DOCUMENTS_REVIEWED",command);
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());
        requireStatus(state,"PENDING_REVIEW");
        validateModality(state,command.modality());
        List<Document> values=validateDocuments(state,command,true);
        for(Document value:values)repository.appendDocument(state,value,actor.getId(),"ADMIN",now());
        repository.touch(id,command.modality());
        state=reload(state);
        record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"DOCUMENTS_REVIEWED",null);
        audit.record(actor,"CONDUCTOR_DOCUMENTS_REVIEWED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("version",state.version(),"catalogVersion",state.catalogVersion()));
        return dossier(state,true);
    }

    @Transactional
    public Dossier saveApplicant(UUID id,String token,SaveDocuments command) {
        UUID identity=applicant(token);
        State state=repository.lock(id,null,identity).orElseThrow(this::notFound);
        recheckApplicantMutation(state,token);
        String hash=payload("DOCUMENTS_CORRECTED",command);
        if(replay(state,"APPLICANT",identity,command.operationKey(),hash))return dossier(reload(state),false);
        expected(state,command.expectedVersion());
        if(!Set.of("PENDING_REVIEW","RETURNED").contains(state.status()))throw conflict("La solicitud no admite cambios documentales.");
        validateModality(state,command.modality());
        List<Document> values=validateDocuments(state,command,false);
        for(Document value:values)repository.appendDocument(state,value,null,"APPLICANT",now());
        repository.touch(id,command.modality());
        state=reload(state);
        record(state,"APPLICANT",identity,command.operationKey(),hash,"DOCUMENTS_CORRECTED",null);
        return dossier(state,false);
    }

    @Transactional
    public Dossier returnForCorrection(UUID id,Decision command) {
        AppUser actor=admin();State state=lockAdmin(id,actor);
        String hash=payload("RETURNED",command);
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());requireStatus(state,"PENDING_REVIEW");
        publicText(command.reason(),state,repository.documents(id));
        if(state.reviewRounds()==Short.MAX_VALUE)throw conflict("Se alcanzo el limite de rondas registrado.");
        List<Document> documents=repository.documents(id);
        boolean needsCorrection=documents.size()<ConductorDocumentaryCatalog.REQUIREMENTS.size()||documents.stream().anyMatch(doc->Set.of("OBSERVADO","PENDING","POR_CONTRASTAR").contains(doc.result()));
        if(!needsCorrection)throw conflict("Registra los requisitos observados o pendientes antes de devolver.");
        Instant now=now();repository.returnRequest(id,actor.getId(),now,now.plus(Duration.ofHours(48)));
        state=reload(state);record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"RETURNED",command.reason());
        audit.record(actor,"CONDUCTOR_ONBOARDING_RETURNED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("round",state.reviewRounds(),"deadline",state.correctionDeadline().toString()));
        return dossier(state,true);
    }

    @Transactional
    public Dossier extend(UUID id,Decision command) {
        AppUser actor=admin();State state=lockAdmin(id,actor);String hash=payload("DEADLINE_EXTENDED",command);
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());requireStatus(state,"RETURNED");
        publicText(command.reason(),state,repository.documents(id));
        if(command.deadline()==null||!command.deadline().isAfter(now())||
                (state.correctionDeadline()!=null&&!command.deadline().isAfter(state.correctionDeadline())))
            throw conflict("La ampliacion debe establecer un plazo futuro mayor al anterior.");
        repository.extend(id,actor.getId(),now(),command.deadline());state=reload(state);
        record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"DEADLINE_EXTENDED",command.reason());
        return dossier(state,true);
    }

    @Transactional
    public Dossier resubmit(UUID id,String token,Decision command) {
        UUID identity=applicant(token);State state=repository.lock(id,null,identity).orElseThrow(this::notFound);
        recheckApplicantMutation(state,token);
        if(legacy.opening(id,state.organizationId()).isPresent())
            throw conflict("La subsanacion de una cuenta anterior requiere revision presencial y Reabrir revision existente por ADMIN.");
        String hash=payload("RESUBMITTED",command);
        if(replay(state,"APPLICANT",identity,command.operationKey(),hash))return dossier(reload(state),false);
        expected(state,command.expectedVersion());requireStatus(state,"RETURNED");
        publicText(command.reason(),state,repository.documents(id));
        repository.resubmit(id);state=reload(state);
        record(state,"APPLICANT",identity,command.operationKey(),hash,"RESUBMITTED",command.reason());
        return dossier(state,false);
    }

    /** Returned legacy dossiers are corrected in person through ADMIN; no applicant identity is adopted. */
    @Transactional
    public Dossier resubmitExisting(UUID id,Decision command) {
        AppUser actor=admin();requireLegacyAdmin(actor);
        State state=lockAdmin(id,actor);String hash=payload("LEGACY_RESUBMITTED",command);
        legacy.opening(id,actor.getOrganizationId()).orElseThrow(()->conflict("El expediente no corresponde a una regularizacion administrativa explicita."));
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());requireStatus(state,"RETURNED");
        if(state.linkedUserId()!=null)throw conflict("Una cuenta provisionada no se devuelve a revision mediante este flujo.");
        publicText(command.reason(),state,repository.documents(id));
        repository.resubmit(id);state=reload(state);
        record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"LEGACY_RESUBMITTED",command.reason());
        audit.record(actor,"CONDUCTOR_LEGACY_REVIEW_REOPENED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("personalApprovalGranted",false,"operationalEnabled",false));
        return dossier(state,true);
    }

    @Transactional
    public Dossier approve(UUID id,Decision command) {
        AppUser actor=admin();State state=lockAdmin(id,actor);String hash=payload("PERSONAL_APPROVED",command);
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());requireStatus(state,"PENDING_REVIEW");
        publicText(command.reason(),state,repository.documents(id));
        personalCompliant(state);
        repository.approve(id,actor.getId(),now());state=reload(state);
        record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"PERSONAL_APPROVED",command.reason());
        audit.record(actor,"CONDUCTOR_PERSONAL_APPROVED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("operationalEnabled",false,"catalogVersion",state.catalogVersion()));
        return dossier(state,true);
    }

    @Transactional
    public Dossier reject(UUID id,Decision command) {
        AppUser actor=admin();State state=lockAdmin(id,actor);String hash=payload("REJECTED",command);
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());
        if(!Set.of("PENDING_REVIEW","RETURNED").contains(state.status()))throw conflict("No es posible rechazar en ese estado.");
        publicText(command.reason(),state,repository.documents(id));
        if(command.reiteration()&&state.reviewRounds()<4)throw conflict("El rechazo por reiteracion requiere al menos cuatro devoluciones.");
        Instant now=now();repository.reject(id,actor.getId(),now,command.reiteration(),command.reiteration()?now.plus(Duration.ofDays(7)):now);
        state=reload(state);record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"REJECTED",command.reason());
        audit.record(actor,"CONDUCTOR_ONBOARDING_REJECTED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("reiteration",command.reiteration(),"round",state.reviewRounds()));
        return dossier(state,true);
    }

    @Transactional
    public Dossier provision(UUID id,Decision command) {
        AppUser actor=admin();State state=lockAdmin(id,actor);String hash=payload("ACCOUNT_PROVISIONED",command);
        if(legacy.opening(id,actor.getOrganizationId()).isPresent())
            throw conflict("Este expediente regulariza una cuenta existente. Utiliza Vincular cuenta existente.");
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());requireStatus(state,"APPROVED");
        if(state.linkedUserId()!=null)throw conflict("La misma identidad ya fue provisionada; consulta su estado.");
        if(command.groupId()==null)throw conflict("Selecciona un grupo activo de la organizacion.");
        personalCompliant(state);
        if(!repository.activeGroup(command.groupId(),state.organizationId()))throw conflict("El grupo no esta activo en esta organizacion.");
        var group=groups.findByIdAndOrganization_Id(command.groupId(),state.organizationId())
                .filter(value->value.isActive()).orElseThrow(()->conflict("El grupo no esta activo en esta organizacion."));
        var identity=repository.identity(state.identityId());
        if(identity.verifiedAt()==null||users.existsByEmailIgnoreCase(identity.email()))
            throw conflict("La identidad ya tiene cuenta o requiere un flujo autorizado de vinculacion.");
        Document personal=repository.documents(id).stream().filter(doc->"CON-01".equals(doc.requirementId())).findFirst().orElseThrow();
        Document license=repository.documents(id).stream().filter(doc->"CON-02".equals(doc.requirementId())).findFirst().orElseThrow();
        String type=personal.fields().get("documentType").strip().toUpperCase(Locale.ROOT);
        String number=personal.fields().get("documentNumber").strip().toUpperCase(Locale.ROOT);
        String personHash=hasher.hashOrigin("conductor-person:"+type+":"+number);
        if(repository.legacyPersonExists(type,number)||!repository.reservePerson(personHash,state,now()))
            throw conflict("La persona ya esta registrada; se requiere el proceso de vinculacion o traslado autorizado.");
        // Preflight and the V22 insert trigger share the same transaction; rollback returns the creation quota.
        quota.requireAvailable(UserRole.CONDUCTOR);
        var organization=organizations.findById(state.organizationId()).orElseThrow(this::notFound);
        String reviewedName = personal.fields().get("fullName").strip();
        AppUser user=AppUser.organizationUser(organization,identity.email(),identity.passwordHash(),reviewedName,UserRole.CONDUCTOR);
        user.verifyEmail(identity.verifiedAt());
        user.updateDetails(identity.email(),reviewedName,identity.phone(),UserRole.CONDUCTOR);
        user=users.saveAndFlush(user);
        Driver driver=Driver.register(organization,user,group,reviewedName,identity.phone(),type,number,license.fields().get("licenseNumber"));
        driver.deactivate();
        driver=drivers.saveAndFlush(driver);
        repository.completePerson(personHash,user.getId(),driver.getId());
        identities.bindConductor(actor,user,type,number,id);
        repository.transferOtpHistory(state.identityId(),user.getId(),user.getEmail(),tokens.hash(tokens.generate()),now());
        repository.provision(id,user.getId(),driver.getId(),now());state=reload(state);
        record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"ACCOUNT_PROVISIONED",null);
        audit.record(actor,"CONDUCTOR_ACCOUNT_PROVISIONED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("userId",user.getId().toString(),"driverId",driver.getId().toString(),"operationalEnabled",false));
        return dossier(state,true);
    }

    /** Completes a reviewed legacy association without creating accounts or consuming historical quota. */
    @Transactional
    public Dossier provisionExisting(UUID id,Decision command) {
        AppUser actor=admin();requireLegacyAdmin(actor);
        State state=lockAdmin(id,actor);String hash=payload("EXISTING_ACCOUNT_LINKED",command);
        var opening=legacy.opening(id,actor.getOrganizationId()).orElseThrow(()->conflict("El expediente no corresponde a una regularizacion administrativa explicita."));
        if(replay(state,"ADMIN",actor.getId(),command.operationKey(),hash))return dossier(reload(state),true);
        expected(state,command.expectedVersion());requireStatus(state,"APPROVED");
        if(state.linkedUserId()!=null)throw conflict("La cuenta ya fue vinculada. Consulta el estado actual.");
        if(opening.userId()==null||opening.driverId()==null||!opening.identityId().equals(state.identityId())||!legacy.lockBinding(opening))
            throw conflict("La asociacion explicita requiere conciliacion autorizada.");
        var user=users.findByIdForUpdate(opening.userId()).orElseThrow(this::notFound);
        var driver=drivers.lockByIdAndOrganizationId(opening.driverId(),state.organizationId()).orElseThrow(this::notFound);
        if(!user.isActive()||user.getRole()!=UserRole.CONDUCTOR||!Objects.equals(user.getOrganizationId(),state.organizationId())
                ||driver.getUser()==null||!user.getId().equals(driver.getUser().getId()))
            throw conflict("La cuenta y ficha existentes no mantienen el vinculo autorizado en esta organizacion.");
        UUID retainedGroupId=driver.getGroup().getId();
        if(command.groupId()!=null&&!command.groupId().equals(retainedGroupId)||!repository.activeGroup(retainedGroupId,state.organizationId()))
            throw conflict("Selecciona el grupo activo que ya corresponde a la ficha existente. Un traslado requiere su procedimiento propio.");
        if(legacy.hasOpenWork(driver.getId()))throw conflict("Finaliza las asignaciones pendientes antes de vincular la cuenta.");
        personalCompliant(state);publicText(command.reason(),state,repository.documents(id));
        Document personal=repository.documents(id).stream().filter(doc->"CON-01".equals(doc.requirementId())).findFirst().orElseThrow();
        Document license=repository.documents(id).stream().filter(doc->"CON-02".equals(doc.requirementId())).findFirst().orElseThrow();
        String type=personal.fields().get("documentType").strip().toUpperCase(Locale.ROOT);
        String number=personal.fields().get("documentNumber").strip();
        if(!type.equals(driver.getDocumentType())||!number.equals(driver.getDocumentNumber()))
            throw conflict("El DNI del expediente no coincide con la ficha existente. Se requiere conciliacion documental autorizada.");
        String personHash=identities.requireReviewedConductorDocument(user.getId(),type,number);
        if(!personHash.equals(opening.personHash()))throw conflict("La persona revisada no corresponde a la asociacion original.");
        String storedLicense=driver.getLicenseNumber();
        if(storedLicense==null||!storedLicense.equalsIgnoreCase(license.fields().get("licenseNumber").strip()))
            throw conflict("La licencia revisada no coincide con la ficha existente. Corrige la documentacion mediante el procedimiento autorizado.");
        var preliminary=repository.identity(state.identityId());
        if(!user.getEmail().equals(preliminary.email()))
            throw conflict("El correo de la cuenta cambio desde la apertura. Se requiere conciliar el expediente sin adoptar otra identidad.");
        if(legacy.hasOnboardingLink(user.getId(),driver.getId()))throw conflict("La cuenta o ficha ya tiene otra provision registrada.");
        driver.deactivate();drivers.flush();
        identities.bindConductor(actor,user,type,number,id);
        repository.provision(id,user.getId(),driver.getId(),now());state=reload(state);
        record(state,"ADMIN",actor.getId(),command.operationKey(),hash,"EXISTING_ACCOUNT_LINKED",command.reason());
        audit.record(actor,"CONDUCTOR_EXISTING_ACCOUNT_LINKED","CONDUCTOR_ONBOARDING_REQUEST",id,
                Map.of("userId",user.getId().toString(),"driverId",driver.getId().toString(),"credentialsPreserved",true,
                        "emailVerificationGranted",false,"operationalEnabled",false));
        return dossier(state,true);
    }

    @Transactional(noRollbackFor=OnboardingInvitationRejectedException.class)
    public Dossier reapply(UUID id,String token,Reapply command,String remoteAddress) {
        UUID identityId=applicant(token);
        State visible=repository.find(id,null,identityId).orElseThrow(this::notFound);
        if(legacy.opening(id,visible.organizationId()).isPresent())
            throw conflict("Una regularizacion anterior no permite crear otra solicitud ni adoptar la cuenta mediante una invitacion.");
        String hash=payload("REAPPLIED",command);
        var completed=repository.priorOperation(id,"APPLICANT",identityId,command.operationKey());
        if(completed.isPresent()) {
            State replayState=repository.lock(id,null,identityId).orElseThrow(this::notFound);
            recheckApplicantMutation(replayState,token);
            if(!completed.get().payloadHash().equals(hash))throw conflict("La clave de operacion ya tiene otro contenido.");
            return dossier(repository.find(completed.get().resultingRequestId(),null,identityId).orElseThrow(this::notFound),false);
        }
        var preliminary=repository.identity(identityId);
        Instant attemptAt=now();
        String emailHash=hasher.hashOrigin("onboarding-email:"+preliminary.email());
        String originHash=hasher.hashOrigin(remoteAddress);
        Instant emailBlock=invitationGuards.lockGuard("EMAIL",emailHash,attemptAt);
        Instant originBlock=invitationGuards.lockGuard("ORIGIN",originHash,attemptAt);
        if((emailBlock!=null&&emailBlock.isAfter(attemptAt))||(originBlock!=null&&originBlock.isAfter(attemptAt)))
            throw new OnboardingInvitationRejectedException(true);
        State state=repository.lock(id,null,identityId).orElseThrow(this::notFound);
        recheckApplicantMutation(state,token);
        var prior=repository.priorOperation(id,"APPLICANT",identityId,command.operationKey());
        if(prior.isPresent()) {
            if(!prior.get().payloadHash().equals(hash))throw conflict("La clave de operacion ya tiene otro contenido.");
            return dossier(repository.find(prior.get().resultingRequestId(),null,identityId).orElseThrow(this::notFound),false);
        }
        expected(state,command.expectedVersion());requireStatus(state,"REJECTED");
        if(state.reapplyAfter()!=null&&state.reapplyAfter().isAfter(now()))throw conflict("Todavia no termina el periodo de espera para volver a postular.");
        if(!repository.activeOrganization(state.organizationId()))throw conflict("La organizacion no esta activa.");
        if(invitationGuards.findPendingRequest(identityId,state.organizationId()).isPresent())
            throw conflict("Ya existe una solicitud pendiente de la misma identidad en la organizacion.");
        var identity=repository.identity(identityId);
        var invitation=repository.lockInvitation(state.organizationId(),identity.email(),hasher.hashCode(command.invitationCode(),identity.email()),now()).orElse(null);
        if(invitation==null) {
            Instant cutoff=attemptAt.minus(Duration.ofMinutes(15));
            invitationGuards.pruneFailuresAtOrBefore(cutoff);
            invitationGuards.recordFailure(emailHash,originHash,attemptAt);
            boolean limited=false;
            if(invitationGuards.countEmailFailures(emailHash,cutoff)>=5) {
                invitationGuards.blockGuard("EMAIL",emailHash,attemptAt.plus(Duration.ofMinutes(30)),attemptAt);limited=true;
            }
            if(invitationGuards.countOriginFailures(originHash,cutoff)>=50) {
                invitationGuards.blockGuard("ORIGIN",originHash,attemptAt.plus(Duration.ofMinutes(15)),attemptAt);limited=true;
            }
            throw new OnboardingInvitationRejectedException(limited);
        }
        UUID next=repository.reapply(state,invitation.id(),command.operationKey(),now());state=reload(state);
        repository.event(state,"APPLICANT",identityId,command.operationKey(),hash,"REAPPLIED",null,now(),next);
        return dossier(repository.find(next,null,identityId).orElseThrow(this::notFound),false);
    }

    private List<Document> validateDocuments(State state,SaveDocuments command,boolean admin) {
        Set<String> ids=new HashSet<>();List<Document> values=new ArrayList<>();
        for(DocumentChange change:command.documents()) {
            var definition=ConductorDocumentaryCatalog.requirement(change.requirementId());
            if(definition==null||!ids.add(change.requirementId()))throw validation("Requisito documental desconocido o duplicado.");
            Map<String,String> fields=new TreeMap<>();
            if(change.fields()!=null)for(var entry:change.fields().entrySet()) {
                if(!definition.fieldKeys().contains(entry.getKey())||entry.getValue()==null||entry.getValue().length()>200)
                    throw validation("El expediente solo admite campos estructurados del catalogo, sin archivos.");
                if(!entry.getValue().isBlank())fields.put(entry.getKey(),entry.getValue().strip());
            }
            if(!Set.of("DATED","NO_EXPIRY","UNKNOWN").contains(change.expiryMode())||
                    ("DATED".equals(change.expiryMode())!=(change.validUntil()!=null)))throw validation("Indica una vigencia conocida, sin vencimiento o desconocida, sin fechas ficticias.");
            if(!Set.of("CONFIRMED","POR_CONTRASTAR").contains(change.applicability())||!RESULTS.contains(change.result()))throw validation("Resultado o aplicabilidad documental invalido.");
            if(!admin&&(!Set.of("PENDING","POR_CONTRASTAR").contains(change.result())||!blank(change.reviewReference())||!blank(change.observation())))
                throw forbidden("El postulante no puede registrar una revision ADMIN.");
            Document document=new Document(change.requirementId(),"PERSON",Map.copyOf(fields),change.expiryMode(),change.validUntil(),
                    change.applicability(),trim(change.condition()),trim(change.sourceReference()),admin?change.result():"PENDING",
                    admin?trim(change.reviewReference()):null,admin?trim(change.observation()):null);
            if(admin) {
                if(Set.of("CONFORME","NO_APLICA_JUSTIFICADO").contains(document.result()))validateReviewed(document,command.modality());
                if("OBSERVADO".equals(document.result())&&blank(document.observation()))throw validation("Una observacion requiere una indicacion de subsanacion sin datos personales.");
            }
            values.add(document);
        }
        if(admin){
            // Corrections shown to the applicant must not repeat the reviewed
            // name from another requirement or from the current dossier.
            List<Document> known=new ArrayList<>(repository.documents(state.id()));known.addAll(values);
            for(Document document:values)publicText(document.observation(),state,known);
        }
        return values;
    }

    /** L-02 reuses the approved personal schema after provisioning; no new approval is inferred. */
    public List<Document> validateQualificationDocuments(State state,List<DocumentChange> changes,boolean reviewed) {
        return validateDocuments(state,new SaveDocuments(state.version(),"qualification-validation",state.modality(),changes),reviewed);
    }

    private void validateReviewed(Document document,String modality) {
        if(!"CONFIRMED".equals(document.applicability())||blank(document.sourceReference())||blank(document.reviewReference()))
            throw conflict("Un requisito por contrastar o sin comprobacion visual no puede marcarse conforme.");
        boolean mandatory=ConductorDocumentaryCatalog.requirement(document.requirementId()).mandatory();
        if("NO_APLICA_JUSTIFICADO".equals(document.result())) {
            if(mandatory||blank(document.condition())||blank(document.observation()))throw conflict("No aplica requiere condicion y motivo verificables y no sustituye requisitos obligatorios.");
            if("CON-05".equals(document.requirementId())&&"COLECTIVO".equals(modality))
                throw conflict("Las condiciones personales del colectivo deben contrastarse, no omitirse.");
            return;
        }
        if("UNKNOWN".equals(document.expiryMode())||("DATED".equals(document.expiryMode())&&!document.validUntil().isAfter(now())))
            throw conflict("No se puede aprobar un requisito vencido o con vigencia desconocida.");
        for(String field:ConductorDocumentaryCatalog.requirement(document.requirementId()).fieldKeys())
            if(blank(document.fields().get(field)))throw conflict("Faltan campos de un requisito obligatorio aplicable.");
        if("CON-01".equals(document.requirementId())&&(!"DNI".equalsIgnoreCase(document.fields().get("documentType"))||
                !document.fields().get("documentNumber").matches("[0-9]{8}")))throw conflict("El piloto admite DNI documentado; otro tipo requiere una regla expresa.");
        if("CON-02".equals(document.requirementId())) {
            if(!"A".equalsIgnoreCase(document.fields().get("licenseClass"))||
                    !LICENSE_CATEGORIES.contains(document.fields().get("licenseCategory").toUpperCase(Locale.ROOT))||
                    !"DATED".equals(document.expiryMode()))throw conflict("Se requiere licencia vigente compatible con el servicio M1; A-I o restricciones desconocidas no habilitan.");
            if(Set.of("UNKNOWN","DESCONOCIDAS","POR_CONTRASTAR").contains(document.fields().get("restrictions").toUpperCase(Locale.ROOT)))
                throw conflict("Las restricciones de la licencia aun deben contrastarse.");
        }
        if("CON-03".equals(document.requirementId())&& !"DATED".equals(document.expiryMode()))
            throw conflict("La habilitacion personal aplicable requiere vencimiento documental comprobado.");
        if("CON-05".equals(document.requirementId())) {
            if(!"ELIGIBLE".equals(document.fields().get("eligibilityResult")))throw conflict("La condicion personal del colectivo aun no es conforme.");
            try { if(LocalDate.parse(document.fields().get("reviewDate")).isAfter(LocalDate.ofInstant(now(),java.time.ZoneId.of("America/Lima"))))throw conflict("La comprobacion no puede tener fecha futura."); }
            catch(java.time.format.DateTimeParseException exception){throw validation("La fecha de comprobacion debe ser una fecha real.");}
        }
    }

    private void personalCompliant(State state) {
        if(state.modality()==null)throw conflict("Debe identificarse la modalidad del expediente.");
        Map<String,Document> values=new HashMap<>();repository.documents(state.id()).forEach(item->values.put(item.requirementId(),item));
        for(var required:ConductorDocumentaryCatalog.REQUIREMENTS) {
            var document=values.get(required.requirementId());
            if(document==null||!Set.of("CONFORME","NO_APLICA_JUSTIFICADO").contains(document.result()))
                throw conflict("El expediente tiene requisitos personales pendientes, observados o por contrastar.");
            validateReviewed(document,state.modality());
        }
    }

    private Dossier dossier(State state,boolean full) {
        Map<String,Document> stored=new HashMap<>();repository.documents(state.id()).forEach(item->stored.put(item.requirementId(),item));
        List<Document> documents=new ArrayList<>();
        for(var definition:ConductorDocumentaryCatalog.REQUIREMENTS) {
            var item=stored.getOrDefault(definition.requirementId(),new Document(definition.requirementId(),"PERSON",Map.of(),"UNKNOWN",null,
                    "POR_CONTRASTAR",null,null,definition.mandatory()?"PENDING":"POR_CONTRASTAR",null,null));
            documents.add(full?item:new Document(item.requirementId(),item.entity(),Map.of(),"UNKNOWN",null,item.applicability(),
                    null,null,item.result(),null,item.observation()));
        }
        boolean overdue="RETURNED".equals(state.status())&&state.correctionDeadline()!=null&&!state.correctionDeadline().isAfter(now());
        var existing=legacy.opening(state.id(),state.organizationId());
        return new Dossier(state.id(),state.status(),state.version(),state.reviewRounds(),state.modality(),state.catalogId(),state.catalogVersion(),
                state.catalogHash(),ConductorDocumentaryCatalog.REQUIREMENTS,List.copyOf(documents),repository.history(state.id()),
                state.correctionDeadline(),overdue,state.personalApprovedAt()!=null,state.linkedUserId(),state.linkedDriverId(),false,state.reapplyAfter(),
                existing.isPresent(),existing.map(LegacyConductorRegularizationRepository.Opening::userId).orElse(null),
                existing.map(LegacyConductorRegularizationRepository.Opening::driverId).orElse(null));
    }

    private State lockAdmin(UUID id,AppUser actor) {
        State state=repository.lock(id,actor.getOrganizationId(),null).orElseThrow(this::notFound);
        if(!repository.activeOrganization(state.organizationId()))throw conflict("La organizacion no esta activa.");
        return state;
    }
    private State reload(State state){return repository.find(state.id(),state.organizationId(),null).orElseThrow(this::notFound);}
    private AppUser admin(){AppUser actor=current.requireTenantActor();if(actor.getRole()!=UserRole.ADMIN||!actor.isEmailVerified())throw forbidden("Se requiere ADMIN verificado de la organizacion.");return actor;}
    private void requireLegacyAdmin(AppUser actor){identities.requireVerified(actor.getId());if(!terms.hasCurrentAcceptanceForCurrentActor())throw forbidden("La regularizacion requiere terminos ADMIN vigentes.");}
    private UUID applicant(String token){if(token==null||!ACCESS.matcher(token).matches())throw unauthorized();return access.findIdentityForActiveToken(tokens.hash(token),now()).orElseThrow(this::unauthorized);}
    private void recheckApplicantMutation(State state,String token) {
        String hash=tokens.hash(token);
        if(!access.lockAccountAndTokenForMutation(state.identityId(),hash)||
                !access.findIdentityForActiveToken(hash,now()).filter(state.identityId()::equals).isPresent())
            throw unauthorized();
    }
    private boolean replay(State state,String kind,UUID actor,String key,String hash){var prior=repository.priorOperation(state.id(),kind,actor,key);if(prior.isEmpty())return false;if(!prior.get().payloadHash().equals(hash))throw conflict("La clave de operacion ya tiene otro contenido.");return true;}
    private void expected(State state,long version){if(state.version()!=version)throw conflict("La solicitud cambio. Consulta su version actual antes de continuar.");if(!ConductorDocumentaryCatalog.HASH.equals(state.catalogHash()))throw conflict("La version del catalogo requiere una revision autorizada.");}
    private void validateModality(State state,String modality){if(!Set.of("TAXI","COLECTIVO").contains(modality))throw validation("La modalidad debe ser TAXI o COLECTIVO.");if(state.modality()!=null&&!state.modality().equals(modality))throw conflict("La modalidad del expediente no puede sustituirse durante una revision.");}
    private void requireStatus(State state,String status){if(!status.equals(state.status()))throw conflict("La transicion no esta permitida desde el estado actual.");}
    private void record(State state,String kind,UUID actor,String key,String hash,String action,String reason){repository.event(state,kind,actor,key,hash,action,reason,now(),state.id());}
    private String payload(String action,Object command){return ConductorDocumentaryCatalog.hash(action+":"+repository.write(command));}
    private void publicText(String text,State state,List<Document> documents){
        if(blank(text))return;
        if(UNSAFE_PUBLIC_TEXT.matcher(text).find())throw validation("Las indicaciones no deben incluir correos, numeros personales, claves ni codigos.");
        var identity=repository.identity(state.identityId());String normalized=text.toLowerCase(Locale.ROOT);
        for(String value:List.of(identity.email(),identity.fullName()))if(value.length()>3&&normalized.contains(value.toLowerCase(Locale.ROOT)))throw validation("Usa indicaciones de correccion sin datos personales.");
        for(Document document:documents)for(var entry:document.fields().entrySet())if(Set.of("fullName","documentNumber","licenseNumber","titleNumber").contains(entry.getKey())&&entry.getValue().length()>3&&normalized.contains(entry.getValue().toLowerCase(Locale.ROOT)))throw validation("Usa referencias al requisito, sin copiar sus datos personales.");
    }
    private Instant now(){return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);}
    private static boolean blank(String value){return value==null||value.isBlank();}
    private static String trim(String value){return blank(value)?null:value.strip();}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"La solicitud no existe en el alcance autorizado.");}
    private ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private ApplicationException validation(String message){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,message);}
    private ApplicationException forbidden(String message){return new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,message);}
    private ApplicationException unauthorized(){return new ApplicationException(HttpStatus.UNAUTHORIZED,ErrorCode.AUTH_TOKEN_INVALID,"El acceso de postulante no es valido o ha expirado.");}
}
