package pe.rutafija.dashboard.api;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.rutafija.dashboard.application.DashboardService;

@RestController
public class DashboardController {
    private final DashboardService dashboards;
    public DashboardController(DashboardService dashboards) { this.dashboards = dashboards; }
    @GetMapping("/api/v1/dashboard/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardContract.Admin> admin() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboards.admin()); }
    @GetMapping("/api/v1/platform/dashboard")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<DashboardContract.Platform> platform() { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboards.platform()); }
}
