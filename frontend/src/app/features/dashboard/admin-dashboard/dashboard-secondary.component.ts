import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { AdminDashboardViewModel } from './admin-dashboard.models';

@Component({
  selector: 'rf-dashboard-secondary',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div class="secondary-details">
    <details (toggle)="activityToggle($event)"><summary>Actividad reciente</summary>
      @if (model().activity; as activity) {
        @if (activity.state === 'loading') { <p role="status">Consultando actividad…</p> }
        @else if (activity.state === 'ready') { <ul>@for (item of activity.items; track item.id) { <li>{{ item.time }} · {{ item.label }}</li> } @empty { <li>No hay actividad en esta consulta.</li> }</ul> }
        @else { <p role="status">Actividad no disponible.</p> }
      } @else { <p>Consulta los eventos registrados y su trazabilidad.</p> }
      <button type="button" (click)="navigate.emit('/audit')">Ver auditoría →</button></details>
    <details><summary>Accesos rápidos</summary><nav aria-label="Accesos rápidos del Dashboard">@for (link of ['/users', '/organization', '/groups', '/assignments', '/announcements']; track link; let i = $index) { <button type="button" (click)="navigate.emit(link)">{{ ['Usuarios', 'Organización', 'Grupos', 'Asignaciones', 'Comunicados'][i] }} →</button> }</nav></details>
  </div>
  <footer><span>© {{ model().operationalDate.slice(0, 4) }} Ruta Fija CRM Operativo</span><span>Corte: {{ model().updatedLabel }} · {{ model().zoneId }}</span><nav aria-label="Enlaces secundarios"><button type="button" (click)="navigate.emit('/audit')">Auditoría</button><button type="button" (click)="navigate.emit('/admin-terms')">Términos ADMIN</button></nav></footer>`,
  styles: `:host{display:grid;min-width:0;gap:18px;color:var(--dash-muted,#52627a);font-size:12px}button{min-height:44px;padding:6px;border:1px solid var(--dash-border,#e5eaf2);border-radius:6px;background:white;color:var(--dash-link,#1d4ed8);font:inherit;cursor:pointer}button:focus-visible,summary:focus-visible{outline:3px solid #1d4ed8;outline-offset:3px}.secondary-details{display:flex;flex-wrap:wrap;gap:12px}.secondary-details details{padding:0 4px}.secondary-details summary{min-height:44px;align-content:center;cursor:pointer}.secondary-details p{margin:4px 0 8px}.secondary-details nav{display:flex;flex-wrap:wrap;gap:8px}footer{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:8px;padding-top:12px;border-top:1px solid #e3e9f2}footer nav{display:flex;gap:8px}footer button{border:0;background:transparent;color:inherit}`,
})
export class DashboardSecondaryComponent {
  readonly model = input.required<Pick<AdminDashboardViewModel, 'operationalDate' | 'updatedLabel' | 'zoneId' | 'activity'>>();
  readonly navigate = output<string>();
  readonly requestActivity = output<void>();
  activityToggle(event: Event): void { if ((event.target as HTMLDetailsElement).open) this.requestActivity.emit(); }
}
