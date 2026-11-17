import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DashboardIcon } from './admin-dashboard.models';

@Component({
  selector: 'rf-dashboard-icon',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { 'aria-hidden': 'true' },
  template: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" focusable="false">
    @switch (name()) {
      @case ('driver') { <circle cx="12" cy="7" r="3"/><path d="M6 21v-3a6 6 0 0 1 12 0v3z"/> }
      @case ('vehicle') { <path d="M5 16H3V8l2-4h14l2 4v8h-2M7 16h10M3 9h18M7 4l-1 5m11-5 1 5"/><circle cx="6" cy="17" r="2"/><circle cx="18" cy="17" r="2"/><path d="M6 12h2m8 0h2"/> }
      @case ('calendar') { <rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 3v4m8-4v4M4 11h16"/> }
      @case ('play') { <circle cx="12" cy="12" r="9"/><path d="m10 8 6 4-6 4z"/> }
      @case ('alert') { <path d="m10.3 4-8 14a2 2 0 0 0 1.7 3h16a2 2 0 0 0 1.7-3l-8-14a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4m0 4h.01"/> }
      @case ('fleet') { <rect x="3" y="4" width="7" height="16" rx="1"/><rect x="14" y="4" width="7" height="16" rx="1"/><path d="M6 8h1m10 0h1M6 12h1m10 0h1M6 16h1m10 0h1"/> }
      @case ('chart') { <path d="M4 3v17h17M8 15v-4m5 4V7m5 8V4"/> }
      @case ('map') { <path d="m3 6 6-3 6 3 6-3v15l-6 3-6-3-6 3zM9 3v15m6-12v15"/> }
      @case ('bell') { <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/> }
      @case ('arrow') { <path d="M4 12h16m-6-6 6 6-6 6"/> }
      @case ('export') { <path d="M12 3v12m-4-4 4 4 4-4M4 15v5h16v-5"/> }
      @case ('check') { <path d="m5 12 4 4L19 6"/> }
      @case ('shield') { <path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6zM8 12l3 3 5-6"/> }
      @case ('close') { <path d="m6 6 12 12M6 18 18 6"/> }
    }
  </svg>`,
  styles: `:host{display:inline-flex;width:18px;height:18px;flex:0 0 auto}svg{width:100%;height:100%}`,
})
export class DashboardIconComponent {
  readonly name = input.required<DashboardIcon>();
}
