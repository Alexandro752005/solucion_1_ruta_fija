import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { DashboardRouteCatalog } from './admin-dashboard.models';

@Component({
  selector: 'rf-dashboard-route-picker', changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<label>Ruta registrada<select aria-label="Ruta del esquema" [value]="catalog().selectedId" [disabled]="catalog().state !== 'ready' || !catalog().options.length" (change)="choose($event)">
    @for (route of catalog().options; track route.id) { <option [value]="route.id">{{ route.name }}</option> }
    @if (!catalog().options.length) { <option value="">Sin ruta seleccionable</option> }
  </select></label>
  <div class="controls"><button type="button" (click)="page.emit(-1)" [disabled]="catalog().page <= 1 || catalog().state !== 'ready'" aria-label="Página anterior de rutas">←</button><span>Página {{ catalog().page }}</span><button type="button" (click)="page.emit(1)" [disabled]="!catalog().hasNext || catalog().state !== 'ready'" aria-label="Consultar página siguiente de rutas">→</button><button type="button" (click)="open.emit()" [disabled]="!catalog().selectedId">Ver ruta</button></div>
  @if (catalog().state === 'loading' || catalog().state === 'refreshing') { <p role="status">Consultando catálogo de rutas…</p> }
  @else if (catalog().state !== 'ready') { <p role="status">Catálogo no confirmado. <button type="button" (click)="retry.emit()">Reintentar catálogo</button></p> }`,
  styles: `:host{display:grid;min-width:0;gap:6px;margin:8px 0 12px;font-size:12px;color:#52627a}label{display:grid;min-width:0;gap:4px}select{width:100%;min-width:0;min-height:44px;padding:6px;border:1px solid #dce4f0;border-radius:8px;background:white;font:inherit;color:#111827}.controls{display:flex;flex-wrap:wrap;align-items:center;gap:6px}button{min-height:44px;min-width:44px;padding:6px 10px;border:1px solid #dce4f0;border-radius:8px;color:#1d4ed8;background:#f3f7ff;font:inherit;cursor:pointer}button:disabled{color:#64748b;cursor:default;background:#f8fafc}button:focus-visible,select:focus-visible{outline:3px solid #1d4ed8;outline-offset:2px}p{margin:0}`,
})
export class DashboardRoutePickerComponent {
  readonly catalog = input.required<DashboardRouteCatalog>();
  readonly select = output<string>(); readonly page = output<-1 | 1>(); readonly open = output<void>(); readonly retry = output<void>();
  choose(event: Event): void { this.select.emit((event.target as HTMLSelectElement).value); }
}
