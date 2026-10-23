package pe.rutafija.dashboard.api;

import java.time.Instant;
import java.util.List;

public final class DashboardContract {
    private DashboardContract() { }
    public record Priority(int order, String code, long count, String route) { }
    public record Admin(Instant generatedAt, Instant upcomingUntil, List<Priority> priorities,
                        long servicesInProgress, long activeDrivers, long activeVehicles) { }
    public record Platform(Instant generatedAt, long organizations, long activeOrganizations,
                           long activeAdmins, long pendingAdmins) { }
}
