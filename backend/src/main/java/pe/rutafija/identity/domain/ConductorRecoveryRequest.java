package pe.rutafija.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "conductor_recovery_request")
public class ConductorRecoveryRequest {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "conductor_user_id", nullable = false)
    private UUID conductorUserId;

    @Column(name = "authorized_by_admin_id", nullable = false)
    private UUID authorizedByAdminId;

    @Column(name = "previous_email", nullable = false, length = 180)
    private String previousEmail;

    @Column(name = "requested_email", nullable = false, length = 180)
    private String requestedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_method", nullable = false, length = 32)
    private RecoveryEvidenceMethod evidenceMethod;

    /** A short reference only; evidence documents and their contents are not stored. */
    @Column(name = "evidence_reference", nullable = false, length = 200)
    private String evidenceReference;

    @Column(nullable = false, length = 300)
    private String reason;

    @Column(name = "expediente_reviewed", nullable = false)
    private boolean expedienteReviewed;

    @Column(name = "independent_evidence_confirmed", nullable = false)
    private boolean independentEvidenceConfirmed;

    @Column(name = "password_reset_required", nullable = false)
    private boolean passwordResetRequired;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ConductorRecoveryStatus status;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_reset_token_hash", length = 64)
    private String passwordResetTokenHash;

    @Column(name = "password_reset_expires_at")
    private Instant passwordResetExpiresAt;

    @Column(name = "password_reset_consumed_at")
    private Instant passwordResetConsumedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ConductorRecoveryRequest() {
    }

    private ConductorRecoveryRequest(
            UUID organizationId,
            UUID conductorUserId,
            UUID authorizedByAdminId,
            String previousEmail,
            String requestedEmail,
            RecoveryEvidenceMethod evidenceMethod,
            String evidenceReference,
            String reason,
            boolean passwordResetRequired,
            Instant now
    ) {
        this.id = UUID.randomUUID();
        this.organizationId = Objects.requireNonNull(organizationId);
        this.conductorUserId = Objects.requireNonNull(conductorUserId);
        this.authorizedByAdminId = Objects.requireNonNull(authorizedByAdminId);
        this.previousEmail = Objects.requireNonNull(previousEmail);
        this.requestedEmail = Objects.requireNonNull(requestedEmail);
        this.evidenceMethod = Objects.requireNonNull(evidenceMethod);
        this.evidenceReference = Objects.requireNonNull(evidenceReference);
        this.reason = Objects.requireNonNull(reason);
        this.expedienteReviewed = true;
        this.independentEvidenceConfirmed = true;
        this.passwordResetRequired = passwordResetRequired;
        this.status = ConductorRecoveryStatus.OTP_PENDING;
        this.createdAt = Objects.requireNonNull(now);
        this.updatedAt = now;
    }

    public static ConductorRecoveryRequest authorize(
            UUID organizationId,
            UUID conductorUserId,
            UUID authorizedByAdminId,
            String previousEmail,
            String requestedEmail,
            RecoveryEvidenceMethod evidenceMethod,
            String evidenceReference,
            String reason,
            boolean passwordResetRequired,
            Instant now
    ) {
        return new ConductorRecoveryRequest(
                organizationId,
                conductorUserId,
                authorizedByAdminId,
                previousEmail,
                requestedEmail,
                evidenceMethod,
                evidenceReference,
                reason,
                passwordResetRequired,
                now
        );
    }

    public void markEmailVerified(Instant verifiedAt) {
        if (status != ConductorRecoveryStatus.OTP_PENDING) {
            throw new IllegalStateException("Only a pending conductor recovery can verify its new email");
        }
        this.emailVerifiedAt = Objects.requireNonNull(verifiedAt);
        if (passwordResetRequired) {
            this.status = ConductorRecoveryStatus.PASSWORD_RESET_PENDING;
        } else {
            this.status = ConductorRecoveryStatus.COMPLETED;
            this.completedAt = verifiedAt;
        }
        this.updatedAt = verifiedAt;
    }

    public void issuePasswordResetToken(String tokenHash, Instant expiresAt, Instant issuedAt) {
        if (status != ConductorRecoveryStatus.PASSWORD_RESET_PENDING || emailVerifiedAt == null) {
            throw new IllegalStateException("Verified email and a pending password reset are required");
        }
        this.passwordResetTokenHash = Objects.requireNonNull(tokenHash);
        this.passwordResetExpiresAt = Objects.requireNonNull(expiresAt);
        this.passwordResetConsumedAt = null;
        this.updatedAt = Objects.requireNonNull(issuedAt);
    }

    public boolean hasActivePasswordResetAt(Instant now) {
        return status == ConductorRecoveryStatus.PASSWORD_RESET_PENDING
                && passwordResetTokenHash != null
                && passwordResetConsumedAt == null
                && passwordResetExpiresAt != null
                && passwordResetExpiresAt.isAfter(now);
    }

    public boolean matchesPasswordResetTokenHash(String candidateHash) {
        return passwordResetTokenHash != null && passwordResetTokenHash.equals(candidateHash);
    }

    public void completePasswordReset(Instant completedAt) {
        if (status != ConductorRecoveryStatus.PASSWORD_RESET_PENDING
                || emailVerifiedAt == null
                || passwordResetConsumedAt != null
                || passwordResetTokenHash == null
                || passwordResetExpiresAt == null
                || !passwordResetExpiresAt.isAfter(completedAt)) {
            throw new IllegalStateException("A verified, unused password reset is required");
        }
        this.passwordResetConsumedAt = Objects.requireNonNull(completedAt);
        this.status = ConductorRecoveryStatus.COMPLETED;
        this.completedAt = completedAt;
        this.updatedAt = completedAt;
    }

    public void cancel(Instant cancelledAt) {
        if (status == ConductorRecoveryStatus.COMPLETED) {
            throw new IllegalStateException("A completed conductor recovery cannot be cancelled");
        }
        this.status = ConductorRecoveryStatus.CANCELLED;
        this.passwordResetTokenHash = null;
        this.passwordResetExpiresAt = null;
        this.updatedAt = Objects.requireNonNull(cancelledAt);
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getConductorUserId() {
        return conductorUserId;
    }

    public UUID getAuthorizedByAdminId() {
        return authorizedByAdminId;
    }

    public String getPreviousEmail() {
        return previousEmail;
    }

    public String getRequestedEmail() {
        return requestedEmail;
    }

    public RecoveryEvidenceMethod getEvidenceMethod() {
        return evidenceMethod;
    }

    public String getEvidenceReference() {
        return evidenceReference;
    }

    public String getReason() {
        return reason;
    }

    public boolean isExpedienteReviewed() {
        return expedienteReviewed;
    }

    public boolean isIndependentEvidenceConfirmed() {
        return independentEvidenceConfirmed;
    }

    public boolean isPasswordResetRequired() {
        return passwordResetRequired;
    }

    public ConductorRecoveryStatus getStatus() {
        return status;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPasswordResetTokenHash() {
        return passwordResetTokenHash;
    }

    public Instant getPasswordResetExpiresAt() {
        return passwordResetExpiresAt;
    }

    public Instant getPasswordResetConsumedAt() {
        return passwordResetConsumedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
