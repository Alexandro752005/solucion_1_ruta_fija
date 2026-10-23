package pe.rutafija.availability.domain;

import java.time.*;
import java.util.*;
import java.util.function.Supplier;

/** Detection state only. Events never assert alert delivery, human coverage or host independence. */
public final class AvailabilityPolicy {
    public enum Kind { CONFIRMED_FAILURE, VERIFIED_RECOVERY }
    public record Event(Kind kind,UUID incidentId,Instant firstFailureAt,Instant confirmedAt,Instant observedAt,
        long detectionSeconds,Long recoverySeconds,boolean detectionWithinFiveMinutes,boolean recoveryWithinThirtyMinutes) { }
    private final int failuresToConfirm,successesToRecover;
    private final Supplier<UUID> ids;
    private Instant previous,firstFailure,confirmed;
    private UUID incident;
    private int failures,successes;
    public AvailabilityPolicy(){this(3,2,UUID::randomUUID);}
    public AvailabilityPolicy(int failuresToConfirm,int successesToRecover,Supplier<UUID> ids){
        if(failuresToConfirm<2||successesToRecover<2)throw new IllegalArgumentException("BOUNDED_SUSTAINED_FAILURE_AND_RECOVERY_REQUIRED");
        this.failuresToConfirm=failuresToConfirm;this.successesToRecover=successesToRecover;this.ids=Objects.requireNonNull(ids);
    }
    public List<Event> observe(Instant at,boolean allRequiredProbesHealthy){
        Objects.requireNonNull(at);if(previous!=null&&!at.isAfter(previous))throw new IllegalArgumentException("STRICTLY_INCREASING_OBSERVATION_TIME_REQUIRED");
        previous=at;
        if(!allRequiredProbesHealthy){
            successes=0;failures++;if(firstFailure==null)firstFailure=at;
            if(incident==null&&failures>=failuresToConfirm){
                incident=Objects.requireNonNull(ids.get());confirmed=at;var detection=Duration.between(firstFailure,at);
                return List.of(new Event(Kind.CONFIRMED_FAILURE,incident,firstFailure,confirmed,at,detection.getSeconds(),null,detection.compareTo(Duration.ofMinutes(5))<=0,false));
            }
            return List.of();
        }
        failures=0;
        if(incident==null){firstFailure=null;successes=0;return List.of();}
        if(++successes<successesToRecover)return List.of();
        var detect=Duration.between(firstFailure,confirmed);var recover=Duration.between(confirmed,at);
        var event=new Event(Kind.VERIFIED_RECOVERY,incident,firstFailure,confirmed,at,detect.getSeconds(),recover.getSeconds(),detect.compareTo(Duration.ofMinutes(5))<=0,recover.compareTo(Duration.ofMinutes(30))<=0);
        incident=null;firstFailure=null;confirmed=null;successes=0;return List.of(event);
    }
    public boolean incidentOpen(){return incident!=null;}
    public boolean suspectedFailure(){return incident==null&&firstFailure!=null;}
}
