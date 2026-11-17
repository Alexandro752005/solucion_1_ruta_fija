package pe.rutafija.livemap.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Minimal current-state projection. No route history or device telemetry is exposed. */
public final class LiveMapContract {
    private LiveMapContract() { }
    public record Vehicles(Instant serverTime,int limit,List<Vehicle> items) {
        public Vehicles { items=List.copyOf(items); }
        @Override public String toString(){return "LiveMapVehicles[count="+items.size()+"]";}
    }
    public record Vehicle(UUID driverId,UUID vehicleId,String driverName,String plate,BigDecimal latitude,
                          BigDecimal longitude,Instant capturedAt,Instant receivedAt,Instant expiresAt,String freshness) {
        @Override public String toString(){return "LiveMapVehicle[private map data redacted]";}
    }
    public record Group(UUID id,String name) { }
    public record Groups(List<Group> items) { public Groups { items=List.copyOf(items); } }
}
