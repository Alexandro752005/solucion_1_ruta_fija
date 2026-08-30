import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { ManagementApiService } from '../../core/management/management-api.service';
import { Driver } from '../../core/management/management.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import {
  Assignment,
  INCIDENT_CATEGORIES,
  INCIDENT_STATUSES,
  Incident,
  IncidentCategory,
  IncidentStatus,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';

@Component({
  selector: 'rf-incidents-page',
  imports: [ReactiveFormsModule],
  templateUrl: './incidents.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class IncidentsPage {
  private readonly api = inject(OperationsApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly categories = INCIDENT_CATEGORIES;
  readonly statuses = INCIDENT_STATUSES;
  readonly incidents = signal<readonly Incident[]>([]);
  readonly drivers = signal<readonly Driver[]>([]);
  readonly assignments = signal<readonly Assignment[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly followUpSaving = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedStatus = signal<IncidentStatus | ''>('');
  readonly selectedCategory = signal<IncidentCategory | ''>('');
  readonly selectedForFollowUp = signal<Incident | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    driverId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    assignmentId: new FormControl('', { nonNullable: true }),
    category: new FormControl<IncidentCategory>('AVERIA', { nonNullable: true, validators: [Validators.required] }),
    description: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)],
    }),
  });
  readonly followUpForm = new FormGroup({
    note: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1000)],
    }),
    resolve: new FormControl(false, { nonNullable: true }),
  });

  constructor() {
    this.load();
    this.loadResources();
    this.realtime.connect();
    this.realtime.events.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.load());
    this.destroyRef.onDestroy(() => this.realtime.disconnect());
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listIncidents({
        page,
        size: 20,
        sort: 'reportedAt,desc',
        status: this.selectedStatus() || undefined,
        category: this.selectedCategory() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.incidents.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar las incidencias.')),
      });
  }

  applyFilters(status: string, category: string): void {
    this.selectedStatus.set(this.isIncidentStatus(status) ? status : '');
    this.selectedCategory.set(this.isIncidentCategory(category) ? category : '');
    this.load(0);
  }

  submit(): void {
    this.error.set(null);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .createIncident({
        driverId: value.driverId,
        assignmentId: value.assignmentId || undefined,
        category: value.category,
        description: value.description.trim(),
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: () => {
          this.notice.set('Incidencia registrada desde el CRM web.');
          this.form.reset({ driverId: '', assignmentId: '', category: 'AVERIA', description: '' });
          this.load(0);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo registrar la incidencia.')),
      });
  }

  selectAssignment(assignmentId: string): void {
    this.form.controls.assignmentId.setValue(assignmentId);
    const assignment = this.assignments().find((candidate) => candidate.id === assignmentId);
    if (assignment !== undefined) {
      this.form.controls.driverId.setValue(assignment.driverId);
    }
  }

  beginFollowUp(incident: Incident): void {
    if (incident.status === 'RESOLVED') {
      return;
    }
    this.selectedForFollowUp.set(incident);
    this.followUpForm.reset({ note: '', resolve: false });
  }

  cancelFollowUp(): void {
    this.selectedForFollowUp.set(null);
    this.followUpForm.reset({ note: '', resolve: false });
  }

  submitFollowUp(): void {
    const incident = this.selectedForFollowUp();
    if (incident === null) {
      return;
    }
    if (this.followUpForm.invalid) {
      this.followUpForm.markAllAsTouched();
      return;
    }
    const value = this.followUpForm.getRawValue();
    this.followUpSaving.set(true);
    this.api
      .followUpIncident(incident.id, {
        version: incident.version,
        note: value.note.trim(),
        resolve: value.resolve,
      })
      .pipe(finalize(() => this.followUpSaving.set(false)))
      .subscribe({
        next: () => {
          this.notice.set(value.resolve ? 'Incidencia resuelta.' : 'Seguimiento registrado.');
          this.cancelFollowUp();
          this.load();
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo registrar el seguimiento.')),
      });
  }

  categoryLabel(category: IncidentCategory): string {
    return ({
      AVERIA: 'Avería',
      ACCIDENTE: 'Accidente',
      RETRASO: 'Retraso',
      OTRO: 'Otro',
    } as Record<IncidentCategory, string>)[category];
  }

  statusLabel(status: IncidentStatus): string {
    return ({ OPEN: 'Abierta', FOLLOW_UP: 'En seguimiento', RESOLVED: 'Resuelta' } as Record<IncidentStatus, string>)[status];
  }

  dateLabel(value: string): string {
    return new Date(value).toLocaleString('es-PE', { dateStyle: 'short', timeStyle: 'short' });
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

  private loadResources(): void {
    this.managementApi.listDrivers({ page: 0, size: 100, sort: 'fullName,asc', active: true }).subscribe({
      next: (response) => this.drivers.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los conductores.')),
    });
    this.api.listAssignments({ page: 0, size: 100, sort: 'scheduledAt,desc' }).subscribe({
      next: (response) => this.assignments.set(response.items),
      error: () => {
        // La asignación es opcional para registrar una incidencia.
      },
    });
  }

  private isIncidentStatus(value: string): value is IncidentStatus {
    return (INCIDENT_STATUSES as readonly string[]).includes(value);
  }

  private isIncidentCategory(value: string): value is IncidentCategory {
    return (INCIDENT_CATEGORIES as readonly string[]).includes(value);
  }
}
