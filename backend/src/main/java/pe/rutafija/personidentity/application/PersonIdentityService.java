package pe.rutafija.personidentity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.domain.*;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.personidentity.api.PersonIdentityContract.*;
import pe.rutafija.personidentity.infrastructure.PersonIdentityRepository;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserProvider;
import pe.rutafija.shared.security.CurrentUserService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** ID-01 stores a stable pseudonymous binding, never original ADMIN/owner documents. */
@Service
public class PersonIdentityService {
    private static final Pattern UNSAFE_REFERENCE=Pattern.compile("[0-9]{6,}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private final PersonIdentityRepository repository;
    private final AppUserRepository users;
    private final InvitationCodeHasher hasher;
    private final CurrentUserProvider provider;
    private final CurrentUserService current;
    private final PasswordEncoder passwords;
    private final LoginRateLimiter limiter;
    private final AuditService audit;
    private final Clock clock;
    private final DriverRepository drivers;
    private final RefreshTokenRepository refreshTokens;
    public PersonIdentityService(PersonIdentityRepository repository,AppUserRepository users,InvitationCodeHasher hasher,
            CurrentUserProvider provider,CurrentUserService current,PasswordEncoder passwords,LoginRateLimiter limiter,AuditService audit,Clock clock,
            DriverRepository drivers,RefreshTokenRepository refreshTokens){
        this.repository=repository;this.users=users;this.hasher=hasher;this.provider=provider;this.current=current;
        this.passwords=passwords;this.limiter=limiter;this.audit=audit;this.clock=clock;
        this.drivers=drivers;this.refreshTokens=refreshTokens;
    }
    @Transactional(readOnly=true)
    public Status status(){UUID id=provider.requireCurrentUser().userId();return new Status(id,repository.verified(id));}
    @Transactional
    public Status enrollOwner(SelfEnrollment command){
        AppUser owner=current.requireSuperAdminActor();
        if(!owner.isEmailVerified())throw forbidden("Se requiere SUPER_ADMIN con correo verificado.");
        String limit="person-owner-enrollment:"+owner.getId();limiter.checkAndRecordAttempt(limit);
        if(!passwords.matches(command.currentPassword(),owner.getPasswordHash()))throw new InvalidCredentialsException();
        if(!Boolean.TRUE.equals(command.ownerDeclaration())||!"IN_PERSON".equals(command.identityReview().method()))
            throw forbidden("El titular debe declarar expresamente su comprobacion manual presencial propia.");
        reviewAndBind(owner,owner,command.identityReview(),"OWNER_SELF_REVIEW");
        limiter.recordSuccess(limit);return new Status(owner.getId(),true);
    }
    @Transactional
    public Status reviewAdmin(UUID userId,AdminReview command){
        AppUser owner=current.requireSuperAdminActor();requireVerified(owner.getId());
        if(!owner.isEmailVerified())throw forbidden("Se requiere SUPER_ADMIN con correo verificado.");
        safeReason(command.reason());
        AppUser target=users.findOneById(userId).filter(user->user.getRole()==UserRole.ADMIN).orElseThrow(this::notFound);
        reviewAndBind(owner,target,command.identityReview(),command.identityReview().method());return new Status(userId,true);
    }
    @Transactional
    public Status reviewExistingConductor(UUID driverId,AdminReview command){
        AppUser reviewer=current.requireTenantActor();requireVerified(reviewer.getId());
        if(reviewer.getRole()!=UserRole.ADMIN||!reviewer.isEmailVerified())throw forbidden("La revision requiere ADMIN verificado de la misma organizacion.");
        safeReason(command.reason());
        // Mobile writes already lock their account before their Driver. Keep
        // that order when an ADMIN regularizes the same account concurrently.
        UUID targetId=repository.driverUserId(driverId,reviewer.getOrganizationId()).orElseThrow(this::notFound);
        repository.lockUser(targetId);
        var driver=drivers.lockByIdAndOrganizationId(driverId,reviewer.getOrganizationId()).orElseThrow(this::notFound);
        AppUser target=driver.getUser();
        if(target==null||!targetId.equals(target.getId())||target.getRole()!=UserRole.CONDUCTOR||!Objects.equals(target.getOrganizationId(),reviewer.getOrganizationId()))
            throw conflict("El conductor requiere un vinculo explicito con la misma cuenta antes de su revision.");
        boolean alreadyVerified=repository.verified(target.getId());
        reviewAndBind(reviewer,target,command.identityReview(),command.identityReview().method());
        boolean storedMatches=Objects.equals(driver.getDocumentType(),command.identityReview().documentType())&&
                Objects.equals(driver.getDocumentNumber(),command.identityReview().documentNumber());
        if(!alreadyVerified){
            // Reviewing a DNI never approves the legacy dossier or enables a
            // vehicle/service. Keep the same account and Driver, closed to work.
            driver.deactivate();drivers.flush();
            refreshTokens.revokeActiveByUserId(target.getId(),now());repository.revokeSessions(target.getId());
        }
        audit.record(reviewer,"CONDUCTOR_IDENTITY_REVIEWED","DRIVER",driverId,
                Map.of("storedIdentityMatches",storedMatches,"documentaryApprovalGranted",false,"operationalEnabled",false));
        return new Status(target.getId(),true);
    }
    @Transactional
    public void bindNewAdmin(AppUser owner,AppUser candidate,IdentityReview review){
        requireVerified(owner.getId());
        if(owner.getRole()!=UserRole.SUPER_ADMIN||candidate.getRole()!=UserRole.ADMIN)throw forbidden("La revision corresponde a SUPER_ADMIN y a una cuenta ADMIN.");
        reviewAndBind(owner,candidate,review,review.method());
    }
    @Transactional
    public void bindConductor(AppUser reviewer,AppUser candidate,String documentType,String documentNumber,UUID requestId){
        if(reviewer.getRole()!=UserRole.ADMIN||candidate.getRole()!=UserRole.CONDUCTOR||
                !Objects.equals(reviewer.getOrganizationId(),candidate.getOrganizationId()))throw forbidden("La revision documental requiere ADMIN de la misma organizacion.");
        String hash=documentHash(documentType,documentNumber);ensureStableKey();
        bind(hash,candidate.getId(),reviewer.getId(),"CONDUCTOR_DOSSIER","Reviewed onboarding request "+requestId);
        repository.bridgeConductor(hash);
        audit.record(reviewer,"PERSON_IDENTITY_REVIEWED","APP_USER",candidate.getId(),Map.of("reviewKind","CONDUCTOR_DOSSIER"));
    }
    @Transactional(readOnly=true)
    public void requireVerified(UUID userId){if(!repository.verified(userId))throw conflict("La identidad documental requiere comprobacion manual autorizada.");}
    public boolean verified(UUID userId){return repository.verified(userId);}
    private void reviewAndBind(AppUser reviewer,AppUser target,IdentityReview review,String method){
        if(review==null||!Boolean.TRUE.equals(review.originalReviewed())||!Boolean.TRUE.equals(review.identityConfirmed()))
            throw forbidden("Confirma la revision visual del original y la identidad de la misma persona.");
        if(!Set.of("IN_PERSON","PRIOR_SECURE_CONTACT").contains(review.method()))throw validation("Metodo de revision no autorizado.");
        safeReference(review.reference());String hash=documentHash(review.documentType(),review.documentNumber());
        String reference=review.reference().toLowerCase(Locale.ROOT);
        if(reference.replaceAll("[^0-9]","").contains(review.documentNumber())||
                reference.contains(target.getEmail().toLowerCase(Locale.ROOT))||
                (target.getFullName().length()>3&&reference.contains(target.getFullName().toLowerCase(Locale.ROOT))))
            throw validation("La referencia de revision no debe contener los datos personales comprobados.");
        ensureStableKey();
        bind(hash,target.getId(),reviewer.getId(),method,review.reference().strip());
        audit.record(reviewer,"PERSON_IDENTITY_REVIEWED","APP_USER",target.getId(),Map.of("reviewKind",method,"originalRetained",false));
    }
    private void bind(String hash,UUID userId,UUID reviewer,String method,String reference){
        // The authenticated writer already holds its actor lock. Never wait on
        // another person's lock while retaining that actor: reject contention
        // explicitly rather than introducing an actor/person lock inversion.
        if(!repository.lockPerson(hash))throw conflict("Existe una revision de identidad concurrente; consulta el resultado antes de volver a intentarlo.");
        repository.lockUser(userId);
        // A legacy document is only a conflict signal, never proof authorizing
        // implicit adoption. Its original account must be reviewed separately.
        for(var legacy:repository.driverDocuments()){
            if(!Objects.equals(legacy.userId(),userId)&&"DNI".equalsIgnoreCase(legacy.documentType())&&legacy.documentNumber()!=null&&
                    legacy.documentNumber().matches("[0-9]{8}")&&hash.equals(documentHash("DNI",legacy.documentNumber())))
                throw conflict("Los registros anteriores requieren conciliacion manual antes de vincular esta identidad a otra cuenta.");
        }
        var existing=repository.byUser(userId);
        if(existing.isPresent()){
            if(!existing.get().personHash().equals(hash))throw conflict("La cuenta ya corresponde a otra identidad; una correccion requiere un procedimiento autorizado.");
            return;
        }
        if(repository.byHash(hash).isPresent())throw conflict("La persona ya tiene una cuenta; utiliza su recuperacion o el procedimiento autorizado de vinculacion.");
        repository.bind(hash,userId,reviewer,method,reference,now());
    }
    private String documentHash(String type,String number){
        if(!"DNI".equals(type)||number==null||!number.matches("[0-9]{8}"))throw validation("El piloto requiere DNI de ocho digitos revisado; no se consulta RENIEC.");
        return hasher.hashOrigin("conductor-person:DNI:"+number);
    }
    private void ensureStableKey(){
        String fingerprint=hasher.hashOrigin("person-identity:key-fingerprint:v1");
        var existing=repository.keyFingerprint();
        if(existing.isEmpty()){
            for(var legacy:repository.legacyDocuments()){
                if(!"DNI".equalsIgnoreCase(legacy.documentType())||legacy.documentNumber()==null||
                        !legacy.personHash().equals(documentHash("DNI",legacy.documentNumber().strip())))
                    throw conflict("La clave o los vinculos documentales previos requieren una revision tecnica antes de nuevas vinculaciones.");
            }
            repository.initializeKey(fingerprint,now());existing=repository.keyFingerprint();
        }
        if(existing.isEmpty()||!existing.get().equals(fingerprint))throw conflict("La clave estable de identidad cambio; restaura la configuracion autorizada antes de vincular personas.");
    }
    private void safeReference(String text){if(text==null||text.isBlank()||text.length()>160||UNSAFE_REFERENCE.matcher(text).find())throw validation("Usa una referencia de revision sin DNI, telefono, correo ni secretos.");}
    private void safeReason(String text){if(text==null||text.isBlank()||text.length()>500||UNSAFE_REFERENCE.matcher(text).find())throw validation("Usa un motivo de hasta 500 caracteres sin DNI, telefono, correo ni secretos.");}
    private Instant now(){return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);}
    private ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private ApplicationException forbidden(String message){return new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,message);}
    private ApplicationException validation(String message){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,message);}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"La cuenta ADMIN no existe.");}
}
