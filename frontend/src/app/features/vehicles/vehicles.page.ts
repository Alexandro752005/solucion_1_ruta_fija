import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  VEHICLE_STATUSES,
  Vehicle,
  VehiclePayload,
  VehicleStatus,
} from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

interface PendingVehicleStatus {
  readonly vehicle: Vehicle;
  readonly status: VehicleStatus;
}

@Component({
  selector: 'rf-vehicles-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './vehicles.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VehiclesPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);

  readonly canManage = computed(() =>
    this.session.hasAnyRole(['ADMINISTRADOR']),
  );
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );
  readonly statuses = VEHICLE_STATUSES;
  readonly administrativeStatuses: readonly VehicleStatus[] = [
    'DISPONIBLE',
    'MANTENIMIENTO',
    'INACTIVO',
  ];
  readonly vehicles = signal<readonly Vehicle[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly savingStatusId = signal<string | null>(null);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly selectedStatus = signal<VehicleStatus | ''>('');
  readonly editing = signal<Vehicle | null>(null);
  readonly formOpen = signal(false);
  readonly selectedVehicle = signal<Vehicle | null>(null);
  readonly detailOpen = signal(false);
  readonly pendingStatus = signal<PendingVehicleStatus | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);

  readonly form = new FormGroup({
    plate: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(15)],
    }),
    brand: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(60)],
    }),
    model: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(60)],
    }),
    year: new FormControl<number | null>(null, {
      validators: [Validators.min(1900), Validators.max(2100)],
    }),
    color: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(40)],
    }),
  });

  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api
      .listVehicles({
        page,
        size: 20,
        sort: 'plate,asc',
        search: this.search().trim() || undefined,
        status: this.selectedStatus() || undefined,
      })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (response) => {
          this.vehicles.set(response.items);
          this.page.set(response.page);
          this.totalPages.set(response.totalPages);
          this.totalItems.set(response.totalItems);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los vehículos.')),
      });
  }

  applyFilters(search: string, status: string): void {
    this.search.set(search);
    this.selectedStatus.set(this.isVehicleStatus(status) ? status : '');
    this.load(0);
  }

  clearFilters(): void {
    this.search.set('');
    this.selectedStatus.set('');
    this.load(0);
  }

  startCreate(): void {
    this.notice.set(null);
    this.error.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
  }

  viewDetails(vehicle: Vehicle): void {
    this.selectedVehicle.set(vehicle);
    this.detailOpen.set(true);
  }

  closeDetails(): void {
    if (this.savingStatusId() === null) {
      this.detailOpen.set(false);
      this.selectedVehicle.set(null);
      this.pendingStatus.set(null);
    }
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
      ? this.api.createVehicle(payload)
      : this.api.updateVehicle(current.id, payload);

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Vehículo registrado correctamente.' : 'Vehículo actualizado correctamente.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el vehículo.')),
    });
  }

  startEdit(vehicle: Vehicle): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(vehicle);
    this.form.reset({
      plate: vehicle.plate,
      brand: vehicle.brand ?? '',
      model: vehicle.model ?? '',
      year: vehicle.year ?? null,
      color: vehicle.color ?? '',
    });
    this.detailOpen.set(false);
    this.selectedVehicle.set(null);
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

  changeStatus(vehicle: Vehicle, status: string): void {
    if (!this.isAdministrativeStatus(status) || status === vehicle.status) {
      return;
    }
    this.pendingStatus.set({ vehicle, status });
  }

  cancelStatusChange(): void {
    if (this.savingStatusId() === null) {
      this.pendingStatus.set(null);
    }
  }

  confirmStatusChange(): void {
    const pending = this.pendingStatus();
    if (pending === null || this.savingStatusId() !== null) {
      return;
    }
    this.error.set(null);
    this.savingStatusId.set(pending.vehicle.id);
    this.api
      .setVehicleStatus(pending.vehicle.id, pending.status)
      .pipe(finalize(() => this.savingStatusId.set(null)))
      .subscribe({
        next: () => {
          this.notice.set('Estado del vehículo actualizado correctamente.');
          this.pendingStatus.set(null);
          this.detailOpen.set(false);
          this.selectedVehicle.set(null);
          this.load();
        },
        error: (error: unknown) => {
          this.pendingStatus.set(null);
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo actualizar el estado del vehículo.'));
        },
      });
  }

  statusLabel(status: VehicleStatus): string {
    return ({
      DISPONIBLE: 'Disponible',
      EN_SERVICIO: 'En servicio',
      MANTENIMIENTO: 'Mantenimiento',
      INACTIVO: 'Inactivo',
    } as Record<VehicleStatus, string>)[status];
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

  private payloadFromForm(): VehiclePayload {
    const value = this.form.getRawValue();
    return {
      plate: value.plate.trim(),
      brand: value.brand.trim() || undefined,
      model: value.model.trim() || undefined,
      year: value.year,
      color: value.color.trim() || undefined,
    };
  }

  private isVehicleStatus(value: string): value is VehicleStatus {
    return (VEHICLE_STATUSES as readonly string[]).includes(value);
  }

  private isAdministrativeStatus(value: string): value is VehicleStatus {
    return this.administrativeStatuses.includes(value as VehicleStatus);
  }

  private resetForm(close = true): void {
    this.editing.set(null);
    this.form.reset({ plate: '', brand: '', model: '', year: null, color: '' });
    if (close) {
      this.formOpen.set(false);
    }
  }
}
