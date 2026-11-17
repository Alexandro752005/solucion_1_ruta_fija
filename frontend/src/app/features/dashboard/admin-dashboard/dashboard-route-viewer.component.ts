import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { AdminDashboardViewModel } from './admin-dashboard.models';

@Component({
  selector: 'rf-dashboard-route-viewer', changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<p>Trazado registrado; no muestra ubicación de vehículos ni calcula calles.</p><p>Versión {{ route().revision }} · {{ route().name }}</p>
    @if (route().state === 'stale' || route().state === 'refreshing') { <p role="status">Trazado del corte anterior.</p> }
    @if (segments().length) {
      <div class="map-controls" aria-label="Controles del esquema"><button type="button" (click)="zoomBy(1)" [disabled]="zoom() >= 4" aria-label="Acercar esquema">+</button><button type="button" (click)="zoomBy(-1)" [disabled]="zoom() <= 1" aria-label="Alejar esquema">−</button><button type="button" (click)="reset()">Restablecer vista</button><span aria-live="polite">{{ zoom() }}×</span></div>
      <svg viewBox="0 0 340 150" role="img" [attr.aria-label]="route().name + ', versión ' + route().revision + '. Esquema entre paradas registradas.'">
        <svg [attr.viewBox]="viewBox()" width="340" height="150" overflow="hidden">
          @for (segment of segments(); track $index) { <polyline [attr.points]="segment" fill="none" stroke="#2563eb" stroke-width="2"/> }
          @for (point of route().points; track point.key) { <circle [attr.cx]="point.x" [attr.cy]="point.y" r="4" fill="#059669" stroke="white" stroke-width="1"/><text [attr.x]="point.x" [attr.y]="point.y > 75 ? point.y - 8 : point.y + 12" [attr.text-anchor]="point.x > 240 ? 'end' : 'start'">{{ point.label }}</text> }
        </svg>
      </svg>
      <div class="map-controls" aria-label="Desplazar esquema"><button type="button" (click)="pan(-1,0)" [disabled]="x() <= 0" aria-label="Desplazar hacia la izquierda">←</button><button type="button" (click)="pan(0,-1)" [disabled]="y() <= 0" aria-label="Desplazar hacia arriba">↑</button><button type="button" (click)="pan(0,1)" [disabled]="y() >= maxY()" aria-label="Desplazar hacia abajo">↓</button><button type="button" (click)="pan(1,0)" [disabled]="x() >= maxX()" aria-label="Desplazar hacia la derecha">→</button></div>
    } @else { <p role="status">{{ route().notice || 'La ruta no tiene coordenadas adyacentes suficientes.' }}</p> }
    <h3>Paradas con coordenadas registradas</h3><ol>@for (point of route().points; track point.key) { <li>{{ point.label }}</li> } @empty { <li>No hay coordenadas representables en esta consulta.</li> }</ol>`,
  styles: `:host{display:block;min-width:0;font-size:14px;color:#334155}p{line-height:1.5}svg{display:block;width:100%;height:auto;min-height:160px;background:#f7f9fc;border:1px solid #e5eaf2;border-radius:10px}svg svg{border:0;min-height:0}text{fill:#334155;font:7px Arial,sans-serif}.map-controls{display:flex;flex-wrap:wrap;align-items:center;gap:8px;margin:10px 0}button{min-width:44px;min-height:44px;padding:8px;border:1px solid #d9e2f0;border-radius:8px;background:#f3f7ff;color:#1d4ed8;font:inherit;cursor:pointer}button:disabled{color:#64748b;background:#f8fafc;cursor:default}button:focus-visible{outline:3px solid #1d4ed8;outline-offset:2px}li{overflow-wrap:anywhere}`,
})
export class DashboardRouteViewerComponent {
  readonly route = input.required<AdminDashboardViewModel['route']>();
  readonly zoom = signal(1); readonly x = signal(0); readonly y = signal(0);
  readonly maxX = computed(() => 340 - 340 / this.zoom()); readonly maxY = computed(() => 150 - 150 / this.zoom());
  readonly viewBox = computed(() => `${this.x()} ${this.y()} ${340 / this.zoom()} ${150 / this.zoom()}`);
  readonly segments = computed(() => (this.route().segments ?? [this.route().points]).filter(points => points.length >= 2).map(points => points.map(point => `${point.x},${point.y}`).join(' ')));
  private readonly geometryKey = computed(() => JSON.stringify([this.route().name, this.route().revision, this.route().points, this.route().segments]));
  constructor() { effect(() => { this.geometryKey(); this.reset(); }); }
  reset(): void { this.zoom.set(1); this.x.set(0); this.y.set(0); }
  zoomBy(delta: -1 | 1): void {
    const prior = this.zoom(), next = Math.max(1, Math.min(4, prior + delta));
    const cx = this.x() + 170 / prior, cy = this.y() + 75 / prior;
    this.zoom.set(next); this.x.set(Math.max(0, Math.min(this.maxX(), cx - 170 / next))); this.y.set(Math.max(0, Math.min(this.maxY(), cy - 75 / next)));
  }
  pan(dx: number, dy: number): void { this.x.update(x => Math.max(0, Math.min(this.maxX(), x + dx * 25 / this.zoom()))); this.y.update(y => Math.max(0, Math.min(this.maxY(), y + dy * 25 / this.zoom()))); }
}
