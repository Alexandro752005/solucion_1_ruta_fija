import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, Observable } from 'rxjs';
import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { RoutesApiService, RouteSummary, RouteView, RouteStop, RouteCommand } from '../../core/routes/routes-api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

interface StopDraft { key: string; name: string; latitude: string; longitude: string; }
@Component({ selector: 'rf-routes-page', imports: [FormsModule, PageHeaderComponent], templateUrl: './routes.page.html', changeDetection: ChangeDetectionStrategy.OnPush })
export class RoutesPage {
  private readonly api = inject(RoutesApiService); private readonly errors = inject(ApiErrorService);
  readonly routes = signal<readonly RouteSummary[]>([]); readonly selected = signal<RouteView | null>(null);
  readonly loading = signal(false); readonly saving = signal(false); readonly uncertain = signal(false);
  readonly error = signal<UiError | null>(null); readonly notice = signal<string | null>(null);
  readonly editing = signal(false); readonly offset = signal(0);
  name = ''; reason = ''; stops: StopDraft[] = []; private pendingKey: string | null = null; private selectionRequest = 0;
  constructor() { this.load(); }
  load(offset = this.offset()): void { this.loading.set(true); this.api.list(offset).pipe(finalize(() => this.loading.set(false))).subscribe({ next: (rows) => { this.routes.set(rows); this.offset.set(offset); }, error: (e: unknown) => this.error.set(this.errors.toUiError(e, 'No se pudo consultar el catálogo de rutas.')) }); }
  choose(id: string): void { if (this.saving() || this.uncertain()) return; const request = ++this.selectionRequest; this.loading.set(true); this.api.get(id).pipe(finalize(() => { if (request === this.selectionRequest) this.loading.set(false); })).subscribe({ next: (v) => { if (request === this.selectionRequest) this.acceptView(v); }, error: (e: unknown) => { if (request === this.selectionRequest) this.error.set(this.errors.toUiError(e, 'No se pudo consultar la ruta.')); } }); }
  newRoute(): void { if (this.saving() || this.uncertain()) return; this.selectionRequest++; this.loading.set(false); this.selected.set(null); this.name = ''; this.reason = ''; this.stops = [this.blank(), this.blank(), this.blank()]; this.editing.set(true); this.error.set(null); this.notice.set(null); }
  addStop(): void { if (this.stops.length < 100) this.stops.splice(Math.max(1, this.stops.length - 1), 0, this.blank()); }
  removeStop(i: number): void { if (this.stops.length > 3 && i > 0 && i < this.stops.length - 1) this.stops.splice(i, 1); }
  moveStop(i: number, delta: number): void { const next = i + delta; if (i > 0 && i < this.stops.length - 1 && next > 0 && next < this.stops.length - 1) [this.stops[i], this.stops[next]] = [this.stops[next], this.stops[i]]; }
  save(): void {
    if (this.saving() || this.uncertain()) return;
    const body = this.command(); if (!body) return;
    const current = this.selected();
    if (current && !current.active) return;
    this.run(current ? this.api.revise(current.id, { ...body, expectedVersion: current.version }) : this.api.create(body), body.operationKey);
  }
  changeStatus(active: boolean): void { const v = this.selected(); if (!v || !this.reason.trim() || this.saving() || this.uncertain()) return; const key = crypto.randomUUID(); this.run(this.api.status(v.id, { expectedVersion: v.version, operationKey: key, reason: this.reason.trim(), active }), key); }
  reconcile(): void {
    if (!this.pendingKey || this.saving()) return;
    this.loading.set(true); this.api.operation(this.pendingKey).pipe(finalize(() => this.loading.set(false))).subscribe({ next: (v) => { this.acceptView(v); this.pendingKey = null; this.uncertain.set(false); this.notice.set('Estado confirmado mediante consulta. No se repitió el cambio.'); this.load(); }, error: (e: unknown) => this.error.set(this.errors.toUiError(e, 'El resultado todavía no está acreditado. Conserva el caso y vuelve a consultar; no reenvíes el cambio.')) });
  }
  private run(request: Observable<RouteView>, key: string): void {
    this.pendingKey = key; this.saving.set(true); this.error.set(null); this.notice.set(null);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({ next: (v) => { this.acceptView(v); this.pendingKey = null; this.uncertain.set(false); this.notice.set('Cambio confirmado. Las versiones anteriores se conservaron.'); this.load(); }, error: (e: unknown) => { const rejected = e instanceof HttpErrorResponse && [400,403,404,409,422].includes(e.status); this.uncertain.set(!rejected); if (rejected) this.pendingKey = null; this.error.set(this.errors.toUiError(e, rejected ? 'El servidor rechazó el cambio. Revisa los datos y consulta la versión actual.' : 'Cambio no confirmado. Consulta su resultado antes de otra acción.')); } });
  }
  private command(): RouteCommand | null {
    const rows: RouteStop[] = [];
    if (!this.name.trim() || !this.reason.trim() || this.stops.length < 3) { this.invalid(); return null; }
    for (let i = 0; i < this.stops.length; i++) {
      const s = this.stops[i]; const lat = s.latitude.trim() ? Number(s.latitude) : null; const lng = s.longitude.trim() ? Number(s.longitude) : null;
      if (!s.name.trim() || (lat === null) !== (lng === null) || (lat !== null && (!Number.isFinite(lat) || lat < -90 || lat > 90)) || (lng !== null && (!Number.isFinite(lng) || lng < -180 || lng > 180))) { this.invalid(); return null; }
      rows.push({ key: s.key, name: s.name.trim(), kind: i === 0 ? 'START' : i === this.stops.length - 1 ? 'END' : 'INTERMEDIATE', latitude: lat, longitude: lng });
    }
    return { name: this.name.trim(), reason: this.reason.trim(), operationKey: crypto.randomUUID(), stops: rows };
  }
  private invalid(): void { this.error.set(this.errors.toUiError(null, 'Completa nombre, motivo y las paradas de inicio, intermedias y fin. Indica ambas coordenadas válidas o ninguna.')); }
  private acceptView(v: RouteView): void { this.selected.set(v); const current = v.versions.find((x) => x.id === v.currentVersionId); this.name = current?.name ?? v.name; this.reason = ''; this.stops = current?.stops.map((x) => ({ key: x.key, name: x.name, latitude: x.latitude == null ? '' : String(x.latitude), longitude: x.longitude == null ? '' : String(x.longitude) })) ?? []; this.editing.set(true); }
  private blank(): StopDraft { return { key: crypto.randomUUID(), name: '', latitude: '', longitude: '' }; }
}
