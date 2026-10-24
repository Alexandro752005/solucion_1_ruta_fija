package pe.rutafija.adminlifecycle.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.adminlifecycle.api.AdminLifecycleContract.*;
import pe.rutafija.adminlifecycle.infrastructure.AdminActivationRepository;
import pe.rutafija.adminlifecycle.infrastructure.AdminActivationRepository.Row;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.*;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.pilotquota.application.PilotCreationQuotaService;
import pe.rutafija.personidentity.application.PersonIdentityService;
import pe.rutafija.personidentity.api.PersonIdentityContract.IdentityReview;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;
import pe.rutafija.terms.api.dto.*;
import pe.rutafija.terms.application.AdminTermsService;

import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;

/** Manages inactive ADMIN candidates; possession of a scoped credential never grants a CRM role. */
@Service
public class AdminLifecycleService {
    private static final Pattern CREDENTIAL=Pattern.compile("ac1\\.[A-Za-z0-9_-]{43}");
    private static final Pattern ACTIVATION=Pattern.compile("aa1\\.[A-Za-z0-9_-]{43}");
    private static final Duration CREDENTIAL_TTL=Duration.ofHours(24),SCOPE_TTL=Duration.ofMinutes(30);
    private final AdminActivationRepository repository;
    private final AppUserRepository users;
    private final OrganizationRepository organizations;
    private final CurrentUserService current;
    private final PilotCreationQuotaService quota;
    private final PasswordEncoder passwords;
    private final OpaqueTokenService tokens;
    private final LoginRateLimiter limiter;
    private final EmailOtpService otp;
    private final AdminTermsService terms;
    private final RefreshTokenRepository refreshTokens;
    private final AuditService audit;
    private final Clock clock;
    private final PersonIdentityService identities;

    public AdminLifecycleService(AdminActivationRepository repository,AppUserRepository users,
            OrganizationRepository organizations,CurrentUserService current,PilotCreationQuotaService quota,
            PasswordEncoder passwords,OpaqueTokenService tokens,LoginRateLimiter limiter,EmailOtpService otp,
            AdminTermsService terms,RefreshTokenRepository refreshTokens,AuditService audit,Clock clock,PersonIdentityService identities) {
        this.repository=repository;this.users=users;this.organizations=organizations;this.current=current;this.quota=quota;
        this.passwords=passwords;this.tokens=tokens;this.limiter=limiter;this.otp=otp;this.terms=terms;
        this.refreshTokens=refreshTokens;this.audit=audit;this.clock=clock;
        this.identities=identities;
    }

    @Transactional(readOnly=true)
    public List<Summary> list(){superAdmin();return repository.list();}

    @Transactional
    public Created create(Create command) {
        AppUser actor=superAdmin();
        if(!repository.lockOrganization(command.organizationId()))throw conflict("La organizacion debe estar activa.");
        return createCandidate(actor,command.organizationId(),command.email(),command.fullName(),command.reason(),null,null,null,command.identityReview());
    }

    @Transactional
    public Created replacement(UUID outgoingId,Replacement command) {
        AppUser actor=superAdmin();
        if(!Boolean.TRUE.equals(command.identityChecked())||!Boolean.TRUE.equals(command.replacementAuthorized())
                ||!Set.of("PRIOR_SECURE_CONTACT","IN_PERSON").contains(command.verificationMethod())||command.verificationReference().isBlank())
            throw forbidden("La sustitucion requiere comprobacion independiente y autorizacion expresas.");
        var hint=users.findOneById(outgoingId).filter(user->user.getRole()==UserRole.ADMIN&&user.getOrganizationId()!=null).orElseThrow(this::notFound);
        if(!repository.lockOrganization(hint.getOrganizationId()))throw conflict("La organizacion debe estar activa.");
        AppUser outgoing=users.findByIdForUpdate(outgoingId).orElseThrow(this::notFound);
        if(outgoing.getRole()!=UserRole.ADMIN||(!outgoing.isActive()&&!repository.authorizedRevocation(outgoingId)))
            throw conflict("Se requiere el ADMIN saliente vigente o revocado previamente por SUPER_ADMIN.");
        if(repository.completedReplacement(outgoingId))throw conflict("El saliente ya tiene un reemplazo completado y requiere revisar su historia.");
        if(repository.pendingReplacement(outgoingId))throw conflict("Ya hay un reemplazo pendiente para esa cuenta ADMIN.");
        return createCandidate(actor,outgoing.getOrganizationId(),command.email(),command.fullName(),command.reason(),outgoingId,
                command.verificationMethod(),command.verificationReference(),command.identityReview());
    }

