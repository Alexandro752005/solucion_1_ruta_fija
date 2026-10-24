package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ServiceTimeContract {
    private ServiceTimeContract() { }
    public record Time(@NotNull UUID segmentId,@NotNull Instant startedAt,@NotNull Instant endedAt) { }
    public record Correct(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max=300) String reason,UUID requestId,@NotEmpty @Size(max=1000) List<@NotNull @Valid Time> segments) { }
    public record Request(@NotNull UUID requestKey,@NotBlank @Size(max=300) String reason) { }
    public record EffectiveSegment(UUID segmentId,UUID driverId,UUID vehicleId,int ordinal,Instant originalStartedAt,
        Instant originalEndedAt,Instant startedAt,Instant endedAt) { }
    public record Change(UUID segmentId,int ordinal,Instant beforeStartedAt,Instant beforeEndedAt,Instant startedAt,Instant endedAt) { }
    public record Revision(UUID id,UUID operationKey,int revision,UUID actorId,Instant correctedAt,String reason,String originalTerminalResult,
        String effectiveTerminalResult,String verificationEffect,UUID requestId,List<Change> segments) { }
    public record RequestView(UUID id,UUID requestKey,UUID segmentId,UUID requestedBy,String reason,Instant requestedAt,UUID correctionId) { }
    public record View(UUID serviceId,String status,long serviceVersion,List<EffectiveSegment> segments,List<Revision> corrections,List<RequestView> requests) { }
}
