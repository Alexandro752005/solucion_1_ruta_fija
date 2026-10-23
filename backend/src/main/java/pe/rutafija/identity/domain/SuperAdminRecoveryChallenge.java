package pe.rutafija.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "super_admin_recovery_challenge")
public class SuperAdminRecoveryChallenge {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "installation_id", nullable = false)
    private UUID installationId;

    @Column(name = "account_reference", nullable = false)
    private UUID accountReference;

    @Column(name = "requested_email", nullable = false, length = 180)
    private String requestedEmail;

    @Column(nullable = false, length = 43, unique = true)
    private String nonce;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "failed_proof_attempts", nullable = false)
    private short failedProofAttempts;

    @Column(name = "proof_verified_at")
    private Instant proofVerifiedAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SuperAdminRecoveryChallenge() {
    }

    private SuperAdminRecoveryChallenge(
            UUID id,
            AppUser user,
            UUID installationId,
            UUID accountReference,
            String requestedEmail,
            String nonce,
            Instant expiresAt,
            Instant now
    ) {
        this.id = Objects.requireNonNull(id);
        this.user = Objects.requireNonNull(user);
        this.installationId = Objects.requireNonNull(installationId);
        this.accountReference = Objects.requireNonNull(accountReference);
        this.requestedEmail = Objects.requireNonNull(requestedEmail);
        this.nonce = Objects.requireNonNull(nonce);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static SuperAdminRecoveryChallenge create(
            UUID id,
            AppUser user,
            UUID installationId,
            UUID accountReference,
            String requestedEmail,
            String nonce,
            Instant expiresAt,
            Instant now
    ) {
        return new SuperAdminRecoveryChallenge(
                id,
                user,
                installationId,
                accountReference,
                requestedEmail,
                nonce,
                expiresAt,
                now
        );
    }

    public boolean isActiveAt(Instant now) {
        return consumedAt == null && expiresAt.isAfter(now);
    }

    public boolean isProofVerified() {
        return proofVerifiedAt != null;
    }

    public void recordInvalidProof(Instant now) {
        if (failedProofAttempts < 3) {
            failedProofAttempts++;
        }
        if (failedProofAttempts >= 3) {
            consumedAt = now;
        }
        updatedAt = now;
    }

    public void verifyProof(Instant now) {
        if (proofVerifiedAt != null || consumedAt != null) {
            throw new IllegalStateException("A recovery proof can be accepted only once");
        }
        proofVerifiedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public void consume(Instant now) {
        consumedAt = Objects.requireNonNull(now);
        updatedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public UUID getInstallationId() {
        return installationId;
    }

    public UUID getAccountReference() {
        return accountReference;
    }

    public String getRequestedEmail() {
        return requestedEmail;
    }

    public String getNonce() {
        return nonce;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public short getFailedProofAttempts() {
        return failedProofAttempts;
    }
}
