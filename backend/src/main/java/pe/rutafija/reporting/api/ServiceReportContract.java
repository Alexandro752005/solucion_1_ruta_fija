package pe.rutafija.reporting.api;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class ServiceReportContract {
    private ServiceReportContract() { }
    public record Issue(@NotNull UUID operationKey,@NotNull LocalDate from,@NotNull LocalDate to) { }
    public record Totals(long scheduled,long completed,long cancelled,long interrupted,long pending,long transferred,
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS) BigDecimal completionPercent) { }
    public record Coverage(long excludedLegacyOffers,long canonicalServicesWithoutFirstOffer,String notice) { }
    public record Item(UUID serviceId,long serviceVersion,Instant cohortScheduledAt,Instant currentScheduledAt,
        String status,boolean validCompletion,Instant originalStartedAt,Instant originalEndedAt,
        Instant effectiveStartedAt,Instant effectiveEndedAt,Instant completionRecordedAt,boolean declaredClosed,
        String terminalResult,List<UUID> correctionIds,List<UUID> driverIds,List<UUID> vehicleIds,List<UUID> groupIds) { }
    public record View(String ruleVersion,String scope,LocalDate from,LocalDate to,String zoneId,Instant cutoffAt,
        Totals totals,Coverage coverage,List<Item> items) { }
    public record Emission(UUID id,UUID seriesId,int revision,UUID previousEmissionId,Instant issuedAt,
        String contentSha256,View report) { }
    public record Summary(UUID id,int revision,UUID previousEmissionId,Instant issuedAt,String contentSha256) { }
}
