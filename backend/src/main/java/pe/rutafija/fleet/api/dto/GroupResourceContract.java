package pe.rutafija.fleet.api.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class GroupResourceContract {
    private GroupResourceContract() { }
    public record MembershipCommand(@NotNull @Min(0) Long expectedVersion,
        @NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=160) String reason,
        @NotBlank @Pattern(regexp="DRIVER|VEHICLE") String resourceKind,
        @NotNull UUID resourceId,@NotNull Boolean active) { }
    public record Member(UUID resourceId,String kind,boolean active,String source,Instant recordedAt) { }
    public record Commitment(UUID assignmentId,String status,String attentionKind,Instant scheduledAt,Instant scheduledEndAt,String groupReferenceSource) { }
    public record Change(UUID id,UUID actorId,String resourceKind,UUID resourceId,boolean active,long resultingVersion,Instant occurredAt) { }
    public record Resources(UUID groupId,long version,boolean active,List<Member> memberships,List<Commitment> commitments,List<Change> history,Instant observedAt) { }
}