    @Transactional
    public Summary revoke(UUID userId,Rotate command) {
        AppUser actor=superAdmin();
        AppUser hint=users.findOneById(userId).filter(user->user.getRole()==UserRole.ADMIN&&user.getOrganizationId()!=null).orElseThrow(this::notFound);
        if(!repository.lockOrganization(hint.getOrganizationId()))throw conflict("La organizacion debe estar activa.");
        AppUser target=users.findByIdForUpdate(userId).orElseThrow(this::notFound);
        if(target.getRole()!=UserRole.ADMIN)throw forbidden("La revocacion corresponde a una cuenta ADMIN.");
        target.deactivate();users.flush();Instant now=now();
        refreshTokens.revokeActiveByUserId(userId,now);otp.invalidateForEmailChange(userId);repository.revokeSessionsVersion(userId);
        // A security revocation must also invalidate a pending candidate's scoped access.
        // The undisclosed random replacement can only be replaced by a new explicit SUPER_ADMIN issuance.
        repository.byUser(userId).filter(value->!"COMPLETED".equals(value.status())).ifPresent(value->{
            repository.lock(value.id());
            repository.rotate(value.id(),tokens.hash("ac1."+tokens.generate()),now,now.plus(CREDENTIAL_TTL));
        });
        repository.securityRevocation(userId,target.getOrganizationId(),actor.getId(),command.reason(),now);
        audit.record(actor,"ADMIN_SECURITY_REVOKED","APP_USER",userId,Map.of());
        return repository.list().stream().filter(value->value.adminUserId().equals(userId)).findFirst().orElseThrow(this::notFound);
    }

    private Created createCandidate(AppUser actor,UUID organizationId,String requestedEmail,String fullName,String reason,
                                    UUID outgoingId,String method,String reference,IdentityReview identityReview) {
        String email=requestedEmail.strip().toLowerCase(Locale.ROOT);
        if(users.existsByEmailIgnoreCase(email))throw conflict("El correo ya esta asociado a una cuenta; utiliza su flujo autorizado.");
        quota.requireAvailable(UserRole.ADMIN);
        var organization=organizations.findById(organizationId).orElseThrow(this::notFound);
        AppUser candidate=AppUser.organizationUser(organization,email,passwords.encode(tokens.generate()),fullName,UserRole.ADMIN);
        candidate.deactivate();candidate=users.saveAndFlush(candidate);
        identities.bindNewAdmin(actor,candidate,identityReview);
        Instant now=now(),expiresAt=now.plus(CREDENTIAL_TTL);String credential="ac1."+tokens.generate();UUID id=UUID.randomUUID();
        repository.insert(id,candidate.getId(),organizationId,actor.getId(),outgoingId,method,reference,reason,tokens.hash(credential),now,expiresAt);
        repository.event(id,outgoingId==null?"ADMIN_CREATED":"ADMIN_REPLACEMENT_PREPARED",actor.getId(),now,reason);
        Map<String,Object> details=new HashMap<>();details.put("activationId",id.toString());details.put("status","PENDING_CLAIM");
        if(outgoingId!=null){details.put("outgoingAdminUserId",outgoingId.toString());details.put("verificationMethod",method);}
        audit.record(actor,outgoingId==null?"ADMIN_CREATED":"ADMIN_REPLACEMENT_PREPARED","APP_USER",candidate.getId(),details);
        return new Created(candidate.getId(),organizationId,id,"PENDING_CLAIM",credential,expiresAt);
    }

    @Transactional
    public Created rotate(UUID candidateId,Rotate command) {
        AppUser actor=superAdmin();Row hint=repository.byUser(candidateId).orElseThrow(this::notFound);
        Row row=lock(hint);requireInactive(row);
        if("COMPLETED".equals(row.status()))throw conflict("Una cuenta activada no puede reiniciarse por esta ruta.");
        // Invalidating the challenge preserves rolling send counts and verification blocks.
        otp.invalidateForEmailChange(candidateId);
        Instant now=now(),expiresAt=now.plus(CREDENTIAL_TTL);String credential="ac1."+tokens.generate();
        repository.rotate(row.id(),tokens.hash(credential),now,expiresAt);
        refreshTokens.revokeActiveByUserId(candidateId,now);
        repository.event(row.id(),"ACTIVATION_CREDENTIAL_ROTATED",actor.getId(),now,command.reason());
        audit.record(actor,"ADMIN_ACTIVATION_CREDENTIAL_ROTATED","APP_USER",candidateId,Map.of("activationId",row.id().toString()));
        return new Created(candidateId,row.organizationId(),row.id(),"PENDING_CLAIM",credential,expiresAt);
    }

