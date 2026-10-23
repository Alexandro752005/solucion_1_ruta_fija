import { ChangeDetectionStrategy, Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import {
  ConductorRecoveryStartPayload,
  ConductorRecoveryStarted,
  ConductorRecoveryStatus,
  ManagedUser,
  RecoveryEvidenceMethod,
} from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

type OrganizationUserRole = 'CONDUCTOR';

@Component({
  selector: 'rf-users-page',
  imports: [ReactiveFormsModule, RouterLink, DatePipe, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './users.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage implements OnDestroy {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  readonly session = inject(AuthSessionService);

  readonly roles: readonly OrganizationUserRole[] = ['CONDUCTOR'];
  readonly users = signal<readonly ManagedUser[]>([]);
  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly changingStatus = signal(false);
  readonly page = signal(0);
  readonly totalPages = signal(0);
  readonly totalItems = signal(0);
  readonly search = signal('');
  readonly roleFilter = signal('');
  readonly activeFilter = signal<boolean | undefined>(undefined);
  readonly editing = signal<ManagedUser | null>(null);
  readonly formOpen = signal(false);
  readonly pendingStatusUser = signal<ManagedUser | null>(null);
  readonly recoveryTarget = signal<ManagedUser | null>(null);
  readonly recoveryStarted = signal<ConductorRecoveryStarted | null>(null);
  readonly recoveryStatus = signal<ConductorRecoveryStatus | null>(null);
  readonly recoverySaving = signal(false);
  readonly recoveryError = signal<UiError | null>(null);
  readonly pendingRecoveryCancel = signal(false);
  readonly error = signal<UiError | null>(null);
  readonly notice = signal<string | null>(null);
  readonly organizationName = computed(
    () => this.session.user()?.organizationName?.trim() || 'Organizaci\u00f3n asignada',
  );

  readonly form = new FormGroup({
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(180)],
    }),
    password: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.minLength(12), Validators.maxLength(128)],
    }),
    fullName: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(160)],
    }),
    phone: new FormControl('', { nonNullable: true, validators: [Validators.maxLength(30)] }),
    role: new FormControl<OrganizationUserRole>('CONDUCTOR', {
      nonNullable: true,
      validators: [Validators.required],
    }),
  });

  readonly recoveryForm = new FormGroup({
    newEmail: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.email, Validators.maxLength(180)],
    }),
    evidenceMethod: new FormControl<RecoveryEvidenceMethod>('PRIOR_SECURE_CONTACT', {
      nonNullable: true,
      validators: [Validators.required],
    }),
    evidenceReference: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(200)],
    }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(300)],
    }),
    expedienteReviewed: new FormControl(false, {
      nonNullable: true,
      validators: [Validators.requiredTrue],
    }),
    independentEvidenceConfirmed: new FormControl(false, {
      nonNullable: true,
      validators: [Validators.requiredTrue],
    }),
  });

  constructor() {
    this.load();
  }

  load(page = this.page()): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listUsers({
      page,
      size: 20,
      sort: 'fullName,asc',
      search: this.search().trim() || undefined,
      role: this.roleFilter() || undefined,
      active: this.activeFilter(),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (response) => {
        this.users.set(response.items);
        this.page.set(response.page);
        this.totalPages.set(response.totalPages);
        this.totalItems.set(response.totalItems);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudieron cargar los usuarios.')),
    });
  }

  applyFilters(search: string, role: string, active: string): void {
    this.search.set(search);
    this.roleFilter.set(role);
    this.activeFilter.set(active === '' ? undefined : active === 'true');
    this.load(0);
  }

  clearFilters(): void {
    this.search.set('');
    this.roleFilter.set('');
    this.activeFilter.set(undefined);
    this.load(0);
  }

  startCreate(): void {
    this.informOnboarding();
  }

  startEdit(user: ManagedUser): void {
    void user;
    this.informOnboarding();
  }

  closeForm(): void {
    if (!this.saving()) {
      this.resetForm();
    }
  }

  submit(): void {
    this.informOnboarding();
  }

  requestToggleActive(user: ManagedUser): void {
    this.notice.set(null);
    this.error.set(null);
    this.pendingStatusUser.set(user);
  }

  cancelToggleActive(): void {
    if (!this.changingStatus()) {
      this.pendingStatusUser.set(null);
    }
  }

  confirmToggleActive(): void {
    const user = this.pendingStatusUser();
    if (user === null) {
      return;
    }

    this.changingStatus.set(true);
    this.api.setUserActive(user.id, !user.active).pipe(finalize(() => this.changingStatus.set(false))).subscribe({
      next: () => {
        this.notice.set(`Usuario ${user.active ? 'desactivado' : 'activado'} correctamente.`);
        this.pendingStatusUser.set(null);
        this.load();
      },
      error: (error: unknown) => {
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo actualizar el estado del usuario.'));
        this.pendingStatusUser.set(null);
      },
    });
  }

  startRecovery(user: ManagedUser): void {
    if (user.role !== 'CONDUCTOR' || !user.active) return;
    this.recoveryError.set(null);
    this.recoveryStarted.set(null);
    this.recoveryStatus.set(null);
    this.recoveryForm.reset({
      newEmail: '',
      evidenceMethod: 'PRIOR_SECURE_CONTACT',
      evidenceReference: '',
      reason: '',
      expedienteReviewed: false,
      independentEvidenceConfirmed: false,
    });
    this.recoveryTarget.set(user);
  }

  closeRecovery(): void {
    if (this.recoverySaving()) return;
    this.recoveryTarget.set(null);
    this.recoveryStarted.set(null);
    this.recoveryStatus.set(null);
    this.recoveryError.set(null);
    this.pendingRecoveryCancel.set(false);
    this.recoveryForm.reset();
  }

  ngOnDestroy(): void {
    this.recoveryTarget.set(null);
    this.recoveryStarted.set(null);
    this.recoveryStatus.set(null);
    this.recoveryForm.reset();
    this.resetForm();
  }

  private informOnboarding(): void {
    this.resetForm();
    this.error.set(null);
    this.notice.set('La incorporación y revisión de identidad se realizan desde Solicitudes y su expediente restringido.');
  }

  submitRecovery(): void {
    const user = this.recoveryTarget();
    if (!user || this.recoveryStarted() || this.recoveryForm.invalid || this.recoverySaving()) {
      this.recoveryForm.markAllAsTouched();
      return;
    }
    this.recoveryError.set(null);
    const value = this.recoveryForm.getRawValue();
    const payload: ConductorRecoveryStartPayload = {
      newEmail: value.newEmail.trim(),
      evidenceMethod: value.evidenceMethod,
      evidenceReference: value.evidenceReference.trim(),
      reason: value.reason.trim(),
      expedienteReviewed: value.expedienteReviewed,
      independentEvidenceConfirmed: value.independentEvidenceConfirmed,
      passwordResetRequired: true,
    };
    this.recoverySaving.set(true);
    this.api.startConductorRecovery(user.id, payload)
      .pipe(finalize(() => this.recoverySaving.set(false)))
      .subscribe({
        next: (started) => this.recoveryStarted.set(started),
        error: (error: unknown) => this.recoveryError.set(
          this.apiErrors.toUiError(error, 'No se pudo iniciar la recuperación autorizada.'),
        ),
      });
  }

  refreshRecoveryStatus(): void {
    const user = this.recoveryTarget();
    const started = this.recoveryStarted();
    if (!user || !started || this.recoverySaving()) return;
    this.recoverySaving.set(true);
    this.api.getConductorRecovery(user.id, started.recoveryId)
      .pipe(finalize(() => this.recoverySaving.set(false)))
      .subscribe({
        next: (status) => this.recoveryStatus.set(status),
        error: (error: unknown) => this.recoveryError.set(
          this.apiErrors.toUiError(error, 'No se pudo consultar el estado de la recuperación.'),
        ),
      });
  }

  requestRecoveryCancel(): void {
    if (this.recoveryStarted() && this.recoveryStatus()?.status !== 'COMPLETED') {
      this.pendingRecoveryCancel.set(true);
    }
  }

  cancelRecovery(): void {
    const user = this.recoveryTarget();
    const started = this.recoveryStarted();
    if (!user || !started || this.recoverySaving()) return;
    this.recoverySaving.set(true);
    this.api.cancelConductorRecovery(user.id, started.recoveryId)
      .pipe(finalize(() => this.recoverySaving.set(false)))
      .subscribe({
        next: (status) => {
          this.recoveryStatus.set(status);
          this.pendingRecoveryCancel.set(false);
          this.recoveryStarted.set(null);
        },
        error: (error: unknown) => {
          this.recoveryError.set(this.apiErrors.toUiError(
            error,
            'No se pudo cancelar la recuperación. Actualice el estado antes de reintentar.',
          ));
          this.pendingRecoveryCancel.set(false);
        },
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

  roleLabel(role: string): string {
    return ({ ADMIN: 'Admin', CONDUCTOR: 'Conductor' } as Record<string, string>)[role] ?? role;
  }

  private resetForm(close = true): void {
    this.editing.set(null);
    this.form.controls.password.enable();
    this.form.reset({ email: '', password: '', fullName: '', phone: '', role: 'CONDUCTOR' });
    if (close) {
      this.formOpen.set(false);
    }
  }
}
