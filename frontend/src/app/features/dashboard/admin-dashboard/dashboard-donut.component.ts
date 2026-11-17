import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DashboardDonut } from './admin-dashboard.models';

@Component({
  selector: 'rf-dashboard-donut',
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard-donut.component.html',
  styleUrl: './dashboard-donut.component.scss',
})
export class DashboardDonutComponent {
  readonly model = input.required<DashboardDonut>();
  readonly invalid = computed(() => {
    const ring = this.model();
    const values = [...ring.segments, ...ring.legend].map(slice => slice.value);
    return !Number.isSafeInteger(ring.total) || ring.total < 0 ||
      values.some(value => !Number.isSafeInteger(value) || value < 0) ||
      ring.segments.reduce((sum, slice) => sum + slice.value, 0) !== ring.total ||
      ring.legend.reduce((sum, slice) => sum + slice.value, 0) !== ring.total;
  });
  readonly arcs = computed(() => {
    const ring = this.model();
    if (ring.total === 0 || this.invalid()) return [];
    let offset = 0;
    return ring.segments.filter(slice => slice.value > 0).map(slice => {
      const length = slice.value / ring.total * 100;
      const arc = { ...slice, dash: `${length} ${100 - length}`, offset: -offset };
      offset += length;
      return arc;
    });
  });

  percent(value: number): string {
    return this.model().total === 0 ? '—' :
      `${new Intl.NumberFormat('es-PE', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(value / this.model().total * 100)} %`;
  }
}
