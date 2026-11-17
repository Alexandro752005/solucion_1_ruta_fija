import { DestroyRef, Injectable, WritableSignal, effect, inject, signal, untracked } from '@angular/core';
import { Observable, Subscription, finalize, forkJoin } from 'rxjs';
import { AuthSessionService } from '../../../core/auth/auth-session.service';
import { ApiErrorService } from '../../../core/api/api-error.service';
import { UiError } from '../../../core/api/api-error.model';
import { ServiceReportsApiService } from '../../../core/reports/service-reports-api.service';
import { IssueServiceReport, ServiceReportEmission, ServiceReportEmissionSummary, ServiceReportExportFormat, ServiceReportView } from '../../../core/reports/service-reports.models';
import { InvalidDashboardResponse, isCount, isDay, isInstant, isUuid, validateServices } from './admin-dashboard.adapter';

function validateEmissionSummary(value: ServiceReportEmissionSummary): void {
  if (!value || !isUuid(value.id) || !isCount(value.revision) || value.revision < 1 || !isInstant(value.issuedAt)
    || !/^[a-f\d]{64}$/i.test(value.contentSha256) || value.previousEmissionId != null && !isUuid(value.previousEmissionId)) throw new InvalidDashboardResponse();
}

/** A page-scoped operation survives closing its dialog, never a session change. */
@Injectable()
export class DashboardReportFacade {
  private readonly api = inject(ServiceReportsApiService);
  private readonly session = inject(AuthSessionService);
  private readonly errors = inject(ApiErrorService);
  private readonly destroy = inject(DestroyRef);
  private actor = '';
  private generation = 0;
  private readonly subscriptions = new Map<string, Subscription>();
  private readonly sequences = new Map<string, number>();
  private readonly objectUrls = new Map<string, ReturnType<typeof setTimeout>>();
  readonly day = signal(''); readonly zone = signal('');
  readonly current = signal<ServiceReportView | null>(null);
  readonly emissions = signal<readonly ServiceReportEmissionSummary[]>([]);
  readonly selected = signal<ServiceReportEmission | null>(null);
  readonly pending = signal<IssueServiceReport | null>(null);
  readonly loading = signal(false); readonly selecting = signal(false); readonly issuing = signal(false); readonly downloading = signal(false);
  readonly error = signal<UiError | null>(null); readonly notice = signal('');

  constructor() {
    effect(() => {
      const user = this.session.user();
      const key = user?.role === 'ADMIN' && user.organizationId ? `${user.id}|${user.organizationId}` : '';
      untracked(() => { if (key !== this.actor) { this.reset(); this.actor = key; } });
    });
    this.destroy.onDestroy(() => this.reset());
  }
  private cancel(key: string): void { this.sequences.set(key, (this.sequences.get(key) ?? 0) + 1); this.subscriptions.get(key)?.unsubscribe(); this.subscriptions.delete(key); }
  private reset(): void {
    this.generation++; this.subscriptions.forEach(sub => sub.unsubscribe()); this.subscriptions.clear(); this.sequences.clear();
    this.objectUrls.forEach((timer, url) => { clearTimeout(timer); URL.revokeObjectURL(url); }); this.objectUrls.clear();
    this.day.set(''); this.zone.set(''); this.current.set(null); this.emissions.set([]); this.selected.set(null); this.pending.set(null);
    this.loading.set(false); this.selecting.set(false); this.issuing.set(false); this.downloading.set(false); this.error.set(null); this.notice.set('');
  }
  private problem(error: unknown, message: string): void {
    this.error.set(error instanceof InvalidDashboardResponse ? { message: 'La respuesta no permite confirmar este reporte. Consulta de nuevo sin emitir otra referencia.', fieldErrors: [] } : this.errors.toUiError(error, message));
  }
  private call<T>(key: string, observable: Observable<T>, busy: WritableSignal<boolean>, accept: (value: T) => void, message: string): void {
    this.cancel(key); const sequence = this.sequences.get(key)!; const generation = this.generation;
    const valid = () => generation === this.generation && sequence === this.sequences.get(key);
    busy.set(true); this.error.set(null);
    const job = new Subscription(); this.subscriptions.set(key, job);
    job.add(observable.pipe(finalize(() => { if (valid()) { busy.set(false); this.subscriptions.delete(key); } })).subscribe({
      next: value => { if (valid()) { try { accept(value); } catch (error) { this.problem(error, message); } } },
      error: error => { if (valid()) this.problem(error, message); },
    }));
  }

