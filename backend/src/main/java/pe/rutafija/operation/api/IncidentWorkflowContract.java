package pe.rutafija.operation.api;

import jakarta.validation.constraints.*;
import pe.rutafija.operation.domain.IncidentSeverity;
import java.time.Instant;
import java.util.*;

public final class IncidentWorkflowContract {
    private IncidentWorkflowContract(){}
    public record Responsibility(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull UUID adminId,@NotBlank @Size(max=300) String reason){}
    public record Assessment(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotNull IncidentSeverity severity,@NotBlank @Size(max=300) String reason){}
    public record Information(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max=1000) String note){}
    public enum EvidenceMode { REFERENCE, UNAVAILABLE_JUSTIFIED, NOT_REQUIRED }
    public record Closure(@NotNull UUID operationKey,@NotNull @PositiveOrZero Long expectedVersion,
        @NotBlank @Size(max=300) String diagnosis,@NotBlank @Size(max=300) String actions,
        @NotBlank @Size(max=300) String result,@NotNull Boolean evidenceRequired,@NotNull EvidenceMode evidenceMode,
        @Size(max=100) String evidenceReference,@Size(max=300) String evidenceExplanation){}
    public record Candidate(UUID adminId,String label){}
    public record Action(UUID id,UUID operationKey,UUID actorId,String kind,Map<String,Object> detail,Instant recordedAt,long resultingVersion){}
    public record Operation(UUID operationKey,UUID incidentId,String kind,Instant recordedAt,long resultingVersion){}
}
