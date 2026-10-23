package pe.rutafija.routing.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class RouteContract {
    private RouteContract() { }
    public record Stop(@NotBlank @Size(max=64) @Pattern(regexp="[A-Za-z0-9_-]+") String key,
        @NotBlank @Size(max=120) String name,@NotBlank @Pattern(regexp="START|INTERMEDIATE|END") String kind,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,@DecimalMin("-180") @DecimalMax("180") Double longitude) { }
    public record Create(@NotBlank @Size(max=120) String name,@NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=160) String reason,@NotNull @Size(min=3,max=100) List<@NotNull @Valid Stop> stops) { }
    public record Revise(@NotNull @Min(1) Long expectedVersion,@NotBlank @Size(max=120) String name,
        @NotBlank @Size(min=8,max=100) String operationKey,@NotBlank @Size(max=160) String reason,
        @NotNull @Size(min=3,max=100) List<@NotNull @Valid Stop> stops) { }
    public record Status(@NotNull @Min(1) Long expectedVersion,@NotBlank @Size(min=8,max=100) String operationKey,
        @NotBlank @Size(max=160) String reason,@NotNull Boolean active) { }
    public record Version(UUID id,int revision,String name,List<Stop> stops,String contentSha256,Instant createdAt) { }
    public record View(UUID id,String name,boolean active,long version,UUID currentVersionId,List<Version> versions,Instant observedAt) { }
    public record Summary(UUID id,String name,boolean active,long version,UUID currentVersionId) { }
    public record Declaration(Instant declaredAt,String terminalResult,UUID actorId) { }
    public record Assigned(UUID assignmentId,UUID versionId,String referenceStatus,Version route,Declaration declaration,UUID replacementAssignmentId) { }
    public record Replace(@NotNull @Min(0) Long expectedVersion,@NotNull UUID routeVersionId,
        @NotBlank @Size(min=8,max=100) String operationKey,@NotBlank @Size(max=160) String reason) { }
}
