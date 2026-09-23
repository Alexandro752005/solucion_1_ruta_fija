import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize, Observable } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import {
  AssignmentReport,
  AvailabilityReport,
  IncidentReport,
  ReportExportFormat,
  StatusCount,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';

function dateInputValue(date: Date): string {
  const pad = (number: number) => number.toString().padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function escapeCsvCell(value: string): string {
  // Excel y otras hojas pueden evaluar fórmulas incluso cuando el valor llega desde un CSV.
  const protectedValue = /^[\t\r ]*[=+\-@]/.test(value) ? `'${value}` : value;
  return `"${protectedValue.replaceAll('"', '""')}"`;
}

@Component({
  selector: 'rf-reports-page',
  imports: [ReactiveFormsModule, PageHeaderComponent],
  templateUrl: './reports.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReportsPage {
  private readonly api = inject(OperationsApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly availability = signal<AvailabilityReport | null>(null);
  readonly assignmentReport = signal<AssignmentReport | null>(null);
  readonly incidentReport = signal<IncidentReport | null>(null);
  readonly loadingAvailability = signal(true);
  readonly loadingRange = signal(true);
  readonly exporting = signal<string | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );

  readonly form = new FormGroup({
    from: new FormControl(dateInputValue(new Date(Date.now() - 29 * 24 * 60 * 60 * 1000)), {
      nonNullable: true,
      validators: [Validators.required],
    }),
    to: new FormControl(dateInputValue(new Date()), {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  constructor() {
    this.loadAvailability();
    this.loadRangeReports();
    this.realtime.connect();
    this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => {
      this.loadAvailability();
      this.loadRangeReports();
    });
    this.destroyRef.onDestroy(() => this.realtime.disconnect());
  }

  refresh(): void {
    this.loadAvailability();
    this.loadRangeReports();
  }

  submit(): void {
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.to < value.from) {
      this.error.set({ message: 'La fecha final debe ser posterior o igual a la inicial.', fieldErrors: [] });
      return;
    }
    this.loadRangeReports();
  }

  downloadAssignmentsCsv(): void {
    this.notice.set(null);
    const report = this.assignmentReport();
    if (report === null) {
      return;
    }
    this.downloadCsv(
      `asignaciones-${report.from}-${report.to}.csv`,
      ['Estado', 'Conductor', 'Grupo', 'Vehículo', 'Origen', 'Destino', 'Inicio programado', 'Fin programado'],
      report.items.map((item) => [
        this.label(item.status),
        item.driverName,
        item.groupName,
        item.vehiclePlate,
        item.originText,
        item.destinationText,
        item.scheduledAt,
        item.scheduledEndAt,
      ]),
    );
  }

  downloadIncidentsCsv(): void {
    this.notice.set(null);
    const report = this.incidentReport();
    if (report === null) {
      return;
    }
    this.downloadCsv(
      `incidencias-${report.from}-${report.to}.csv`,
      ['Estado', 'Categoría', 'Conductor', 'Descripción', 'Reportada en', 'Seguimiento'],
      report.items.map((item) => [
        this.label(item.status),
        this.label(item.category),
        item.driverName,
        item.description,
        item.reportedAt,
        item.followUpNote ?? '',
      ]),
    );
  }

  exportAvailability(format: ReportExportFormat): void {
    this.downloadReport(
      this.api.exportAvailabilityReport(format),
      `disponibilidad-operativa.${format}`,
      `disponibilidad-${format}`,
    );
  }

  exportAssignments(format: ReportExportFormat): void {
    const report = this.assignmentReport();
    if (report === null) {
      return;
    }
    this.downloadReport(
      this.api.exportAssignmentReport(report.from, report.to, format),
      `asignaciones-${report.from}-${report.to}.${format}`,
      `asignaciones-${format}`,
    );
  }

  exportIncidents(format: ReportExportFormat): void {
    const report = this.incidentReport();
    if (report === null) {
      return;
    }
    this.downloadReport(
      this.api.exportIncidentReport(report.from, report.to, format),
      `incidencias-${report.from}-${report.to}.${format}`,
      `incidencias-${format}`,
    );
  }

  label(status: string): string {
    return ({
      DISPONIBLE: 'Disponible',
      RESERVADO: 'Reservado',
      EN_SERVICIO: 'En servicio',
      DESCANSO: 'Descanso',
      NO_DISPONIBLE: 'No disponible',
      MANTENIMIENTO: 'Mantenimiento',
      INACTIVO: 'Inactivo',
      PENDING_RESPONSE: 'Pendiente de respuesta',
      SCHEDULED: 'Programada',
      COMPLETED: 'Completada',
      REJECTED: 'Rechazada',
      CANCELLED: 'Cancelada',
      EXPIRED: 'Vencida',
      OPEN: 'Abierta',
      FOLLOW_UP: 'En seguimiento',
      RESOLVED: 'Resuelta',
      AVERIA: 'Avería',
      ACCIDENTE: 'Accidente',
      RETRASO: 'Retraso',
      OTRO: 'Otro',
    } as Record<string, string>)[status] ?? status;
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
  }

  countLabel(count: StatusCount): string {
    return `${this.label(count.status)}: ${count.total}`;
  }

  private loadAvailability(): void {
    this.loadingAvailability.set(true);
    this.api
      .availabilityReport()
      .pipe(finalize(() => this.loadingAvailability.set(false)))
      .subscribe({
        next: (report) => this.availability.set(report),
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el resumen de disponibilidad.')),
      });
  }

  private loadRangeReports(): void {
    const value = this.form.getRawValue();
    this.loadingRange.set(true);
    this.error.set(null);
    let completed = 0;
    const finish = () => {
      completed += 1;
      if (completed === 2) {
        this.loadingRange.set(false);
      }
    };
    this.api.assignmentReport(value.from, value.to).pipe(finalize(finish)).subscribe({
      next: (report) => this.assignmentReport.set(report),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el reporte de asignaciones.')),
    });
    this.api.incidentReport(value.from, value.to).pipe(finalize(finish)).subscribe({
      next: (report) => this.incidentReport.set(report),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el reporte de incidencias.')),
    });
  }

  private downloadCsv(filename: string, headers: readonly string[], rows: readonly (readonly string[])[]): void {
    const content = [headers, ...rows].map((row) => row.map(escapeCsvCell).join(',')).join('\r\n');
    const url = URL.createObjectURL(new Blob([`\uFEFF${content}`], { type: 'text/csv;charset=utf-8' }));
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
    this.notice.set('Archivo CSV generado correctamente.');
  }

  private downloadReport(request: Observable<Blob>, filename: string, operation: string): void {
    if (this.exporting() !== null) {
      return;
    }
    this.notice.set(null);
    this.exporting.set(operation);
    request.pipe(finalize(() => this.exporting.set(null))).subscribe({
      next: (content) => {
        const url = URL.createObjectURL(content);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        anchor.click();
        URL.revokeObjectURL(url);
        this.notice.set('Exportación generada correctamente.');
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo generar la exportación solicitada.')),
    });
  }
}
