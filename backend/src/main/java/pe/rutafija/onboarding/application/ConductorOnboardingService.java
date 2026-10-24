package pe.rutafija.onboarding.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.application.OpaqueTokenService;
import pe.rutafija.identity.application.OtpCodeGenerator;
import pe.rutafija.identity.application.OtpCodeHasher;
import pe.rutafija.identity.application.OtpEmailSender;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.invitation.application.InvitationCodeHasher;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingAcceptedResponse;
import pe.rutafija.onboarding.api.dto.ConductorOnboardingOtpResponse;
import pe.rutafija.onboarding.api.dto.StartConductorOnboardingRequest;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository.ChallengeRow;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository.IdentityRow;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository.InvitationRow;
import pe.rutafija.onboarding.infrastructure.ConductorOnboardingRepository.OnboardingRequestRow;
import pe.rutafija.organization.infrastructure.OrganizationRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class ConductorOnboardingService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration CHALLENGE_TTL = Duration.ofMinutes(30);
    private static final Duration SEND_WINDOW = Duration.ofMinutes(60);
    private static final Duration OTP_BLOCK = Duration.ofMinutes(30);
    private static final Duration INVITATION_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration EMAIL_INVITATION_BLOCK = Duration.ofMinutes(30);
    private static final Duration ORIGIN_INVITATION_PAUSE = Duration.ofMinutes(15);
    private static final long EMAIL_FAILURE_LIMIT = 5;
    private static final long ORIGIN_FAILURE_LIMIT = 50;

    private final ConductorOnboardingRepository repository;
    private final OrganizationRepository organizations;
    private final AppUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final InvitationCodeHasher invitationCodeHasher;
    private final OpaqueTokenService opaqueTokenService;
    private final OtpCodeGenerator otpCodeGenerator;
    private final OtpCodeHasher otpCodeHasher;
    private final OtpEmailSender emailSender;
    private final AuditService auditService;
    private final Clock clock;

    public ConductorOnboardingService(
            ConductorOnboardingRepository repository,
            OrganizationRepository organizations,
            AppUserRepository users,
            PasswordEncoder passwordEncoder,
            InvitationCodeHasher invitationCodeHasher,
            OpaqueTokenService opaqueTokenService,
            OtpCodeGenerator otpCodeGenerator,
            OtpCodeHasher otpCodeHasher,
            OtpEmailSender emailSender,
            AuditService auditService,
            Clock clock
    ) {
        this.repository = repository;
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.invitationCodeHasher = invitationCodeHasher;
        this.opaqueTokenService = opaqueTokenService;
        this.otpCodeGenerator = otpCodeGenerator;
        this.otpCodeHasher = otpCodeHasher;
        this.emailSender = emailSender;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = OnboardingInvitationRejectedException.class)
    public ConductorOnboardingOtpResponse start(
            StartConductorOnboardingRequest request,
            String idempotencyKey,
            String remoteAddress
    ) {
        String email = normalizeEmail(request.email());
        String operationKey = normalizeIdempotencyKey(idempotencyKey);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        String emailHash = invitationCodeHasher.hashOrigin("onboarding-email:" + email);
        String originHash = invitationCodeHasher.hashOrigin(remoteAddress);
        Instant emailBlockedUntil = repository.lockGuard("EMAIL", emailHash, now);
        Instant originPausedUntil = repository.lockGuard("ORIGIN", originHash, now);
        Instant activeGuard = laterActive(emailBlockedUntil, originPausedUntil, now);
        if (activeGuard != null) {
            throw new OnboardingInvitationRejectedException(true);
        }

        String code = request.invitationCode();
        InvitationRow invitation = null;
        if (code != null && code.matches("[0-9]{6}")) {
            String expectedHash = invitationCodeHasher.hashCode(code, email);
            invitation = repository.findActiveInvitation(email, expectedHash, now).orElse(null);
            if (invitation != null
                    && !invitationCodeHasher.matchesCode(code, email, invitation.codeHash())) {
                invitation = null;
            }
        }
        if (invitation == null) {
            boolean limited = recordInvitationFailure(emailHash, originHash, now);
            throw new OnboardingInvitationRejectedException(limited);
        }

        IdentityRow existingIdentity = repository.findIdentityForUpdate(email).orElse(null);
        if (users.existsByEmailIgnoreCase(email)
                || (existingIdentity != null && existingIdentity.emailVerifiedAt() != null)) {
            throw conflict("La identidad ya existe. Utiliza su flujo de acceso autorizado.");
        }
        if (existingIdentity != null
                && repository.findPendingRequest(existingIdentity.id(), invitation.organizationId()).isPresent()) {
            throw conflict("Ya existe una solicitud pendiente para esta organización.");
        }

        IdentityRow candidate = new IdentityRow(
                existingIdentity == null ? UUID.randomUUID() : existingIdentity.id(),
                email,
                passwordEncoder.encode(request.password()),
                request.fullName().strip(),
                optionalText(request.phone()),
                existingIdentity == null ? null : existingIdentity.emailVerifiedAt(),
                existingIdentity == null ? now : existingIdentity.createdAt(),
                now
        );
        IdentityRow identity = repository.upsertIdentity(candidate);
        ChallengeRow prior = repository.findChallengeByIdentityForUpdate(identity.id()).orElse(null);
        if (prior != null && isBlocked(prior, now)) {
            throw otpBlocked();
        }
        if (prior != null && prior.consumedAt() == null && prior.challengeExpiresAt().isAfter(now)) {
            if (!prior.idempotencyKey().equals(operationKey)
                    || !prior.invitationId().equals(invitation.id())) {
                throw conflict("Ya hay una verificación de incorporación activa para esta identidad.");
            }
            if (prior.otpHash() != null
                    && prior.otpExpiresAt() != null
                    && prior.otpExpiresAt().isAfter(now)) {
                String rawToken = opaqueTokenService.generate();
                ChallengeRow refreshed = withToken(prior, opaqueTokenService.hash(rawToken), now);
                repository.saveChallenge(refreshed);
                return otpResponse(rawToken, refreshed, false);
            }
        }

        List<Instant> recentSends = recentSends(prior, now);
        if (recentSends.size() >= 3) {
            Instant allowedAt = recentSends.get(0).plus(SEND_WINDOW);
            long seconds = Math.max(1, Duration.between(now, allowedAt).toSeconds());
            throw new ApplicationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.EMAIL_OTP_RATE_LIMITED,
                    "Se alcanzó el límite de envíos. Podrás solicitar otro código en " + seconds + " segundos."
            );
        }

        String rawToken = opaqueTokenService.generate();
        String otp = otpCodeGenerator.generate();
        Instant expiresAt = now.plus(OTP_TTL);
        Instant challengeExpiresAt = now.plus(CHALLENGE_TTL);
        List<Instant> sendTimes = new ArrayList<>(recentSends);
        sendTimes.add(now);
        sendTimes.sort(Comparator.naturalOrder());
        ChallengeRow issued = new ChallengeRow(
                prior == null ? UUID.randomUUID() : prior.id(),
                identity.id(),
                invitation.id(),
                operationKey,
                opaqueTokenService.hash(rawToken),
                otpCodeHasher.hash(otp),
                now,
                expiresAt,
                challengeExpiresAt,
                failuresOnIssue(prior, now),
                null,
                at(sendTimes, 0),
                at(sendTimes, 1),
                at(sendTimes, 2),
                null,
                null,
                prior == null ? now : prior.createdAt(),
                now
        );
        repository.saveChallenge(issued);
        emailSender.sendVerificationCode(email, otp, expiresAt);
        UUID invitationId = invitation.id();
        organizations.findById(invitation.organizationId()).ifPresent(organization ->
                auditService.recordSystem(
                        organization,
                        "CONDUCTOR_ONBOARDING_OTP_SENT",
                        "ORGANIZATION_INVITATION",
                        invitationId,
                        Map.of()
                ));
        return otpResponse(rawToken, issued, true);
    }

    @Transactional
    public ConductorOnboardingOtpResponse resend(String rawToken) {
        String tokenHash;
        try {
            tokenHash = opaqueTokenService.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            throw invalidOtp();
        }
        UUID identityId = repository.findIdentityIdByVerificationToken(tokenHash).orElseThrow(this::invalidOtp);
        IdentityRow identity = repository.findIdentityByIdForUpdate(identityId).orElseThrow(this::invalidOtp);
        ChallengeRow prior = repository.findChallengeByTokenForUpdate(tokenHash)
                .orElseThrow(this::invalidOtp);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        if (prior.consumedAt() != null || !prior.challengeExpiresAt().isAfter(now)) {
            throw new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.EMAIL_OTP_EXPIRED,
                    "La verificación venció. Inicia nuevamente la incorporación."
            );
        }
        if (isBlocked(prior, now)) {
            throw otpBlocked();
        }
        InvitationRow invitation = repository.lockInvitation(prior.invitationId(), identity.email())
                .orElseThrow(this::invitationNoLongerAvailable);
        if (invitation.revokedAt() != null
                || invitation.usedAt() != null
                || !invitation.expiresAt().isAfter(now)) {
            throw invitationNoLongerAvailable();
        }

        List<Instant> recentSends = recentSends(prior, now);
        if (recentSends.size() >= 3) {
            Instant allowedAt = recentSends.get(0).plus(SEND_WINDOW);
            long seconds = Math.max(1, Duration.between(now, allowedAt).toSeconds());
            throw new ApplicationException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    ErrorCode.EMAIL_OTP_RATE_LIMITED,
                    "Se alcanzó el límite de envíos. Podrás solicitar otro código en " + seconds + " segundos."
            );
        }

        String nextToken = opaqueTokenService.generate();
        String otp = otpCodeGenerator.generate();
        Instant expiresAt = now.plus(OTP_TTL);
        List<Instant> sendTimes = new ArrayList<>(recentSends);
        sendTimes.add(now);
        sendTimes.sort(Comparator.naturalOrder());
        ChallengeRow refreshed = new ChallengeRow(
                prior.id(),
                prior.identityId(),
                prior.invitationId(),
                prior.idempotencyKey(),
                opaqueTokenService.hash(nextToken),
                otpCodeHasher.hash(otp),
                now,
                expiresAt,
                now.plus(CHALLENGE_TTL),
                failuresOnIssue(prior, now),
                null,
                at(sendTimes, 0),
                at(sendTimes, 1),
                at(sendTimes, 2),
                null,
                null,
                prior.createdAt(),
                now
        );
        repository.saveChallenge(refreshed);
        emailSender.sendVerificationCode(identity.email(), otp, expiresAt);
        UUID invitationId = invitation.id();
        organizations.findById(invitation.organizationId()).ifPresent(organization ->
                auditService.recordSystem(
                        organization,
                        "CONDUCTOR_ONBOARDING_OTP_RESENT",
                        "ORGANIZATION_INVITATION",
                        invitationId,
                        Map.of()
                ));
        return otpResponse(nextToken, refreshed, true);
    }

    @Transactional(noRollbackFor = OnboardingOtpRejectedException.class)
    public ConductorOnboardingAcceptedResponse verify(String rawToken, String code) {
        String tokenHash;
        try {
            tokenHash = opaqueTokenService.hash(rawToken);
        } catch (IllegalArgumentException exception) {
            throw invalidOtp();
        }
        UUID identityId = repository.findIdentityIdByVerificationToken(tokenHash).orElseThrow(this::invalidOtp);
        IdentityRow identity = repository.findIdentityByIdForUpdate(identityId).orElseThrow(this::invalidOtp);
        ChallengeRow challenge = repository.findChallengeByTokenForUpdate(tokenHash)
                .orElseThrow(this::invalidOtp);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);

        if (challenge.consumedAt() != null && challenge.requestId() != null) {
            OnboardingRequestRow prior = repository.findRequest(challenge.requestId()).orElseThrow(this::invalidOtp);
            return accepted(prior);
        }
        if (isBlocked(challenge, now)) {
            throw otpBlocked();
        }
        if (!challenge.challengeExpiresAt().isAfter(now)
                || challenge.otpHash() == null
                || challenge.otpExpiresAt() == null
                || !challenge.otpExpiresAt().isAfter(now)) {
            throw new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.EMAIL_OTP_EXPIRED,
                    "El código venció. Inicia nuevamente la incorporación."
            );
        }
        if (!otpCodeHasher.matches(code, challenge.otpHash())) {
            short failures = (short) Math.min(2, challenge.failedAttempts() + 1);
            Instant blockedUntil = failures >= 2 ? now.plus(OTP_BLOCK) : challenge.blockedUntil();
            ChallengeRow rejected = new ChallengeRow(
                    challenge.id(), challenge.identityId(), challenge.invitationId(), challenge.idempotencyKey(),
                    challenge.verificationTokenHash(), failures >= 2 ? null : challenge.otpHash(),
                    challenge.otpIssuedAt(), failures >= 2 ? null : challenge.otpExpiresAt(),
                    challenge.challengeExpiresAt(), failures, blockedUntil, challenge.sendAt1(),
                    challenge.sendAt2(), challenge.sendAt3(), challenge.consumedAt(), challenge.requestId(),
                    challenge.createdAt(), now
            );
            repository.saveChallenge(rejected);
            throw new OnboardingOtpRejectedException(failures >= 2);
        }

        OnboardingRequestRow existing = repository.findRequestByIdempotency(
                identity.id(), challenge.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            if (!existing.invitationId().equals(challenge.invitationId())) {
                throw conflict("La clave de idempotencia pertenece a otra invitación.");
            }
            return accepted(existing);
        }
        InvitationRow invitation = repository.lockInvitation(challenge.invitationId(), identity.email())
                .orElseThrow(this::invitationNoLongerAvailable);
        if (invitation.revokedAt() != null
                || invitation.usedAt() != null
                || !invitation.expiresAt().isAfter(now)) {
            throw invitationNoLongerAvailable();
        }
        if (repository.findPendingRequest(identity.id(), invitation.organizationId()).isPresent()) {
            throw conflict("Ya existe una solicitud pendiente para esta organización.");
        }
        if (users.existsByEmailIgnoreCase(identity.email())) {
            throw conflict("La identidad ya tiene una cuenta. Utiliza su flujo de acceso autorizado.");
        }

        OnboardingRequestRow request = repository.createRequest(new OnboardingRequestRow(
                UUID.randomUUID(),
                identity.id(),
                invitation.organizationId(),
                invitation.id(),
                challenge.idempotencyKey(),
                "PENDING_REVIEW",
                now
        ));
        if (!repository.markInvitationUsed(invitation.id(), now)) {
            throw invitationNoLongerAvailable();
        }
        repository.markIdentityVerified(identity.id(), now);
        repository.saveChallenge(new ChallengeRow(
                challenge.id(), challenge.identityId(), challenge.invitationId(), challenge.idempotencyKey(),
                challenge.verificationTokenHash(), null, challenge.otpIssuedAt(), null,
                challenge.challengeExpiresAt(), challenge.failedAttempts(), challenge.blockedUntil(),
                challenge.sendAt1(), challenge.sendAt2(), challenge.sendAt3(), now, request.id(),
                challenge.createdAt(), now
        ));
        organizations.findById(invitation.organizationId()).ifPresent(organization ->
                auditService.recordSystem(
                        organization,
                        "CONDUCTOR_ONBOARDING_SUBMITTED",
                        "CONDUCTOR_ONBOARDING_REQUEST",
                        request.id(),
                        Map.of("invitationId", invitation.id().toString(), "status", request.status())
                ));
        return accepted(request);
    }

    private boolean recordInvitationFailure(String emailHash, String originHash, Instant now) {
        repository.pruneFailuresAtOrBefore(now.minus(INVITATION_FAILURE_WINDOW));
        repository.recordFailure(emailHash, originHash, now);
        Instant cutoff = now.minus(INVITATION_FAILURE_WINDOW);
        long emailFailures = repository.countEmailFailures(emailHash, cutoff);
        long originFailures = repository.countOriginFailures(originHash, cutoff);
        boolean limited = false;
        if (emailFailures >= EMAIL_FAILURE_LIMIT) {
            repository.blockGuard("EMAIL", emailHash, now.plus(EMAIL_INVITATION_BLOCK), now);
            limited = true;
        }
        if (originFailures >= ORIGIN_FAILURE_LIMIT) {
            repository.blockGuard("ORIGIN", originHash, now.plus(ORIGIN_INVITATION_PAUSE), now);
            limited = true;
        }
        return limited;
    }

    private List<Instant> recentSends(ChallengeRow challenge, Instant now) {
        if (challenge == null) {
            return new ArrayList<>();
        }
        Instant cutoff = now.minus(SEND_WINDOW);
        return java.util.stream.Stream.of(challenge.sendAt1(), challenge.sendAt2(), challenge.sendAt3())
                .filter(value -> value != null && value.isAfter(cutoff))
                .sorted()
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }

    private static boolean isBlocked(ChallengeRow challenge, Instant now) {
        return challenge.blockedUntil() != null && challenge.blockedUntil().isAfter(now);
    }

    private static short failuresOnIssue(ChallengeRow challenge, Instant now) {
        if (challenge == null || (challenge.blockedUntil() != null && !challenge.blockedUntil().isAfter(now))) {
            return 0;
        }
        return challenge.failedAttempts();
    }

    private static Instant laterActive(Instant left, Instant right, Instant now) {
        Instant activeLeft = left != null && left.isAfter(now) ? left : null;
        Instant activeRight = right != null && right.isAfter(now) ? right : null;
        if (activeLeft == null) {
            return activeRight;
        }
        return activeRight == null || activeLeft.isAfter(activeRight) ? activeLeft : activeRight;
    }

    private static Instant at(List<Instant> values, int index) {
        return values.size() > index ? values.get(index) : null;
    }

    private ChallengeRow withToken(ChallengeRow challenge, String tokenHash, Instant now) {
        return new ChallengeRow(
                challenge.id(), challenge.identityId(), challenge.invitationId(), challenge.idempotencyKey(),
                tokenHash, challenge.otpHash(), challenge.otpIssuedAt(), challenge.otpExpiresAt(),
                challenge.challengeExpiresAt(), challenge.failedAttempts(), challenge.blockedUntil(),
                challenge.sendAt1(), challenge.sendAt2(), challenge.sendAt3(), challenge.consumedAt(),
                challenge.requestId(), challenge.createdAt(), now
        );
    }

    private static ConductorOnboardingOtpResponse otpResponse(
            String token,
            ChallengeRow challenge,
            boolean emailSent
    ) {
        return new ConductorOnboardingOtpResponse(
                token,
                challenge.otpExpiresAt(),
                challenge.challengeExpiresAt(),
                emailSent
        );
    }

    private static ConductorOnboardingAcceptedResponse accepted(OnboardingRequestRow request) {
        return new ConductorOnboardingAcceptedResponse(request.id(), request.status());
    }

    private static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 100) {
            throw new ApplicationException(
                    HttpStatus.BAD_REQUEST,
                    ErrorCode.VALIDATION_ERROR,
                    "La clave de idempotencia es obligatoria y no puede exceder 100 caracteres."
            );
        }
        return idempotencyKey.strip();
    }

    private static String optionalText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.strip();
    }

    private ApplicationException invalidOtp() {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.EMAIL_OTP_INVALID,
                "El código de verificación no es válido."
        );
    }

    private ApplicationException otpBlocked() {
        return new ApplicationException(
                HttpStatus.TOO_MANY_REQUESTS,
                ErrorCode.EMAIL_OTP_LOCKED,
                "Se alcanzó el límite de validación. Inténtalo nuevamente después del bloqueo."
        );
    }

    private ApplicationException invitationNoLongerAvailable() {
        return new ApplicationException(
                HttpStatus.CONFLICT,
                ErrorCode.RESOURCE_CONFLICT,
                "La invitación ya no está disponible."
        );
    }

    private ApplicationException conflict(String message) {
        return new ApplicationException(HttpStatus.CONFLICT, ErrorCode.RESOURCE_CONFLICT, message);
    }
}
