package pe.rutafija.operation.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import pe.rutafija.operation.api.dto.*;
import java.time.Instant;
import java.util.*;

public final class ScheduledServiceContract {
    private ScheduledServiceContract(){}
    public record Reoffer(@Min(0) long expectedVersion,@NotBlank @Size(min=8,max=100) String operationKey,
            @NotBlank @Size(max=160) String reason,@NotNull @Valid AssignmentCreateRequest offer){}
    public record Cancel(@Min(0) long expectedVersion,@NotBlank @Size(min=8,max=100) String operationKey,
            @NotBlank @Size(max=160) String reason){}
    public record Summary(UUID id,String status,String effectiveStatus,long version,Instant scheduledAt,
            Instant scheduledEndAt,UUID routeVersionId,UUID serviceTypeVersionId,UUID currentOfferId,
            Instant cancelledAt,String cancellationReason,Instant originalStartedAt,boolean startOverdue){}
    public record View(Summary service,List<AssignmentResponse> offers){}
    public record Receipt(UUID id,UUID serviceId,String operationKey,String action,UUID previousOfferId,
            UUID resultingOfferId,long resultingVersion,Instant occurredAt){}
}
