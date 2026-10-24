package pe.rutafija.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.fleet.domain.Driver;
import pe.rutafija.fleet.infrastructure.DriverRepository;
import pe.rutafija.identity.api.dto.ConductorRecoveryOtpResponse;
import pe.rutafija.identity.api.dto.ConductorRecoveryStartedResponse;
import pe.rutafija.identity.api.dto.ConductorRecoveryStatusResponse;
import pe.rutafija.identity.api.dto.StartConductorRecoveryRequest;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.ConductorRecoveryRequest;
import pe.rutafija.identity.domain.ConductorRecoveryStatus;
import pe.rutafija.identity.domain.RecoveryEvidenceMethod;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.ConductorRecoveryRequestRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.CurrentUserService;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ConductorRecoveryService {

    /** Provisional operational lifetime; Q-015 does not prescribe an exact duration. */
    static final Duration PASSWORD_RESET_TOKEN_TTL = Duration.ofMinutes(30);
    private static final List<ConductorRecoveryStatus> ACTIVE_STATUSES = List.of(
            ConductorRecoveryStatus.OTP_PENDING,
            ConductorRecoveryStatus.PASSWORD_RESET_PENDING
    );

    private final AppUserRepository userRepository;
    private final ConductorRecoveryRequestRepository recoveryRepository;
    private final DriverRepository driverRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailOtpService emailOtpService;
    private final OpaqueTokenService opaqueTokenService;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;
    private final Clock clock;

    public ConductorRecoveryService(
            AppUserRepository userRepository,
            ConductorRecoveryRequestRepository recoveryRepository,
            DriverRepository driverRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailOtpService emailOtpService,
            OpaqueTokenService opaqueTokenService,
            PasswordEncoder passwordEncoder,
            CurrentUserService currentUserService,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.recoveryRepository = recoveryRepository;
        this.driverRepository = driverRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailOtpService = emailOtpService;
        this.opaqueTokenService = opaqueTokenService;
        this.passwordEncoder = passwordEncoder;
        this.currentUserService = currentUserService;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public ConductorRecoveryStartedResponse authorizeAndStart(
            UUID conductorUserId,
            StartConductorRecoveryRequest request
    ) {
        AppUser admin = requireAdminActor();
        AppUser conductor = lockTenantConductor(conductorUserId, admin.getOrganizationId());
        Driver driver = driverRepository.findByUser_Id(conductor.getId())
                .filter(found -> found.getOrganizationId().equals(admin.getOrganizationId()))
                .orElseThrow(this::notFound);
        if (!driver.getUser().getId().equals(conductor.getId())) {
            throw notFound();
        }
        if (!Boolean.TRUE.equals(request.expedienteReviewed())
                || !Boolean.TRUE.equals(request.independentEvidenceConfirmed())) {
            throw invalidEvidenceConfirmation();
        }
        if (recoveryRepository.existsByConductorUserIdAndStatusIn(conductor.getId(), ACTIVE_STATUSES)) {
            throw conflict("Ya existe una recuperación asistida pendiente para esta cuenta");
        }

        String newEmail = normalizeEmail(request.newEmail());
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, conductor.getId())) {
            throw conflict("El correo solicitado ya está asociado a otra cuenta");
        }
        if (newEmail.equals(conductor.getEmail())) {
            throw conflict("Indique un correo nuevo para recuperar el canal");
        }

        Instant now = Instant.now(clock);
        RecoveryEvidenceMethod evidenceMethod = request.evidenceMethod();
        String evidenceReference = request.evidenceReference().strip();
        String reason = request.reason().strip();
        ConductorRecoveryRequest recovery = recoveryRepository.saveAndFlush(ConductorRecoveryRequest.authorize(
                admin.getOrganizationId(),
                conductor.getId(),
                admin.getId(),
                conductor.getEmail(),
                newEmail,
                evidenceMethod,
                evidenceReference,
                reason,
                Boolean.TRUE.equals(request.passwordResetRequired()),
                now
        ));
        EmailOtpTicket ticket = emailOtpService.beginForConductorRecovery(
                conductor,
                newEmail,
                recovery.getId()
        );
        auditService.record(admin, "CONDUCTOR_RECOVERY_AUTHORIZED", "CONDUCTOR_RECOVERY_REQUEST", recovery.getId(), Map.of(
                "evidenceMethod", evidenceMethod.name(),
                "expedienteReviewed", true,
                "independentEvidenceConfirmed", true,
                "passwordResetRequired", recovery.isPasswordResetRequired()
        ));
        return new ConductorRecoveryStartedResponse(
                recovery.getId(),
                recovery.getStatus().name(),
                ticket.verificationToken(),
                ticket.otpExpiresAt(),
                ticket.challengeExpiresAt(),
                ticket.emailSent(),
                recovery.isPasswordResetRequired()
        );
    }

    @Transactional(readOnly = true)
    public ConductorRecoveryStatusResponse status(UUID conductorUserId, UUID recoveryId) {
        AppUser admin = requireAdminActor();
        AppUser conductor = userRepository.findByIdAndOrganization_IdAndRole(
                        conductorUserId,
                        admin.getOrganizationId(),
                        UserRole.CONDUCTOR
                )
                .filter(AppUser::isActive)
                .filter(AppUser::isEmailVerified)
                .orElseThrow(this::notFound);
        return recoveryRepository.findByIdAndOrganizationIdAndConductorUserId(
                        recoveryId,
                        admin.getOrganizationId(),
                        conductor.getId()
                )
                .map(ConductorRecoveryStatusResponse::from)
                .orElseThrow(this::notFound);
    }

    @Transactional
    public ConductorRecoveryStartedResponse resendOtp(
            UUID conductorUserId,
            UUID recoveryId,
            String verificationToken
    ) {
        AppUser admin = requireAdminActor();
        AppUser conductor = lockTenantConductor(conductorUserId, admin.getOrganizationId());
        ConductorRecoveryRequest recovery = recoveryRepository.findForUpdate(
                        recoveryId,
                        admin.getOrganizationId(),
                        conductor.getId()
                )
                .orElseThrow(this::notFound);
        if (recovery.getStatus() != ConductorRecoveryStatus.OTP_PENDING) {
            throw conflict("La recuperación ya no espera la validación OTP");
        }
        EmailOtpTicket ticket = emailOtpService.resendConductorRecovery(recoveryId, verificationToken);
        return new ConductorRecoveryStartedResponse(
                recoveryId,
                recovery.getStatus().name(),
                ticket.verificationToken(),
                ticket.otpExpiresAt(),
                ticket.challengeExpiresAt(),
                ticket.emailSent(),
                recovery.isPasswordResetRequired()
        );
    }

    @Transactional
    public ConductorRecoveryStatusResponse cancel(UUID conductorUserId, UUID recoveryId) {
        AppUser admin = requireAdminActor();
        AppUser conductor = lockTenantConductor(conductorUserId, admin.getOrganizationId());
        ConductorRecoveryRequest recovery = recoveryRepository.findForUpdate(
                        recoveryId,
                        admin.getOrganizationId(),
                        conductor.getId()
                )
                .orElseThrow(this::notFound);
        if (recovery.getStatus() == ConductorRecoveryStatus.COMPLETED) {
            throw conflict("La recuperación ya fue completada");
        }
        recovery.cancel(Instant.now(clock));
        emailOtpService.cancelConductorRecovery(conductor.getId(), recoveryId);
        auditService.record(admin, "CONDUCTOR_RECOVERY_CANCELLED", "CONDUCTOR_RECOVERY_REQUEST", recoveryId, Map.of());
        return ConductorRecoveryStatusResponse.from(recovery);
    }

    @Transactional(noRollbackFor = EmailOtpCodeRejectedException.class)
    public ConductorRecoveryOtpResponse verifyOtp(UUID recoveryId, String verificationToken, String code) {
        EmailOtpService.RecoveryEmailProof proof = emailOtpService.verifyConductorRecovery(
                recoveryId,
                verificationToken,
                code
        );
        AppUser conductor = proof.user();
        ConductorRecoveryRequest recovery = recoveryRepository.findForUpdate(
                        recoveryId,
                        conductor.getOrganizationId(),
                        conductor.getId()
                )
                .filter(request -> request.getStatus() == ConductorRecoveryStatus.OTP_PENDING)
                .filter(request -> request.getRequestedEmail().equals(proof.newEmail()))
                .orElseThrow(this::invalidRecoveryProof);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(proof.newEmail(), conductor.getId())) {
            throw conflict("El correo solicitado ya está asociado a otra cuenta");
        }

        Instant now = Instant.now(clock);
        recovery.markEmailVerified(now);
        auditService.record(conductor, "CONDUCTOR_RECOVERY_EMAIL_VERIFIED", "CONDUCTOR_RECOVERY_REQUEST", recoveryId, Map.of(
                "passwordResetRequired", recovery.isPasswordResetRequired()
        ));

        String rawResetToken = null;
        Instant resetExpiresAt = null;
        if (recovery.isPasswordResetRequired()) {
            rawResetToken = opaqueTokenService.generate();
            resetExpiresAt = now.plus(PASSWORD_RESET_TOKEN_TTL);
            recovery.issuePasswordResetToken(
                    opaqueTokenService.hash(rawResetToken),
                    resetExpiresAt,
                    now
            );
        } else {
            conductor.completeConductorEmailRecovery(proof.newEmail(), now);
            refreshTokenRepository.revokeActiveByUserId(conductor.getId(), now);
            auditService.record(conductor, "CONDUCTOR_RECOVERY_COMPLETED", "CONDUCTOR_RECOVERY_REQUEST", recoveryId, Map.of());
        }
        return new ConductorRecoveryOtpResponse(
                recoveryId,
                recovery.getStatus().name(),
                "",
                recovery.isPasswordResetRequired(),
                rawResetToken,
                resetExpiresAt
        );
    }

    @Transactional
    public Instant completePasswordReset(UUID recoveryId, String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidRecoveryProof();
        }
        String tokenHash = opaqueTokenService.hash(rawToken);
        ConductorRecoveryRequest lookup = recoveryRepository.findByPasswordResetTokenHash(tokenHash)
                .filter(request -> request.getId().equals(recoveryId))
                .orElseThrow(this::invalidRecoveryProof);
        AppUser conductor = userRepository.findByIdForUpdate(lookup.getConductorUserId())
                .orElseThrow(this::invalidRecoveryProof);
        ConductorRecoveryRequest recovery = recoveryRepository.findByIdForUpdate(recoveryId)
                .filter(request -> request.getConductorUserId().equals(conductor.getId()))
                .orElseThrow(this::invalidRecoveryProof);

        Instant now = Instant.now(clock);
        if (conductor.getRole() != UserRole.CONDUCTOR
                || !conductor.isActive()
                || !conductor.isEmailVerified()
                || !recovery.matchesPasswordResetTokenHash(tokenHash)
                || !recovery.hasActivePasswordResetAt(now)) {
            throw invalidRecoveryProof();
        }

        if (userRepository.existsByEmailIgnoreCaseAndIdNot(recovery.getRequestedEmail(), conductor.getId())) {
            throw conflict("El correo solicitado ya está asociado a otra cuenta");
        }
        conductor.completeConductorPasswordReset(
                recovery.getRequestedEmail(),
                passwordEncoder.encode(newPassword),
                now
        );
        refreshTokenRepository.revokeActiveByUserId(conductor.getId(), now);
        recovery.completePasswordReset(now);
        auditService.record(conductor, "CONDUCTOR_PASSWORD_RESET_COMPLETED", "CONDUCTOR_RECOVERY_REQUEST", recoveryId, Map.of());
        auditService.record(conductor, "CONDUCTOR_RECOVERY_COMPLETED", "CONDUCTOR_RECOVERY_REQUEST", recoveryId, Map.of());
        return now;
    }

    private AppUser requireAdminActor() {
        AppUser admin = currentUserService.requireTenantActor();
        if (admin.getRole() != UserRole.ADMIN || !admin.isEmailVerified()) {
            throw new ApplicationException(
                    HttpStatus.FORBIDDEN,
                    ErrorCode.FORBIDDEN_ROLE,
                    "La recuperación requiere un ADMIN verificado de la organización"
            );
        }
        return admin;
    }

    private AppUser lockTenantConductor(UUID conductorUserId, UUID organizationId) {
        AppUser conductor = userRepository.findByIdForUpdate(conductorUserId)
                .filter(user -> organizationId.equals(user.getOrganizationId()))
                .filter(user -> user.getRole() == UserRole.CONDUCTOR)
                .orElseThrow(this::notFound);
        if (!conductor.isActive() || !conductor.isEmailVerified()) {
            throw notFound();
        }
        return conductor;
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private ApplicationException invalidEvidenceConfirmation() {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "Confirme la revisión del expediente y la evidencia independiente antes de autorizar"
        );
    }

    private ApplicationException invalidRecoveryProof() {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.EMAIL_OTP_INVALID,
                "La prueba de recuperación no es válida, venció o ya fue utilizada"
        );
    }

    private ApplicationException notFound() {
        return new ApplicationException(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "El conductor o la recuperación solicitada no existe"
        );
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
