package pe.rutafija.operation.api;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;

public final class ServiceAttentionContract {
    private ServiceAttentionContract() { }
    public enum Reason { ACCIDENT_RISK,UNSAFE_BREAKDOWN,IMPOSSIBLE_SERVICE }
    public enum VehicleDecision { NO_CHANGE,READY,KEEP_WITHDRAWN }
    public record OwnInterrupt(@NotNull UUID clientEventId,@NotNull @PositiveOrZero Long version,@NotNull Instant occurredAt,
        @NotNull Reason reason,@NotBlank @Size(max=300) String detail,@NotNull Boolean safeStopConfirmed) { }
    public record AdminInterrupt(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull Reason reason,@NotBlank @Size(max=300) String detail,@NotNull @AssertTrue Boolean safeStopConfirmed,
        @NotBlank @Pattern(regexp="[A-Z][A-Z0-9_:-]{2,99}") String verificationReference) { }
    public record Resolve(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull Boolean releaseDriver,@NotNull VehicleDecision vehicleDecision,@NotNull @AssertTrue Boolean safetyAndTransferConfirmed,
        @NotBlank @Size(max=300) String reason,@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_:-]{2,99}") String verificationReference) { }
    public record Resolution(UUID id,UUID operationKey,UUID actorId,String resourceKind,String disposition,String reason,
        String verificationReference,Instant recordedAt) { }
    public record Attention(UUID id,UUID operationKey,UUID serviceId,UUID assignmentId,UUID segmentId,UUID driverId,UUID vehicleId,UUID incidentId,
        UUID recordedBy,String source,Reason reason,String detail,boolean safeStopConfirmed,String verificationReference,
        Instant interruptedAt,long version,boolean driverPending,boolean vehiclePending,List<Resolution> resolutions) { }
}
