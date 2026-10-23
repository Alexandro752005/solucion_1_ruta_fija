import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';
import { ApiErrorService } from '../../core/api/api-error.service';
import { MaintenanceApiService, MaintenanceBasic, MaintenancePreview, MaintenanceView } from '../../core/maintenance/maintenance-api.service';

@Component({
  selector: 'rf-maintenance-page', imports: [FormsModule, RouterLink, DatePipe], changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './maintenance.page.html',
})
export class MaintenancePage {
  private readonly api = inject(MaintenanceApiService); private readonly errors = inject(ApiErrorService);
  readonly vehicleId = inject(ActivatedRoute).snapshot.paramMap.get('vehicleId') ?? '';
  readonly view = signal<MaintenanceView | null>(null); readonly preview = signal<MaintenancePreview | null>(null);
  readonly busy = signal(false); readonly uncertain = signal(false); readonly error = signal<string | null>(null); readonly notice = signal<string | null>(null);
  kind: 'PREVENTIVE' | 'URGENT' = 'PREVENTIVE'; startDate = ''; endDate = ''; reason = ''; responsibleReference = ''; evidenceReference = '';
  confirmed = false; private previewDate = '';
  constructor() { this.refresh(); }
  refresh() {
    if (this.busy()) return; this.busy.set(true);
    this.api.read(this.vehicleId).subscribe({ next: (v) => { this.view.set(v); this.uncertain.set(false); this.preview.set(null); this.confirmed = false; this.busy.set(false); this.error.set(null); },
      error: (e: unknown) => { this.busy.set(false); this.error.set(this.errors.toUiError(e).message); } });
  }
  check() {
    if (!this.startDate || this.busy() || this.uncertain()) return; this.busy.set(true); const date = this.startDate;
    this.api.preview(this.vehicleId, date).subscribe({ next: (v) => { this.preview.set(v); this.previewDate = date; this.busy.set(false); this.error.set(null); }, error: (e: unknown) => { this.busy.set(false); this.error.set(this.errors.toUiError(e).message); } });
  }
  create() {
    const c = this.basic(); if (!c) return;
    if (this.kind === 'PREVENTIVE' && (!this.startDate || !this.endDate || this.endDate < this.startDate || this.previewDate !== this.startDate || !this.preview()?.canConfirmPreventive)) { this.error.set('Consulta y resuelve los compromisos desde el inicio; indica un intervalo previsto válido.'); return; }
    this.write(this.api.create(this.vehicleId, { ...c, kind: this.kind, startDate: this.kind === 'URGENT' ? null : this.startDate, endDate: this.endDate || null }));
  }
  work(record: string) {
    const c = this.basic(); if (!c) return;
    const code = /^[A-Z][A-Z0-9_.:-]{2,159}$/;
    if (!code.test(this.responsibleReference) || !code.test(this.evidenceReference)) { this.error.set('Indica códigos de responsable y comprobación, sin datos personales.'); return; }
    this.write(this.api.work(this.vehicleId, record, { ...c, responsibleReference: this.responsibleReference, evidenceReference: this.evidenceReference, workCompleted: true }));
  }
  cancel(record: string) { const c = this.basic(); if (c) this.write(this.api.cancel(this.vehicleId, record, c)); }
  authorizeReturn() { const c = this.basic(); if (c) this.write(this.api.authorizeReturn(this.vehicleId, c)); }
  private basic(): MaintenanceBasic | null {
    if (this.busy() || this.uncertain() || !this.view()) return null;
    if (!this.reason.trim() || !this.confirmed) { this.error.set('Indica el motivo sin datos personales y confirma la acción que vas a ejecutar.'); return null; }
    return { expectedVersion: this.view()!.version, operationKey: crypto.randomUUID(), reason: this.reason.trim() };
  }
  private write(request: Observable<MaintenanceView>) {
    this.busy.set(true); this.error.set(null); this.notice.set(null);
    request.subscribe({ next: (v) => { this.view.set(v); this.preview.set(null); this.confirmed = false; this.busy.set(false); this.notice.set('Acción registrada por el servidor. El retorno no habilita conductores ni omite otros requisitos.'); },
      error: (e: unknown) => { this.busy.set(false); const failure = this.errors.toUiError(e); this.error.set(failure.message);
        const status = typeof e === 'object' && e !== null && 'status' in e ? Number(e.status) : 0;
        this.uncertain.set(![400,401,403,404,409,422].includes(status)); this.confirmed = false;
        this.notice.set('Consulta el estado del servidor antes de otra acción; la solicitud no se repetirá automáticamente.'); } });
  }
}
