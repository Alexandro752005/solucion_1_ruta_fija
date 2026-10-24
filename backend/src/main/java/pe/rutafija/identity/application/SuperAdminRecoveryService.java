package pe.rutafija.identity.application;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.rutafija.audit.application.AuditService;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryChallengeResponse;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryCompletionResponse;
import pe.rutafija.identity.api.dto.SuperAdminRecoveryEnrollmentResponse;
import pe.rutafija.identity.application.EmailOtpService.RecoveryEmailProof;
import pe.rutafija.identity.domain.AppUser;
import pe.rutafija.identity.domain.SuperAdminRecoveryChallenge;
import pe.rutafija.identity.domain.SuperAdminRecoveryCredential;
import pe.rutafija.identity.domain.UserRole;
import pe.rutafija.identity.infrastructure.AppUserRepository;
import pe.rutafija.identity.infrastructure.RefreshTokenRepository;
import pe.rutafija.identity.infrastructure.SuperAdminRecoveryChallengeRepository;
import pe.rutafija.identity.infrastructure.SuperAdminRecoveryCredentialRepository;
import pe.rutafija.shared.exception.ApplicationException;
import pe.rutafija.shared.exception.ErrorCode;
import pe.rutafija.shared.security.AuthenticatedUser;
import pe.rutafija.shared.security.CurrentUserProvider;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SuperAdminRecoveryService {

    static final Duration PROOF_TTL = Duration.ofMinutes(5);
    private static final short MAX_PROOF_ATTEMPTS = 3;
    private static final short CREDENTIAL_SINGLETON_ID = 1;
    private static final Pattern EMERGENCY_CODE = Pattern.compile("[A-Za-z0-9_-]{43}");
    private static final Pattern SIGNATURE = Pattern.compile("[A-Za-z0-9_-]{86}");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppUserRepository userRepository;
    private final SuperAdminRecoveryCredentialRepository credentialRepository;
    private final SuperAdminRecoveryChallengeRepository challengeRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final EmailOtpService emailOtpService;
    private final OtpCodeHasher codeHasher;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public SuperAdminRecoveryService(
            AppUserRepository userRepository,
            SuperAdminRecoveryCredentialRepository credentialRepository,
            SuperAdminRecoveryChallengeRepository challengeRepository,
            RefreshTokenRepository refreshTokenRepository,
            EmailOtpService emailOtpService,
            OtpCodeHasher codeHasher,
            PasswordEncoder passwordEncoder,
            AuditService auditService,
            CurrentUserProvider currentUserProvider,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.challengeRepository = challengeRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.emailOtpService = emailOtpService;
        this.codeHasher = codeHasher;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Transactional
    public SuperAdminRecoveryEnrollmentResponse enroll(String publicKeyBase64, String currentPassword) {
        AuthenticatedUser authenticated = currentUserProvider.requireCurrentUser();
        AppUser owner = userRepository.findByIdForUpdate(authenticated.userId())
                .orElseThrow(this::recoveryUnavailable);
        if (owner.getRole() != UserRole.SUPER_ADMIN || !owner.isActive() || !owner.isEmailVerified()) {
            throw recoveryUnavailable();
        }
        if (!passwordEncoder.matches(currentPassword, owner.getPasswordHash())) {
            auditService.record(owner, "SUPER_ADMIN_RECOVERY_ENROLLMENT_REJECTED", Map.of("reason", "REAUTH_FAILED"));
            throw new ApplicationException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.AUTH_INVALID_CREDENTIALS,
                    "La contraseña actual no es válida"
            );
        }
        if (credentialRepository.findSingletonForUpdate().isPresent()) {
            throw new ApplicationException(
                    HttpStatus.CONFLICT,
                    ErrorCode.SUPER_ADMIN_RECOVERY_ALREADY_ENROLLED,
                    "La instalación ya tiene una clave de recuperación inscrita"
            );
        }

        byte[] publicKeyBytes = parseEd25519PublicKey(publicKeyBase64).getEncoded();
        String publicKeyX509 = Base64.getEncoder().encodeToString(publicKeyBytes);
        String emergencyCode = generateEmergencyCode();
        Instant now = Instant.now(clock);
        UUID installationId = UUID.randomUUID();
        credentialRepository.saveAndFlush(SuperAdminRecoveryCredential.enroll(
                owner,
                installationId,
                publicKeyX509,
                passwordEncoder.encode(emergencyCode),
                now
        ));
        auditService.record(owner, "SUPER_ADMIN_RECOVERY_ENROLLED", Map.of(
                "installationId", installationId.toString(),
                "signatureAlgorithm", "Ed25519"
        ));

        return new SuperAdminRecoveryEnrollmentResponse(
                installationId,
                sha256Hex(publicKeyBytes),
                emergencyCode,
                now
        );
    }

    @Transactional
    public SuperAdminRecoveryChallengeResponse start(String rawAccountEmail, String rawNewEmail) {
        String accountEmail = normalizeEmail(rawAccountEmail);
        String newEmail = normalizeEmail(rawNewEmail);
        Instant now = Instant.now(clock);
        UUID challengeId = UUID.randomUUID();
        UUID accountReference = UUID.randomUUID();
        byte[] nonceBytes = new byte[32];
        RANDOM.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        Instant expiresAt = now.plus(PROOF_TTL);

        AppUser owner = userRepository.findByEmailIgnoreCase(accountEmail).orElse(null);
        SuperAdminRecoveryCredential credential = credentialRepository.findSingleton().orElse(null);
        boolean eligible = owner != null
                && owner.getRole() == UserRole.SUPER_ADMIN
                && owner.isActive()
                && owner.isEmailVerified()
                && !accountEmail.equals(newEmail)
                && !userRepository.existsByEmailIgnoreCaseAndIdNot(newEmail, owner.getId())
                && credential != null
                && owner.getId().equals(credential.getUser().getId())
                && !challengeRepository.existsByUser_IdAndProofVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfter(
                        owner.getId(), now
                );

        UUID installationId = eligible ? credential.getInstallationId() : UUID.randomUUID();
        String challengeToSign = canonicalChallenge(
                installationId,
                accountReference,
                newEmail,
                challengeId,
                nonce,
                expiresAt
        );
        if (eligible) {
            challengeRepository.save(SuperAdminRecoveryChallenge.create(
                    challengeId,
                    owner,
                    installationId,
                    accountReference,
                    newEmail,
                    nonce,
                    expiresAt,
                    now
            ));
        }

        return new SuperAdminRecoveryChallengeResponse(
                challengeId,
                installationId,
                accountReference,
                nonce,
                expiresAt,
                challengeToSign
        );
    }

    @Transactional(noRollbackFor = RecoveryProofRejectedException.class)
    public EmailOtpTicket prove(UUID challengeId, String emergencyCode, String signatureBase64Url) {
        SuperAdminRecoveryChallenge initial = challengeRepository.findById(challengeId)
                .orElseThrow(RecoveryProofRejectedException::new);
        AppUser owner = userRepository.findByIdForUpdate(initial.getUser().getId())
                .orElseThrow(RecoveryProofRejectedException::new);
        SuperAdminRecoveryCredential credential = credentialRepository.findSingletonForUpdate()
                .orElseThrow(RecoveryProofRejectedException::new);
        SuperAdminRecoveryChallenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(RecoveryProofRejectedException::new);
        Instant now = Instant.now(clock);

        if (!challenge.isActiveAt(now)
                || challenge.isProofVerified()
                || owner.getRole() != UserRole.SUPER_ADMIN
                || !owner.isActive()
                || !owner.isEmailVerified()
                || !owner.getId().equals(credential.getUser().getId())
                || !challenge.getInstallationId().equals(credential.getInstallationId())
                || !challenge.getInstallationId().equals(initial.getInstallationId())
                || userRepository.existsByEmailIgnoreCaseAndIdNot(challenge.getRequestedEmail(), owner.getId())
                || challengeRepository.existsByUser_IdAndProofVerifiedAtIsNotNullAndConsumedAtIsNullAndExpiresAtAfter(
                        owner.getId(), now
                )) {
            throw recoveryExpiredOrInvalid(challenge, now);
        }

        boolean validCode = emergencyCode != null
                && EMERGENCY_CODE.matcher(emergencyCode).matches()
                && passwordEncoder.matches(emergencyCode, credential.getEmergencyCodeHash());
        boolean validSignature = verifySignature(credential.getPublicKeyX509(), challenge, signatureBase64Url);
        if (!validCode || !validSignature) {
            challenge.recordInvalidProof(now);
            auditService.record(owner, "SUPER_ADMIN_RECOVERY_PROOF_REJECTED", Map.of(
                    "attempt", challenge.getFailedProofAttempts()
            ));
            throw new RecoveryProofRejectedException();
        }

        challenge.verifyProof(now);
        EmailOtpTicket ticket = emailOtpService.beginForSuperAdminRecovery(
                owner,
                challenge.getRequestedEmail(),
                challenge.getId()
        );
        auditService.record(owner, "SUPER_ADMIN_RECOVERY_PROOF_ACCEPTED", Map.of());
        return ticket;
    }

    @Transactional
    public EmailOtpTicket resendEmail(UUID challengeId, String verificationToken) {
        SuperAdminRecoveryChallenge initial = challengeRepository.findById(challengeId)
                .orElseThrow(this::recoveryUnavailable);
        userRepository.findByIdForUpdate(initial.getUser().getId()).orElseThrow(this::recoveryUnavailable);
        SuperAdminRecoveryChallenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(this::recoveryUnavailable);
        Instant now = Instant.now(clock);
        if (!challenge.isActiveAt(now) || !challenge.isProofVerified()) {
            throw recoveryExpiredOrInvalid(challenge, now);
        }
        return emailOtpService.resendSuperAdminRecovery(challengeId, verificationToken);
    }

    @Transactional(noRollbackFor = EmailOtpCodeRejectedException.class)
    public SuperAdminRecoveryCompletionResponse complete(
            UUID challengeId,
            String verificationToken,
            String code,
            String newPassword
    ) {
        RecoveryEmailProof emailProof = emailOtpService.verifySuperAdminRecovery(
                challengeId,
                verificationToken,
                code
        );
        AppUser owner = userRepository.findByIdForUpdate(emailProof.user().getId())
                .orElseThrow(this::recoveryUnavailable);
        SuperAdminRecoveryCredential credential = credentialRepository.findSingletonForUpdate()
                .orElseThrow(this::recoveryUnavailable);
        SuperAdminRecoveryChallenge challenge = challengeRepository.findByIdForUpdate(challengeId)
                .orElseThrow(this::recoveryUnavailable);
        Instant now = Instant.now(clock);

        if (owner.getRole() != UserRole.SUPER_ADMIN
                || !owner.isActive()
                || !challenge.isProofVerified()
                || !challenge.isActiveAt(now)
                || !owner.getId().equals(challenge.getUser().getId())
                || !owner.getId().equals(credential.getUser().getId())
                || !challenge.getInstallationId().equals(credential.getInstallationId())
                || !challenge.getRequestedEmail().equals(emailProof.newEmail())
                || userRepository.existsByEmailIgnoreCaseAndIdNot(emailProof.newEmail(), owner.getId())) {
            throw recoveryUnavailable();
        }

        String newEmergencyCode = generateEmergencyCode();
        owner.completeSuperAdminRecovery(
                emailProof.newEmail(),
                passwordEncoder.encode(newPassword),
                now
        );
        int revokedSessions = refreshTokenRepository.revokeActiveByUserId(owner.getId(), now);
        challenge.consume(now);
        credential.rotateEmergencyCode(passwordEncoder.encode(newEmergencyCode), now);
        auditService.record(owner, "SUPER_ADMIN_RECOVERY_COMPLETED", Map.of(
                "installationId", credential.getInstallationId().toString(),
                "refreshTokensRevoked", revokedSessions
        ));

        return new SuperAdminRecoveryCompletionResponse(emailProof.newEmail(), now, newEmergencyCode);
    }

    @Transactional(readOnly = true)
    public PublicKey parseEnrolledPublicKey(String encodedKey) {
        return parseEd25519PublicKey(encodedKey);
    }

    private PublicKey parseEd25519PublicKey(String encodedKey) {
        try {
            byte[] der = Base64.getDecoder().decode(encodedKey);
            if (der.length != 44) {
                throw invalidPublicKey();
            }
            PublicKey key = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
            if (!"EdDSA".equalsIgnoreCase(key.getAlgorithm())
                    && !"Ed25519".equalsIgnoreCase(key.getAlgorithm())) {
                throw invalidPublicKey();
            }
            return key;
        } catch (IllegalArgumentException | java.security.GeneralSecurityException exception) {
            throw invalidPublicKey();
        }
    }

    private boolean verifySignature(String publicKeyBase64, SuperAdminRecoveryChallenge challenge, String signatureBase64Url) {
        if (signatureBase64Url == null || !SIGNATURE.matcher(signatureBase64Url).matches()) {
            return false;
        }
        try {
            byte[] signatureBytes = Base64.getUrlDecoder().decode(signatureBase64Url);
            if (signatureBytes.length != 64) {
                return false;
            }
            PublicKey publicKey = parseEd25519PublicKey(publicKeyBase64);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(canonicalChallenge(challenge).getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);
        } catch (java.security.GeneralSecurityException | IllegalArgumentException exception) {
            return false;
        }
    }

    private String canonicalChallenge(SuperAdminRecoveryChallenge challenge) {
        return canonicalChallenge(
                challenge.getInstallationId(),
                challenge.getAccountReference(),
                challenge.getRequestedEmail(),
                challenge.getId(),
                challenge.getNonce(),
                challenge.getExpiresAt()
        );
    }

    private String canonicalChallenge(
            UUID installationId,
            UUID accountReference,
            String newEmail,
            UUID challengeId,
            String nonce,
            Instant expiresAt
    ) {
        return String.join("\n",
                "ruta-fija-super-admin-recovery-v1",
                installationId.toString(),
                accountReference.toString(),
                normalizeEmail(newEmail),
                challengeId.toString(),
                nonce,
                Long.toString(expiresAt.toEpochMilli())
        );
    }

    private ApplicationException recoveryExpiredOrInvalid(SuperAdminRecoveryChallenge challenge, Instant now) {
        if (!challenge.isActiveAt(now)) {
            return new ApplicationException(
                    HttpStatus.GONE,
                    ErrorCode.SUPER_ADMIN_RECOVERY_EXPIRED,
                    "La solicitud de recuperación venció. Inicia una nueva solicitud."
            );
        }
        return new RecoveryProofRejectedException();
    }

    private ApplicationException recoveryUnavailable() {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.SUPER_ADMIN_RECOVERY_INVALID,
                "La solicitud de recuperación no es válida"
        );
    }

    private ApplicationException invalidPublicKey() {
        return new ApplicationException(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_ERROR,
                "La clave pública X.509 Ed25519 no tiene un formato válido"
        );
    }

    private String generateEmergencyCode() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    private String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