    @Transactional
    public Claimed claim(Claim command) {
        if(command.credential()==null||!CREDENTIAL.matcher(command.credential()).matches())throw unauthorized();
        String hash=tokens.hash(command.credential());limiter.checkAndRecordAttempt("admin-activation:"+hash);
        Row hint=repository.byCredential(hash).orElseThrow(this::unauthorized);Row row=lock(hint);requireInactive(row);
        if(!"PENDING_CLAIM".equals(row.status())||row.credentialConsumedAt()!=null||!row.credentialExpiresAt().isAfter(now())
                ||!row.credentialHash().equals(hash))throw unauthorized();
        if(command.newPassword().getBytes(StandardCharsets.UTF_8).length>72)throw validation("La contrasena elegida excede la longitud admitida.");
        Instant now=now(),expiresAt=now.plus(SCOPE_TTL);String token="aa1."+tokens.generate();
        repository.setPassword(row.userId(),passwords.encode(command.newPassword()),now);
        repository.claim(row.id(),tokens.hash(token),now,expiresAt);
        repository.event(row.id(),"ACTIVATION_CREDENTIAL_CLAIMED",row.userId(),now,null);
        limiter.recordSuccess("admin-activation:"+hash);
        return new Claimed(row.id(),token,expiresAt,"IN_PROGRESS");
    }

    @Transactional(readOnly=true)
    public Status status(String token){return status(scope(token,false,false));}

    @Transactional
    public EmailOtpTicket beginOtp(String token) {
        Row row=scope(token,false,true);requireInactive(row);
        if(row.emailVerifiedAt()!=null)throw conflict("El correo ya fue verificado en esta activacion.");
        return otp.beginForAdminActivation(users.findOneById(row.userId()).orElseThrow(this::notFound),row.id());
    }

    @Transactional
    public EmailOtpTicket resendOtp(String token,OtpToken command) {
        Row row=scope(token,false,true);requireInactive(row);
        if(row.emailVerifiedAt()!=null)throw conflict("El correo ya fue verificado en esta activacion.");
        return otp.resendAdminActivation(row.id(),row.userId(),command.verificationToken());
    }

    @Transactional(noRollbackFor=EmailOtpCodeRejectedException.class)
    public Status verifyOtp(String token,VerifyOtp command) {
        Row row=scope(token,false,true);requireInactive(row);
        if(row.emailVerifiedAt()!=null)return status(row);
        otp.verifyAdminActivation(row.id(),row.userId(),command.verificationToken(),command.code());
        repository.markEmailVerified(row.id(),now());repository.event(row.id(),"EMAIL_VERIFIED",row.userId(),now(),null);
        return status(repository.byId(row.id()).orElseThrow(this::notFound));
    }

    @Transactional
    public AdminTermsCurrentResponse currentTerms(String token) {
        Row row=scope(token,false,true);requireEmail(row);
        return terms.getCurrentTermsForActivation(row.userId(),row.organizationId());
    }

    @Transactional
    public AdminTermsAcceptanceResponse acceptTerms(String token,AcceptAdminTermsRequest command) {
        Row row=scope(token,false,true);requireEmail(row);
        var response=terms.acceptCurrentTermsForActivation(row.userId(),row.organizationId(),command);
        repository.event(row.id(),"TERMS_ACCEPTED",row.userId(),now(),null);
        return response;
    }

