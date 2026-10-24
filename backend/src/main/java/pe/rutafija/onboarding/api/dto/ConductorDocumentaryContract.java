package pe.rutafija.onboarding.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Structured documentary data only. There is deliberately no attachment or binary field. */
public final class ConductorDocumentaryContract {
    private ConductorDocumentaryContract() { }

    public record DocumentChange(
            @NotBlank @Size(max = 12) String requirementId,
            Map<String, String> fields,
            @NotBlank String expiryMode,
            Instant validUntil,
            @NotBlank String applicability,
            @Size(max = 500) String condition,
            @Size(max = 500) String sourceReference,
            @NotBlank String result,
            @Size(max = 500) String reviewReference,
            @Size(max = 500) String observation) { }

    public record SaveDocuments(
            @NotNull @Min(0) Long expectedVersion,
            @NotBlank @Size(min = 8, max = 100) String operationKey,
            @NotBlank String modality,
            @NotEmpty @Size(max = 5) List<@NotNull @Valid DocumentChange> documents) { }

    public record Decision(
            @NotNull @Min(0) Long expectedVersion,
            @NotBlank @Size(min = 8, max = 100) String operationKey,
            @NotBlank @Size(max = 500) String reason,
            boolean reiteration,
            Instant deadline,
            UUID groupId) { }

    public record Reapply(
            @NotNull @Min(0) Long expectedVersion,
            @NotBlank @Size(min = 8, max = 100) String operationKey,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String invitationCode) { }

    public record OpenLegacyRegularization(
            @NotBlank @Size(min = 8, max = 100) String operationKey,
            @NotBlank @Size(max = 500) String reason,
            @NotNull @AssertTrue Boolean identityPreservedConfirmed) { }

    public record LegacyRegularizationOpened(UUID requestId, String status,
                                             UUID existingUserId, UUID existingDriverId) { }

    public record Requirement(String requirementId, String title, boolean mandatory, List<String> fieldKeys) { }

    public record Document(String requirementId, String entity, Map<String, String> fields,
                           String expiryMode, Instant validUntil, String applicability,
                           String condition, String sourceReference, String result,
                           String reviewReference, String observation) { }

    public record History(String action, Instant occurredAt, String reason, int round) { }

    public record Dossier(UUID requestId, String status, long version, int reviewRounds, String modality,
                          String catalogId, String catalogVersion, String catalogHash,
                          List<Requirement> catalog, List<Document> documents, List<History> history,
                          Instant correctionDeadline, boolean overdue, boolean personalApproved,
                          UUID provisionedUserId, UUID provisionedDriverId, boolean operationalEnabled,
                          Instant reapplyAfter, boolean existingAccountRegularization,
                          UUID existingUserId, UUID existingDriverId) { }
}
