package pe.rutafija.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.EmailOtpChallenge;
import pe.rutafija.identity.domain.EmailOtpPurpose;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.EmailOtpChallengeRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.exception.ExternalServiceUnavailableException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class EmailOtpService {

    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration CHALLENGE_TTL = Duration.ofMinutes(30);
    static final Duration SEND_WINDOW = Duration.ofMinutes(60);
    static final Duration VALIDATION_BLOCK = Duration.ofMinutes(30);

    private final AppUserRepository userRepository;
    private final EmailOtpChallengeRepository challengeRepository;
    private final OpaqueTokenService opaqueTokenService;
    private final OtpCodeGenerator codeGenerator;
    private final OtpCodeHasher codeHasher;
    private final OtpEmailSender emailSender;
    private final AuditService auditService;
    private final Clock clock;

    public EmailOtpService(
            AppUserRepository userRepository,
            EmailOtpChallengeRepository challengeRepository,
            OpaqueTokenService opaqueTokenService,
            OtpCodeGenerator codeGenerator,
            OtpCodeHasher codeHasher,
            OtpEmailSender emailSender,
            AuditService auditService,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.challengeRepository = challengeRepository;
        this.opaqueTokenService = opaqueTokenService;
        this.codeGenerator = codeGenerator;
        this.codeHasher = codeHasher;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public EmailOtpTicket beginForLogin(AppUser candidate) {
        AppUser user = lockUser(candidate.getId());
        if (user.isEmailVerified()) {
            return null;
        }
        ensureUserCanAuthenticate(user);

        Instant now = Instant.now(clock);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> challengeRepository.save(EmailOtpChallenge.create(user, now)));
        rejectIfBlocked(challenge, now);

        if (challenge.hasActiveCodeAt(now)) {
            if (challenge.getPurpose() != EmailOtpPurpose.LOGIN
                    || !user.getEmail().equals(challenge.getRecipientEmail())) {
                throw invalidChallenge();
            }
            String verificationToken = opaqueTokenService.generate();
            challenge.replaceVerificationToken(opaqueTokenService.hash(verificationToken), now);
            return ticket(verificationToken, challenge, false);
        }

        return issueCode(user, challenge, now, EmailOtpPurpose.LOGIN, user.getEmail(), null);
    }

    @Transactional
    public EmailOtpTicket beginForSuperAdminRecovery(AppUser candidate, String newEmail, UUID recoveryChallengeId) {
        AppUser user = lockUser(candidate.getId());
        if (user.getRole() != UserRole.SUPER_ADMIN) {
            throw invalidChallenge();
        }
        ensureUserCanAuthenticate(user);

        Instant now = Instant.now(clock);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> challengeRepository.save(EmailOtpChallenge.create(user, now)));
        rejectIfBlocked(challenge, now);

        if (challenge.hasActiveCodeAt(now)
                && challenge.getPurpose() == EmailOtpPurpose.SUPER_ADMIN_RECOVERY
                && newEmail.equals(challenge.getRecipientEmail())
                && recoveryChallengeId.equals(challenge.getPurposeReferenceId())) {
            String verificationToken = opaqueTokenService.generate();
            challenge.replaceVerificationToken(opaqueTokenService.hash(verificationToken), now);
            return ticket(verificationToken, challenge, false);
        }

        return issueCode(
                user,
                challenge,
                now,
                EmailOtpPurpose.SUPER_ADMIN_RECOVERY,
                newEmail,
                recoveryChallengeId
        );
    }

    @Transactional
    public EmailOtpTicket beginForConductorRecovery(AppUser candidate, String newEmail, UUID recoveryRequestId) {
        AppUser user = lockUser(candidate.getId());
        if (user.getRole() != UserRole.CONDUCTOR) {
            throw invalidChallenge();
        }
        ensureUserCanAuthenticate(user);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, user.getId())) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.RESOURCE_CONFLICT,
                    "El correo solicitado ya está asociado a otra cuenta"
            );
        }

        Instant now = Instant.now(clock);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(() -> challengeRepository.save(EmailOtpChallenge.create(user, now)));
        rejectIfBlocked(challenge, now);

        if (challenge.hasActiveCodeAt(now)
                && challenge.getPurpose() == EmailOtpPurpose.CONDUCTOR_RECOVERY
                && newEmail.equals(challenge.getRecipientEmail())
                && recoveryRequestId.equals(challenge.getPurposeReferenceId())) {
            String verificationToken = opaqueTokenService.generate();
            challenge.replaceVerificationToken(opaqueTokenService.hash(verificationToken), now);
            return ticket(verificationToken, challenge, false);
        }

        // Replacing a challenge never clears its rolling send or failed-attempt counters.
        if (challenge.hasActiveChallengeAt(now)) {
            challenge.invalidateCurrentChallenge(now);
        }
        return issueCode(
                user,
                challenge,
                now,
                EmailOtpPurpose.CONDUCTOR_RECOVERY,
                newEmail,
                recoveryRequestId
        );
    }

    @Transactional
    public EmailOtpTicket resend(String rawVerificationToken) {
        UUID userId = userIdForToken(rawVerificationToken);
        AppUser user = lockUser(userId);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge, rawVerificationToken);
        ensurePurpose(challenge, EmailOtpPurpose.LOGIN);
        ensureUserCanAuthenticate(user);
        rejectIfBlocked(challenge, Instant.now(clock));
        return issueCode(user, challenge, Instant.now(clock));
    }

    @Transactional
    public EmailOtpTicket resendSuperAdminRecovery(UUID recoveryChallengeId, String rawVerificationToken) {
        UUID userId = userIdForToken(rawVerificationToken);
        AppUser user = lockUser(userId);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge, rawVerificationToken);
        ensurePurpose(challenge, EmailOtpPurpose.SUPER_ADMIN_RECOVERY);
        if (!recoveryChallengeId.equals(challenge.getPurposeReferenceId())
                || user.getRole() != UserRole.SUPER_ADMIN) {
            throw invalidChallenge();
        }
        ensureUserCanAuthenticate(user);
        rejectIfBlocked(challenge, Instant.now(clock));
        return issueCode(user, challenge, Instant.now(clock));
    }

    @Transactional
    public EmailOtpTicket resendConductorRecovery(UUID recoveryRequestId, String rawVerificationToken) {
        UUID userId = userIdForToken(rawVerificationToken);
        AppUser user = lockUser(userId);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge, rawVerificationToken);
        ensurePurpose(challenge, EmailOtpPurpose.CONDUCTOR_RECOVERY);
        if (!recoveryRequestId.equals(challenge.getPurposeReferenceId())
                || user.getRole() != UserRole.CONDUCTOR) {
            throw invalidChallenge();
        }
        ensureUserCanAuthenticate(user);
        rejectIfBlocked(challenge, Instant.now(clock));
        return issueCode(user, challenge, Instant.now(clock));
    }

    @Transactional(noRollbackFor = EmailOtpCodeRejectedException.class)
    public AppUser verify(String rawVerificationToken, String code) {
        UUID userId = userIdForToken(rawVerificationToken);
        AppUser user = lockUser(userId);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge, rawVerificationToken);
        ensurePurpose(challenge, EmailOtpPurpose.LOGIN);
        ensureUserCanAuthenticate(user);

        Instant now = Instant.now(clock);
        rejectIfBlocked(challenge, now);
        if (!challenge.hasActiveChallengeAt(now)
                || challenge.getOtpHash() == null
                || challenge.getOtpExpiresAt() == null
                || !challenge.getOtpExpiresAt().isAfter(now)) {
            throw new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.EMAIL_OTP_EXPIRED,
                    "El código venció. Solicita uno nuevo para continuar."
            );
        }

        if (!codeHasher.matches(code, challenge.getOtpHash())) {
            challenge.recordInvalidCode(now, VALIDATION_BLOCK);
            boolean blocked = challenge.getFailedAttempts() >= 2;
            throw new EmailOtpCodeRejectedException(blocked);
        }

        challenge.consume(now);
        user.verifyEmail(now);
        auditService.record(user, "EMAIL_VERIFIED", Map.of());
        return user;
    }

    @Transactional(noRollbackFor = EmailOtpCodeRejectedException.class)
    public RecoveryEmailProof verifySuperAdminRecovery(
            UUID recoveryChallengeId,
            String rawVerificationToken,
            String code
    ) {
        UUID userId = userIdForToken(rawVerificationToken);
        AppUser user = lockUser(userId);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge, rawVerificationToken);
        ensurePurpose(challenge, EmailOtpPurpose.SUPER_ADMIN_RECOVERY);
        if (!recoveryChallengeId.equals(challenge.getPurposeReferenceId())
                || user.getRole() != UserRole.SUPER_ADMIN) {
            throw invalidChallenge();
        }
        ensureUserCanAuthenticate(user);

        Instant now = Instant.now(clock);
        rejectIfBlocked(challenge, now);
        if (!challenge.hasActiveChallengeAt(now)
                || challenge.getOtpHash() == null
                || challenge.getOtpExpiresAt() == null
                || !challenge.getOtpExpiresAt().isAfter(now)) {
            throw new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.EMAIL_OTP_EXPIRED,
                    "El código venció. Inicia una nueva solicitud de recuperación."
            );
        }

        if (!codeHasher.matches(code, challenge.getOtpHash())) {
            challenge.recordInvalidCode(now, VALIDATION_BLOCK);
            boolean blocked = challenge.getFailedAttempts() >= 2;
            throw new EmailOtpCodeRejectedException(blocked);
        }

        String newEmail = challenge.getRecipientEmail();
        challenge.consume(now);
        auditService.record(user, "SUPER_ADMIN_RECOVERY_EMAIL_VERIFIED", Map.of());
        return new RecoveryEmailProof(user, newEmail);
    }

    @Transactional(noRollbackFor = EmailOtpCodeRejectedException.class)
    public RecoveryEmailProof verifyConductorRecovery(
            UUID recoveryRequestId,
            String rawVerificationToken,
            String code
    ) {
        UUID userId = userIdForToken(rawVerificationToken);
        AppUser user = lockUser(userId);
        EmailOtpChallenge challenge = challengeRepository.findByUserIdForUpdate(userId)
                .orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge, rawVerificationToken);
        ensurePurpose(challenge, EmailOtpPurpose.CONDUCTOR_RECOVERY);
        if (!recoveryRequestId.equals(challenge.getPurposeReferenceId())
                || user.getRole() != UserRole.CONDUCTOR) {
            throw invalidChallenge();
        }
        ensureUserCanAuthenticate(user);

        Instant now = Instant.now(clock);
        rejectIfBlocked(challenge, now);
        if (!challenge.hasActiveCodeAt(now) || challenge.getOtpHash() == null) {
            throw new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.EMAIL_OTP_EXPIRED,
                    "El código venció. Solicita una nueva validación con el ADMIN de tu organización."
            );
        }
        if (!codeHasher.matches(code, challenge.getOtpHash())) {
            challenge.recordInvalidCode(now, VALIDATION_BLOCK);
            throw new EmailOtpCodeRejectedException(challenge.getFailedAttempts() >= 2);
        }

        String newEmail = challenge.getRecipientEmail();
        challenge.consume(now);
        return new RecoveryEmailProof(user, newEmail);
    }

    @Transactional
    public void cancelConductorRecovery(UUID userId, UUID recoveryRequestId) {
        AppUser user = lockUser(userId);
        if (user.getRole() != UserRole.CONDUCTOR) {
            throw invalidChallenge();
        }
        challengeRepository.findByUserIdForUpdate(userId)
                .filter(challenge -> challenge.getPurpose() == EmailOtpPurpose.CONDUCTOR_RECOVERY
                        && recoveryRequestId.equals(challenge.getPurposeReferenceId()))
                .ifPresent(challenge -> challenge.invalidateCurrentChallenge(Instant.now(clock)));
    }

    @Transactional
    public void invalidateForEmailChange(UUID userId) {
        AppUser user = lockUser(userId);
        challengeRepository.findByUserIdForUpdate(user.getId())
                .ifPresent(challenge -> challenge.invalidateCurrentChallenge(Instant.now(clock)));
    }

    /** Only the scoped ADMIN activation workflow may verify an inactive account. */
    @Transactional
    public EmailOtpTicket beginForAdminActivation(AppUser candidate,UUID activationId) {
        AppUser user=lockUser(candidate.getId());ensureInactiveAdmin(user);
        Instant now=Instant.now(clock);
        EmailOtpChallenge challenge=challengeRepository.findByUserIdForUpdate(user.getId())
                .orElseGet(()->challengeRepository.save(EmailOtpChallenge.create(user,now)));
        rejectIfBlocked(challenge,now);
        if(challenge.hasActiveCodeAt(now)&&challenge.getPurpose()==EmailOtpPurpose.ADMIN_ACTIVATION
                &&activationId.equals(challenge.getPurposeReferenceId())&&user.getEmail().equals(challenge.getRecipientEmail())) {
            String token=opaqueTokenService.generate();challenge.replaceVerificationToken(opaqueTokenService.hash(token),now);
            return ticket(token,challenge,false);
        }
        return issueCode(user,challenge,now,EmailOtpPurpose.ADMIN_ACTIVATION,user.getEmail(),activationId);
    }

    @Transactional
    public EmailOtpTicket resendAdminActivation(UUID activationId,UUID expectedUserId,String rawToken) {
        UUID userId=userIdForToken(rawToken);if(!userId.equals(expectedUserId))throw invalidChallenge();
        AppUser user=lockUser(userId);ensureInactiveAdmin(user);
        EmailOtpChallenge challenge=challengeRepository.findByUserIdForUpdate(userId).orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge,rawToken);ensurePurpose(challenge,EmailOtpPurpose.ADMIN_ACTIVATION);
        if(!activationId.equals(challenge.getPurposeReferenceId()))throw invalidChallenge();
        rejectIfBlocked(challenge,Instant.now(clock));return issueCode(user,challenge,Instant.now(clock));
    }

    @Transactional(noRollbackFor=EmailOtpCodeRejectedException.class)
    public AppUser verifyAdminActivation(UUID activationId,UUID expectedUserId,String rawToken,String code) {
        UUID userId=userIdForToken(rawToken);if(!userId.equals(expectedUserId))throw invalidChallenge();
        AppUser user=lockUser(userId);ensureInactiveAdmin(user);
        EmailOtpChallenge challenge=challengeRepository.findByUserIdForUpdate(userId).orElseThrow(this::invalidChallenge);
        ensureChallengeMatches(challenge,rawToken);ensurePurpose(challenge,EmailOtpPurpose.ADMIN_ACTIVATION);
        if(!activationId.equals(challenge.getPurposeReferenceId()))throw invalidChallenge();
        Instant now=Instant.now(clock);rejectIfBlocked(challenge,now);
        if(!challenge.hasActiveCodeAt(now)||challenge.getOtpHash()==null)
            throw new ApplicationException(HttpStatus.GONE,ErrorCode.EMAIL_OTP_EXPIRED,"El codigo vencio. Solicita otro para continuar la activacion.");
        if(!codeHasher.matches(code,challenge.getOtpHash())) {
            challenge.recordInvalidCode(now,VALIDATION_BLOCK);
            throw new EmailOtpCodeRejectedException(challenge.getFailedAttempts()>=2);
        }
        challenge.consume(now);user.verifyEmail(now);
        auditService.record(user,"ADMIN_ACTIVATION_EMAIL_VERIFIED",Map.of());return user;
    }

    private void ensureInactiveAdmin(AppUser user) {
        if(user.getRole()!=UserRole.ADMIN||user.isActive()||user.getOrganization()==null||!user.getOrganization().isActive())
            throw invalidChallenge();
    }

    private EmailOtpTicket issueCode(AppUser user, EmailOtpChallenge challenge, Instant now) {
        return issueCode(
                user,
                challenge,
                now,
                challenge.getPurpose(),
                challenge.getRecipientEmail(),
                challenge.getPurposeReferenceId()
        );
    }

    private EmailOtpTicket issueCode(
            AppUser user,
            EmailOtpChallenge challenge,
            Instant now,
            EmailOtpPurpose purpose,
            String recipientEmail,
            UUID purposeReferenceId
    ) {
        Instant nextAllowedAt = challenge.nextSendAllowedAt(now, SEND_WINDOW);
        if (nextAllowedAt != null) {
            long seconds = Math.max(1, Duration.between(now, nextAllowedAt).toSeconds());
            throw new ApplicationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.EMAIL_OTP_RATE_LIMITED,
                    "Se alcanzó el límite de envíos. Podrás solicitar otro código en " + seconds + " segundos."
            );
        }

        String token = opaqueTokenService.generate();
        String code = codeGenerator.generate();
        Instant otpExpiresAt = now.plus(OTP_TTL);
        Instant challengeExpiresAt = now.plus(CHALLENGE_TTL);
        challenge.issueCode(
                opaqueTokenService.hash(token),
                codeHasher.hash(code),
                now,
                otpExpiresAt,
                challengeExpiresAt,
                SEND_WINDOW,
                purpose,
                recipientEmail,
                purposeReferenceId
        );
        challengeRepository.saveAndFlush(challenge);

        emailSender.sendVerificationCode(recipientEmail, code, otpExpiresAt);
        auditService.record(user, switch (purpose) {
            case LOGIN -> "EMAIL_OTP_SENT";
            case SUPER_ADMIN_RECOVERY -> "SUPER_ADMIN_RECOVERY_OTP_SENT";
            case CONDUCTOR_RECOVERY -> "CONDUCTOR_RECOVERY_OTP_SENT";
            case ADMIN_ACTIVATION -> "ADMIN_ACTIVATION_OTP_SENT";
        }, Map.of());
        return ticket(token, challenge, true);
    }

    private EmailOtpTicket ticket(String rawToken, EmailOtpChallenge challenge, boolean emailSent) {
        return new EmailOtpTicket(
                rawToken,
                challenge.getOtpExpiresAt(),
                challenge.getChallengeExpiresAt(),
                emailSent
        );
    }

    private UUID userIdForToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw invalidChallenge();
        }
        return challengeRepository.findUserIdByVerificationTokenHash(opaqueTokenService.hash(rawToken))
                .orElseThrow(this::invalidChallenge);
    }

    private void ensureChallengeMatches(EmailOtpChallenge challenge, String rawToken) {
        if (!challenge.matchesVerificationTokenHash(opaqueTokenService.hash(rawToken))) {
            throw invalidChallenge();
        }
        if (!challenge.hasActiveChallengeAt(Instant.now(clock))) {
            throw new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.EMAIL_OTP_EXPIRED,
                    "La solicitud de verificación venció. Inicia sesión nuevamente para continuar."
            );
        }
    }

    private void ensurePurpose(EmailOtpChallenge challenge, EmailOtpPurpose expectedPurpose) {
        if (challenge.getPurpose() != expectedPurpose) {
            throw invalidChallenge();
        }
    }

    private void rejectIfBlocked(EmailOtpChallenge challenge, Instant now) {
        if (challenge.isBlockedAt(now)) {
            long seconds = Math.max(1, Duration.between(now, challenge.getBlockedUntil()).toSeconds());
            throw new ApplicationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.EMAIL_OTP_LOCKED,
                    "La validación está bloqueada temporalmente. Inténtalo en " + seconds + " segundos."
            );
        }
    }

    private AppUser lockUser(UUID userId) {
        return userRepository.findByIdForUpdate(userId).orElseThrow(this::invalidChallenge);
    }

    private void ensureUserCanAuthenticate(AppUser user) {
        if (!user.isActive() || (user.getOrganization() != null && !user.getOrganization().isActive())) {
            throw invalidChallenge();
        }
    }

    private ApplicationException invalidChallenge() {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.EMAIL_OTP_INVALID,
                "La solicitud de verificación no es válida. Inicia sesión nuevamente."
        );
    }

    public record RecoveryEmailProof(AppUser user, String newEmail) {
    }
}
