package pe.rutafija.personidentity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.identity.application.LoginRateLimiter;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.personidentity.api.PersonIdentityContract.*;
import pe.rutafija.personidentity.infrastructure.PersonIdentityRepository;
import pe.rutafija.personidentity.infrastructure.PersonIdentityReviewCorrectionRepository;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.terms.application.AdminTermsService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** A personally witnessed typo correction before any binding; never a change of account owner. */
@Service
public class PersonIdentityReviewCorrectionService {
    private final CurrentUserService current;
    private final AppUserRepository users;
    private final DriverRepository drivers;
    private final PersonIdentityRepository identities;
    private final PersonIdentityService reviews;
    private final PersonIdentityReviewCorrectionRepository corrections;
    private final AdminTermsService terms;
    private final PasswordEncoder passwords;
    private final LoginRateLimiter limiter;
    private final InvitationCodeHasher hasher;
    private final AuditService audit;
    private final Clock clock;
    public PersonIdentityReviewCorrectionService(CurrentUserService current,AppUserRepository users,
            DriverRepository drivers,PersonIdentityRepository identities,PersonIdentityService reviews,
            PersonIdentityReviewCorrectionRepository corrections,AdminTermsService terms,PasswordEncoder passwords,
            LoginRateLimiter limiter,InvitationCodeHasher hasher,AuditService audit,Clock clock){
        this.current=current;this.users=users;this.drivers=drivers;this.identities=identities;this.reviews=reviews;
        this.corrections=corrections;this.terms=terms;this.passwords=passwords;this.limiter=limiter;
        this.hasher=hasher;this.audit=audit;this.clock=clock;
    }
    @Transactional
    public Status correct(UUID driverId,ConductorReviewCorrection command){
        var actor=current.requireTenantActor();
        if(actor.getRole()!=UserRole.ADMIN||!actor.isEmailVerified())throw forbidden();
        reviews.requireVerified(actor.getId());
        if(!terms.hasCurrentAcceptanceForCurrentActor())throw forbidden();
        String rateKey="person-conductor-review-correction:"+actor.getId();limiter.checkAndRecordAttempt(rateKey);
        if(!passwords.matches(command.currentPassword(),actor.getPasswordHash()))throw new InvalidCredentialsException();
        if(!Boolean.TRUE.equals(command.dataEntryErrorConfirmed())||!Boolean.TRUE.equals(command.samePersonConfirmed()))
            throw validation("Confirma el error de transcripcion y que corresponde a la misma persona ya registrada.");
        UUID targetId=identities.driverUserId(driverId,actor.getOrganizationId()).orElseThrow(this::notFound);
        var target=users.findByIdForUpdate(targetId).orElseThrow(this::notFound);
        var driver=drivers.lockByIdAndOrganizationId(driverId,actor.getOrganizationId()).orElseThrow(this::notFound);
        if(target.getRole()!=UserRole.CONDUCTOR||!target.isActive()||!Objects.equals(target.getOrganizationId(),actor.getOrganizationId())
                ||driver.getUser()==null||!targetId.equals(driver.getUser().getId()))
            throw conflict("La correccion requiere la cuenta CONDUCTOR activa ya vinculada a esta ficha y organizacion.");
        var review=command.identityReview();
        String correctedHash=reviews.validateConductorReviewCorrection(target,review,command.reason());
        if(!review.documentType().equals(driver.getDocumentType())||!review.documentNumber().equals(driver.getDocumentNumber()))
            throw conflict("El DNI original vuelto a revisar debe coincidir exactamente con la ficha heredada. Este flujo corrige la revision, no modifica documentos ni cambia de persona.");
        String reference=review.reference().strip(),reason=command.reason().strip();
        // A keyed payload proof excludes the password and never stores a brute-forceable raw DNI digest.
        String payload=hasher.hashOrigin("person-review-correction:v1:"+actor.getId()+":"+driverId+":"+targetId+":"+
                correctedHash+":"+review.method()+":"+reference.length()+":"+reference+":"+reason.length()+":"+reason+":true:true");
        var prior=corrections.prior(actor.getId(),command.operationKey());
        if(prior.isPresent()){
            var saved=prior.get();
            if(!saved.userId().equals(targetId)||!saved.driverId().equals(driverId)||!saved.organizationId().equals(actor.getOrganizationId())
                    ||!saved.payloadHash().equals(payload)||!saved.correctedHash().equals(correctedHash)
                    ||identities.byUser(targetId).filter(value->value.personHash().equals(correctedHash)).isEmpty())
                throw conflict("La clave de correccion ya tiene otro contenido o su resultado requiere revision autorizada.");
            limiter.recordSuccess(rateKey);return new Status(targetId,true);
        }
        var previous=identities.byUser(targetId).orElseThrow(()->conflict("No existe una revision anterior que corregir. Utiliza la revision personal inicial."));
        if(previous.personHash().equals(correctedHash))throw conflict("La revision ya coincide con la ficha. No hay un error pendiente que corregir.");
        if(driver.isActive()||Set.of(DriverAvailabilityStatus.RESERVADO,DriverAvailabilityStatus.EN_SERVICIO,DriverAvailabilityStatus.ATENCION).contains(driver.getAvailabilityStatus()))
            throw conflict("La correccion exige una ficha inactiva y sin servicio ni atencion pendientes.");
        if(corrections.hasBindingOrProvision(targetId,driverId,previous.personHash(),correctedHash))
            throw conflict("La persona ya tiene una asociacion o expediente vinculado. Se requiere otro procedimiento autorizado; no se modifica su identidad.");
        if(corrections.hasOpenWork(driverId))throw conflict("Finaliza las asignaciones pendientes antes de corregir la revision.");
        for(String personHash:List.of(previous.personHash(),correctedHash).stream().sorted().toList())
            if(!identities.lockPerson(personHash))throw conflict("Existe otra revision concurrente de esta identidad. Consulta su resultado antes de continuar.");
        if(identities.byHash(correctedHash).filter(value->!targetId.equals(value.userId())).isPresent())
            throw conflict("La persona revisada ya corresponde a otra cuenta. No se permite adoptar ni fusionar identidades.");
        UUID correctionId=corrections.correct(UUID.randomUUID(),actor.getId(),targetId,driverId,actor.getOrganizationId(),
                previous.personHash(),correctedHash,review.method(),reference,reason,command.operationKey(),payload,
                Instant.now(clock).truncatedTo(ChronoUnit.MICROS));
        audit.record(actor,"CONDUCTOR_IDENTITY_REVIEW_CORRECTED","DRIVER",driverId,
                Map.of("correctionId",correctionId.toString(),"sameAccountPreserved",true,"originalRetained",false,
                        "documentaryApprovalGranted",false,"emailVerificationGranted",false,"operationalEnabled",false));
        limiter.recordSuccess(rateKey);return new Status(targetId,true);
    }
    private ApplicationException forbidden(){return new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La correccion requiere ADMIN con correo, identidad y terminos vigentes de su organizacion.");}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"La ficha no existe en el alcance autorizado.");}
    private ApplicationException conflict(String text){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,text);}
    private ApplicationException validation(String text){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,text);}
}
