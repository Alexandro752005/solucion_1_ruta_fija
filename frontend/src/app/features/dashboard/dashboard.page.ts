import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { DashboardApiService, PlatformDashboard } from '../../core/dashboard/dashboard-api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusIndicatorComponent } from '../../shared/ui/status-indicator.component';
import { AdminDashboardComponent } from './admin-dashboard/admin-dashboard.component';

@Component({
  selector: 'rf-dashboard-page',
  imports: [RouterLink, PageHeaderComponent, StatusIndicatorComponent, AdminDashboardComponent],
  templateUrl: './dashboard.page.html', styleUrl: './dashboard.page.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardPage {
  readonly session = inject(AuthSessionService);
  private readonly dashboardApi = inject(DashboardApiService);
  readonly isAdmin = computed(() => this.session.hasAnyRole(['ADMIN']));
  readonly isSuperAdmin = computed(() => this.session.hasAnyRole(['SUPER_ADMIN']));
  readonly organizationTotal = signal<number | null>(null);
  readonly loadingOrganizations = signal(false);
  readonly organizationsError = signal(false);
  readonly platform = signal<PlatformDashboard | null>(null);

  constructor() {
    if (this.isSuperAdmin()) {
      this.loadingOrganizations.set(true);
      this.dashboardApi.platform().pipe(finalize(() => this.loadingOrganizations.set(false))).subscribe({
        next: response => { this.organizationTotal.set(response.organizations); this.platform.set(response); },
        error: () => { this.platform.set(null); this.organizationsError.set(true); },
      });
    }
  }
}
