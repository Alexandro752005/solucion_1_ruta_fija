import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
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
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

type AssignmentActionKind = 'reserve' | 'start' | 'complete';

interface PendingAssignmentAction {
  readonly assignment: Assignment;
  readonly kind: AssignmentActionKind;
}

@Component({
  selector: 'rf-assignments-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './assignments.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AssignmentsPage {
  private readonly api = inject(OperationsApiService);
  private readonly managementApi = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
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
  readonly selectedDriverId = signal('');
  readonly selectedVehicleId = signal('');
  readonly fromFilter = signal('');
  readonly toFilter = signal('');
  readonly editing = signal<Assignment | null>(null);
  readonly formOpen = signal(false);
  readonly selectedAssignment = signal<Assignment | null>(null);
  readonly detailOpen = signal(false);
  readonly pendingAction = signal<PendingAssignmentAction | null>(null);
  readonly cancellationTarget = signal<Assignment | null>(null);
  readonly linkedVehicleIds = signal<ReadonlySet<string> | null>(null);
  readonly loadingEligibleVehicles = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly eligibleVehicles = computed(() => {
    const linked = this.linkedVehicleIds();
    return linked === null ? [] : this.vehicles().filter((vehicle) => linked.has(vehicle.id));
  });

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
  readonly cancellationForm = new FormGroup({
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(300)],
    }),
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
        driverId: this.selectedDriverId() || undefined,
        vehicleId: this.selectedVehicleId() || undefined,
        from: this.filterInstant(this.fromFilter(), false),
        to: this.filterInstant(this.toFilter(), true),
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

  applyFilters(status: string, driverId: string, vehicleId: string, from: string, to: string): void {
    this.selectedStatus.set(this.isAssignmentStatus(status) ? status : '');
    this.selectedDriverId.set(driverId);
    this.selectedVehicleId.set(vehicleId);
    this.fromFilter.set(from);
    this.toFilter.set(to);
    this.load(0);
  }

  clearFilters(): void {
    this.selectedStatus.set('');
    this.selectedDriverId.set('');
    this.selectedVehicleId.set('');
    this.fromFilter.set('');
    this.toFilter.set('');
    this.load(0);
  }

  startCreate(): void {
    this.error.set(null);
    this.notice.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
  }

  viewDetails(assignment: Assignment): void {
    this.selectedAssignment.set(assignment);
    this.detailOpen.set(true);
  }

  closeDetails(): void {
    if (this.actionId() === null) {
      this.detailOpen.set(false);
      this.selectedAssignment.set(null);
      this.pendingAction.set(null);
    }
  }

  driverChanged(driverId: string): void {
    this.form.controls.vehicleId.setValue('');
    this.loadEligibleVehicles(driverId);
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
        this.notice.set(current === null ? 'Asignación programada correctamente.' : 'Asignación actualizada correctamente.');
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
    this.detailOpen.set(false);
    this.selectedAssignment.set(null);
    this.formOpen.set(true);
    this.loadEligibleVehicles(assignment.driverId, assignment.vehicleId);
  }

  cancelEdit(): void {
    this.resetForm();
  }

  closeForm(): void {
    if (!this.saving()) {
      this.resetForm();
    }
  }

  reserve(assignment: Assignment): void {
    if (this.canReserve(assignment)) {
      this.pendingAction.set({ assignment, kind: 'reserve' });
    }
  }

  start(assignment: Assignment): void {
    if (this.canStart(assignment)) {
      this.pendingAction.set({ assignment, kind: 'start' });
    }
  }

  complete(assignment: Assignment): void {
    if (this.canComplete(assignment)) {
      this.pendingAction.set({ assignment, kind: 'complete' });
    }
  }

  cancel(assignment: Assignment): void {
    if (!this.canCancel(assignment)) {
      return;
    }
    this.cancellationForm.reset({ reason: '' });
    this.cancellationTarget.set(assignment);
  }

  closeCancellation(): void {
    if (this.actionId() === null) {
      this.cancellationTarget.set(null);
      this.cancellationForm.reset({ reason: '' });
    }
  }

  submitCancellation(): void {
    const assignment = this.cancellationTarget();
    if (assignment === null || this.cancellationForm.invalid || this.actionId() !== null) {
      this.cancellationForm.markAllAsTouched();
      return;
    }
    const reason = this.cancellationForm.controls.reason.getRawValue().trim();
    this.actionId.set(assignment.id);
    this.error.set(null);
    this.api.cancelAssignment(assignment.id, { version: assignment.version, reason })
      .pipe(finalize(() => this.actionId.set(null)))
      .subscribe({
        next: () => {
          this.notice.set('Asignación cancelada correctamente.');
          this.cancellationTarget.set(null);
          this.detailOpen.set(false);
          this.selectedAssignment.set(null);
          this.load();
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo cancelar la asignación.')),
      });
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

  pendingActionTitle(): string {
    return ({ reserve: 'Reservar recursos', start: 'Iniciar servicio', complete: 'Completar servicio' } as Record<AssignmentActionKind, string>)[this.pendingAction()?.kind ?? 'reserve'];
  }

  pendingActionMessage(): string {
    const kind = this.pendingAction()?.kind;
    if (kind === 'reserve') return 'Se reservará el conductor para esta programación. ¿Deseas continuar?';
    if (kind === 'start') return 'El conductor y el vehículo pasarán a servicio. ¿Deseas iniciar?';
    return 'El servicio se marcará como completado y los recursos serán liberados. ¿Deseas continuar?';
  }

  pendingActionLabel(): string {
    if (this.actionId() !== null) return 'Procesando…';
    return ({ reserve: 'Reservar', start: 'Iniciar', complete: 'Completar' } as Record<AssignmentActionKind, string>)[this.pendingAction()?.kind ?? 'reserve'];
  }

  cancelPendingAction(): void {
    if (this.actionId() === null) {
      this.pendingAction.set(null);
    }
  }

  confirmPendingAction(): void {
    const pending = this.pendingAction();
    if (pending === null || this.actionId() !== null) {
      return;
    }
    const { assignment, kind } = pending;
    const request = kind === 'reserve'
      ? this.api.reserveAssignment(assignment.id, { version: assignment.version })
      : kind === 'start'
        ? this.api.startAssignment(assignment.id, { version: assignment.version })
        : this.api.completeAssignment(assignment.id, { version: assignment.version });
    const notice = kind === 'reserve'
      ? 'Conductor reservado para la asignación.'
      : kind === 'start'
        ? 'Servicio iniciado correctamente.'
        : 'Servicio completado y recursos liberados.';

    this.actionId.set(assignment.id);
    this.error.set(null);
    request.pipe(finalize(() => this.actionId.set(null))).subscribe({
      next: () => {
        this.notice.set(notice);
        this.pendingAction.set(null);
        this.detailOpen.set(false);
        this.selectedAssignment.set(null);
        this.load();
      },
      error: (error: unknown) => {
        this.pendingAction.set(null);
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo actualizar la asignación.'));
      },
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

  private loadEligibleVehicles(driverId: string, keepVehicleId?: string): void {
    this.linkedVehicleIds.set(null);
    if (!driverId) {
      return;
    }
    this.loadingEligibleVehicles.set(true);
    this.managementApi.getDriver(driverId)
      .pipe(finalize(() => this.loadingEligibleVehicles.set(false)))
      .subscribe({
        next: (detail) => {
          this.linkedVehicleIds.set(new Set(detail.vehicles.map((vehicle) => vehicle.vehicleId)));
          if (keepVehicleId && detail.vehicles.some((vehicle) => vehicle.vehicleId === keepVehicleId)) {
            this.form.controls.vehicleId.setValue(keepVehicleId);
          }
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los vehículos vinculados.')),
      });
  }

  driverStatusLabel(status: string): string {
    return ({ DISPONIBLE: 'Disponible', RESERVADO: 'Reservado', EN_SERVICIO: 'En servicio', DESCANSO: 'Descanso', NO_DISPONIBLE: 'No disponible' } as Record<string, string>)[status] ?? 'No disponible';
  }

  vehicleStatusLabel(status: string): string {
    return ({ DISPONIBLE: 'Disponible', EN_SERVICIO: 'En servicio', MANTENIMIENTO: 'Mantenimiento', INACTIVO: 'Inactivo' } as Record<string, string>)[status] ?? 'No disponible';
  }

  private filterInstant(value: string, endOfDay: boolean): string | undefined {
    if (!value) return undefined;
    const timestamp = Date.parse(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00.000'}`);
    return Number.isNaN(timestamp) ? undefined : new Date(timestamp).toISOString();
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

  private resetForm(close = true): void {
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
    this.linkedVehicleIds.set(null);
    if (close) {
      this.formOpen.set(false);
    }
  }
}
