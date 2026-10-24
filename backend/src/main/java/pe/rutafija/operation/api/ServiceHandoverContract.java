package pe.rutafija.operation.api;

import jakarta.validation.constraints.*;
import pe.rutafija.operation.api.dto.mobile.MobileGpsStartProof;
import java.time.Instant;
import java.util.*;

public final class ServiceHandoverContract {
    private ServiceHandoverContract(){}
    public record Prepare(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull UUID driverId,@NotNull UUID vehicleId,@NotNull Instant scheduledAt,@NotNull Instant scheduledEndAt,
        @NotBlank @Size(max=300) String reason,@NotBlank @Pattern(regexp="[A-Z][A-Z0-9_:-]{2,99}") String pointReference,
        @NotNull @AssertTrue Boolean safetyAndTransferConfirmed){}
    public record Withdraw(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,@NotBlank @Size(max=300) String reason){}
    public record Assume(@NotNull UUID clientEventId,@NotNull @PositiveOrZero Long version,@NotNull Instant occurredAt,
        @NotNull MobileGpsStartProof gps,@NotNull @AssertTrue Boolean readyConfirmed){}
    public record Handover(UUID id,UUID operationKey,UUID outgoingAssignmentId,UUID incomingAssignmentId,UUID incomingDriverId,
        UUID incomingVehicleId,UUID preparedBy,String reason,String pointReference,Instant preparedAt,String state,
        Instant resolvedAt,UUID resolvedBy,UUID resolutionKey,Long pauseSeconds){}
    public record View(ScheduledServiceContract.Summary service,List<Handover> handovers){}
}
