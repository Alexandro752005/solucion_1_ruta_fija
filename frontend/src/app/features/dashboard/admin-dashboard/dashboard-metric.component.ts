import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { DashboardMetric } from './admin-dashboard.models';
import { DashboardIconComponent } from './dashboard-icon.component';

@Component({
  selector: 'rf-dashboard-metric',
  imports: [DashboardIconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<button type="button" [class]="'metric-card accent-' + metric().tone" [attr.data-metric]="metric().id" (click)="navigate.emit(metric().destination)">
    <span class="metric-icon"><rf-dashboard-icon [name]="metric().icon"/></span>
    <span class="metric-copy"><span class="metric-label">{{ metric().label }}</span><strong class="metric-value">{{ hasValue() ? metric().value : '—' }}</strong><span class="metric-support">{{ support() }}</span></span>
    <span class="metric-arrow" aria-hidden="true">›</span>
  </button>`,
  styles: `:host{display:block;min-width:0}.metric-card{display:flex;align-items:center;gap:12px;min-height:104px;width:100%;height:100%;padding:14px 12px;border:1px solid var(--dash-border,#e5eaf2);border-radius:14px;background:white;text-align:left;color:inherit;font:inherit;cursor:pointer;box-sizing:border-box}.metric-card:hover{border-color:#abc4f0;box-shadow:0 3px 10px #20396008}.metric-card:focus-visible{outline:3px solid #1d4ed8;outline-offset:3px}.metric-icon{display:grid;width:36px;height:36px;flex:0 0 36px;place-items:center;border-radius:50%;background:var(--accent-bg);color:var(--accent)}.metric-icon rf-dashboard-icon{width:20px;height:20px}.metric-copy{display:grid;gap:3px;min-width:0}.metric-label{font-size:12px;font-weight:600;line-height:1.35}.metric-value{font-size:26px;line-height:1.05;letter-spacing:-.6px}.metric-support{color:var(--dash-muted,#52627a);font-size:12px}.metric-arrow{margin-left:auto;color:#7891b5;font-size:22px}.accent-blue{--accent:#2563eb;--accent-bg:#eff6ff}.accent-green{--accent:#047857;--accent-bg:#ecfdf5}.accent-amber{--accent:#b45309;--accent-bg:#fffbeb}.accent-violet{--accent:#7c3aed;--accent-bg:#f5f0ff}.accent-red{--accent:#dc2626;--accent-bg:#fff1f2}`,
})
export class DashboardMetricComponent {
  readonly metric = input.required<DashboardMetric>();
  readonly navigate = output<string>();
  readonly hasValue = computed(() => ['ready', 'refreshing', 'stale'].includes(this.metric().state) && this.metric().value !== null);
  readonly support = computed(() => this.metric().state === 'ready' ? this.metric().supporting : this.metric().state === 'refreshing' ? 'Dato anterior · actualizando' : this.metric().state === 'stale' ? 'Dato anterior · actualización pendiente' : this.metric().state === 'loading' ? 'Consultando…' : this.metric().state === 'forbidden' ? 'Consulta no autorizada' : 'No disponible');
}
