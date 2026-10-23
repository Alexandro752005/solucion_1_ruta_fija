package pe.rutafija.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_otp_challenge")
public class EmailOtpChallenge {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailOtpPurpose purpose;

    @Column(name = "recipient_email", nullable = false, length = 180)
    private String recipientEmail;

    @Column(name = "purpose_reference_id")
    private UUID purposeReferenceId;

    @Column(name = "otp_hash", length = 64)
    private String otpHash;

    @Column(name = "otp_issued_at")
    private Instant otpIssuedAt;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    @Column(name = "challenge_expires_at")
    private Instant challengeExpiresAt;

    @Column(name = "failed_attempts", nullable = false)
    private short failedAttempts;

    @Column(name = "blocked_until")
    private Instant blockedUntil;

    @Column(name = "send_at_1")
    private Instant sendAt1;

    @Column(name = "send_at_2")
    private Instant sendAt2;

    @Column(name = "send_at_3")
    private Instant sendAt3;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected EmailOtpChallenge() {
    }

    private EmailOtpChallenge(AppUser user, Instant now) {
        this.id = UUID.randomUUID();
        this.user = Objects.requireNonNull(user);
        this.purpose = EmailOtpPurpose.LOGIN;
        this.recipientEmail = user.getEmail();
        this.failedAttempts = 0;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static EmailOtpChallenge create(AppUser user, Instant now) {
        return new EmailOtpChallenge(user, now);
    }

    public boolean isBlockedAt(Instant now) {
        return blockedUntil != null && blockedUntil.isAfter(now);
    }

    public boolean hasActiveCodeAt(Instant now) {
        return consumedAt == null
                && otpHash != null
                && otpExpiresAt != null
                && otpExpiresAt.isAfter(now)
                && challengeExpiresAt != null
                && challengeExpiresAt.isAfter(now);
    }

    public boolean hasActiveChallengeAt(Instant now) {
        return consumedAt == null
                && verificationTokenHash != null
                && challengeExpiresAt != null
                && challengeExpiresAt.isAfter(now);
    }

    public Instant nextSendAllowedAt(Instant now, Duration window) {
        List<Instant> recent = recentSends(now, window);
        if (recent.size() < 3) {
            return null;
        }
        return recent.get(0).plus(window);
    }

    public void replaceVerificationToken(String tokenHash, Instant now) {
        if (!hasActiveChallengeAt(now)) {
            throw new IllegalStateException("An active email verification challenge is required");
        }
        this.verificationTokenHash = Objects.requireNonNull(tokenHash);
        this.updatedAt = now;
    }

    public void issueCode(
            String tokenHash,
            String codeHash,
            Instant now,
            Instant otpExpiry,
            Instant challengeExpiry,
            Duration sendWindow,
            EmailOtpPurpose purpose,
            String recipientEmail,
            UUID purposeReferenceId
    ) {
        if (isBlockedAt(now)) {
            throw new IllegalStateException("A blocked email verification challenge cannot be issued");
        }
        Instant nextSendAllowedAt = nextSendAllowedAt(now, sendWindow);
        if (nextSendAllowedAt != null) {
            throw new IllegalStateException("The email OTP send window is exhausted");
        }

        if (blockedUntil != null && !blockedUntil.isAfter(now)) {
            failedAttempts = 0;
            blockedUntil = null;
        }

        verificationTokenHash = Objects.requireNonNull(tokenHash);
        otpHash = Objects.requireNonNull(codeHash);
        otpIssuedAt = Objects.requireNonNull(now);
        otpExpiresAt = Objects.requireNonNull(otpExpiry);
        challengeExpiresAt = Objects.requireNonNull(challengeExpiry);
        this.purpose = Objects.requireNonNull(purpose);
        this.recipientEmail = Objects.requireNonNull(recipientEmail);
        this.purposeReferenceId = purposeReferenceId;
        consumedAt = null;

        List<Instant> recent = recentSends(now, sendWindow);
        recent.add(now);
        recent.sort(Comparator.naturalOrder());
        sendAt1 = recent.size() > 0 ? recent.get(0) : null;
        sendAt2 = recent.size() > 1 ? recent.get(1) : null;
        sendAt3 = recent.size() > 2 ? recent.get(2) : null;
        updatedAt = now;
    }

    public boolean matchesVerificationTokenHash(String tokenHash) {
        return verificationTokenHash != null && verificationTokenHash.equals(tokenHash);
    }

    public String getOtpHash() {
        return otpHash;
    }

    public EmailOtpPurpose getPurpose() {
        return purpose;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public UUID getPurposeReferenceId() {
        return purposeReferenceId;
    }

    public Instant getOtpExpiresAt() {
        return otpExpiresAt;
    }

    public Instant getChallengeExpiresAt() {
        return challengeExpiresAt;
    }

    public short getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getBlockedUntil() {
        return blockedUntil;
    }

    public AppUser getUser() {
        return user;
    }

    public void recordInvalidCode(Instant now, Duration blockDuration) {
        if (failedAttempts < 2) {
            failedAttempts++;
        }
        if (failedAttempts >= 2) {
            blockedUntil = now.plus(blockDuration);
            otpHash = null;
            otpExpiresAt = now;
        }
        updatedAt = now;
    }

    public void consume(Instant now) {
        consumedAt = Objects.requireNonNull(now);
        verificationTokenHash = null;
        otpHash = null;
        otpExpiresAt = now;
        updatedAt = now;
    }

    public void invalidateCurrentChallenge(Instant now) {
        verificationTokenHash = null;
        otpHash = null;
        otpIssuedAt = null;
        otpExpiresAt = null;
        challengeExpiresAt = null;
        consumedAt = null;
        purpose = EmailOtpPurpose.LOGIN;
        recipientEmail = user.getEmail();
        purposeReferenceId = null;
        updatedAt = now;
    }

    private List<Instant> recentSends(Instant now, Duration window) {
        Instant cutoff = now.minus(window);
        List<Instant> recent = new ArrayList<>(3);
        addIfRecent(recent, sendAt1, cutoff);
        addIfRecent(recent, sendAt2, cutoff);
        addIfRecent(recent, sendAt3, cutoff);
        recent.sort(Comparator.naturalOrder());
        return recent;
    }

    private static void addIfRecent(List<Instant> result, Instant sentAt, Instant cutoff) {
        if (sentAt != null && sentAt.isAfter(cutoff)) {
            result.add(sentAt);
        }
    }
}
