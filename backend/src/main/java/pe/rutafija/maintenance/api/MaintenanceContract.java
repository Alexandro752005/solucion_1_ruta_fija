package pe.rutafija.maintenance.api;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class MaintenanceContract {
    private MaintenanceContract(){ }
    public record Create(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=160) String reason,@NotBlank @Pattern(regexp="PREVENTIVE|URGENT") String kind,LocalDate startDate,LocalDate endDate) { }
    public record Basic(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(min=8,max=100) String operationKey,@NotBlank @Size(max=160) String reason) { }
    public record Work(@NotNull @Min(0) Long expectedVersion,@NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=160) String reason,@NotBlank @Size(max=160) String responsibleReference,
        @NotBlank @Size(max=160) String evidenceReference,@NotNull @AssertTrue Boolean workCompleted) { }
    public record Record(UUID id,String kind,String status,Instant startsAt,Instant plannedEndsAt,Instant blockingEndsAt,
        UUID createdBy,Instant createdAt,UUID workCompletedBy,Instant workCompletedAt,UUID returnedBy,Instant returnedAt) { }
    public record Commitment(UUID assignmentId,UUID driverId,String status,Instant scheduledAt,Instant scheduledEndAt) { }
    public record Change(UUID id,String action,UUID recordId,UUID actorId,long resultingVersion,Instant occurredAt) { }
    public record View(UUID vehicleId,long version,List<Record> records,boolean mechanicallyBlockedNow,List<Change> history,Instant observedAt) { }
    public record Preview(UUID vehicleId,LocalDate firstDay,Instant blockingStartsAt,List<Commitment> commitments,boolean canConfirmPreventive,Instant observedAt) { }
}
