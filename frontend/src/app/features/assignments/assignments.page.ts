import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { ManagementApiService } from '../../core/management/management-api.service';
import { Driver, Vehicle } from '../../core/management/management.models';
import { OperationRealtimeService } from '../../core/operations/operation-realtime.service';
import {
  ASSIGNMENT_STATUSES,
  Assignment,
  AssignmentPayload,
  AssignmentStatus,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';

@Component({
  selector: 'rf-assignments-page',
  imports: [ReactiveFormsModule],
  templateUrl: './assignments.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignmentsPage {
  private readonly api = inject(OperationsApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly realtime = inject(OperationRealtimeService);
  private readonly destroyRef = inject(DestroyRef);

  readonly statuses = ASSIGNMENT_STATUSES;
  readonly assignments = signal<readonly Assignment[]>([]);
  readonly drivers = signal<readonly Driver[]>([]);
  readonly vehicles = signal<readonly Vehicle[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly actionId = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly selectedStatus = signal<AssignmentStatus | ''>('');
  readonly editing = signal<Assignment | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    driverId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    vehicleId: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    originText: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(250)],
    }),
    destinationText: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(250)],
    }),
    scheduledAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    scheduledEndAt: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    notes: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(500)] }),
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
      .listAssignments({
        page,
        size: 20,
        sort: 'scheduledAt,desc',
        status: this.selectedStatus() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.assignments.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar las asignaciones.')),
      });
  }

  applyStatus(status: string): void {
    this.selectedStatus.set(this.isAssignmentStatus(status) ? status : '');
    this.load(0);
  }

  submit(): void {
    this.error.set(null);
    this.notice.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const payload = this.payloadFromForm();
    if (payload === null) {
      this.error.set({ message: 'Las fechas de programación no son válidas.', fieldErrors: [] });
      return;
    }

    const current = this.editing();
    this.saving.set(true);
    const request = current === null
      ? this.api.createAssignment(payload, this.idempotencyKey())
      : this.api.updateAssignment(current.id, { ...payload, version: current.version });
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Asignación programada en estado SCHEDULED.' : 'Asignación actualizada.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar la asignación.')),
    });
  }

  startEdit(assignment: Assignment): void {
    if (!this.canEdit(assignment)) {
      return;
    }
    this.editing.set(assignment);
    this.form.reset({
      driverId: assignment.driverId,
      vehicleId: assignment.vehicleId,
      originText: assignment.originText,
      destinationText: assignment.destinationText,
      scheduledAt: this.toDateTimeLocal(assignment.scheduledAt),
      scheduledEndAt: this.toDateTimeLocal(assignment.scheduledEndAt),
      notes: assignment.notes ?? '',
    });
  }

  cancelEdit(): void {
    this.resetForm();
  }

  reserve(assignment: Assignment): void {
    this.runAction(
      assignment,
      'reservar',
      this.api.reserveAssignment(assignment.id, { version: assignment.version }),
      'Conductor reservado para la asignación.',
    );
  }

  start(assignment: Assignment): void {
    this.runAction(
      assignment,
      'iniciar',
      this.api.startAssignment(assignment.id, { version: assignment.version }),
      'Servicio iniciado; conductor y vehículo quedaron EN_SERVICIO.',
    );
  }

  complete(assignment: Assignment): void {
    this.runAction(
      assignment,
      'completar',
      this.api.completeAssignment(assignment.id, { version: assignment.version }),
      'Servicio completado; conductor y vehículo volvieron a DISPONIBLE.',
    );
  }

  cancel(assignment: Assignment): void {
    const reason = globalThis.prompt('Indica el motivo de cancelación (máximo 300 caracteres):');
    if (reason === null || reason.trim().length === 0) {
      return;
    }
    this.runAction(
      assignment,
      'cancelar',
      this.api.cancelAssignment(assignment.id, { version: assignment.version, reason: reason.trim() }),
      'Asignación cancelada y recursos liberados cuando correspondía.',
    );
  }

  canEdit(assignment: Assignment): boolean {
    return assignment.status === 'SCHEDULED' && assignment.reservedAt === null;
  }

  canReserve(assignment: Assignment): boolean {
    return assignment.status === 'SCHEDULED' && assignment.reservedAt === null;
  }

  canStart(assignment: Assignment): boolean {
    return assignment.status === 'SCHEDULED' && assignment.reservedAt !== null;
  }

  canComplete(assignment: Assignment): boolean {
    return assignment.status === 'EN_SERVICIO';
  }

  canCancel(assignment: Assignment): boolean {
    return assignment.status === 'SCHEDULED' || assignment.status === 'EN_SERVICIO';
  }

  statusLabel(status: AssignmentStatus): string {
    return ({
      SCHEDULED: 'Programada',
      EN_SERVICIO: 'En servicio',
      COMPLETED: 'Completada',
      CANCELLED: 'Cancelada',
    } as Record<AssignmentStatus, string>)[status];
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

  private runAction(
    assignment: Assignment,
    action: string,
    request: ReturnType<OperationsApiService['reserveAssignment']>,
    notice: string,
  ): void {
    if (!globalThis.confirm(`¿Deseas ${action} esta asignación?`)) {
      return;
    }
    this.actionId.set(assignment.id);
    this.error.set(null);
    request.pipe(finalize(() => this.actionId.set(null))).subscribe({
      next: () => {
        this.notice.set(notice);
        this.load();
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, `No se pudo ${action} la asignación.`)),
    });
  }

  private loadResources(): void {
    this.managementApi.listDrivers({ page: 0, size: 100, sort: 'fullName,asc', active: true }).subscribe({
      next: (response) => this.drivers.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los conductores.')),
    });
    this.managementApi.listVehicles({ page: 0, size: 100, sort: 'plate,asc', active: true }).subscribe({
      next: (response) => this.vehicles.set(response.items),
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los vehículos.')),
    });
  }

  private payloadFromForm(): AssignmentPayload | null {
    const value = this.form.getRawValue();
    const scheduledAt = this.toIso(value.scheduledAt);
    const scheduledEndAt = this.toIso(value.scheduledEndAt);
    if (scheduledAt === null || scheduledEndAt === null || scheduledEndAt <= scheduledAt) {
      return null;
    }
    return {
      driverId: value.driverId,
      vehicleId: value.vehicleId,
      originText: value.originText.trim(),
      destinationText: value.destinationText.trim(),
      scheduledAt,
      scheduledEndAt,
      notes: value.notes.trim() || undefined,
    };
  }

  private toIso(value: string): string | null {
    const timestamp = Date.parse(value);
    return Number.isNaN(timestamp) ? null : new Date(timestamp).toISOString();
  }

  private toDateTimeLocal(value: string): string {
    const date = new Date(value);
    const pad = (number: number) => number.toString().padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
  }

  private idempotencyKey(): string {
    return typeof globalThis.crypto?.randomUUID === 'function'
      ? globalThis.crypto.randomUUID()
      : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
  }

  private isAssignmentStatus(value: string): value is AssignmentStatus {
    return (ASSIGNMENT_STATUSES as readonly string[]).includes(value);
  }

  private resetForm(): void {
    this.editing.set(null);
    this.form.reset({
      driverId: '',
      vehicleId: '',
      originText: '',
      destinationText: '',
      scheduledAt: '',
      scheduledEndAt: '',
      notes: '',
    });
  }
}
