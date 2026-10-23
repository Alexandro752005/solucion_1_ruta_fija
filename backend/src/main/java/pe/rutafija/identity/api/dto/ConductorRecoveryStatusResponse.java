package pe.rutafija.identity.api.dto;

import pe.rutafija.identity.domain.ConductorRecoveryRequest;

import java.time.Instant;
import java.util.UUID;

public record ConductorRecoveryStatusResponse(
        UUID recoveryId,
        UUID organizationId,
        UUID conductorUserId,
        UUID authorizedByAdminId,
        String previousEmail,
        String requestedEmail,
        String evidenceMethod,
        String evidenceReference,
        String reason,
        boolean expedienteReviewed,
        boolean independentEvidenceConfirmed,
        boolean passwordResetRequired,
        String status,
        Instant emailVerifiedAt,
        Instant passwordResetExpiresAt,
        Instant passwordResetConsumedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ConductorRecoveryStatusResponse from(ConductorRecoveryRequest recovery) {
        return new ConductorRecoveryStatusResponse(
                recovery.getId(),
                recovery.getOrganizationId(),
                recovery.getConductorUserId(),
                recovery.getAuthorizedByAdminId(),
                null,
                null,
                recovery.getEvidenceMethod().name(),
                null,
                null,
                recovery.isExpedienteReviewed(),
                recovery.isIndependentEvidenceConfirmed(),
                recovery.isPasswordResetRequired(),
                recovery.getStatus().name(),
                recovery.getEmailVerifiedAt(),
                recovery.getPasswordResetExpiresAt(),
                recovery.getPasswordResetConsumedAt(),
                recovery.getCompletedAt(),
                recovery.getCreatedAt(),
                recovery.getUpdatedAt()
        );
    }
}
