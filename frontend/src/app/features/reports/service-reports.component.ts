import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { ServiceReportsApiService } from '../../core/reports/service-reports-api.service';
import { IssueServiceReport, ServiceReportEmission, ServiceReportEmissionSummary, ServiceReportExportFormat, ServiceReportView } from '../../core/reports/service-reports.models';

function localDate(date: Date): string { return new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date); }

@Component({
  selector: 'rf-service-reports', imports: [ReactiveFormsModule], templateUrl: './service-reports.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ServiceReportsComponent {
  private readonly api = inject(ServiceReportsApiService);
  private readonly errors = inject(ApiErrorService);
  private readonly destroyRef = inject(DestroyRef);
  private querySequence = 0;
  private selectionSequence = 0;
  readonly view = signal<ServiceReportView | null>(null);
  readonly emissions = signal<readonly ServiceReportEmissionSummary[]>([]);
  readonly selected = signal<ServiceReportEmission | null>(null);
  readonly loading = signal(false);
  readonly selecting = signal(false);
  readonly issuing = signal(false);
  readonly exporting = signal<ServiceReportExportFormat | null>(null);
  readonly pendingIssue = signal<IssueServiceReport | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly form = new FormGroup({
    from: new FormControl(localDate(new Date(Date.now() - 29 * 86400000)), { nonNullable: true, validators: [Validators.required] }),
    to: new FormControl(localDate(new Date()), { nonNullable: true, validators: [Validators.required] }),
  });
  constructor() { this.refresh(); }
  refresh(): void {
    if (!this.validRange() || this.issuing() || this.pendingIssue()) return;
    const { from, to } = this.form.getRawValue(); const sequence = ++this.querySequence;
    this.loading.set(true); this.error.set(null); this.selected.set(null); ++this.selectionSequence;
    forkJoin({ view: this.api.current(from, to), emissions: this.api.list(from, to) })
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { if (sequence === this.querySequence) this.loading.set(false); }))
      .subscribe({ next: result => { if (sequence === this.querySequence) { this.view.set(result.view); this.emissions.set(result.emissions); } },
        error: err => { if (sequence === this.querySequence) { this.view.set(null); this.emissions.set([]); this.error.set(this.errors.toUiError(err, 'No se pudieron consultar los servicios.')); } } });
  }
  issue(): void {
    if (this.issuing() || this.pendingIssue() || this.loading() || !this.validRange()) return;
    const range = this.form.getRawValue();
    if (this.view()?.from !== range.from || this.view()?.to !== range.to) {
      this.error.set({ message: 'Consulta primero el periodo seleccionado antes de emitir.', fieldErrors: [] }); return;
    }
    const command: IssueServiceReport = { operationKey: crypto.randomUUID(), ...range };
    this.pendingIssue.set(command); this.submitIssue(command);
  }
  recoverIssue(): void {
    const command = this.pendingIssue(); if (command && !this.issuing()) this.submitIssue(command);
  }
  private submitIssue(command: IssueServiceReport): void {
    this.issuing.set(true); this.error.set(null); this.notice.set(null);
    this.api.issue(command).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.issuing.set(false))).subscribe({
      next: emission => {
        this.pendingIssue.set(null); this.selected.set(emission); ++this.selectionSequence;
        this.emissions.update(rows => [emission, ...rows.filter(row => row.id !== emission.id)].sort((a,b) => b.revision - a.revision));
        this.notice.set('Emisión confirmada por el servidor. Los tres formatos usan este mismo contenido persistido.');
      },
      error: err => this.error.set(this.errors.toUiError(err, 'No se confirmó la emisión. Conservamos su referencia: usa «Consultar o recuperar emisión» sin crear otra.')),
    });
  }
  select(id: string): void {
    if (this.issuing() || this.pendingIssue() || this.exporting()) return;
    const sequence = ++this.selectionSequence; this.selecting.set(true); this.error.set(null); this.selected.set(null);
    this.api.read(id).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => { if (sequence === this.selectionSequence) this.selecting.set(false); })).subscribe({
      next: emission => { if (sequence === this.selectionSequence) this.selected.set(emission); },
      error: err => { if (sequence === this.selectionSequence) this.error.set(this.errors.toUiError(err, 'No se pudo leer esta emisión.')); },
    });
  }
  download(format: ServiceReportExportFormat): void {
    const emission = this.selected(); if (!emission || this.exporting() || this.selecting() || this.issuing() || this.pendingIssue()) return;
    this.exporting.set(format); this.error.set(null);
    this.api.export(emission.id, format).pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.exporting.set(null))).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url;
        anchor.download = `servicios-${emission.id}-v${emission.revision}.${format}`; anchor.click(); URL.revokeObjectURL(url);
        this.notice.set(`Descarga ${format.toUpperCase()} de la emisión v${emission.revision} confirmada.`);
      }, error: err => this.error.set(this.errors.toUiError(err, 'No se pudo descargar esta emisión. La versión seleccionada se conserva.')),
    });
  }
  dateLabel(value: string | null): string { return value ? new Date(value).toLocaleString('es-PE', { timeZone: 'America/Lima', dateStyle: 'short', timeStyle: 'short' }) : 'No registrado'; }
  statusLabel(value: string): string {
    return ({ PENDING_RESPONSE: 'Sin aceptación', PENDING_REASSIGNMENT: 'Por reasignar', CONFIRMED: 'Confirmado', IN_SERVICE: 'En servicio', COMPLETED: 'Completado', CANCELLED: 'Cancelado', INTERRUPTED: 'Interrumpido', TRANSFERRED: 'Relevado' } as Record<string,string>)[value] ?? value;
  }
  private validRange(): boolean {
    const range = this.form.getRawValue();
    const delta = Date.parse(`${range.to}T00:00:00Z`) - Date.parse(`${range.from}T00:00:00Z`);
    if (this.form.invalid || !Number.isFinite(delta) || delta < 0 || delta >= 90 * 86400000) {
      this.form.markAllAsTouched(); this.error.set({ message: 'Selecciona un periodo válido de hasta90 días, inclusive.', fieldErrors: [] }); return false;
    }
    return true;
  }
}
