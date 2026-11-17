import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { AdminDashboardViewModel } from './admin-dashboard.models';
import { DashboardDonutComponent } from './dashboard-donut.component';
import { DashboardIconComponent } from './dashboard-icon.component';
import { DashboardMetricComponent } from './dashboard-metric.component';
import { DashboardSecondaryComponent } from './dashboard-secondary.component';
import { DashboardRoutePickerComponent } from './dashboard-route-picker.component';

/** Read-only presentation. Its host owns data access and authorized navigation. */
@Component({
  selector: 'rf-admin-dashboard-view',
  imports: [DashboardDonutComponent, DashboardIconComponent, DashboardMetricComponent, DashboardSecondaryComponent, DashboardRoutePickerComponent],
  templateUrl: './admin-dashboard-view.component.html',
  styleUrl: './admin-dashboard-view.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AdminDashboardViewComponent {
  readonly model = input.required<AdminDashboardViewModel>();
  readonly navigate = output<string>();
  readonly selectDate = output<string>();
  readonly requestExport = output<void>();
  readonly requestRoute = output<void>();
  readonly requestActivity = output<void>();
  readonly selectRoute = output<string>(); readonly routePage = output<-1 | 1>(); readonly retryRoutes = output<void>();

  readonly pending = computed(() => this.model().priorities.filter(priority => priority.count > 0));
  readonly prioritiesCurrent = computed(() => this.model().prioritiesState === 'ready');
  readonly prioritiesAvailable = computed(() => this.dataVisible(this.model().prioritiesState) && this.model().priorities.length === 8);
  readonly showEmptyNotice = computed(() => this.prioritiesCurrent() && this.pending().length === 0 &&
    this.model().noticesState === 'ready' && this.model().noticeTotal === 0);
  readonly routePoints = computed(() => this.model().route.points.map(point => `${point.x},${point.y}`).join(' '));
  readonly routeSegments = computed(() => (this.model().route.segments ?? [this.model().route.points]).filter(segment => segment.length >= 2).map(segment => segment.map(point => `${point.x},${point.y}`).join(' ')));
  readonly fullDate = computed(() => this.date(this.model().operationalDate, true));
  readonly selectedDateLabel = computed(() => this.date(this.model().selectedDate));

  date(value: string, full = false): string {
    if (!value || !Number.isFinite(Date.parse(`${value}T12:00:00Z`))) return 'Fecha no disponible';
    return new Intl.DateTimeFormat('es-PE', {
      timeZone: 'UTC', day: 'numeric', month: full ? 'long' : 'short',
      ...(full ? { weekday: 'long', year: 'numeric' } as const : {}),
    }).format(new Date(`${value}T12:00:00Z`));
  }

  dataVisible(state: string): boolean { return ['ready', 'empty', 'refreshing', 'stale'].includes(state); }

  changeDate(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    if (/^\d{4}-\d{2}-\d{2}$/.test(value) && value <= this.model().operationalDate) this.selectDate.emit(value);
  }

  driverLabel(code: string): string {
    return `CONDUCTOR-…${code.slice(-8)}`;
  }
}