  open(day: string, zone: string): void {
    if (!this.actor) return;
    if (this.pending()) { this.notice.set('Se conserva la operación pendiente y su período. Usa la misma referencia para confirmar el resultado.'); return; }
    this.cancel('query'); this.cancel('select'); this.cancel('download'); this.loading.set(false); this.selecting.set(false); this.downloading.set(false);
    this.day.set(''); this.zone.set(''); this.current.set(null); this.emissions.set([]); this.selected.set(null); this.notice.set('');
    if (!isDay(day)) { this.error.set({ message: 'La fecha de la organización no está confirmada.', fieldErrors: [] }); return; }
    try { new Intl.DateTimeFormat('es-PE', { timeZone: zone }); } catch { this.error.set({ message: 'La zona de la organización no está confirmada.', fieldErrors: [] }); return; }
    this.day.set(day); this.zone.set(zone);
    this.call('query', forkJoin({ current: this.api.current(day, day), emissions: this.api.list(day, day) }), this.loading, result => {
      const current = validateServices(result.current, day, zone);
      if (!Array.isArray(result.emissions)) throw new InvalidDashboardResponse();
      result.emissions.forEach(validateEmissionSummary);
      if (new Set(result.emissions.map(row => row.id)).size !== result.emissions.length) throw new InvalidDashboardResponse();
      this.current.set(current); this.emissions.set(result.emissions);
    }, 'No se pudo consultar el período y sus emisiones.');
  }
  issue(): void {
    if (!this.actor || this.loading() || this.issuing() || this.selecting() || this.downloading() || this.pending() || !this.current()) return;
    if (this.current()!.from !== this.day() || this.current()!.zoneId !== this.zone()) return;
    const command: IssueServiceReport = { operationKey: crypto.randomUUID(), from: this.day(), to: this.day() };
    this.pending.set(command); this.submit(command);
  }
  recover(): void { const command = this.pending(); if (this.actor && command && !this.issuing()) this.submit(command); }
  private submit(command: IssueServiceReport): void {
    this.notice.set(''); this.selected.set(null);
    this.call('issue', this.api.issue(command), this.issuing, emission => {
      this.validateEmission(emission);
      this.selected.set(emission); this.pending.set(null);
      this.emissions.update(rows => [emission, ...rows.filter(row => row.id !== emission.id)].sort((a, b) => b.revision - a.revision));
      this.notice.set('Emisión confirmada. CSV, Excel y PDF usarán el mismo contenido persistido y su corte.');
    }, 'No se confirmó la emisión. Conservamos su referencia; consulta o recupera con la misma operación.');
  }
  private validateEmission(value: ServiceReportEmission): void {
    validateEmissionSummary(value);
    if (!isUuid(value.seriesId)) throw new InvalidDashboardResponse();
    validateServices(value.report, this.day(), this.zone());
  }
  select(id: string): void {
    if (!this.actor || this.pending() || this.loading() || this.issuing() || this.downloading() || !this.emissions().some(row => row.id === id)) return;
    this.selected.set(null);
    this.call('select', this.api.read(id), this.selecting, emission => {
      this.validateEmission(emission);
      const summary = this.emissions().find(row => row.id === id);
      if (!summary || emission.id !== id || emission.revision !== summary.revision || emission.contentSha256 !== summary.contentSha256) throw new InvalidDashboardResponse();
      this.selected.set(emission);
    }, 'No se pudo leer esta emisión.');
  }
  download(format: ServiceReportExportFormat): void {
    const emission = this.selected();
    if (!this.actor || !emission || this.pending() || this.selecting() || this.issuing() || this.downloading() || !['csv', 'xlsx', 'pdf'].includes(format)) return;
    this.call('download', this.api.export(emission.id, format), this.downloading, blob => {
      if (!(blob instanceof Blob) || blob.size === 0 || blob.type.includes('json') || blob.type.includes('html')) throw new InvalidDashboardResponse();
      const url = URL.createObjectURL(blob), anchor = document.createElement('a'); anchor.href = url;
      anchor.download = `servicios-${emission.id}-v${emission.revision}.${format}`; anchor.click();
      const timer = setTimeout(() => { URL.revokeObjectURL(url); this.objectUrls.delete(url); }, 1000); this.objectUrls.set(url, timer);
      this.notice.set(`Descarga ${format.toUpperCase()} solicitada de la emisión v${emission.revision}. Se conserva su SHA-256 de contenido.`);
    }, 'No se pudo descargar esta emisión. La selección confirmada se conserva.');
  }
  date(value: string): string {
    try { return new Intl.DateTimeFormat('es-PE', { timeZone: this.zone() || 'UTC', dateStyle: 'short', timeStyle: 'short' }).format(new Date(value)); } catch { return 'Fecha no disponible'; }
  }
}
