import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  AdministrativeDriverStatus,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import {
  DRIVER_STATUSES,
  Driver,
  DriverDetail,
  DriverPayload,
  DriverStatus,
  ManagedUser,
  TransportGroup,
  Vehicle,
} from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

type PendingDriverAction =
  | { readonly kind: 'active'; readonly driver: Driver }
  | { readonly kind: 'availability'; readonly driver: Driver; readonly status: AdministrativeDriverStatus }
  | { readonly kind: 'unlink'; readonly vehicleId: string; readonly plate: string };

@Component({
  selector: 'rf-drivers-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './drivers.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DriversPage {
  private readonly api = inject(ManagementApiService);
  private readonly operationsApi = inject(OperationsApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);

  readonly canManage = computed(() =>
    this.session.hasAnyRole(['ADMIN']),
  );
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly statuses = DRIVER_STATUSES;
  readonly administrativeStatuses: readonly AdministrativeDriverStatus[] = [
    'DISPONIBLE',
    'DESCANSO',
    'NO_DISPONIBLE',
  ];
  readonly drivers = signal<readonly Driver[]>([]);
  readonly groups = signal<readonly TransportGroup[]>([]);
  readonly driverUsers = signal<readonly ManagedUser[]>([]);
  readonly vehicles = signal<readonly Vehicle[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly detailLoading = signal(false);
  readonly linking = signal(false);
  readonly actionInProgress = signal(false);
  readonly changingAvailabilityId = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly selectedGroupId = signal('');
  readonly selectedStatus = signal<DriverStatus | ''>('');
  readonly editing = signal<Driver | null>(null);
  readonly formOpen = signal(false);
  readonly selectedDriver = signal<DriverDetail | null>(null);
  readonly detailOpen = signal(false);
  readonly pendingAction = signal<PendingDriverAction | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    userId: new FormControl('', { nonNullable: true }),
    groupId: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(30)],
    }),
    documentType: new FormControl('DNI', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(20)],
    }),
    documentNumber: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(30)],
    }),
    licenseNumber: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(40)],
    }),
  });
  readonly vehicleControl = new FormControl('', {
    nonNullable: true,
    validators: [Validators.required],
  });

  constructor() {
    this.load();
    this.loadGroups();
    if (this.canManage()) {
      this.loadDriverUsers();
      this.loadVehicles();
    }
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listDrivers({
        page,
        size: 20,
        sort: 'fullName,asc',
        search: this.search().trim() || undefined,
        groupId: this.selectedGroupId() || undefined,
        status: this.selectedStatus() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.drivers.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los conductores.')),
      });
  }

  applyFilters(search: string, groupId: string, status: string): void {
    this.search.set(search);
    this.selectedGroupId.set(groupId);
    this.selectedStatus.set(this.isDriverStatus(status) ? status : '');
    this.load(0);
  }

  clearFilters(): void {
    this.search.set('');
    this.selectedGroupId.set('');
    this.selectedStatus.set('');
    this.load(0);
  }

  startCreate(): void {
    this.notice.set(null);
    this.error.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
  }

  submit(): void {
    this.notice.set(null);
    this.error.set(null);
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const current = this.editing();
    const payload = this.payloadFromForm();
    this.saving.set(true);
    const request = current === null
      ? this.api.createDriver(payload)
      : this.api.updateDriver(current.id, payload);

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Conductor registrado correctamente.' : 'Conductor actualizado correctamente.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el conductor.')),
    });
  }

  startEdit(driver: Driver): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(driver);
    this.form.reset({
      userId: driver.userId ?? '',
      groupId: driver.groupId,
      fullName: driver.fullName,
      phone: driver.phone ?? '',
      documentType: driver.documentType,
      documentNumber: driver.documentNumber,
      licenseNumber: driver.licenseNumber ?? '',
    });
    this.detailOpen.set(false);
    this.selectedDriver.set(null);
    this.formOpen.set(true);
  }

  cancelEdit(): void {
    this.resetForm();
  }

  closeForm(): void {
    if (!this.saving()) {
      this.resetForm();
    }
  }

  viewDetails(driver: Driver): void {
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    this.error.set(null);
    this.api
      .getDriver(driver.id)
      .pipe(finalize(() => this.detailLoading.set(false)))
      .subscribe({
        next: (detail) => {
          this.selectedDriver.set(detail);
          this.vehicleControl.reset('');
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el detalle del conductor.')),
      });
  }

  closeDetails(): void {
    if (!this.detailLoading() && !this.linking() && !this.actionInProgress()) {
      this.detailOpen.set(false);
      this.selectedDriver.set(null);
      this.pendingAction.set(null);
    }
  }

  toggleActive(driver: Driver): void {
    this.pendingAction.set({ kind: 'active', driver });
  }

  changeAvailability(driver: Driver, status: string): void {
    if (!this.canChangeAvailability(driver) || !this.isAdministrativeStatus(status)) {
      return;
    }
    if (status === driver.availabilityStatus) {
      return;
    }
    this.pendingAction.set({ kind: 'availability', driver, status });
  }

  canChangeAvailability(driver: Driver): boolean {
    return driver.active
      && driver.availabilityStatus !== 'RESERVADO'
      && driver.availabilityStatus !== 'EN_SERVICIO';
  }

  linkVehicle(): void {
    const detail = this.selectedDriver();
    if (detail === null || this.vehicleControl.invalid) {
      this.vehicleControl.markAsTouched();
      return;
    }

    this.linking.set(true);
    this.error.set(null);
    this.api
      .linkVehicle(detail.driver.id, this.vehicleControl.getRawValue())
      .pipe(finalize(() => this.linking.set(false)))
      .subscribe({
        next: (updated) => {
          this.selectedDriver.set(updated);
          this.vehicleControl.reset('');
          this.notice.set('Vehículo vinculado correctamente.');
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo vincular el vehículo.')),
      });
  }

  unlinkVehicle(vehicleId: string, plate: string): void {
    if (this.selectedDriver() !== null) {
      this.pendingAction.set({ kind: 'unlink', vehicleId, plate });
    }
  }

  markPrimary(vehicleId: string): void {
    const detail = this.selectedDriver();
    if (detail === null) {
      return;
    }

    this.error.set(null);
    this.api.markPrimaryVehicle(detail.driver.id, vehicleId).subscribe({
      next: (updated) => {
        this.selectedDriver.set(updated);
        this.notice.set('Vehículo principal actualizado.');
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo definir el vehículo principal.')),
    });
  }

  pendingActionTitle(): string {
    const action = this.pendingAction();
    if (action?.kind === 'active') {
      return action.driver.active ? 'Desactivar conductor' : 'Activar conductor';
    }
    if (action?.kind === 'availability') {
      return 'Cambiar disponibilidad';
    }
    return 'Desvincular vehículo';
  }

  pendingActionMessage(): string {
    const action = this.pendingAction();
    if (action?.kind === 'active') {
      return `${action.driver.active ? 'Se desactivará' : 'Se activará'} a ${action.driver.fullName}. ¿Deseas continuar?`;
    }
    if (action?.kind === 'availability') {
      return `La disponibilidad de ${action.driver.fullName} cambiará a ${this.statusLabel(action.status)}. ¿Deseas continuar?`;
    }
    if (action?.kind === 'unlink') {
      return `Se desvinculará el vehículo ${action.plate}. ¿Deseas continuar?`;
    }
    return '';
  }

  pendingActionLabel(): string {
    if (this.actionInProgress()) {
      return 'Procesando…';
    }
    const action = this.pendingAction();
    if (action?.kind === 'active') {
      return action.driver.active ? 'Desactivar' : 'Activar';
    }
    return action?.kind === 'availability' ? 'Cambiar' : 'Desvincular';
  }

  pendingActionDanger(): boolean {
    const action = this.pendingAction();
    return action?.kind === 'unlink' || (action?.kind === 'active' && action.driver.active);
  }

  cancelPendingAction(): void {
    if (!this.actionInProgress()) {
      this.pendingAction.set(null);
    }
  }

  confirmPendingAction(): void {
    const action = this.pendingAction();
    if (action === null || this.actionInProgress()) {
      return;
    }
    this.actionInProgress.set(true);
    this.error.set(null);

    if (action.kind === 'active') {
      this.api.setDriverActive(action.driver.id, !action.driver.active)
        .pipe(finalize(() => this.actionInProgress.set(false)))
        .subscribe({
          next: () => {
            this.notice.set(`Conductor ${action.driver.active ? 'desactivado' : 'activado'} correctamente.`);
            this.pendingAction.set(null);
            this.load();
            this.detailOpen.set(false);
            this.selectedDriver.set(null);
          },
          error: (error: unknown) => {
            this.pendingAction.set(null);
            this.error.set(this.apiErrors.toUiError(error, 'No se pudo actualizar el estado del conductor.'));
          },
        });
      return;
    }

    if (action.kind === 'availability') {
      this.changingAvailabilityId.set(action.driver.id);
      this.operationsApi.changeDriverAvailability(action.driver.id, action.status)
        .pipe(finalize(() => {
          this.actionInProgress.set(false);
          this.changingAvailabilityId.set(null);
        }))
        .subscribe({
          next: () => {
            this.notice.set('Disponibilidad del conductor actualizada.');
            this.pendingAction.set(null);
            this.load();
          },
          error: (error: unknown) => {
            this.pendingAction.set(null);
            this.error.set(this.apiErrors.toUiError(error, 'No se pudo cambiar la disponibilidad.'));
          },
        });
      return;
    }

    const detail = this.selectedDriver();
    if (detail === null) {
      this.actionInProgress.set(false);
      this.pendingAction.set(null);
      return;
    }
    this.api.unlinkVehicle(detail.driver.id, action.vehicleId)
      .pipe(finalize(() => this.actionInProgress.set(false)))
      .subscribe({
        next: () => {
          this.selectedDriver.set({
            ...detail,
            vehicles: detail.vehicles.filter((vehicle) => vehicle.vehicleId !== action.vehicleId),
          });
          this.pendingAction.set(null);
          this.notice.set('Vehículo desvinculado correctamente.');
        },
        error: (error: unknown) => {
          this.pendingAction.set(null);
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo desvincular el vehículo.'));
        },
      });
  }

  availableDriverUsers(): readonly ManagedUser[] {
    const editingId = this.editing()?.id;
    const assignedUsers = new Set(
      this.drivers()
        .filter((driver) => driver.id !== editingId && driver.userId !== null && driver.userId !== undefined)
        .map((driver) => driver.userId as string),
    );
    return this.driverUsers().filter((user) => !assignedUsers.has(user.id));
  }

  linkableVehicles(): readonly Vehicle[] {
    const linked = new Set(
      this.selectedDriver()?.vehicles.map((vehicle) => vehicle.vehicleId) ?? [],
    );
    return this.vehicles().filter((vehicle) => !linked.has(vehicle.id));
  }

  statusLabel(status: DriverStatus): string {
    return ({
      DISPONIBLE: 'Disponible',
      RESERVADO: 'Reservado',
      EN_SERVICIO: 'En servicio',
      DESCANSO: 'Descanso',
      NO_DISPONIBLE: 'No disponible',
    } as Record<DriverStatus, string>)[status];
  }

  vehicleStatusLabel(status: string): string {
    return ({
      DISPONIBLE: 'Disponible',
      EN_SERVICIO: 'En servicio',
      MANTENIMIENTO: 'Mantenimiento',
      INACTIVO: 'Inactivo',
    } as Record<string, string>)[status] ?? 'Estado no disponible';
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

  private loadGroups(): void {
    this.api
      .listGroups({ page: 0, size: 100, sort: 'name,asc' })
      .subscribe({
        next: (response) => this.groups.set(response.items),
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los grupos.')),
      });
  }

  private loadDriverUsers(): void {
    this.api
      .listUsers({
        page: 0,
        size: 100,
        sort: 'fullName,asc',
        role: 'CONDUCTOR',
        active: true,
      })
      .subscribe({
        next: (response) => this.driverUsers.set(response.items),
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los usuarios conductores.')),
      });
  }

  private loadVehicles(): void {
    this.api
      .listVehicles({ page: 0, size: 100, sort: 'plate,asc', active: true })
      .subscribe({
        next: (response) => this.vehicles.set(response.items),
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los vehículos.')),
      });
  }

  private payloadFromForm(): DriverPayload {
    const value = this.form.getRawValue();
    return {
      userId: value.userId || null,
      groupId: value.groupId,
      fullName: value.fullName.trim(),
      phone: value.phone.trim() || undefined,
      documentType: value.documentType.trim(),
      documentNumber: value.documentNumber.trim(),
      licenseNumber: value.licenseNumber.trim() || undefined,
    };
  }

  private isDriverStatus(value: string): value is DriverStatus {
    return (DRIVER_STATUSES as readonly string[]).includes(value);
  }

  private isAdministrativeStatus(value: string): value is AdministrativeDriverStatus {
    return (this.administrativeStatuses as readonly string[]).includes(value);
  }

  private resetForm(close = true): void {
    this.editing.set(null);
    this.form.reset({
      userId: '',
      groupId: '',
      fullName: '',
      phone: '',
      documentType: 'DNI',
      documentNumber: '',
      licenseNumber: '',
    });
    if (close) {
      this.formOpen.set(false);
    }
  }
}
