import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TopbarIcon } from './topbar.model';

const PATHS: Record<TopbarIcon, string> = {
  home: 'm3 10 9-7 9 7 M5 9v12h14V9 M9 21v-8h6v8',
  grid: 'M3 3h6v6H3z M15 3h6v6h-6z M3 15h6v6H3z M15 15h6v6h-6z',
  bolt: 'm13 2-9 12h7l-1 8 10-13h-8z',
  bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
  chevron: 'm6 9 6 6 6-6',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8 M17 4a4 4 0 0 1 0 7 M22 21v-2a4 4 0 0 0-3-3.87',
  vehicle: 'M4 7h16l2 6v7h-3v-3H5v3H2v-7z M2 13h20 M7 7V4h10v3 M6 15h1 M17 15h1',
  map: 'm3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2z M9 3v16 M15 5v16',
  shield: 'm12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6z M8 12l3 3 5-6',
  message: 'M21 3H3v14h5l4 4 4-4h5z M7 8h10 M7 12h7',
  alert: 'm12 3 10 18H2z M12 9v5 M12 17h.01',
  calendar: 'M3 5h18v16H3z M7 2v6 M17 2v6 M3 11h18 M7 15h3',
  organization: 'M4 21V7l8-4 8 4v14 M2 21h20 M9 21v-5h6v5 M8 8h1 M15 8h1 M8 12h1 M15 12h1',
  terms: 'M5 2h10l4 4v16H5z M15 2v5h4 M9 11h6 M9 15h6 M9 18h4',
  logout: 'M9 3H3v18h6 M14 7l5 5-5 5 M8 12h13',
  menu: 'M3 6h18 M3 12h18 M3 18h18',
};

@Component({
  selector: 'rf-topbar-icon',
  template: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" focusable="false"><path [attr.d]="paths[name()]" /></svg>',
  styles: ':host{display:inline-flex;width:1.2rem;height:1.2rem;flex:none;align-items:center;justify-content:center}svg{display:block;width:100%;height:100%}',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TopbarIconComponent {
  readonly name = input.required<TopbarIcon>();
  protected readonly paths = PATHS;
}
