import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';

import { ApiErrorService } from '../../core/api/api-error.service';
import { UiError } from '../../core/api/api-error.model';
import { AuthSessionService } from '../../core/auth/auth-session.service';
import { ManagementApiService } from '../../core/management/management-api.service';
import { ManagedUser, UserCreatePayload, UserUpdatePayload } from '../../core/management/management.models';
import { ConfirmDialogComponent } from '../../shared/ui/confirm-dialog.component';
import { ModalComponent } from '../../shared/ui/modal.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { PaginationComponent } from '../../shared/ui/pagination.component';

type OrganizationUserRole = 'ADMIN' | 'CONDUCTOR';

@Component({
  selector: 'rf-users-page',
  imports: [ReactiveFormsModule, PageHeaderComponent, ModalComponent, ConfirmDialogComponent, PaginationComponent],
  templateUrl: './users.page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPage {
  private readonly api = inject(ManagementApiService);
  private readonly apiErrors = inject(ApiErrorService);
  readonly session = inject(AuthSessionService);

  readonly roles: readonly OrganizationUserRole[] = ['ADMIN', 'CONDUCTOR'];
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
    this.notice.set(null);
    this.error.set(null);
    this.resetForm(false);
    this.formOpen.set(true);
  }

  startEdit(user: ManagedUser): void {
    this.notice.set(null);
    this.error.set(null);
    this.editing.set(user);
    this.form.reset({
      email: user.email,
      password: '',
      fullName: user.fullName,
      phone: user.phone ?? '',
      role: user.role as OrganizationUserRole,
    });
    this.form.controls.password.disable();
    this.formOpen.set(true);
  }

  closeForm(): void {
    if (!this.saving()) {
      this.resetForm();
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
    const value = this.form.getRawValue();
    this.saving.set(true);
    const request = current === null
      ? this.api.createUser({
          email: value.email.trim(),
          password: value.password,
          fullName: value.fullName.trim(),
          phone: value.phone.trim() || undefined,
          role: value.role,
        } satisfies UserCreatePayload)
      : this.api.updateUser(current.id, {
          email: value.email.trim(),
          fullName: value.fullName.trim(),
          phone: value.phone.trim() || undefined,
          role: value.role,
        } satisfies UserUpdatePayload);

    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.notice.set(current === null ? 'Usuario creado correctamente.' : 'Usuario actualizado correctamente.');
        this.resetForm();
        this.load(0);
      },
      error: (error: unknown) =>
        this.error.set(this.apiErrors.toUiError(error, 'No se pudo guardar el usuario.')),
    });
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
