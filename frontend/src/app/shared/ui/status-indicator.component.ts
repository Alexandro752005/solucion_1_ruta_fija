import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

export type StatusTone = 'success' | 'info' | 'warning' | 'danger' | 'muted';

@Component({
  selector: 'rf-status-indicator',
  template: `
    <span
      class="rf-status"
      [class.rf-status--success]="tone === 'success'"
      [class.rf-status--info]="tone === 'info'"
      [class.rf-status--warning]="tone === 'warning'"
      [class.rf-status--danger]="tone === 'danger'"
      [class.rf-status--muted]="tone === 'muted'"
    >
      <span class="rf-status__dot" aria-hidden="true"></span>
      <span>{{ label }}</span>
    </span>
  `,
  styles: `
    :host { display: inline-flex; }
    .rf-status { display: inline-flex; align-items: center; color: var(--rf-text); font-size: .82rem; font-weight: 650; gap: .42rem; }
    .rf-status__dot { width: .58rem; height: .58rem; flex: 0 0 auto; border: 1px solid rgb(0 0 0 / 18%); border-radius: 50%; background: var(--rf-inactive); }
    .rf-status--success .rf-status__dot { background: var(--rf-success); }
    .rf-status--info .rf-status__dot { background: var(--rf-info); }
    .rf-status--warning .rf-status__dot { background: var(--rf-warning); }
    .rf-status--danger .rf-status__dot { background: var(--rf-error); }
    .rf-status--muted .rf-status__dot { background: var(--rf-inactive); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusIndicatorComponent {
  @Input({ required: true }) label = '';
  @Input() tone: StatusTone = 'muted';
}
