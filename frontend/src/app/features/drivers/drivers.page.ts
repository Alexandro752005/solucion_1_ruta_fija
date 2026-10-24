import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  AdministrativeDriverStatus,
} from '../../core/operations/operations.models';
import { OperationsApiService } from '../../core/operations/operations-api.service';
import { OnboardingApiService } from '../../core/onboarding/onboarding-api.service';
import { ExistingDriverRegularizationCommand } from '../../core/onboarding/onboarding.models';
import {
  DRIVER_STATUSES,
  Driver,
  DriverDetail,
  DriverIncorporationStatus,
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
  imports: [ReactiveFormsModule, RouterLink, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './drivers.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DriversPage implements OnDestroy {
  private readonly api = inject(ManagementApiService);
  private readonly operationsApi = inject(OperationsApiService);
  private readonly onboardingApi = inject(OnboardingApiService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly session = inject(AuthSessionService);
  private readonly destroyRef = inject(DestroyRef);
  private detailRequestSequence = 0;
  private incorporationRequestSequence = 0;
  private regularizationAttempt: { readonly driverId: string; readonly userId: string; readonly body: ExistingDriverRegularizationCommand } | null = null;

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
  readonly incorporationStatus = signal<DriverIncorporationStatus | null>(null);
  readonly incorporationLoading = signal(false);
  readonly incorporationError = signal<string | null>(null);
  readonly pendingAction = signal<PendingDriverAction | null>(null);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly regularizationTarget = signal<{ readonly driverId: string; readonly userId: string } | null>(null);
  readonly regularizationSaving = signal(false);
  readonly regularizationUnconfirmed = signal(false);
  readonly regularizationError = signal<string | null>(null);
  readonly regularizationRequestId = signal<string | null>(null);
  readonly regularizationForm = new FormGroup({
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
    identityPreservedConfirmed: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
  });
  readonly identityReviewTarget = signal<{ readonly driverId: string; readonly userId: string } | null>(null);
  readonly identityReviewSaving = signal(false);
  readonly identityReviewMustRefresh = signal(false);
  readonly identityReviewMode = signal<'review' | 'correction'>('review');
  readonly identityCorrectionForm = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    dataEntryErrorConfirmed: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
    samePersonConfirmed: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
  });
  readonly identityReviewForm = new FormGroup({
    documentNumber: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[0-9]{8}$/)] }),
    method: new FormControl<'IN_PERSON' | 'PRIOR_SECURE_CONTACT'>('IN_PERSON', { nonNullable: true }),
    reference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(160)] }),
    originalReviewed: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
    identityConfirmed: new FormControl(false, { nonNullable: true, validators: [Validators.requiredTrue] }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(500)] }),
  });

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
          this.identityReviewMustRefresh.set(false);
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los conductores.')),
      });
  }

  startIdentityReview(driver: Driver): void {
    if (!this.canManage() || !driver.userId || this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving() || this.identityReviewMustRefresh()
        || this.loading() || this.linking() || this.actionInProgress() || this.detailLoading()) return;
    this.clearIdentityReview();
    this.detailOpen.set(false);
    this.detailRequestSequence++;
    this.clearIncorporationStatus();
    this.selectedDriver.set(null);
    this.pendingAction.set(null);
    this.error.set(null);
    this.notice.set(null);
    // Deliberately do not read/prefill the legacy DNI or fetch personal data.
    this.identityReviewTarget.set({ driverId: driver.id, userId: driver.userId });
  }

  startIdentityCorrection(driver: Driver): void {
    if (this.identityReviewTarget()) return;
    this.startIdentityReview(driver);
    if (this.identityReviewTarget()?.driverId === driver.id) this.identityReviewMode.set('correction');
  }

  startRegularization(driver: Driver): void {
    if (!this.canManage() || !driver.userId || this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()
        || this.detailLoading() || this.actionInProgress() || this.linking()) return;
    const incorporation = this.incorporationStatus();
    if (incorporation?.driverId === driver.id && incorporation.requestId) return;
    this.clearRegularization();
    this.clearIdentityReview();
    this.detailOpen.set(false);
    this.detailRequestSequence++;
    this.clearIncorporationStatus();
    this.selectedDriver.set(null);
    this.pendingAction.set(null);
    this.notice.set(null);
    this.error.set(null);
    this.regularizationRequestId.set(null);
    this.regularizationTarget.set({ driverId: driver.id, userId: driver.userId });
  }

  closeRegularization(): void {
    if (!this.regularizationSaving()) this.clearRegularization();
  }

  submitRegularization(): void {
    const target = this.regularizationTarget();
    if (!target || !this.canManage() || this.regularizationSaving() || this.regularizationUnconfirmed()) return;
    if (this.regularizationForm.invalid || !this.regularizationForm.controls.reason.value.trim()) {
      this.regularizationForm.markAllAsTouched();
      return;
    }
    this.regularizationAttempt = { ...target, body: {
      operationKey: crypto.randomUUID(), reason: this.regularizationForm.controls.reason.value.trim(),
      identityPreservedConfirmed: true,
    } };
    this.regularizationForm.reset({ reason: '', identityPreservedConfirmed: false });
    this.sendRegularization();
  }

  retryRegularization(): void {
    if (!this.regularizationUnconfirmed() || this.regularizationSaving() || !this.canManage()) return;
    this.sendRegularization();
  }

  private sendRegularization(): void {
    const attempt = this.regularizationAttempt;
    if (!attempt || !this.regularizationTarget()) return;
    this.regularizationSaving.set(true);
    this.regularizationError.set(null);
    this.onboardingApi.regularizeExistingDriver(attempt.driverId, attempt.body)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.regularizationSaving.set(false))).subscribe({
        next: (result) => {
          if (!result.requestId || result.existingDriverId !== attempt.driverId || result.existingUserId !== attempt.userId) {
            this.regularizationFailed();
            return;
          }
          this.regularizationRequestId.set(result.requestId);
          this.clearRegularization();
          this.notice.set('El servidor confirmó el expediente para la cuenta existente. Consulta su revisión y aprobación personal en Solicitudes. Abrirlo no aprueba documentos ni habilita el acceso móvil o la conducción.');
        },
        error: () => this.regularizationFailed(),
      });
  }

  private regularizationFailed(): void {
    this.regularizationUnconfirmed.set(true);
    this.regularizationError.set('No se confirmó la apertura. Requiere una cuenta CONDUCTOR vinculada y una revisión de identidad que coincida con su ficha; una discrepancia requiere revisión y corrección autorizada. Consulta Solicitudes; si la respuesta se perdió, puedes consultar de nuevo con la misma operación. No se aprobaron documentos automáticamente.');
  }

  private clearRegularization(): void {
    this.regularizationTarget.set(null);
    this.regularizationAttempt = null;
    this.regularizationUnconfirmed.set(false);
    this.regularizationError.set(null);
    this.regularizationForm.reset({ reason: '', identityPreservedConfirmed: false });
  }

  closeIdentityReview(): void {
    if (!this.identityReviewSaving()) this.clearIdentityReview();
  }

  submitIdentityReview(): void {
    const target = this.identityReviewTarget();
    if (!target || !this.canManage() || this.identityReviewSaving() || this.identityReviewMustRefresh()) return;
    const correction = this.identityReviewMode() === 'correction';
    if (this.identityReviewForm.invalid || (correction && this.identityCorrectionForm.invalid)) {
      this.identityReviewForm.markAllAsTouched();
      if (correction) this.identityCorrectionForm.markAllAsTouched();
      return;
    }
    const value = this.identityReviewForm.getRawValue();
    const review = { identityReview: {
      documentType: 'DNI', documentNumber: value.documentNumber, method: value.method,
      reference: value.reference.trim(), originalReviewed: true, identityConfirmed: true,
    }, reason: value.reason.trim() } as const;
    const command = correction ? this.api.correctDriverIdentity(target.driverId, {
      ...review, currentPassword: this.identityCorrectionForm.controls.currentPassword.value,
      operationKey: crypto.randomUUID(), dataEntryErrorConfirmed: true, samePersonConfirmed: true,
    }) : this.api.reviewDriverIdentity(target.driverId, review);
    this.identityReviewSaving.set(true);
    this.error.set(null);
    this.notice.set(null);
    this.clearIdentityReview();
    command.pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.identityReviewSaving.set(false))).subscribe({
      next: (status) => {
        if (status.userId !== target.userId || status.identityVerified !== true) {
          if (correction) this.identityCorrectionUnconfirmed(undefined);
          else this.identityReviewUnconfirmed();
          return;
        }
        this.notice.set(correction
          ? 'El servidor confirmó la rectificación del DNI ingresado por error, conservando la cuenta, ficha, historial y contraseñas. Consulta el detalle actualizado para abrir el expediente de regularización. La rectificación no aprueba documentos ni habilita la conducción.'
          : 'El servidor confirmó la revisión de identidad de la cuenta. Esto no aprueba el expediente documental ni habilita la operación; consulta el estado actualizado del conductor. Si se ingresó un DNI por error, utiliza su rectificación restringida.');
        this.load();
      },
      error: (error: unknown) => correction ? this.identityCorrectionUnconfirmed(error) : this.identityReviewUnconfirmed(),
    });
  }

  private identityCorrectionUnconfirmed(error: unknown): void {
    this.clearIdentityReview();
    this.identityReviewMustRefresh.set(true);
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    const message = ({
      400: 'No se confirmó la rectificación. Revisa el DNI de ocho dígitos, los campos y las confirmaciones personales.',
      401: 'No se confirmó la reautenticación. Comprueba tu sesión y la contraseña actual de ADMIN antes de volver a intentarlo.',
      403: 'No tienes autorización para esta rectificación o no se confirmó la reautenticación de ADMIN.',
      404: 'La cuenta o ficha no está disponible para esta organización.',
      409: 'No se rectificó la identidad: el DNI debe coincidir con la ficha conservada y no debe existir un expediente vinculado. Consulta el estado antes de continuar.',
      429: 'Se alcanzó el límite de intentos. Espera antes de volver a comprobar la operación.',
    } as Record<number, string>)[status] ?? 'No se recibió confirmación de la rectificación. La respuesta pudo perderse; consulta el estado antes de realizar otra acción.';
    this.error.set({ message: `${message} Consulta los conductores antes de continuar. Los campos de DNI y contraseña se han borrado y la operación no se reintenta automáticamente.`, fieldErrors: [] });
  }

  private identityReviewUnconfirmed(): void {
    this.clearIdentityReview();
    this.identityReviewMustRefresh.set(true);
    // Server error bodies may contain input fields. Only a fixed message is shown.
    this.error.set({ message: 'No se confirmó la revisión. Consulta los conductores antes de continuar; la operación no se reintenta automáticamente.', fieldErrors: [] });
  }

  private clearIdentityReview(): void {
    this.identityReviewTarget.set(null);
    this.identityReviewMode.set('review');
    this.identityCorrectionForm.reset({ currentPassword: '', dataEntryErrorConfirmed: false, samePersonConfirmed: false });
    this.identityReviewForm.reset({ documentNumber: '', method: 'IN_PERSON', reference: '', originalReviewed: false, identityConfirmed: false, reason: '' });
  }

  ngOnDestroy(): void {
    this.detailRequestSequence++;
    this.clearIncorporationStatus();
    this.clearIdentityReview();
    this.clearRegularization();
    this.regularizationRequestId.set(null);
    this.resetForm();
    this.selectedDriver.set(null);
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
    this.informOnboarding();
  }

  submit(): void {
    this.informOnboarding();
  }

  startEdit(driver: Driver): void {
    void driver;
    this.informOnboarding();
  }

  private informOnboarding(): void {
    this.resetForm();
    this.error.set(null);
    this.notice.set('La incorporación y revisión de identidad se realizan desde Solicitudes y su expediente restringido.');
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
    if (this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()) return;
    const requestSequence = ++this.detailRequestSequence;
    this.clearIncorporationStatus();
    this.selectedDriver.set(null);
    this.detailOpen.set(true);
    this.detailLoading.set(true);
    this.error.set(null);
    this.api
      .getDriver(driver.id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (requestSequence === this.detailRequestSequence) this.detailLoading.set(false);
      }))
      .subscribe({
        next: (detail) => {
          if (requestSequence !== this.detailRequestSequence || !this.detailOpen()) return;
          this.selectedDriver.set(detail);
          this.vehicleControl.reset('');
          this.loadIncorporationStatus();
        },
        error: (error: unknown) => {
          if (requestSequence === this.detailRequestSequence)
            this.error.set(this.apiErrors.toUiError(error, 'No se pudo cargar el detalle del conductor.'));
        },
      });
  }

  closeDetails(): void {
    if (!this.detailLoading() && !this.linking() && !this.actionInProgress()) {
      this.detailOpen.set(false);
      this.detailRequestSequence++;
      this.clearIncorporationStatus();
      this.selectedDriver.set(null);
      this.pendingAction.set(null);
    }
  }

  toggleActive(driver: Driver): void {
    if (this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()) return;
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
    // Availability is declared by CONDUCTOR. ADMIN uses the restricted,
    // motivated qualification/suspension flow instead of impersonating it.
    return false;
  }

  linkVehicle(): void {
    if (this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()) return;
    const detail = this.selectedDriver();
    if (detail === null || this.vehicleControl.invalid) {
      this.vehicleControl.markAsTouched();
      return;
    }
    if (detail.vehicles.length !== 0) {
      this.notice.set('Solo se permite una unidad vinculada vigente. La sustitución requiere desvinculación autorizada; no se reemplaza automáticamente.');
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
          this.loadIncorporationStatus();
          this.vehicleControl.reset('');
          this.notice.set('Vehículo vinculado correctamente.');
        },
        error: (error: unknown) =>
          this.error.set(this.apiErrors.toUiError(error, 'No se pudo vincular el vehículo.')),
      });
  }

  unlinkVehicle(vehicleId: string, plate: string): void {
    if (this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()) return;
    if (this.selectedDriver() !== null) {
      this.pendingAction.set({ kind: 'unlink', vehicleId, plate });
    }
  }

  markPrimary(vehicleId: string): void {
    if (this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()) return;
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
      return `${action.driver.active ? 'Se desactivará' : 'Se activará'} al conductor ${action.driver.id}. ¿Deseas continuar?`;
    }
    if (action?.kind === 'availability') {
      return `La disponibilidad del conductor ${action.driver.id} cambiará a ${this.statusLabel(action.status)}. ¿Deseas continuar?`;
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
    if (this.regularizationTarget() || this.regularizationSaving() || this.identityReviewTarget() || this.identityReviewSaving()) return;
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
            this.detailRequestSequence++;
            this.clearIncorporationStatus();
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
          this.loadIncorporationStatus();
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
    if ((this.selectedDriver()?.vehicles.length ?? 0) !== 0) return [];
    const linked = new Set(
      this.selectedDriver()?.vehicles.map((vehicle) => vehicle.vehicleId) ?? [],
    );
    return this.vehicles().filter((vehicle) => !linked.has(vehicle.id));
  }

  loadIncorporationStatus(): void {
    const driverId = this.selectedDriver()?.driver.id;
    if (!driverId || !this.detailOpen()) return;
    const requestSequence = ++this.incorporationRequestSequence;
    this.incorporationStatus.set(null);
    this.incorporationError.set(null);
    this.incorporationLoading.set(true);
    this.api.getDriverIncorporationStatus(driverId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => {
        if (requestSequence === this.incorporationRequestSequence) this.incorporationLoading.set(false);
      }))
      .subscribe({
        next: (status) => {
          if (requestSequence !== this.incorporationRequestSequence || !this.detailOpen()
              || this.selectedDriver()?.driver.id !== driverId) return;
          if (status.driverId !== driverId) {
            this.incorporationError.set('No se pudo confirmar el estado de este conductor. Consulta de nuevo.');
            return;
          }
          this.incorporationStatus.set(status);
        },
        error: () => {
          if (requestSequence === this.incorporationRequestSequence && this.detailOpen())
            this.incorporationError.set('No se pudo consultar la incorporación. Esto no confirma aprobación ni habilitación.');
        },
      });
  }

  incorporationBlockerLabel(code: string): string {
    return ({
      INCORPORATION_REQUIRES_REVIEW: 'Debe revisarse la correspondencia con la solicitud.',
      PERSONAL_APPROVAL_MISSING: 'Falta la aprobación personal.',
      ACCOUNT_NOT_PROVISIONED: 'Falta completar la cuenta y ficha vinculadas.',
      ACCOUNT_DISABLED: 'La cuenta está deshabilitada.',
      IDENTITY_NOT_REVIEWED: 'La identidad requiere revisión restringida.',
      GROUP_INACTIVE: 'El grupo no está activo.',
      PERSONAL_DOCUMENTS_EXPIRED: 'Hay documentación personal vencida.',
      PERSONAL_DOCUMENTS_REVIEW_REQUIRED: 'La documentación personal requiere revisión.',
      DRIVER_DISABLED: 'La ficha no está habilitada para operar.',
      UNIT_LINK_MISSING: 'No hay unidad vinculada.',
      UNIT_LINK_CARDINALITY_CONFLICT: 'Existen varios vínculos activos; requiere revisión, sin corrección automática.',
      UNIT_AND_SERVICE_REVIEW_PENDING: 'Falta completar la revisión de unidad y servicio antes de operar.',
    } as Record<string, string>)[code] ?? 'Existe una condición pendiente de revisión.';
  }

  personalDocumentsLabel(state: string): string {
    return ({ CURRENT: 'Revisión registrada y vigente', EXPIRED: 'Vencida', UNKNOWN: 'Sin confirmar',
      REVIEW_REQUIRED: 'Requiere revisión' } as Record<string, string>)[state] ?? 'Sin confirmar';
  }

  private clearIncorporationStatus(): void {
    this.incorporationRequestSequence++;
    this.incorporationStatus.set(null);
    this.incorporationLoading.set(false);
    this.incorporationError.set(null);
  }

  statusLabel(status: DriverStatus): string {
    return ({
      DISPONIBLE: 'Disponible',
      RESERVADO: 'Reservado',
      EN_SERVICIO: 'En servicio',
      ATENCION: 'Atención segura pendiente',
      DESCANSO: 'Descanso',
      NO_DISPONIBLE: 'No disponible',
    } as Record<DriverStatus, string>)[status];
  }

  vehicleStatusLabel(status: string): string {
    return ({
      DISPONIBLE: 'Disponible',
      EN_SERVICIO: 'En servicio',
      ATENCION: 'Atención segura pendiente',
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
