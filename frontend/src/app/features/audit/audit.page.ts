import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuditApiService } from '../../core/audit/audit-api.service';
import { AuditEvent } from '../../core/audit/audit.models';

function asInstant(value: string, endOfDay = false): string | undefined {
  if (!value) {
    return undefined;
  }
  const date = new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
  return Number.isNaN(date.getTime()) ? undefined : date.toISOString();
}

@Component({
  selector: 'rf-audit-page',
  imports: [ReactiveFormsModule],
  templateUrl: './audit.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AuditPage {
  private readonly api = inject(AuditApiService);
  private readonly apiErrors = inject(ApiErrorService);

  readonly events = signal<readonly AuditEvent[]>([]);
  readonly selected = signal<AuditEvent | null>(null);
  readonly loading = signal(true);
  readonly loadingDetail = signal(false);
  readonly page = signal(0);
  readonly totalItems = signal(0);
  readonly totalPages = signal(0);
  readonly error = signal<UiError | null>(null);

  readonly form = new FormGroup({
    action: new FormControl('', { nonNullable: true }),
    entityType: new FormControl('', { nonNullable: true }),
    from: new FormControl('', { nonNullable: true }),
    to: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.load();
  }

  applyFilters(): void {
    this.selected.set(null);
    this.load(0);
  }

  clearFilters(): void {
    this.form.reset({ action: '', entityType: '', from: '', to: '' });
    this.selected.set(null);
    this.load(0);
  }

  select(event: AuditEvent): void {
    this.loadingDetail.set(true);
    this.error.set(null);
    this.api.get(event.id).pipe(finalize(() => this.loadingDetail.set(false))).subscribe({
      next: (detail) => this.selected.set(detail),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el detalle de auditoría.')),
    });
  }

  previousPage(): void {
    if (this.page() > 0) {
      this.load(this.page() - 1);
    }
  }

  nextPage(): void {
    if (this.page() + 1 < this.totalPages()) {
      this.load(this.page() + 1);
    }
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'medium' });
  }

  metadataLabel(event: AuditEvent): string {
    const entries = Object.entries(event.metadata);
    return entries.length === 0 ? 'Sin metadatos adicionales' : JSON.stringify(event.metadata, null, 2);
  }

  private load(page = this.page()): void {
    const value = this.form.getRawValue();
    this.loading.set(true);
    this.error.set(null);
    this.api.list({
      page,
      size: 20,
      sort: 'occurredAt,desc',
      action: value.action.trim() || undefined,
      entityType: value.entityType.trim() || undefined,
      from: asInstant(value.from),
      to: asInstant(value.to, true),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.events.set(response.items);
        this.page.set(response.page);
        this.totalItems.set(response.totalItems);
        this.totalPages.set(response.totalPages);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron consultar los eventos de auditoría.')),
    });
  }
}