    @Transactional
    public Status complete(String token) {
        Row row=scope(token,true,true);
        if("COMPLETED".equals(row.status()))return status(row);
        requireEmail(row);
        if(!terms.hasCurrentAcceptanceForActivation(row.userId(),row.organizationId()))
            throw conflict("Falta la aceptacion expresa de los terminos aprobados actualmente vigentes.");
        AppUser candidate=users.findByIdForUpdate(row.userId()).orElseThrow(this::notFound);
        identities.requireVerified(candidate.getId());
        AppUser outgoing=null;
        if(row.outgoingId()!=null) {
            outgoing=users.findByIdForUpdate(row.outgoingId()).orElseThrow(this::notFound);
            if(outgoing.getRole()!=UserRole.ADMIN||!row.organizationId().equals(outgoing.getOrganizationId())
                    ||(!outgoing.isActive()&&!repository.authorizedRevocation(outgoing.getId()))||repository.completedReplacement(outgoing.getId()))
                throw conflict("El ADMIN saliente ya cambio; requiere una nueva comprobacion autorizada.");
            outgoing.deactivate();
        }
        candidate.activate();users.flush();
        Instant now=now();
        refreshTokens.revokeActiveByUserId(candidate.getId(),now);
        otp.invalidateForEmailChange(candidate.getId());
        if(outgoing!=null) {
            refreshTokens.revokeActiveByUserId(outgoing.getId(),now);
            otp.invalidateForEmailChange(outgoing.getId());
            repository.revokeSessionsVersion(outgoing.getId());
        }
        repository.complete(row.id(),now);repository.event(row.id(),"ACTIVATION_COMPLETED",candidate.getId(),now,null);
        audit.record(candidate,row.outgoingId()==null?"ADMIN_ACTIVATED":"ADMIN_REPLACEMENT_COMPLETED","APP_USER",candidate.getId(),
                row.outgoingId()==null?Map.of("activationId",row.id().toString()):Map.of("activationId",row.id().toString(),"outgoingAdminUserId",row.outgoingId().toString()));
        return status(repository.byId(row.id()).orElseThrow(this::notFound));
    }

    private Row lock(Row hint) {
        if(!repository.lockOrganization(hint.organizationId()))throw conflict("La organizacion no esta activa.");
        List<UUID> ids=new ArrayList<>();ids.add(hint.userId());if(hint.outgoingId()!=null)ids.add(hint.outgoingId());ids.sort(Comparator.naturalOrder());
        for(UUID id:ids)users.findByIdForUpdate(id).orElseThrow(this::notFound);
        return repository.lock(hint.id()).orElseThrow(this::notFound);
    }
    private Row scope(String token,boolean completedReplay,boolean lock) {
        if(token==null||!ACTIVATION.matcher(token).matches())throw unauthorized();
        Row row=repository.byToken(tokens.hash(token)).orElseThrow(this::unauthorized);if(lock)row=lock(row);
        if(row.tokenHash()==null||!row.tokenHash().trim().equals(tokens.hash(token))||row.expiresAt()==null||!row.expiresAt().isAfter(now()))throw unauthorized();
        if("COMPLETED".equals(row.status())){if(!completedReplay)throw unauthorized();}
        else if(!"IN_PROGRESS".equals(row.status())||row.revokedAt()!=null)throw unauthorized();
        if(!completedReplay||!"COMPLETED".equals(row.status()))requireInactive(row);
        return row;
    }
    private void requireInactive(Row row) {
        AppUser user=users.findOneById(row.userId()).orElseThrow(this::notFound);
        if(user.getRole()!=UserRole.ADMIN||user.isActive()||!row.organizationId().equals(user.getOrganizationId())||!user.getOrganization().isActive())
            throw forbidden("La cuenta no es un ADMIN inactivo pendiente de activacion autorizada.");
    }
    private void requireEmail(Row row){requireInactive(row);if(row.passwordSetAt()==null||row.emailVerifiedAt()==null)throw conflict("Completa la contrasena y verifica el correo antes de continuar.");}
    private Status status(Row row){return new Status(row.id(),row.userId(),row.organizationId(),row.status(),row.passwordSetAt()!=null,row.emailVerifiedAt()!=null,
            terms.hasCurrentAcceptanceForActivation(row.userId(),row.organizationId()),row.expiresAt(),row.outgoingId()!=null,identities.verified(row.userId()));}
    private AppUser superAdmin(){AppUser actor=current.requireSuperAdminActor();if(!actor.isEmailVerified())throw forbidden("Se requiere SUPER_ADMIN verificado.");identities.requireVerified(actor.getId());return actor;}
    private Instant now(){return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);}
    private ApplicationException notFound(){return new ApplicationException(HttpStatus.NOT_FOUND,ErrorCode.RESOURCE_NOT_FOUND,"La cuenta o activacion solicitada no existe.");}
    private ApplicationException conflict(String message){return new ApplicationException(HttpStatus.CONFLICT,ErrorCode.RESOURCE_CONFLICT,message);}
    private ApplicationException forbidden(String message){return new ApplicationException(HttpStatus.FORBIDDEN,ErrorCode.FORBIDDEN_ROLE,message);}
    private ApplicationException validation(String message){return new ApplicationException(HttpStatus.BAD_REQUEST,ErrorCode.VALIDATION_ERROR,message);}
    private ApplicationException unauthorized(){return new ApplicationException(HttpStatus.UNAUTHORIZED,ErrorCode.AUTH_TOKEN_INVALID,"La credencial temporal no es valida, fue usada o ha expirado.");}
}
