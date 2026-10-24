package pe.rutafija.operation.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public final class ServiceGpsContract {
    private ServiceGpsContract() { }
    public record Request(@NotNull UUID requestKey,@NotNull UUID serviceId,Instant fromAt,Instant toAt) { }
    public record Review(@NotNull @PositiveOrZero Long expectedVersion,@NotNull Boolean approved,@NotNull Boolean identityConfirmed,
        @NotBlank @Pattern(regexp="RESTRICTED_IDENTITY_MATCH|SYNTHETIC_RESTRICTED_IDENTITY_MATCH") String verificationReference,@NotBlank @Size(max=200) String currentPassword) {
        @Override public String toString(){return "GpsReview[credentials redacted]";}
    }
    public record Delivery(@NotBlank @Size(max=200) String currentPassword,@Min(1) @Max(1000) int limit,@Min(0) @Max(100000) int offset) {
        @Override public String toString(){return "GpsDelivery[credentials redacted]";}
    }
    public enum Justification { SAFETY_REVIEW,SERVICE_DISCREPANCY }
    public record Attach(@NotNull UUID pointId,@NotNull Justification justification,@NotBlank @Size(max=200) String currentPassword) {
        @Override public String toString(){return "GpsEvidence[credentials redacted]";}
    }
    public record RequestView(UUID id,UUID driverId,UUID serviceId,long version,String status,String effectiveStatus,Instant fromAt,Instant toAt,
        Instant requestedAt,Instant reviewDueAt,Instant reviewedAt,Instant grantExpiresAt,UUID requestKey) { }
    public record Point(UUID id,UUID segmentId,BigDecimal latitude,BigDecimal longitude,BigDecimal accuracyM,Instant capturedAt,Instant expiresAt) {
        @Override public String toString(){return "GpsPoint[id="+id+", coordinates redacted]";}
    }
    public record Points(UUID requestId,Instant grantExpiresAt,List<Point> items,Integer nextOffset) { }
    public record IncidentEvidence(UUID id,UUID incidentId,UUID pointReference,String justification,Instant expiresAt,boolean pointAvailable) { }
}
