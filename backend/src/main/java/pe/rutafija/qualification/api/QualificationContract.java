package pe.rutafija.qualification.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.Document;
import pe.rutafija.onboarding.api.dto.ConductorDocumentaryContract.DocumentChange;
import java.time.*;
import java.util.*;

public final class QualificationContract {
    private QualificationContract() { }
    public interface Command { Long expectedVersion(); String operationKey(); String reason(); }
    public record Basic(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
                        @NotBlank @Size(max=500) String reason, @Size(max=160) String evidenceReference) implements Command { }
    public record ReviewInput(@NotBlank String requirementId, Map<String,String> fields, @NotBlank String expiryMode,
        Instant validUntil, @NotBlank String applicability, @Size(max=500) String condition,
        @Size(max=500) String sourceReference, @NotBlank String result, @Size(max=500) String reviewReference) { }
    public record ReviewCommand(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=500) String reason, UUID vehicleId, @NotBlank String scope,
        @NotEmpty @Size(max=4) List<@NotNull @Valid ReviewInput> documents) implements Command { }
    public record PeriodCommand(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=500) String reason, @NotNull UUID vehicleId, @NotBlank String kind,
        Instant startAt, LocalDate startDate, Instant endAt, LocalDate endDate) implements Command { }
    public record PersonalCommand(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=500) String reason, @NotEmpty @Size(max=5) List<@NotNull @Valid DocumentChange> documents,
        boolean samePersonConfirmed, boolean originalReviewed, @Size(max=500) String reviewReference) implements Command { }
    public record UnitCommand(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=500) String reason, UUID vehicleId, @NotBlank String kind,
        @NotBlank @Size(max=160) String reference) implements Command { }
    public record UnitDecision(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=500) String reason, boolean approved, UUID vehicleId) implements Command { }
    public record VehicleCorrection(@NotNull @Min(0) Long expectedVersion, @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=500) String reason, @NotNull UUID vehicleId,
        @NotBlank @Pattern(regexp="[A-Za-z0-9-]{3,15}") String plate, boolean originalReviewed,
        @NotBlank @Size(max=500) String reviewReference) implements Command { }
    public record Requirement(String scope,String id,String title,boolean mandatory,List<String> fieldKeys) { }
    public record Review(String scope,String requirementId,UUID vehicleId,Map<String,String> fields,String expiryMode,
        Instant validUntil,String applicability,String condition,String sourceReference,String result,String reviewReference,long version) { }
    public record Period(UUID id,UUID vehicleId,String kind,Instant startsAt,Instant endsAt,String status,String reason,long version) { }
    public record UnitRequestView(UUID id,UUID vehicleId,String kind,String status,String reference,Instant createdAt) { }
    public record ServiceAttention(UUID id,UUID assignmentId,String kind,String status,Instant scheduledAt,Instant scheduledEndAt) { }
    public record PersonalDocumentState(String requirementId,String result,String applicability,String observation) { }
    public record Qualification(UUID driverId,UUID accountId,UUID requestId,long version,boolean suspended,boolean operationalEnabled,
        String personalDocumentsStatus,Instant personalValidUntil,List<String> blockers,List<Period> periods,List<UnitRequestView> unitRequests,
        List<Requirement> catalog,List<Review> reviews,List<Document> personalDocuments,Instant observedAt,
        List<ServiceAttention> serviceAttention,List<PersonalDocumentState> personalDocumentStates) { }
}
