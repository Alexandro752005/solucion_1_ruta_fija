package pe.rutafija.operation.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ServiceTypeContract {
    private ServiceTypeContract() { }
    public record Create(@NotBlank @Size(max=100) String name,
        @NotNull @PositiveOrZero Integer beforeMinutes,@NotNull @PositiveOrZero Integer afterMinutes,
        @NotBlank @Size(min=8,max=100) String operationKey,@NotBlank @Size(max=160) String reason) { }
    public record Revise(@NotNull @PositiveOrZero Long expectedVersion,@NotBlank @Size(max=100) String name,
        @NotNull @PositiveOrZero Integer beforeMinutes,@NotNull @PositiveOrZero Integer afterMinutes,
        @NotBlank @Size(min=8,max=100) String operationKey,@NotBlank @Size(max=160) String reason) { }
    public record Status(@NotNull @PositiveOrZero Long expectedVersion,@NotNull Boolean active,
        @NotBlank @Size(min=8,max=100) String operationKey,@NotBlank @Size(max=160) String reason) { }
    public record Summary(UUID id,String name,boolean active,long version,UUID currentVersionId) { }
    public record Version(UUID id,int revision,String name,int beforeMinutes,int afterMinutes,String contentSha256,Instant createdAt) { }
    public record View(UUID id,String name,boolean active,long version,UUID currentVersionId,List<Version> versions,Instant observedAt) { }
    public record Candidate(UUID driverId,UUID vehicleId,UUID periodId) { }
    public record Candidates(UUID serviceTypeVersionId,Instant reservationStartsAt,Instant reservationEndsAt,
        List<Candidate> items,Integer nextOffset,Instant observedAt) { }
}
