package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.DriverAvailabilityStatus;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.*;
import pe.rutafija.onboarding.infrastructure.ConductorDocumentaryRepository;
import pe.rutafija.onboarding.infrastructure.LegacyConductorRegularizationRepository;
import pe.rutafija.personidentity.application.PersonIdentityService;
import pe.rutafija.shared.exception.*;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.terms.application.AdminTermsService;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** A personally reviewed existing account gets a pending dossier, never an inferred approval. */
@Service
public class LegacyConductorRegularizationService {
    private static final Pattern PRIVATE_TEXT=Pattern.compile("[0-9]{6,}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private final LegacyConductorRegularizationRepository legacy;
    private final ConductorDocumentaryRepository documentary;
    private final CurrentUserService current;
    private final AppUserRepository users;
    private final DriverRepository drivers;
    private final PersonIdentityService identities;
    private final AdminTermsService terms;
    private final PasswordEncoder passwords;
    private final OpaqueTokenService tokens;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;
    public LegacyConductorRegularizationService(LegacyConductorRegularizationRepository legacy,
            ConductorDocumentaryRepository documentary,CurrentUserService current,AppUserRepository users,
            DriverRepository drivers,PersonIdentityService identities,AdminTermsService terms,PasswordEncoder passwords,
            OpaqueTokenService tokens,RefreshTokenRepository refreshTokens,AuditService audit,Clock clock){
        this.legacy=legacy;this.documentary=documentary;this.current=current;this.users=users;this.drivers=drivers;
        this.identities=identities;this.terms=terms;this.passwords=passwords;this.tokens=tokens;
        this.refreshTokens=refreshTokens;this.audit=audit;this.clock=clock;
    }
    @Transactional
    public LegacyRegularizationOpened open(UUID driverId,OpenLegacyRegularization command){
        AppUser actor=current.requireTenantActor();
        if(actor.getRole()!=UserRole.ADMIN||!actor.isEmailVerified())throw forbidden();
        identities.requireVerified(actor.getId());
        if(!terms.hasCurrentAcceptanceForCurrentActor())throw forbidden();
        if(!Boolean.TRUE.equals(command.identityPreservedConfirmed()))throw conflict("Confirma que conservaras la cuenta y la ficha existentes.");
        if(command.reason()==null||command.reason().isBlank()||command.reason().length()>500||PRIVATE_TEXT.matcher(command.reason()).find())
            throw conflict("Registra un motivo de regularizacion sin documentos, correos, telefonos ni secretos.");
        String payload=ConductorDocumentaryCatalog.hash("LEGACY_REGULARIZATION_OPENED:"+driverId+":"+documentary.write(command));
        UUID targetId=legacy.driverUserId(driverId,actor.getOrganizationId()).orElseThrow(this::notFound);
        var prior=legacy.openingOperation(actor.getId(),command.operationKey(),actor.getOrganizationId());
        if(prior.isPresent()){
            if(!prior.get().payloadHash().equals(payload)||!driverId.equals(prior.get().driverId()))
                throw conflict("La clave de operacion ya tiene otro contenido.");
            return new LegacyRegularizationOpened(prior.get().requestId(),prior.get().status(),prior.get().userId(),prior.get().driverId());
        }
        AppUser target=users.findByIdForUpdate(targetId).orElseThrow(this::notFound);
        var driver=drivers.lockByIdAndOrganizationId(driverId,actor.getOrganizationId()).orElseThrow(this::notFound);
        if(!target.isActive()||target.getRole()!=UserRole.CONDUCTOR||!Objects.equals(target.getOrganizationId(),actor.getOrganizationId())
                ||driver.getUser()==null||!targetId.equals(driver.getUser().getId()))
            throw conflict("La ficha requiere una cuenta CONDUCTOR activa y un vinculo explicito de la misma organizacion.");
        String reason=command.reason().toLowerCase(Locale.ROOT);
        if(reason.contains(target.getEmail().toLowerCase(Locale.ROOT))||target.getFullName().length()>3&&reason.contains(target.getFullName().toLowerCase(Locale.ROOT)))
            throw conflict("El motivo no debe repetir datos personales de la cuenta.");
        String personHash=identities.requireReviewedConductorDocument(targetId,driver.getDocumentType(),driver.getDocumentNumber());
        if(legacy.hasBinding(personHash,targetId,driverId)||legacy.hasOnboardingLink(targetId,driverId))
            throw conflict("La persona o la cuenta ya tiene un expediente asociado. Consulta Solicitudes; no se abre otro expediente.");
        if(legacy.hasOpenWork(driverId)||Set.of(DriverAvailabilityStatus.RESERVADO,DriverAvailabilityStatus.EN_SERVICIO,DriverAvailabilityStatus.ATENCION).contains(driver.getAvailabilityStatus()))
            throw conflict("Finaliza las asignaciones o atenciones pendientes antes de regularizar esta ficha.");
        Instant now=Instant.now(clock).truncatedTo(ChronoUnit.MICROS);UUID identityId=UUID.randomUUID();
        if(!legacy.createIdentity(identityId,target.getEmail(),passwords.encode(tokens.generate()),target.getFullName(),target.getPhone(),now))
            throw conflict("Ya existe una identidad preliminar con ese correo. Se requiere conciliacion autorizada; no se adopta automaticamente.");
        UUID invitationId=legacy.createInternalInvitation(actor.getOrganizationId(),actor.getId(),target.getEmail(),tokens.hash(tokens.generate()),now);
        UUID requestId=legacy.createRequest(identityId,actor.getOrganizationId(),invitationId,command.operationKey(),now);
        if(!legacy.bind(personHash,identityId,actor.getOrganizationId(),targetId,driverId,now))
            throw conflict("La persona ya tiene un vinculo de incorporacion. Consulta su expediente.");
        driver.deactivate();drivers.flush();
        refreshTokens.revokeActiveByUserId(targetId,now);legacy.invalidateSessions(targetId);
        var state=documentary.find(requestId,actor.getOrganizationId(),null).orElseThrow(this::notFound);
        documentary.event(state,"ADMIN",actor.getId(),command.operationKey(),payload,LegacyConductorRegularizationRepository.OPENED,
                command.reason().strip(),now,requestId);
        audit.record(actor,"CONDUCTOR_LEGACY_REGULARIZATION_OPENED","CONDUCTOR_ONBOARDING_REQUEST",requestId,
                Map.of("userId",targetId.toString(),"driverId",driverId.toString(),"personalApprovalGranted",false,
                        "emailVerificationGranted",false,"invitationDelivered",false,"operationalEnabled",false));
        return new LegacyRegularizationOpened(requestId,state.status(),targetId,driverId);
    }
    private ApplicationException forbidden(){return new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,"La regularizacion requiere ADMIN con identidad, correo y terminos vigentes de su organizacion.");}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"La ficha no existe en el alcance autorizado.");}
    private ApplicationException conflict(String text){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,text);}
}
